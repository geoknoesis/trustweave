package org.trustweave.signatures.trustlists

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element

/** What the signature covers: the verifier must bind the signature to the whole document. */
class LotlSignatureHardeningTest {
    private val verifier = DefaultLotlSignatureVerifier()
    private val ca = TrustListFixtures.generateCaAndSigner()

    @Test
    fun `fragment-only reference is rejected even when the covered element is intact`() {
        val signed =
            SignedXmlTestSupport.signFragmentOnly(
                TrustListFixtures.renderLotlXml(),
                "SchemeInformation",
                "si",
                ca.signerKey,
                ca.signerCert,
            )
        // Content outside the signed fragment is attacker-controlled.
        val doc = SignedXmlTestSupport.parse(signed)
        val root = doc.documentElement
        root.appendChild(doc.createElementNS(root.namespaceURI, "Injected"))
        val result = verifier.verify(SignedXmlTestSupport.serialize(doc), listOf(ca.signerCert))

        assertTrue(result is LotlSignatureValidationResult.Invalid.Malformed, "got: $result")
    }

    @Test
    fun `second ds Signature is rejected`() {
        val signed =
            SignedXmlTestSupport.signEnveloped(
                TrustListFixtures.renderLotlXml(),
                ca.signerKey,
                listOf(ca.signerCert),
            )
        // Nest a copy inside the first signature's own subtree: the enveloped transform removes
        // it from the digest, so the first signature still verifies on its own.
        val doc = SignedXmlTestSupport.parse(signed)
        val first = doc.getElementsByTagNameNS(SignedXmlTestSupport.DS_NS, "Signature").item(0) as Element
        val obj = doc.createElementNS(SignedXmlTestSupport.DS_NS, "ds:Object")
        obj.appendChild(first.cloneNode(true))
        first.appendChild(obj)

        val result = verifier.verify(SignedXmlTestSupport.serialize(doc), listOf(ca.signerCert))

        assertTrue(result is LotlSignatureValidationResult.Invalid.Malformed, "got: $result")
    }

    @Test
    fun `duplicate Id values are rejected`() {
        val signed =
            SignedXmlTestSupport.signEnveloped(
                TrustListFixtures.renderLotlXml(),
                ca.signerKey,
                listOf(ca.signerCert),
                withSignedProperties = true,
                beforeSign = { doc ->
                    val decoy = doc.createElementNS(doc.documentElement.namespaceURI, "Decoy")
                    decoy.setAttribute("Id", "sp1")
                    doc.documentElement.appendChild(decoy)
                },
            )

        val result = verifier.verify(signed, listOf(ca.signerCert))

        assertTrue(
            result is LotlSignatureValidationResult.Invalid.Malformed &&
                result.reason.contains("duplicate"),
            "got: $result",
        )
    }

    @Test
    fun `whole-document reference plus SignedProperties reference is accepted`() {
        val signed =
            SignedXmlTestSupport.signEnveloped(
                TrustListFixtures.renderLotlXml(),
                ca.signerKey,
                listOf(ca.signerCert),
                withSignedProperties = true,
            )

        val result = verifier.verify(signed, listOf(ca.signerCert))

        assertTrue(result is LotlSignatureValidationResult.Valid, "got: $result")
    }
}
