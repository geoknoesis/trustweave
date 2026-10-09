package org.trustweave.signatures.trustlists

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import java.security.Key
import java.security.cert.CertPathBuilder
import java.security.cert.CertStore
import java.security.cert.CollectionCertStoreParameters
import java.security.cert.PKIXBuilderParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509CertSelector
import java.security.cert.X509Certificate
import java.util.Date
import javax.xml.XMLConstants
import javax.xml.crypto.AlgorithmMethod
import javax.xml.crypto.KeySelector
import javax.xml.crypto.KeySelectorException
import javax.xml.crypto.KeySelectorResult
import javax.xml.crypto.XMLCryptoContext
import javax.xml.crypto.dsig.Transform
import javax.xml.crypto.dsig.XMLSignature
import javax.xml.crypto.dsig.XMLSignatureFactory
import javax.xml.crypto.dsig.dom.DOMValidateContext
import javax.xml.crypto.dsig.keyinfo.KeyInfo
import javax.xml.crypto.dsig.keyinfo.X509Data
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

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
 * 3. Extract the signing certificate from `ds:KeyInfo/ds:X509Data` and verify it is valid at
 *    validation time and is either pinned or chains (using the other `ds:X509Data` certificates as
 *    intermediates) to one of the supplied trust anchors (typically the OJ-published LoTL signing
 *    certificate).
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
        data class SignatureCryptoFailed(
            val reason: String,
        ) : Invalid()

        /** Signature is cryptographically valid but the signer cert does not chain to any trust anchor. */
        data class UntrustedSigner(
            val cert: X509Certificate,
        ) : Invalid()

        /** XML structurally unusable: not well-formed, no KeyInfo, no X509Data, etc. */
        data class Malformed(
            val reason: String,
        ) : Invalid()

        /**
         * The signer certificate is not valid at validation time (expired or not yet valid), or the
         * signature claims a future signing time: rotate the pinned certificate or fix the clock. This is
         * a date check; the key-usage check is cades/jades `SignerCertificateInvalid`, not this.
         */
        data class SignerCertificateNotValid(
            val cert: X509Certificate,
            val reason: String,
        ) : Invalid()

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
 *
 * What the signature must cover (anti signature-wrapping): the document contains exactly one
 * `ds:Signature`, a direct child of the document element; it has exactly one reference with
 * `URI=""` (the whole document) carrying the enveloped-signature transform, plus at most one
 * further reference to a `xades:SignedProperties` element inside that signature; no `Id` / `ID` /
 * `id` value occurs twice. Secure validation stays on.
 *
 * Certificate validity is evaluated at validation time ([clock]), never at the signer-asserted
 * `xades:SigningTime`, which a holder of a compromised or expired key could backdate. The signing
 * time is returned for information only.
 *
 * @param clock source of the validation time; inject a fixed clock in tests.
 */
class DefaultLotlSignatureVerifier
    @JvmOverloads
    constructor(
        private val clock: Clock = Clock.System,
    ) : LotlSignatureVerifier {
        private val delegate = EnvelopedTrustListSignatureValidator(clock)

        override fun verify(
            lotlXml: ByteArray,
            trustedSigningCerts: List<X509Certificate>,
        ): LotlSignatureValidationResult = delegate.verify(lotlXml, trustedSigningCerts)
    }

/**
 * Verifies the enveloped signature on a Member-State Trusted Service List (TSL).
 *
 * Same contract and result type as [LotlSignatureVerifier]; the trusted signing certificates for a
 * Member State are the ones its LoTL pointer publishes (see [TslPointer.signingCertificates]).
 */
interface TslSignatureVerifier {
    fun verify(
        tslXml: ByteArray,
        trustedSigningCerts: List<X509Certificate>,
    ): LotlSignatureValidationResult
}

/** Default [TslSignatureVerifier]; shares all checks with [DefaultLotlSignatureVerifier]. */
class DefaultTslSignatureVerifier
    @JvmOverloads
    constructor(
        private val clock: Clock = Clock.System,
    ) : TslSignatureVerifier {
        private val delegate = EnvelopedTrustListSignatureValidator(clock)

        override fun verify(
            tslXml: ByteArray,
            trustedSigningCerts: List<X509Certificate>,
        ): LotlSignatureValidationResult = delegate.verify(tslXml, trustedSigningCerts)
    }

/** Shared implementation behind the LoTL and TSL verifiers. */
internal class EnvelopedTrustListSignatureValidator(
    private val clock: Clock,
) {
    fun verify(
        xml: ByteArray,
        trustedSigningCerts: List<X509Certificate>,
    ): LotlSignatureValidationResult {
        val now = clock.now()
        val doc =
            try {
                parseDocument(xml)
            } catch (t: Throwable) {
                return LotlSignatureValidationResult.Invalid.Malformed(
                    "trusted-list XML is not well-formed: ${t.message}",
                )
            }

        val signatures = doc.getElementsByTagNameNS(XMLDSIG_NS, "Signature")
        if (signatures.length == 0) return LotlSignatureValidationResult.Invalid.MissingSignature
        if (signatures.length > 1) {
            return LotlSignatureValidationResult.Invalid.Malformed(
                "document contains ${signatures.length} ds:Signature elements; exactly one is supported",
            )
        }
        val signatureElem = signatures.item(0) as Element
        if (signatureElem.parentNode !== doc.documentElement) {
            return LotlSignatureValidationResult.Invalid.Malformed(
                "ds:Signature must be a direct child of the document element (enveloped signature)",
            )
        }

        findDuplicateId(doc.documentElement)?.let { dup ->
            return LotlSignatureValidationResult.Invalid.Malformed(
                "duplicate Id attribute value '$dup' in document",
            )
        }

        val keySelector = X509KeyInfoKeySelector()
        val context =
            DOMValidateContext(keySelector, signatureElem).apply {
                // ds:Reference URIs use ID attributes; registering is safe because duplicates were
                // rejected above, so an Id can only resolve to one element.
                registerIdAttributes(doc.documentElement)
                setProperty("org.jcp.xml.dsig.secureValidation", true)
            }

        val factory = XMLSignatureFactory.getInstance("DOM")
        val xmlSignature: XMLSignature =
            try {
                factory.unmarshalXMLSignature(context)
            } catch (t: Throwable) {
                return LotlSignatureValidationResult.Invalid.Malformed(
                    "ds:Signature could not be unmarshalled: ${t.message}",
                )
            }

        checkReferenceLayout(xmlSignature, doc, signatureElem)?.let { problem ->
            return LotlSignatureValidationResult.Invalid.Malformed(problem)
        }

        val cryptoValid =
            try {
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

        val signerCert =
            keySelector.signerCert
                ?: return LotlSignatureValidationResult.Invalid.Malformed(
                    "ds:KeyInfo did not yield an X509Certificate",
                )

        try {
            signerCert.checkValidity(Date(now.toEpochMilliseconds()))
        } catch (e: java.security.cert.CertificateException) {
            return LotlSignatureValidationResult.Invalid.SignerCertificateNotValid(
                signerCert,
                "signer certificate is not valid at $now: ${e.message}",
            )
        }

        if (!chainsToTrustAnchor(signerCert, keySelector.otherCerts, trustedSigningCerts, now)) {
            return LotlSignatureValidationResult.Invalid.UntrustedSigner(signerCert)
        }

        val signingTime = extractSigningTime(signatureElem)
        if (signingTime != null && signingTime > now + MAX_CLOCK_SKEW) {
            return LotlSignatureValidationResult.Invalid.SignerCertificateNotValid(
                signerCert,
                "xades:SigningTime $signingTime lies in the future (validation time $now)",
            )
        }
        return LotlSignatureValidationResult.Valid(signerCert, signingTime)
    }

    // ---------------------------------------------------------------- XML parsing

    private fun parseDocument(bytes: ByteArray): Document {
        val factory =
            DocumentBuilderFactory.newInstance().apply {
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
        walkElements(root) { el ->
            for (name in ID_ATTRIBUTE_NAMES) {
                val attr = el.getAttributeNode(name) ?: continue
                if (!attr.isId) {
                    el.setIdAttributeNode(attr, true)
                }
            }
        }
    }

    /** First Id / ID / id value that occurs more than once in the document, or null. */
    private fun findDuplicateId(root: Element): String? {
        val seen = HashSet<String>()
        var duplicate: String? = null
        walkElements(root) { el ->
            if (duplicate != null) return@walkElements
            val attrs = el.attributes
            for (i in 0 until attrs.length) {
                val attr = attrs.item(i)
                val name = attr.localName ?: attr.nodeName
                if (name in ID_ATTRIBUTE_NAMES && !seen.add(attr.nodeValue)) {
                    duplicate = attr.nodeValue
                    return@walkElements
                }
            }
        }
        return duplicate
    }

    private fun walkElements(
        node: Node,
        action: (Element) -> Unit,
    ) {
        if (node is Element) action(node)
        val children = node.childNodes
        for (i in 0 until children.length) {
            walkElements(children.item(i), action)
        }
    }

    private fun findFirstByNs(
        parent: Node?,
        ns: String,
        localName: String,
    ): Element? {
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

    // ---------------------------------------------------------------- reference layout

    /**
     * Returns a problem description, or null when the references bind the signature to the whole
     * document: exactly one `URI=""` reference with the enveloped transform (and only
     * canonicalization transforms besides), and at most one other reference, which must point at
     * a `xades:SignedProperties` element inside this very signature.
     */
    private fun checkReferenceLayout(
        signature: XMLSignature,
        doc: Document,
        signatureElem: Element,
    ): String? {
        val refs = signature.signedInfo.references
        val whole = refs.filter { it.uri == "" }
        if (whole.size != 1) {
            return "signature must contain exactly one whole-document reference (URI=\"\"), found ${whole.size}"
        }
        val transforms = whole.single().transforms.map { it.algorithm }
        if (Transform.ENVELOPED !in transforms) {
            return "the whole-document reference lacks the enveloped-signature transform"
        }
        transforms.firstOrNull { it != Transform.ENVELOPED && it !in CANONICALIZATION_ALGORITHMS }?.let {
            return "unsupported transform '$it' on the whole-document reference"
        }
        val others = refs.filter { it.uri != "" }
        if (others.size > 1) {
            return "signature has ${others.size} additional references; at most one (SignedProperties) is allowed"
        }
        others.singleOrNull()?.let { ref ->
            val uri = ref.uri.orEmpty()
            if (!uri.startsWith("#") || uri.length < 2) {
                return "additional reference '$uri' is not a same-document fragment"
            }
            val target =
                doc.getElementById(uri.substring(1))
                    ?: return "additional reference '$uri' does not resolve to an element"
            if (target.localName != "SignedProperties" || target.namespaceURI != XADES_NS) {
                return "additional reference '$uri' does not point at xades:SignedProperties"
            }
            if (!isDescendantOf(target, signatureElem)) {
                return "xades:SignedProperties referenced by '$uri' is outside the signature"
            }
        }
        return null
    }

    private fun isDescendantOf(
        node: Node,
        ancestor: Node,
    ): Boolean {
        var cur: Node? = node.parentNode
        while (cur != null) {
            if (cur === ancestor) return true
            cur = cur.parentNode
        }
        return false
    }

    // ---------------------------------------------------------------- XAdES SigningTime

    /**
     * Locate `xades:SigningTime` under `xades:SignedSignatureProperties` and parse it as an
     * ISO-8601 instant. Returns null if absent or unparseable — production LoTL XML always
     * carries it, but the minimal test fixture intentionally does not.
     */
    private fun extractSigningTime(signatureElem: Element): Instant? {
        val signedSigProps =
            findFirstByNs(
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
        val sigValueOk =
            runCatching { signature.signatureValue.validate(context) }
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

    /**
     * The signer is trusted when it equals a pinned certificate (validity already checked by the
     * caller) or when a PKIX path can be built from it, through the intermediates carried in
     * `ds:KeyInfo`, to one of the supplied certificates acting as a trust anchor. Anchors that are
     * not valid at [now] are ignored. Revocation checking is out of scope here.
     */
    private fun chainsToTrustAnchor(
        signerCert: X509Certificate,
        keyInfoCerts: List<X509Certificate>,
        trustedSigningCerts: List<X509Certificate>,
        now: Instant,
    ): Boolean {
        if (trustedSigningCerts.isEmpty()) return false
        val at = Date(now.toEpochMilliseconds())

        val validTrusted =
            trustedSigningCerts.filter {
                runCatching { it.checkValidity(at) }.isSuccess
            }
        if (validTrusted.isEmpty()) return false

        // Direct cert pinning: trust list contains the exact end-entity signer cert.
        if (validTrusted.any { it == signerCert }) return true

        return try {
            val anchors = validTrusted.map { TrustAnchor(it, null) }.toSet()
            val params =
                PKIXBuilderParameters(
                    anchors,
                    X509CertSelector().apply { certificate = signerCert },
                ).apply {
                    isRevocationEnabled = false // online revocation check is out of scope here
                    date = at
                    addCertStore(
                        CertStore.getInstance("Collection", CollectionCertStoreParameters(keyInfoCerts)),
                    )
                }
            CertPathBuilder.getInstance("PKIX").build(params)
            true
        } catch (_: Throwable) {
            false
        }
    }

    // ---------------------------------------------------------------- KeySelector

    /**
     * Collects every `X509Certificate` in `ds:KeyInfo/ds:X509Data`. The signer is the certificate
     * that issued none of the others (the leaf); the rest are offered as PKIX intermediates. The
     * signer's public key verifies the signature.
     */
    private class X509KeyInfoKeySelector : KeySelector() {
        var signerCert: X509Certificate? = null
            private set
        var otherCerts: List<X509Certificate> = emptyList()
            private set

        override fun select(
            keyInfo: KeyInfo?,
            purpose: Purpose?,
            method: AlgorithmMethod?,
            context: XMLCryptoContext?,
        ): KeySelectorResult {
            if (keyInfo == null) throw KeySelectorException("ds:KeyInfo is missing")
            val certs =
                keyInfo.content
                    .filterIsInstance<X509Data>()
                    .flatMap { it.content }
                    .filterIsInstance<X509Certificate>()
            if (certs.isEmpty()) {
                throw KeySelectorException("ds:KeyInfo/ds:X509Data did not contain an X509Certificate")
            }
            val leaf =
                certs.firstOrNull { c ->
                    certs.none { other -> other != c && other.issuerX500Principal == c.subjectX500Principal }
                } ?: certs.first()
            signerCert = leaf
            otherCerts = certs.filter { it != leaf }
            val key: Key = leaf.publicKey
            return KeySelectorResult { key }
        }
    }

    private companion object {
        const val XMLDSIG_NS = "http://www.w3.org/2000/09/xmldsig#"
        const val XADES_NS = "http://uri.etsi.org/01903/v1.3.2#"
        val ID_ATTRIBUTE_NAMES = listOf("Id", "ID", "id")
        val MAX_CLOCK_SKEW = 5.minutes
        val CANONICALIZATION_ALGORITHMS =
            setOf(
                "http://www.w3.org/TR/2001/REC-xml-c14n-20010315",
                "http://www.w3.org/TR/2001/REC-xml-c14n-20010315#WithComments",
                "http://www.w3.org/2001/10/xml-exc-c14n#",
                "http://www.w3.org/2001/10/xml-exc-c14n#WithComments",
                "http://www.w3.org/2006/12/xml-c14n11",
                "http://www.w3.org/2006/12/xml-c14n11#WithComments",
            )
    }
}
