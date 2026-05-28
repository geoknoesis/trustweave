package org.trustweave.signatures.trustlists

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Document
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyPair
import java.security.cert.X509Certificate
import java.util.Base64
import javax.xml.crypto.dsig.CanonicalizationMethod
import javax.xml.crypto.dsig.DigestMethod
import javax.xml.crypto.dsig.Reference
import javax.xml.crypto.dsig.SignatureMethod
import javax.xml.crypto.dsig.SignedInfo
import javax.xml.crypto.dsig.Transform
import javax.xml.crypto.dsig.XMLSignature
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

class LotlSignatureVerifierTest {

    private val verifier = DefaultLotlSignatureVerifier()

    // ---------------------------------------------------------------- happy path

    @Test
    fun `valid signed LoTL verifies and returns the signer cert`() {
        val ca = TrustListFixtures.generateCaAndSigner()
        val signed = signLotl(TrustListFixtures.renderLotlXml(), ca.signerCert, ca.signerKey)

        val result = verifier.verify(signed, listOf(ca.signerCert))

        assertTrue(
            result is LotlSignatureValidationResult.Valid,
            "expected Valid, got: $result",
        )
        result as LotlSignatureValidationResult.Valid
        assertEquals(ca.signerCert, result.signerCert)
        assertNull(result.signingTime, "fixture does not include xades:SigningTime")
    }

    @Test
    fun `signer trusted via CA chain validates`() {
        val ca = TrustListFixtures.generateCaAndSigner()
        val signed = signLotl(TrustListFixtures.renderLotlXml(), ca.signerCert, ca.signerKey)

        // Trust anchor is the CA, not the signer end-entity cert.
        val result = verifier.verify(signed, listOf(ca.caCert))

        assertTrue(
            result is LotlSignatureValidationResult.Valid,
            "expected Valid via CA anchor, got: $result",
        )
    }

    // ---------------------------------------------------------------- crypto failures

    @Test
    fun `swapping the KeyInfo cert produces a crypto failure or untrusted signer`() {
        val ca = TrustListFixtures.generateCaAndSigner()
        val other = TrustListFixtures.generateCaAndSigner(
            caSubject = "CN=Other CA",
            signerSubject = "CN=Other Signer",
        )
        val signed = signLotl(TrustListFixtures.renderLotlXml(), ca.signerCert, ca.signerKey)

        // Replace the X509Certificate base64 in KeyInfo with the OTHER signer cert. The signature
        // bits stay intact but no longer match the embedded key.
        val tampered = replaceX509Certificate(signed, other.signerCert)
        val result = verifier.verify(tampered, listOf(ca.signerCert, other.signerCert))

        // The JDK's XMLSignature.validate covers public-key mismatch; some JDKs surface this as
        // a hard failure, others might still accept the bytes but then anchor-checking against
        // a different identity is the safety net. Either is an acceptable rejection.
        assertTrue(
            result is LotlSignatureValidationResult.Invalid.SignatureCryptoFailed ||
                result is LotlSignatureValidationResult.Invalid.UntrustedSigner,
            "expected SignatureCryptoFailed or UntrustedSigner, got: $result",
        )
    }

    @Test
    fun `tampering with the payload after signing causes a crypto failure`() {
        val ca = TrustListFixtures.generateCaAndSigner()
        val signed = signLotl(TrustListFixtures.renderLotlXml(), ca.signerCert, ca.signerKey)

        // Flip a single byte in the scheme operator name (still well-formed XML).
        val tampered = String(signed)
            .replace("European Commission", "Mallory Commission")
            .toByteArray()

        val result = verifier.verify(tampered, listOf(ca.signerCert))
        assertTrue(
            result is LotlSignatureValidationResult.Invalid.SignatureCryptoFailed,
            "expected SignatureCryptoFailed, got: $result",
        )
    }

    // ---------------------------------------------------------------- trust

    @Test
    fun `signer cert not in trust anchors is rejected`() {
        val ca = TrustListFixtures.generateCaAndSigner()
        val rogue = TrustListFixtures.generateCaAndSigner(
            caSubject = "CN=Rogue CA",
            signerSubject = "CN=Rogue Signer",
        )
        val signed = signLotl(TrustListFixtures.renderLotlXml(), ca.signerCert, ca.signerKey)

        val result = verifier.verify(signed, listOf(rogue.signerCert))

        assertTrue(
            result is LotlSignatureValidationResult.Invalid.UntrustedSigner,
            "expected UntrustedSigner, got: $result",
        )
        result as LotlSignatureValidationResult.Invalid.UntrustedSigner
        assertEquals(ca.signerCert, result.cert)
    }

    @Test
    fun `empty trust anchor list rejects any valid signature`() {
        val ca = TrustListFixtures.generateCaAndSigner()
        val signed = signLotl(TrustListFixtures.renderLotlXml(), ca.signerCert, ca.signerKey)

        val result = verifier.verify(signed, emptyList())

        assertTrue(
            result is LotlSignatureValidationResult.Invalid.UntrustedSigner,
            "expected UntrustedSigner, got: $result",
        )
    }

    // ---------------------------------------------------------------- malformed input

    @Test
    fun `unsigned LoTL is reported as MissingSignature`() {
        val unsigned = TrustListFixtures.renderLotlXml()

        val result = verifier.verify(unsigned, emptyList())

        assertEquals(LotlSignatureValidationResult.Invalid.MissingSignature, result)
    }

    @Test
    fun `garbage bytes are reported as Malformed`() {
        val result = verifier.verify("not xml at all".toByteArray(), emptyList())

        assertTrue(
            result is LotlSignatureValidationResult.Invalid.Malformed,
            "expected Malformed, got: $result",
        )
    }

    // ---------------------------------------------------------------- signing helper

    /**
     * Sign [lotlXml] with [signerKey] / [signerCert] using JDK's XMLDSig, producing an enveloped
     * RSA-SHA256 signature with the signer cert embedded in KeyInfo. Mirrors the structure of a
     * real LoTL but omits the XAdES SignedProperties block — exercising only what the verifier
     * actually depends on.
     */
    private fun signLotl(
        lotlXml: ByteArray,
        signerCert: X509Certificate,
        signerKey: KeyPair,
    ): ByteArray {
        val doc = parseDoc(lotlXml)

        val factory = XMLSignatureFactory.getInstance("DOM")

        val reference: Reference = factory.newReference(
            "",
            factory.newDigestMethod(DigestMethod.SHA256, null),
            listOf(
                factory.newTransform(
                    Transform.ENVELOPED,
                    null as TransformParameterSpec?,
                ),
            ),
            null,
            null,
        )

        val signedInfo: SignedInfo = factory.newSignedInfo(
            factory.newCanonicalizationMethod(
                CanonicalizationMethod.EXCLUSIVE,
                null as C14NMethodParameterSpec?,
            ),
            factory.newSignatureMethod(SignatureMethod.RSA_SHA256, null),
            listOf(reference),
        )

        val kif = KeyInfoFactory.getInstance()
        val x509Data = kif.newX509Data(listOf(signerCert))
        val keyInfo = kif.newKeyInfo(listOf(x509Data))

        val context = DOMSignContext(signerKey.private, doc.documentElement)
        val signature: XMLSignature = factory.newXMLSignature(signedInfo, keyInfo)
        signature.sign(context)

        return serialize(doc)
    }

    /** Swap the base64 inside the (sole) ds:X509Certificate element for [replacement]. */
    private fun replaceX509Certificate(signedXml: ByteArray, replacement: X509Certificate): ByteArray {
        val xml = String(signedXml, Charsets.UTF_8)
        val regex = Regex("(<[^>]*X509Certificate[^>]*>)([^<]*)(</[^>]*X509Certificate>)")
        val newBase64 = Base64.getEncoder().encodeToString(replacement.encoded)
        val replaced = regex.replace(xml) { match ->
            "${match.groupValues[1]}$newBase64${match.groupValues[3]}"
        }
        return replaced.toByteArray(Charsets.UTF_8)
    }

    private fun parseDoc(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    private fun serialize(doc: Document): ByteArray {
        val out = ByteArrayOutputStream()
        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        }
        transformer.transform(DOMSource(doc), StreamResult(out))
        return out.toByteArray()
    }
}
