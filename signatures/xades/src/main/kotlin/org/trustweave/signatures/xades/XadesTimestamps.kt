package org.trustweave.signatures.xades

import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.tsp.TimeStampToken
import org.w3c.dom.Element
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Base64
import javax.xml.crypto.dsig.CanonicalizationMethod
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

/**
 * Verification of the XAdES `SignatureTimeStamp` unsigned property (ETSI EN 319 132-1 §5.5.2):
 * an RFC 3161 token whose message imprint is the digest of the canonicalised `<ds:SignatureValue>`.
 */
internal object XadesTimestamps {
    private const val DS_NS = "http://www.w3.org/2000/09/xmldsig#"
    private const val XADES_NS = "http://uri.etsi.org/01903/v1.3.2#"

    sealed class Outcome {
        /** No `SignatureTimeStamp` element at all. */
        data object None : Outcome()

        /** A time-stamp is present but no TSA trust anchor was configured, so it proves nothing. */
        data object NoAnchors : Outcome()

        data class Valid(
            val genTime: Instant,
        ) : Outcome()

        data class Invalid(
            val reason: String,
        ) : Outcome()
    }

    private val digests =
        mapOf(
            "2.16.840.1.101.3.4.2.1" to "SHA-256",
            "2.16.840.1.101.3.4.2.2" to "SHA-384",
            "2.16.840.1.101.3.4.2.3" to "SHA-512",
        )

    private const val XMLNS_NS = "http://www.w3.org/2000/xmlns/"

    /**
     * Canonical bytes of `<ds:SignatureValue>` — the data a signature time-stamp covers.
     *
     * The JDK exposes no public way to canonicalise a sub-tree, and `<ds:SignatureValue>` is a
     * leaf (attributes + text), so Canonical XML 1.0/1.1 is applied here directly: in-scope
     * namespace declarations (inclusive) or just the visibly-used one (exclusive), attributes
     * sorted by namespace URI then local name, and the C14N escaping rules.
     */
    fun imprintInput(
        signatureValue: Element,
        c14nAlgorithm: String = CanonicalizationMethod.INCLUSIVE,
    ): ByteArray {
        val exclusive =
            c14nAlgorithm == CanonicalizationMethod.EXCLUSIVE || c14nAlgorithm == CanonicalizationMethod.EXCLUSIVE_WITH_COMMENTS
        var child = signatureValue.firstChild
        while (child != null) {
            require(child.nodeType == org.w3c.dom.Node.TEXT_NODE || child.nodeType == org.w3c.dom.Node.CDATA_SECTION_NODE) {
                "<ds:SignatureValue> must contain only text"
            }
            child = child.nextSibling
        }
        val inScope = linkedMapOf<String, String>()
        var node: org.w3c.dom.Node? = signatureValue
        while (node is Element) {
            val attrs = node.attributes
            for (i in 0 until attrs.length) {
                val a = attrs.item(i)
                val isDecl = a.namespaceURI == XMLNS_NS || a.nodeName == "xmlns" || a.nodeName.startsWith("xmlns:")
                if (!isDecl) continue
                val prefix = if (a.nodeName == "xmlns") "" else a.nodeName.removePrefix("xmlns:")
                inScope.putIfAbsent(prefix, a.nodeValue)
            }
            node = node.parentNode
        }
        val ownPrefix = signatureValue.prefix ?: ""
        inScope.putIfAbsent(ownPrefix, signatureValue.namespaceURI ?: "")
        val rendered =
            inScope
                .filter { (prefix, uri) -> prefix != "xml" && !(prefix.isEmpty() && uri.isEmpty()) }
                .filter { (prefix, _) -> !exclusive || prefix == ownPrefix }
                .toSortedMap()
        val out = StringBuilder()
        out.append('<').append(signatureValue.tagName)
        for ((prefix, uri) in rendered) {
            out.append(if (prefix.isEmpty()) " xmlns=\"" else " xmlns:$prefix=\"").append(escapeAttribute(uri)).append('"')
        }
        val attrs = signatureValue.attributes
        val plain = mutableListOf<org.w3c.dom.Node>()
        for (i in 0 until attrs.length) {
            val a = attrs.item(i)
            if (a.nodeName == "xmlns" || a.nodeName.startsWith("xmlns:")) continue
            plain += a
        }
        plain.sortedWith(compareBy({ it.namespaceURI ?: "" }, { it.localName ?: it.nodeName })).forEach {
            out
                .append(' ')
                .append(it.nodeName)
                .append("=\"")
                .append(escapeAttribute(it.nodeValue))
                .append('"')
        }
        out
            .append('>')
            .append(escapeText(signatureValue.textContent ?: ""))
            .append("</")
            .append(signatureValue.tagName)
            .append('>')
        return out.toString().toByteArray(Charsets.UTF_8)
    }

    private fun escapeText(v: String): String =
        v
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\r", "&#xD;")

    private fun escapeAttribute(v: String): String =
        v
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace("\"", "&quot;")
            .replace("\t", "&#x9;")
            .replace("\n", "&#xA;")
            .replace("\r", "&#xD;")

    fun evaluate(
        qualifyingProperties: Element,
        signatureElement: Element,
        anchors: List<X509Certificate>,
        c14nAlgorithms: Set<String>,
    ): Outcome {
        val stamps =
            children(qualifyingProperties, "UnsignedProperties")
                .flatMap { children(it, "UnsignedSignatureProperties") }
                .flatMap { children(it, "SignatureTimeStamp") }
        if (stamps.isEmpty()) return Outcome.None
        if (anchors.isEmpty()) return Outcome.NoAnchors
        val signatureValue =
            children(signatureElement, "SignatureValue", DS_NS).singleOrNull()
                ?: return Outcome.Invalid("signature has no single <ds:SignatureValue>")
        var earliest: Instant? = null
        for (stamp in stamps) {
            when (val one = verifyOne(stamp, signatureValue, anchors, c14nAlgorithms)) {
                is Outcome.Valid -> if (earliest == null || one.genTime < earliest) earliest = one.genTime
                is Outcome.Invalid -> return one
                else -> return Outcome.Invalid("unexpected time-stamp state")
            }
        }
        return Outcome.Valid(earliest!!)
    }

    private fun verifyOne(
        stamp: Element,
        signatureValue: Element,
        anchors: List<X509Certificate>,
        c14nAlgorithms: Set<String>,
    ): Outcome {
        val c14n =
            children(stamp, "CanonicalizationMethod", DS_NS).singleOrNull()?.getAttribute("Algorithm")
                ?: CanonicalizationMethod.INCLUSIVE
        if (c14n !in c14nAlgorithms) return Outcome.Invalid("unsupported time-stamp canonicalization '$c14n'")
        val encapsulated = children(stamp, "EncapsulatedTimeStamp")
        if (encapsulated.size != 1) {
            return Outcome.Invalid("SignatureTimeStamp must carry exactly one EncapsulatedTimeStamp (XMLTimeStamp is unsupported)")
        }
        val tokenBytes =
            try {
                Base64.getMimeDecoder().decode(encapsulated.single().textContent.trim())
            } catch (_: IllegalArgumentException) {
                return Outcome.Invalid("EncapsulatedTimeStamp is not base64")
            }
        val token =
            try {
                TimeStampToken(CMSSignedData(tokenBytes))
            } catch (t: Exception) {
                return Outcome.Invalid("EncapsulatedTimeStamp is not an RFC 3161 token: ${t.message}")
            }
        val info = token.timeStampInfo
        val jca =
            digests[info.messageImprintAlgOID.id]
                ?: return Outcome.Invalid("unsupported time-stamp digest ${info.messageImprintAlgOID.id}")
        val expected =
            try {
                MessageDigest.getInstance(jca).digest(imprintInput(signatureValue, c14n))
            } catch (t: Exception) {
                return Outcome.Invalid("could not canonicalise <ds:SignatureValue>: ${t.message}")
            }
        if (!MessageDigest.isEqual(expected, info.messageImprintDigest)) {
            return Outcome.Invalid("time-stamp message imprint does not match the signature value")
        }
        @Suppress("UNCHECKED_CAST")
        val holders =
            token.certificates.getMatches(
                token.sid as org.bouncycastle.util.Selector<X509CertificateHolder>,
            ) as Collection<X509CertificateHolder>
        val holder = holders.firstOrNull() ?: return Outcome.Invalid("time-stamp carries no TSA certificate")
        val genTime = info.genTime.toInstant().toKotlinInstant()
        val tsaCert: X509Certificate
        try {
            token.validate(JcaSimpleSignerInfoVerifierBuilder().setProvider(BouncyCastleProvider()).build(holder))
            tsaCert = JcaX509CertificateConverter().getCertificate(holder)
        } catch (t: Exception) {
            return Outcome.Invalid("time-stamp signature is invalid: ${t.message}")
        }
        if (!validAt(tsaCert, info.genTime)) return Outcome.Invalid("TSA certificate was not valid at the time-stamp's genTime")
        val trusted =
            anchors.any { anchor ->
                (anchor == tsaCert || signedBy(tsaCert, anchor)) && validAt(anchor, info.genTime)
            }
        if (!trusted) return Outcome.Invalid("TSA certificate is not one of (or issued by) the configured timestampTrustAnchors")
        return Outcome.Valid(genTime)
    }

    private fun validAt(
        cert: X509Certificate,
        at: java.util.Date,
    ): Boolean =
        try {
            cert.checkValidity(at)
            true
        } catch (_: GeneralSecurityException) {
            false
        }

    private fun signedBy(
        cert: X509Certificate,
        issuer: X509Certificate,
    ): Boolean =
        try {
            cert.verify(issuer.publicKey)
            cert.issuerX500Principal == issuer.subjectX500Principal
        } catch (_: GeneralSecurityException) {
            false
        }

    private fun children(
        parent: Element,
        localName: String,
        ns: String = XADES_NS,
    ): List<Element> {
        val out = mutableListOf<Element>()
        var c = parent.firstChild
        while (c != null) {
            if (c is Element && c.namespaceURI == ns && c.localName == localName) out.add(c)
            c = c.nextSibling
        }
        return out
    }
}
