package org.trustweave.signatures.xades

import org.trustweave.signatures.revocation.CertificateRevocationEvaluator
import org.trustweave.signatures.revocation.RevocationEvidence
import org.w3c.dom.Element
import java.util.Base64

/**
 * Reads the revocation evidence a XAdES signature carries in its (unsigned) `<xades:RevocationValues>`.
 * With `coveredBy` (an archive time-stamp), only values preceding it count.
 */
internal object XadesRevocationValues {
    private const val XADES_NS = "http://uri.etsi.org/01903/v1.3.2#"

    /** Evidence in `<xades:UnsignedProperties>/<xades:UnsignedSignatureProperties>/<xades:RevocationValues>`. */
    fun embedded(
        qualifyingProperties: Element,
        coveredBy: Element? = null,
    ): RevocationEvidence {
        val crls = mutableListOf<ByteArray>()
        val ocsp = mutableListOf<ByteArray>()
        val values =
            if (coveredBy == null) {
                children(qualifyingProperties, "UnsignedProperties")
                    .flatMap { children(it, "UnsignedSignatureProperties") }
                    .flatMap { children(it, "RevocationValues") }
            } else {
                // Only values that precede [coveredBy] (an archive time-stamp) are inside its imprint.
                XadesArchiveTimestamps
                    .unsignedProperties(qualifyingProperties)
                    .takeWhile { it !== coveredBy }
                    .filter { it.namespaceURI == XADES_NS && it.localName == "RevocationValues" }
            }
        for (rv in values) {
            children(rv, "CRLValues").flatMap { children(it, "EncapsulatedCRLValue") }.forEach { decode(it)?.let(crls::add) }
            children(rv, "OCSPValues").flatMap { children(it, "EncapsulatedOCSPValue") }.forEach { decode(it)?.let(ocsp::add) }
        }
        return RevocationEvidence(
            crls.take(CertificateRevocationEvaluator.MAX_ITEMS),
            ocsp.take(CertificateRevocationEvaluator.MAX_ITEMS),
        )
    }

    private fun children(
        parent: Element,
        localName: String,
    ): List<Element> {
        val out = mutableListOf<Element>()
        val nodes = parent.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node is Element && node.namespaceURI == XADES_NS && node.localName == localName) out += node
        }
        return out
    }

    private fun decode(element: Element): ByteArray? =
        try {
            Base64.getMimeDecoder().decode(element.textContent.trim()).takeIf { it.size <= CertificateRevocationEvaluator.MAX_ITEM_BYTES }
        } catch (_: IllegalArgumentException) {
            null
        }
}
