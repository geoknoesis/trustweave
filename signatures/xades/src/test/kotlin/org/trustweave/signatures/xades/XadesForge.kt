package org.trustweave.signatures.xades

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Base64
import javax.xml.crypto.dom.DOMStructure
import javax.xml.crypto.dsig.CanonicalizationMethod
import javax.xml.crypto.dsig.DigestMethod
import javax.xml.crypto.dsig.Reference
import javax.xml.crypto.dsig.Transform
import javax.xml.crypto.dsig.XMLSignatureFactory
import javax.xml.crypto.dsig.dom.DOMSignContext
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec
import javax.xml.crypto.dsig.spec.TransformParameterSpec
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Test-only XAdES producer with knobs for building malformed / attacker-shaped signatures that
 * [DefaultXadesSigner] would never emit.
 */
internal object XadesForge {
    const val XADES = "http://uri.etsi.org/01903/v1.3.2#"
    const val DS = "http://www.w3.org/2000/09/xmldsig#"
    const val SIG_ID = "sig-1"
    const val SP_ID = "sig-1-signedprops"

    fun sampleDocument(): Document {
        val xml = """<?xml version="1.0" encoding="UTF-8"?><Invoice><Total Id="total">42.00</Total></Invoice>"""
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder().parse(xml.byteInputStream(Charsets.UTF_8))
    }

    /**
     * @param signedPropertiesUri  URI of the SignedProperties reference; `null` omits it.
     * @param coverWholeDocument   Whether to include the `URI=""` + enveloped reference.
     * @param extraReferenceUris   Additional plain references (e.g. `#total`) to add.
     * @param certDigestOf         Certificate whose SHA-256 goes into SigningCertificateV2;
     *                             `null` omits the SigningCertificateV2 property.
     * @param signingTimeText      Text of SigningTime; `null` omits it.
     * @param beforeSign           Hook to mutate the document (e.g. add a forged element) before
     *                             the signature is computed.
     */
    fun sign(
        doc: Document,
        privateKey: PrivateKey,
        keyInfoCerts: List<X509Certificate>,
        certDigestOf: X509Certificate?,
        signingTimeText: String? =
            java.time.Instant
                .now()
                .toString(),
        signedPropertiesUri: String? = "#$SP_ID",
        coverWholeDocument: Boolean = true,
        extraReferenceUris: List<String> = emptyList(),
        beforeSign: (Document) -> Unit = {},
    ): Document {
        val factory = XMLSignatureFactory.getInstance("DOM")
        val kif = factory.keyInfoFactory
        val qp = doc.createElementNS(XADES, "xades:QualifyingProperties")
        qp.setAttribute("Target", "#$SIG_ID")
        val sp = doc.createElementNS(XADES, "xades:SignedProperties")
        sp.setAttribute("Id", SP_ID)
        qp.appendChild(sp)
        val ssp = doc.createElementNS(XADES, "xades:SignedSignatureProperties")
        sp.appendChild(ssp)
        if (signingTimeText != null) {
            ssp.appendChild(doc.createElementNS(XADES, "xades:SigningTime").apply { textContent = signingTimeText })
        }
        if (certDigestOf != null) {
            ssp.appendChild(signingCertificateV2(doc, certDigestOf))
        }
        beforeSign(doc)

        val sha256 = factory.newDigestMethod(DigestMethod.SHA256, null)
        val refs = mutableListOf<Reference>()
        if (coverWholeDocument) {
            refs +=
                factory.newReference(
                    "",
                    sha256,
                    listOf(factory.newTransform(Transform.ENVELOPED, null as TransformParameterSpec?)),
                    null,
                    null,
                )
        }
        extraReferenceUris.forEach { refs += factory.newReference(it, sha256) }
        if (signedPropertiesUri != null) {
            refs +=
                factory.newReference(
                    signedPropertiesUri,
                    sha256,
                    null,
                    "http://uri.etsi.org/01903#SignedProperties",
                    null,
                )
        }
        val sigMethod =
            if (privateKey.algorithm == "EC") {
                "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256"
            } else {
                "http://www.w3.org/2001/04/xmldsig-more#rsa-sha256"
            }
        val signedInfo =
            factory.newSignedInfo(
                factory.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, null as C14NMethodParameterSpec?),
                factory.newSignatureMethod(sigMethod, null),
                refs,
            )
        val keyInfo = kif.newKeyInfo(listOf(kif.newX509Data(keyInfoCerts)))
        val obj = factory.newXMLObject(listOf(DOMStructure(qp)), null, null, null)
        val sig = factory.newXMLSignature(signedInfo, keyInfo, listOf(obj), SIG_ID, null)
        val ctx = DOMSignContext(privateKey, doc.documentElement)
        ctx.setIdAttributeNS(sp, null, "Id")
        markIds(doc.documentElement, ctx)
        sig.sign(ctx)
        return doc
    }

    fun signingCertificateV2(
        doc: Document,
        cert: X509Certificate,
    ): Element {
        val v2 = doc.createElementNS(XADES, "xades:SigningCertificateV2")
        val c = doc.createElementNS(XADES, "xades:Cert")
        val cd = doc.createElementNS(XADES, "xades:CertDigest")
        cd.appendChild(
            doc.createElementNS(DS, "ds:DigestMethod").apply {
                setAttribute("Algorithm", "http://www.w3.org/2001/04/xmlenc#sha256")
            },
        )
        cd.appendChild(
            doc.createElementNS(DS, "ds:DigestValue").apply {
                textContent = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(cert.encoded))
            },
        )
        c.appendChild(cd)
        v2.appendChild(c)
        return v2
    }

    private fun markIds(
        el: Element,
        ctx: DOMSignContext,
    ) {
        if (el.hasAttribute("Id")) ctx.setIdAttributeNS(el, null, "Id")
        var c = el.firstChild
        while (c != null) {
            if (c is Element) markIds(c, ctx)
            c = c.nextSibling
        }
    }
}
