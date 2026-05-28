package org.trustweave.signatures.trustlists

import kotlinx.datetime.Instant
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import java.security.Key
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.util.Base64
import javax.xml.XMLConstants
import javax.xml.crypto.AlgorithmMethod
import javax.xml.crypto.KeySelector
import javax.xml.crypto.KeySelectorException
import javax.xml.crypto.KeySelectorResult
import javax.xml.crypto.XMLCryptoContext
import javax.xml.crypto.dsig.XMLSignature
import javax.xml.crypto.dsig.XMLSignatureFactory
import javax.xml.crypto.dsig.dom.DOMValidateContext
import javax.xml.crypto.dsig.keyinfo.KeyInfo
import javax.xml.crypto.dsig.keyinfo.X509Data
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Self-validates the enveloped XAdES signature on the EU LoTL XML.
 *
 * Allows callers to drop the "LoTL bytes are pre-verified" assumption baked into
 * [EtsiTrustListParser]. Verify a LoTL with this API first, and only feed [TrustListParser.parse]
 * the bytes once you have a [LotlSignatureValidationResult.Valid].
 *
 * Implementations must:
 * 1. Locate the enveloped `ds:Signature` element in the LoTL XML.
 * 2. Cryptographically verify the signature value AND every `ds:Reference` digest.
 * 3. Extract the signing certificate from `ds:KeyInfo/ds:X509Data` and verify it chains to one of
 *    the supplied trust anchors (typically the OJ-published LoTL signing certificate).
 *
 * XAdES-BES extensions (notably `xades:SigningTime`) are surfaced when present but are optional —
 * production LoTL XML carries them, the minimal fixture used in tests does not.
 */
interface LotlSignatureVerifier {
    fun verify(
        lotlXml: ByteArray,
        trustedSigningCerts: List<X509Certificate>,
    ): LotlSignatureValidationResult
}

/**
 * Outcome of [LotlSignatureVerifier.verify].
 *
 * Distinct subtypes are exposed for each failure mode so callers can react differently — e.g.
 * an [Invalid.UntrustedSigner] is operationally fixable (rotate the pinned cert), whereas
 * [Invalid.SignatureCryptoFailed] indicates tampering or corruption.
 */
sealed class LotlSignatureValidationResult {

    data class Valid(
        val signerCert: X509Certificate,
        val signingTime: Instant?,
    ) : LotlSignatureValidationResult()

    sealed class Invalid : LotlSignatureValidationResult() {
        /** Cryptographic verification of the signature value or a reference digest failed. */
        data class SignatureCryptoFailed(val reason: String) : Invalid()

        /** Signature is cryptographically valid but the signer cert does not chain to any trust anchor. */
        data class UntrustedSigner(val cert: X509Certificate) : Invalid()

        /** XML structurally unusable: not well-formed, no KeyInfo, no X509Data, etc. */
        data class Malformed(val reason: String) : Invalid()

        /** No `ds:Signature` element present in the LoTL document. */
        object MissingSignature : Invalid()
    }
}

/**
 * Default implementation backed by the JDK's `javax.xml.crypto.dsig` API.
 *
 * No third-party XAdES library is required: the XAdES-BES properties (`SigningTime`,
 * `SignedSignatureProperties`, etc.) are wrapped in a `xades:Object` that the JDK already
 * verifies as part of the enveloped signature's references — we only need to peek inside
 * for the optional `SigningTime` field.
 */
class DefaultLotlSignatureVerifier : LotlSignatureVerifier {

    override fun verify(
        lotlXml: ByteArray,
        trustedSigningCerts: List<X509Certificate>,
    ): LotlSignatureValidationResult {
        val doc = try {
            parseDocument(lotlXml)
        } catch (t: Throwable) {
            return LotlSignatureValidationResult.Invalid.Malformed(
                "LoTL XML is not well-formed: ${t.message}",
            )
        }

        val signatureElem = findFirstByNs(doc.documentElement, XMLDSIG_NS, "Signature")
            ?: return LotlSignatureValidationResult.Invalid.MissingSignature

        val keySelector = X509KeyInfoKeySelector()
        val context = DOMValidateContext(keySelector, signatureElem).apply {
            // ds:Reference URIs in enveloped signatures use ID attributes; the spec-compliant way
            // to wire them up is to mark every xml:id-style attribute as an ID on the DOM.
            registerIdAttributes(doc.documentElement)
            setProperty("org.jcp.xml.dsig.secureValidation", true)
        }

        val factory = XMLSignatureFactory.getInstance("DOM")
        val xmlSignature: XMLSignature = try {
            factory.unmarshalXMLSignature(context)
        } catch (t: Throwable) {
            return LotlSignatureValidationResult.Invalid.Malformed(
                "ds:Signature could not be unmarshalled: ${t.message}",
            )
        }

        val cryptoValid = try {
            xmlSignature.validate(context)
        } catch (t: Throwable) {
            return LotlSignatureValidationResult.Invalid.SignatureCryptoFailed(
                "XMLSignature.validate threw: ${t.message}",
            )
        }

        if (!cryptoValid) {
            val reason = buildCryptoFailureReason(xmlSignature, context)
            return LotlSignatureValidationResult.Invalid.SignatureCryptoFailed(reason)
        }

        val signerCert = keySelector.lastSelectedCert
            ?: return LotlSignatureValidationResult.Invalid.Malformed(
                "ds:KeyInfo did not yield an X509Certificate",
            )

        if (!chainsToTrustAnchor(signerCert, trustedSigningCerts)) {
            return LotlSignatureValidationResult.Invalid.UntrustedSigner(signerCert)
        }

        val signingTime = extractSigningTime(signatureElem)
        return LotlSignatureValidationResult.Valid(signerCert, signingTime)
    }

    // ---------------------------------------------------------------- XML parsing

    private fun parseDocument(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // XXE hardening per OWASP cheat sheet — same flags as EtsiTrustListParser.
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    /**
     * Register every `Id` / `id` / `ID` attribute in the document as a DOM ID. The JDK's
     * `DOMValidateContext.setIdAttributeNS` requires the resolver to know which attributes are IDs
     * to satisfy `ds:Reference URI="#…"` lookups; without this, valid signatures fail to validate.
     */
    private fun registerIdAttributes(root: Element) {
        val candidates = listOf("Id", "ID", "id")
        walkElements(root) { el ->
            for (name in candidates) {
                val attr = el.getAttributeNode(name) ?: continue
                if (!attr.isId) {
                    el.setIdAttributeNode(attr, true)
                }
            }
        }
    }

    private fun walkElements(node: Node, action: (Element) -> Unit) {
        if (node is Element) action(node)
        val children = node.childNodes
        for (i in 0 until children.length) {
            walkElements(children.item(i), action)
        }
    }

    private fun findFirstByNs(parent: Node?, ns: String, localName: String): Element? {
        if (parent == null) return null
        val children: NodeList = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                if (localName == child.localName && ns == child.namespaceURI) return child
                val nested = findFirstByNs(child, ns, localName)
                if (nested != null) return nested
            }
        }
        return null
    }

    // ---------------------------------------------------------------- XAdES SigningTime

    /**
     * Locate `xades:SigningTime` under `xades:SignedSignatureProperties` and parse it as an
     * ISO-8601 instant. Returns null if absent or unparseable — production LoTL XML always
     * carries it, but the minimal test fixture intentionally does not.
     */
    private fun extractSigningTime(signatureElem: Element): Instant? {
        val signedSigProps = findFirstByNs(
            signatureElem,
            XADES_NS,
            "SignedSignatureProperties",
        ) ?: return null
        val signingTimeElem = findFirstByNs(signedSigProps, XADES_NS, "SigningTime") ?: return null
        val text = signingTimeElem.textContent?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { Instant.parse(text) }.getOrNull()
    }

    // ---------------------------------------------------------------- diagnostics

    private fun buildCryptoFailureReason(
        signature: XMLSignature,
        context: DOMValidateContext,
    ): String {
        val parts = mutableListOf<String>()
        val sigValueOk = runCatching { signature.signatureValue.validate(context) }
            .getOrDefault(false)
        if (!sigValueOk) parts += "SignatureValue did not verify"

        val refs = signature.signedInfo.references
        for ((index, ref) in refs.withIndex()) {
            val ok = runCatching { ref.validate(context) }.getOrDefault(false)
            if (!ok) parts += "Reference[$index] (URI=${ref.uri}) digest mismatch"
        }
        return parts.joinToString("; ").ifEmpty { "validation failed" }
    }

    // ---------------------------------------------------------------- trust anchor

    private fun chainsToTrustAnchor(
        signerCert: X509Certificate,
        trustedSigningCerts: List<X509Certificate>,
    ): Boolean {
        if (trustedSigningCerts.isEmpty()) return false

        // Direct cert pinning: trust list contains the exact end-entity signer cert.
        if (trustedSigningCerts.any { it == signerCert }) return true

        // Otherwise build a PKIX path with the supplied certs treated as trust anchors and
        // attempt validation. CertPathValidator returns successfully when a path exists.
        val anchors = trustedSigningCerts.map { TrustAnchor(it, null) }.toSet()
        return try {
            val cf = CertificateFactory.getInstance("X.509")
            val path = cf.generateCertPath(listOf(signerCert))
            val params = PKIXParameters(anchors).apply {
                isRevocationEnabled = false // online revocation check is out of scope here
            }
            CertPathValidator.getInstance("PKIX").validate(path, params)
            true
        } catch (_: Throwable) {
            false
        }
    }

    // ---------------------------------------------------------------- KeySelector

    /**
     * Pulls the first `X509Certificate` out of `ds:KeyInfo/ds:X509Data` and uses its public key
     * for signature verification. Records the cert so the caller can subsequently anchor-check it.
     */
    private class X509KeyInfoKeySelector : KeySelector() {
        var lastSelectedCert: X509Certificate? = null
            private set

        override fun select(
            keyInfo: KeyInfo?,
            purpose: Purpose?,
            method: AlgorithmMethod?,
            context: XMLCryptoContext?,
        ): KeySelectorResult {
            if (keyInfo == null) throw KeySelectorException("ds:KeyInfo is missing")
            for (info in keyInfo.content) {
                if (info !is X509Data) continue
                for (entry in info.content) {
                    if (entry is X509Certificate) {
                        lastSelectedCert = entry
                        val key: Key = entry.publicKey
                        return KeySelectorResult { key }
                    }
                }
            }
            throw KeySelectorException("ds:KeyInfo/ds:X509Data did not contain an X509Certificate")
        }
    }

    companion object {
        private const val XMLDSIG_NS = "http://www.w3.org/2000/09/xmldsig#"
        private const val XADES_NS = "http://uri.etsi.org/01903/v1.3.2#"
    }
}
