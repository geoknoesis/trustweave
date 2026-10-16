package org.trustweave.signatures.trustlists

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyPair
import java.security.cert.X509Certificate
import javax.xml.crypto.dom.DOMStructure
import javax.xml.crypto.dsig.CanonicalizationMethod
import javax.xml.crypto.dsig.DigestMethod
import javax.xml.crypto.dsig.Reference
import javax.xml.crypto.dsig.SignatureMethod
import javax.xml.crypto.dsig.Transform
import javax.xml.crypto.dsig.XMLSignatureFactory
import javax.xml.crypto.dsig.dom.DOMSignContext
import javax.xml.crypto.dsig.keyinfo.KeyInfoFactory
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec
import javax.xml.crypto.dsig.spec.TransformParameterSpec
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/** XMLDSig signing helpers shared by the trust-list verifier tests. */
internal object SignedXmlTestSupport {
    const val DS_NS = "http://www.w3.org/2000/09/xmldsig#"
    const val XADES_NS = "http://uri.etsi.org/01903/v1.3.2#"

    fun parse(bytes: ByteArray): Document =
        DocumentBuilderFactory
            .newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(bytes))

    fun serialize(doc: Document): ByteArray {
        val out = ByteArrayOutputStream()
        TransformerFactory
            .newInstance()
            .newTransformer()
            .apply {
                setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
                setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            }.transform(DOMSource(doc), StreamResult(out))
        return out.toByteArray()
    }

    /** Enveloped signature over the whole document, KeyInfo carrying [keyInfoCerts] (leaf first). */
    fun signEnveloped(
        xml: ByteArray,
        signerKey: KeyPair,
        keyInfoCerts: List<X509Certificate>,
        withSignedProperties: Boolean = false,
        beforeSign: (Document) -> Unit = {},
    ): ByteArray {
        val doc = parse(xml)
        beforeSign(doc)
        val factory = XMLSignatureFactory.getInstance("DOM")
        val refs =
            mutableListOf<Reference>(
                factory.newReference(
                    "",
                    factory.newDigestMethod(DigestMethod.SHA256, null),
                    listOf(factory.newTransform(Transform.ENVELOPED, null as TransformParameterSpec?)),
                    null,
                    null,
                ),
            )
        val objects = mutableListOf<javax.xml.crypto.dsig.XMLObject>()
        var spElement: Element? = null
        if (withSignedProperties) {
            val qp = doc.createElementNS(XADES_NS, "xades:QualifyingProperties")
            val sp = doc.createElementNS(XADES_NS, "xades:SignedProperties")
            sp.setAttribute("Id", "sp1")
            qp.appendChild(sp)
            spElement = sp
            objects += factory.newXMLObject(listOf(DOMStructure(qp)), null, null, null)
            refs +=
                factory.newReference(
                    "#sp1",
                    factory.newDigestMethod(DigestMethod.SHA256, null),
                    listOf(
                        factory.newTransform(
                            CanonicalizationMethod.EXCLUSIVE,
                            null as TransformParameterSpec?,
                        ),
                    ),
                    "http://uri.etsi.org/01903#SignedProperties",
                    null,
                )
        }
        return finishSign(doc, factory, refs, objects, signerKey, keyInfoCerts, spElement)
    }

    /** Signature whose only reference is the fragment `#id` (the element must carry Id). */
    fun signFragmentOnly(
        xml: ByteArray,
        elementLocalName: String,
        id: String,
        signerKey: KeyPair,
        signerCert: X509Certificate,
    ): ByteArray {
        val doc = parse(xml)
        val target = doc.getElementsByTagNameNS("*", elementLocalName).item(0) as Element
        target.setAttribute("Id", id)
        val factory = XMLSignatureFactory.getInstance("DOM")
        val ref =
            factory.newReference(
                "#$id",
                factory.newDigestMethod(DigestMethod.SHA256, null),
                listOf(
                    factory.newTransform(
                        CanonicalizationMethod.EXCLUSIVE,
                        null as TransformParameterSpec?,
                    ),
                ),
                null,
                null,
            )
        return finishSign(doc, factory, listOf(ref), emptyList(), signerKey, listOf(signerCert), target)
    }

    private fun finishSign(
        doc: Document,
        factory: XMLSignatureFactory,
        refs: List<Reference>,
        objects: List<javax.xml.crypto.dsig.XMLObject>,
        signerKey: KeyPair,
        keyInfoCerts: List<X509Certificate>,
        idElement: Element?,
    ): ByteArray {
        val signedInfo =
            factory.newSignedInfo(
                factory.newCanonicalizationMethod(
                    CanonicalizationMethod.EXCLUSIVE,
                    null as C14NMethodParameterSpec?,
                ),
                factory.newSignatureMethod(SignatureMethod.RSA_SHA256, null),
                refs,
            )
        val kif = KeyInfoFactory.getInstance()
        val keyInfo = kif.newKeyInfo(listOf(kif.newX509Data(keyInfoCerts)))
        val ctx = DOMSignContext(signerKey.private, doc.documentElement)
        if (idElement != null) ctx.setIdAttributeNS(idElement, null, "Id")
        factory.newXMLSignature(signedInfo, keyInfo, objects, null, null).sign(ctx)
        return serialize(doc)
    }
}
