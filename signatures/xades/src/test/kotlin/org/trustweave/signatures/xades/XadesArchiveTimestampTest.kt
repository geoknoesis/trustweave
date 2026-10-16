package org.trustweave.signatures.xades

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.trustweave.core.identifiers.KeyId
import org.trustweave.signatures.revocation.RevocationEvidence
import org.trustweave.signatures.revocation.RevocationPolicy
import org.trustweave.signatures.trustlists.QualifierUris
import org.trustweave.signatures.trustlists.TrustAnchorMatch
import org.trustweave.signatures.trustlists.TrustAnchorResolver
import org.trustweave.signatures.trustlists.TspService
import org.trustweave.signatures.trustlists.TspServiceStatus
import org.trustweave.signatures.trustlists.TspServiceType
import org.trustweave.signatures.tsa.TsaClient
import org.trustweave.signatures.tsa.TsaConfig
import org.trustweave.signatures.tsa.TsaHashAlgorithm
import org.trustweave.signatures.xades.XadesValidationResult.Invalid
import org.trustweave.signatures.xades.XadesValidationResult.Valid
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.StringWriter
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import java.util.Date
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.time.Clock

/** XAdES B-LTA: the signer adds an ArchiveTimeStamp and the verifier credits it only when it verifies. */
class XadesArchiveTimestampTest {
    private val ca = TestCa()
    private val tsa = TestTsa()
    private val signerKey: KeyPair =
        KeyPairGenerator
            .getInstance("EC")
            .apply { initialize(ECGenParameterSpec("secp256r1")) }
            .generateKeyPair()
    private val signerCert = ca.issue(signerKey.public, "CN=LTA Signer")
    private val tsaConfig = TsaConfig(endpointUrl = "http://tsa.invalid/")
    private val verifier = DefaultXadesVerifier()
    private val xades = "http://uri.etsi.org/01903/v1.3.2#"
    private val xades141 = "http://uri.etsi.org/01903/v1.4.1#"
    private val ds = "http://www.w3.org/2000/09/xmldsig#"

    private val resolver =
        object : TrustAnchorResolver {
            override fun resolve(
                signerCert: X509Certificate,
                chain: List<X509Certificate>,
            ): TrustAnchorMatch =
                TrustAnchorMatch.QualifiedActive(
                    tspName = "Test TSP",
                    territory = "EU",
                    service =
                        TspService(
                            serviceName = "Test CA",
                            serviceType = TspServiceType.CA_FOR_QUALIFIED_CERTIFICATES,
                            status = TspServiceStatus.GRANTED,
                            statusStartingTime = Clock.System.now() - kotlin.time.Duration.parse("PT8760H"),
                            serviceCertificates = listOf(ca.caCert),
                            qualifierUris = listOf(QualifierUris.QC_WITH_SSCD),
                        ),
                    qcWithSscd = true,
                    qcForESig = true,
                )
        }

    private fun chain(): List<ByteArray> = listOf(signerCert.encoded, ca.caCert.encoded)

    /** The first TSA request (signature time-stamp) goes to [tsa], the second (archive) to [archiveTsa]. */
    private suspend fun sign(
        profile: XadesProfile,
        archiveTsa: TestTsa = tsa,
        crlIssuer: TestCa = ca,
    ): XadesSignature {
        val signer =
            DefaultXadesSigner(TestKms(), signerKey.private) { _ ->
                object : TsaClient {
                    private var calls = 0

                    override suspend fun requestTimeStamp(
                        digest: ByteArray,
                        hashAlgorithm: TsaHashAlgorithm,
                        nonce: ByteArray?,
                    ) = (if (calls++ == 0) tsa else archiveTsa).client().requestTimeStamp(digest, hashAlgorithm, nonce)
                }
            }
        return signer.sign(
            XadesSigningRequest(
                profile = profile,
                keyId = KeyId("k"),
                document = XadesForge.sampleDocument(),
                signerCertificateChain = chain(),
                tsaConfig = tsaConfig,
                validationData =
                    XadesValidationData(
                        certificates = listOf(signerCert.encoded, ca.caCert.encoded),
                        revocation = RevocationEvidence(crls = listOf(RevocationFixtures.crl(crlIssuer, thisUpdate = Date()))),
                    ).takeIf { profile.atLeast(XadesProfile.B_LT) },
            ),
        )
    }

    private fun options(required: XadesProfile) =
        XadesVerificationOptions(
            requiredProfile = required,
            trustAnchorResolver = resolver,
            timestampTrustAnchors = listOf(tsa.cert),
            revocationPolicy = RevocationPolicy.REQUIRED,
        )

    private fun element(
        document: Document,
        ns: String,
        name: String,
    ): Element = document.getElementsByTagNameNS(ns, name).item(0) as Element

    private fun reparse(document: Document): Document {
        val out = StringWriter()
        TransformerFactory.newInstance().newTransformer().transform(DOMSource(document), StreamResult(out))
        return DocumentBuilderFactory
            .newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(out.toString().byteInputStream(Charsets.UTF_8))
    }

    private fun flipBase64(element: Element) {
        val bytes = Base64.getMimeDecoder().decode(element.textContent.trim())
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0x01).toByte()
        element.textContent = Base64.getEncoder().encodeToString(bytes)
    }

    @Test
    fun `the signer produces a B-LTA signature that verifies as B-LTA`() =
        runTest {
            val signature = sign(XadesProfile.B_LTA)
            signature.profile shouldBe XadesProfile.B_LTA
            element(signature.document, xades141, "ArchiveTimeStamp")
            val result = verifier.verify(signature.document, options(XadesProfile.B_LTA)).shouldBeInstanceOf<Valid>()
            result.profile shouldBe XadesProfile.B_LTA
            result.revocationChecked shouldBe true
            (result.archiveTimeStamp != null) shouldBe true
        }

    @Test
    fun `a serialised and re-parsed B-LTA signature still verifies as B-LTA`() =
        runTest {
            val reparsed = reparse(sign(XadesProfile.B_LTA).document)
            verifier.verify(reparsed, options(XadesProfile.B_LTA)).shouldBeInstanceOf<Valid>().profile shouldBe XadesProfile.B_LTA
        }

    @Test
    fun `serialised and re-parsed B-B and B-T signatures still verify`() =
        runTest {
            for (profile in listOf(XadesProfile.B_B, XadesProfile.B_T)) {
                val unchecked = options(profile).copy(revocationPolicy = RevocationPolicy.NOT_CHECKED)
                verifier.verify(reparse(sign(profile).document), unchecked).shouldBeInstanceOf<Valid>().profile shouldBe profile
            }
        }

    @Test
    fun `a B-LT signature is not B-LTA`() =
        runTest {
            val signature = sign(XadesProfile.B_LT)
            val wrong = verifier.verify(signature.document, options(XadesProfile.B_LTA)).shouldBeInstanceOf<Invalid.WrongProfile>()
            wrong.found shouldBe XadesProfile.B_LT
            wrong.required shouldBe XadesProfile.B_LTA
        }

    @Test
    fun `a tampered signature value is refused`() =
        runTest {
            val signature = sign(XadesProfile.B_LTA)
            flipBase64(element(signature.document, ds, "SignatureValue"))
            verifier.verify(signature.document, options(XadesProfile.B_LTA)).shouldBeInstanceOf<Invalid.BadSignature>()
        }

    @Test
    fun `a tampered archive time-stamp token is refused`() =
        runTest {
            val signature = sign(XadesProfile.B_LTA)
            val archive = element(signature.document, xades141, "ArchiveTimeStamp")
            flipBase64(archive.getElementsByTagNameNS(xades, "EncapsulatedTimeStamp").item(0) as Element)
            verifier.verify(signature.document, options(XadesProfile.B_LTA)).shouldBeInstanceOf<Invalid.TimeStampInvalid>()
        }

    @Test
    fun `an archive time-stamp over a different imprint is refused`() =
        runTest {
            val signature = sign(XadesProfile.B_LTA)
            val archive = element(signature.document, xades141, "ArchiveTimeStamp")
            val token = archive.getElementsByTagNameNS(xades, "EncapsulatedTimeStamp").item(0) as Element
            token.textContent =
                Base64.getEncoder().encodeToString(tsa.token(MessageDigest.getInstance("SHA-256").digest("other".toByteArray())))
            verifier.verify(signature.document, options(XadesProfile.B_LTA)).shouldBeInstanceOf<Invalid.TimeStampInvalid>()
        }

    @Test
    fun `changing an unsigned property the archive time-stamp covers is refused`() =
        runTest {
            val signature = sign(XadesProfile.B_LTA)
            flipBase64(element(signature.document, xades, "EncapsulatedX509Certificate"))
            verifier.verify(signature.document, options(XadesProfile.B_LTA)).shouldBeInstanceOf<Invalid.TimeStampInvalid>()
        }

    @Test
    fun `removing the revocation values from a B-LTA signature is refused`() =
        runTest {
            val signature = sign(XadesProfile.B_LTA)
            val values = element(signature.document, xades, "RevocationValues")
            values.parentNode.removeChild(values)
            verifier.verify(signature.document, options(XadesProfile.B_LTA)).shouldBeInstanceOf<Invalid.TimeStampInvalid>()
        }

    @Test
    fun `revocation values appended after the last archive time-stamp do not grant B-LTA`() =
        runTest {
            // The archived revocation values are useless (a CRL of another CA); the real CRL is added afterwards,
            // outside the archive time-stamp's imprint.
            val signature = sign(XadesProfile.B_LTA, crlIssuer = TestCa())
            val document = signature.document
            val usp = element(document, xades, "UnsignedSignatureProperties")
            val late = document.createElementNS(xades, "xades:RevocationValues")
            val crlValues = document.createElementNS(xades, "xades:CRLValues")
            crlValues.appendChild(
                document.createElementNS(xades, "xades:EncapsulatedCRLValue").apply {
                    textContent = Base64.getEncoder().encodeToString(RevocationFixtures.crl(ca, thisUpdate = Date()))
                },
            )
            late.appendChild(crlValues)
            usp.appendChild(late)
            val wrong = verifier.verify(document, options(XadesProfile.B_LTA)).shouldBeInstanceOf<Invalid.WrongProfile>()
            (wrong.found == XadesProfile.B_LTA) shouldBe false
            verifier.verify(document, options(XadesProfile.B_T)).shouldBeInstanceOf<Valid>().profile shouldBe XadesProfile.B_T
        }

    @Test
    fun `an archive time-stamp from an untrusted TSA is refused`() =
        runTest {
            val signature = sign(XadesProfile.B_LTA, archiveTsa = TestTsa())
            verifier.verify(signature.document, options(XadesProfile.B_LTA)).shouldBeInstanceOf<Invalid.TimeStampInvalid>()
        }

    @Test
    fun `requiring B-LTA of a B-T signature fails closed on the missing B-LT evidence`() =
        runTest {
            val signature = sign(XadesProfile.B_T)
            verifier.verify(signature.document, options(XadesProfile.B_LTA)).shouldBeInstanceOf<Invalid.RevocationUnavailable>()
        }

    @Test
    fun `B-LTA ordering and request validation`() {
        XadesProfile.B_LTA.atLeast(XadesProfile.B_LT) shouldBe true
        XadesProfile.B_LT.atLeast(XadesProfile.B_LTA) shouldBe false
        val doc = XadesForge.sampleDocument()
        assertThrows<IllegalArgumentException> {
            XadesSigningRequest(XadesProfile.B_LTA, KeyId("k"), doc, chain(), tsaConfig = tsaConfig)
        }
        assertThrows<IllegalArgumentException> {
            XadesSigningRequest(XadesProfile.B_LTA, KeyId("k"), doc, chain())
        }
    }
}
