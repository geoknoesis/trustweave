package org.trustweave.signatures.xades

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.signatures.trustlists.DefaultTrustAnchorResolver
import org.trustweave.signatures.trustlists.MemberStateTsl
import org.trustweave.signatures.trustlists.QualifierUris
import org.trustweave.signatures.trustlists.TrustList
import org.trustweave.signatures.trustlists.TrustedTSP
import org.trustweave.signatures.trustlists.TspService
import org.trustweave.signatures.trustlists.TspServiceStatus
import org.trustweave.signatures.trustlists.TspServiceType
import org.trustweave.signatures.xades.XadesValidationResult.Invalid
import org.trustweave.signatures.xades.XadesValidationResult.Valid
import org.w3c.dom.Document
import java.security.cert.X509Certificate
import javax.xml.parsers.DocumentBuilderFactory

class XadesRoundTripTest {

    private lateinit var kms: TestKms
    private lateinit var ca: TestCa
    private val verifier = DefaultXadesVerifier()

    @BeforeEach
    fun setUp() {
        kms = TestKms()
        ca = TestCa()
    }

    @Test
    fun `roundtrips an enveloped XAdES B-B signature with P-256`() = runBlocking {
        val keyId = generateKey(Algorithm.P256)
        val chain = ca.issueChainBytes(kms.publicKey(keyId), "CN=P256 XAdES Signer")
        val doc = parseSampleDocument()

        val signature = DefaultXadesSigner(kms, kms.privateKey(keyId)).sign(
            XadesSigningRequest(
                profile = XadesProfile.B_B,
                keyId = keyId,
                document = doc,
                signerCertificateChain = chain,
            ),
        )

        assertEquals(XadesProfile.B_B, signature.profile)

        val result = verifier.verify(
            signature.document,
            XadesVerificationOptions(
                requiredProfile = XadesProfile.B_B,
                trustAnchorResolver = resolverFor(ca.caCert),
            ),
        )
        assertTrue(result is Valid, "expected Valid, got $result")
        result as Valid
        assertEquals(XadesProfile.B_B, result.profile)
        assertNotNull(result.signingTime)
    }

    @Test
    fun `untrusted signer resolves to UntrustedSigner`() = runBlocking {
        val keyId = generateKey(Algorithm.P256)
        val chain = ca.issueChainBytes(kms.publicKey(keyId), "CN=Untrusted")
        val doc = parseSampleDocument()

        val signature = DefaultXadesSigner(kms, kms.privateKey(keyId)).sign(
            XadesSigningRequest(XadesProfile.B_B, keyId, doc, chain),
        )

        val otherCa = TestCa(caSubject = "CN=Unrelated CA")
        val result = verifier.verify(
            signature.document,
            XadesVerificationOptions(
                requiredProfile = XadesProfile.B_B,
                trustAnchorResolver = resolverFor(otherCa.caCert),
            ),
        )
        assertTrue(result is Invalid.UntrustedSigner, "got $result")
    }

    @Test
    fun `document without ds-Signature element yields Malformed`() = runBlocking {
        val result = verifier.verify(
            parseSampleDocument(),
            XadesVerificationOptions(
                requiredProfile = XadesProfile.B_B,
                trustAnchorResolver = resolverFor(ca.caCert),
            ),
        )
        assertTrue(result is Invalid.Malformed, "got $result")
    }

    // ---------------------------------------------------------------- helpers

    private suspend fun generateKey(algorithm: Algorithm): KeyId {
        val keyId = "test-${algorithm.name}"
        val result = kms.generateKey(algorithm, mapOf("keyId" to keyId))
        return when (result) {
            is GenerateKeyResult.Success -> result.keyHandle.id
            else -> error("KMS keygen failed: $result")
        }
    }

    private fun parseSampleDocument(): Document {
        val xml = """<?xml version="1.0" encoding="UTF-8"?><Invoice><Total>42.00</Total></Invoice>"""
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val builder = factory.newDocumentBuilder()
        return builder.parse(xml.byteInputStream(Charsets.UTF_8))
    }

    private fun resolverFor(trustedCa: X509Certificate): DefaultTrustAnchorResolver {
        val service = TspService(
            serviceName = "Test CA",
            serviceType = TspServiceType.CA_FOR_QUALIFIED_CERTIFICATES,
            status = TspServiceStatus.GRANTED,
            statusStartingTime = Clock.System.now(),
            serviceCertificates = listOf(trustedCa),
            qualifierUris = listOf(QualifierUris.QC_WITH_SSCD, QualifierUris.QC_FOR_ESIG),
        )
        val trustList = TrustList(
            schemeOperator = "Test",
            sequenceNumber = 1,
            issuedAt = Clock.System.now(),
            nextUpdateAt = null,
            memberStateLists = listOf(
                MemberStateTsl(
                    territory = "EU",
                    schemeOperator = "Test",
                    sequenceNumber = 1,
                    issuedAt = Clock.System.now(),
                    trustedTsps = listOf(
                        TrustedTSP(name = "Test TSP", tradeName = null, services = listOf(service)),
                    ),
                ),
            ),
        )
        return DefaultTrustAnchorResolver(trustList)
    }
}
