package org.trustweave.signatures.cades

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.signatures.cades.CadesValidationResult.Invalid
import org.trustweave.signatures.cades.CadesValidationResult.Valid
import org.trustweave.signatures.trustlists.DefaultTrustAnchorResolver
import org.trustweave.signatures.trustlists.MemberStateTsl
import org.trustweave.signatures.trustlists.QualifierUris
import org.trustweave.signatures.trustlists.TrustList
import org.trustweave.signatures.trustlists.TrustedTSP
import org.trustweave.signatures.trustlists.TspService
import org.trustweave.signatures.trustlists.TspServiceStatus
import org.trustweave.signatures.trustlists.TspServiceType
import org.trustweave.signatures.tsa.TsaConfig
import java.security.cert.X509Certificate

class CadesRoundTripTest {

    private lateinit var kms: TestKms
    private lateinit var ca: TestCa
    private val verifier = DefaultCadesVerifier()

    @BeforeEach
    fun setUp() {
        kms = TestKms()
        ca = TestCa()
    }

    @Test
    fun `roundtrips a CAdES B-B detached signature with P-256`() = runBlocking {
        val keyId = generateKey(Algorithm.P256)
        val chain = ca.issueChainBytes(kms.publicKey(keyId), "CN=P256 CAdES Signer")
        val payload = "TrustWeave eIDAS CAdES B-B test payload".toByteArray()

        val signature = DefaultCadesSigner(kms).sign(
            CadesSigningRequest(
                profile = CadesProfile.B_B,
                keyId = keyId,
                payload = payload,
                signerCertificateChain = chain,
                detached = true,
            ),
        )

        assertTrue(signature.detached)
        assertEquals(CadesProfile.B_B, signature.profile)

        val result = verifier.verify(
            signature.encoded,
            CadesVerificationOptions(
                requiredProfile = CadesProfile.B_B,
                trustAnchorResolver = resolverFor(ca.caCert),
                detachedPayload = payload,
            ),
        )
        assertTrue(result is Valid, "expected Valid, got $result")
        result as Valid
        assertEquals(CadesProfile.B_B, result.profile)
        assertNotNull(result.signingTime)
        assertNull(result.signatureTimeStamp)
    }

    @Test
    fun `roundtrips a CAdES B-B encapsulated signature with Ed25519`() = runBlocking {
        val keyId = generateKey(Algorithm.Ed25519)
        val chain = ca.issueChainBytes(kms.publicKey(keyId), "CN=Ed25519 CAdES Signer")
        val payload = "encapsulated".toByteArray()

        val signature = DefaultCadesSigner(kms).sign(
            CadesSigningRequest(
                profile = CadesProfile.B_B,
                keyId = keyId,
                payload = payload,
                signerCertificateChain = chain,
                detached = false,
            ),
        )

        val result = verifier.verify(
            signature.encoded,
            CadesVerificationOptions(
                requiredProfile = CadesProfile.B_B,
                trustAnchorResolver = resolverFor(ca.caCert),
                detachedPayload = null,
            ),
        )
        assertTrue(result is Valid, "expected Valid, got $result")
    }

    @Test
    fun `roundtrips a CAdES B-T detached signature using an in-process TSA`() = runBlocking {
        val tsa = TestTsa.generate()
        val server = MockWebServer().apply { start() }
        try {
            server.dispatcher = stampingDispatcher(tsa)

            val keyId = generateKey(Algorithm.P256)
            val chain = ca.issueChainBytes(kms.publicKey(keyId), "CN=B-T CAdES Signer")
            val payload = "binary".repeat(8).toByteArray()

            val signature = DefaultCadesSigner(kms).sign(
                CadesSigningRequest(
                    profile = CadesProfile.B_T,
                    keyId = keyId,
                    payload = payload,
                    signerCertificateChain = chain,
                    tsaConfig = TsaConfig(endpointUrl = server.url("/tsa").toString()),
                    detached = true,
                ),
            )
            assertEquals(CadesProfile.B_T, signature.profile)

            val result = verifier.verify(
                signature.encoded,
                CadesVerificationOptions(
                    requiredProfile = CadesProfile.B_T,
                    trustAnchorResolver = resolverFor(ca.caCert),
                    detachedPayload = payload,
                ),
            )
            assertTrue(result is Valid, "expected Valid, got $result")
            result as Valid
            assertNotNull(result.signatureTimeStamp)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `untrusted signer resolves to UntrustedSigner`() = runBlocking {
        val keyId = generateKey(Algorithm.P256)
        val chain = ca.issueChainBytes(kms.publicKey(keyId), "CN=Anyone")
        val payload = "x".toByteArray()

        val signature = DefaultCadesSigner(kms).sign(
            CadesSigningRequest(CadesProfile.B_B, keyId, payload, chain),
        )
        val otherCa = TestCa(caSubject = "CN=Unrelated CA")
        val result = verifier.verify(
            signature.encoded,
            CadesVerificationOptions(
                requiredProfile = CadesProfile.B_B,
                trustAnchorResolver = resolverFor(otherCa.caCert),
                detachedPayload = payload,
            ),
        )
        assertTrue(result is Invalid.UntrustedSigner, "got $result")
    }

    @Test
    fun `tampered detached payload fails BadSignature`() = runBlocking {
        val keyId = generateKey(Algorithm.P256)
        val chain = ca.issueChainBytes(kms.publicKey(keyId), "CN=Anyone")
        val payload = "original".toByteArray()

        val signature = DefaultCadesSigner(kms).sign(
            CadesSigningRequest(CadesProfile.B_B, keyId, payload, chain),
        )
        val result = verifier.verify(
            signature.encoded,
            CadesVerificationOptions(
                requiredProfile = CadesProfile.B_B,
                trustAnchorResolver = resolverFor(ca.caCert),
                detachedPayload = "tampered".toByteArray(),
            ),
        )
        assertTrue(result is Invalid.BadSignature, "got $result")
    }

    @Test
    fun `requiring B-T but receiving B-B yields WrongProfile`() = runBlocking {
        val keyId = generateKey(Algorithm.P256)
        val chain = ca.issueChainBytes(kms.publicKey(keyId), "CN=Anyone")
        val payload = "x".toByteArray()

        val signature = DefaultCadesSigner(kms).sign(
            CadesSigningRequest(CadesProfile.B_B, keyId, payload, chain),
        )
        val result = verifier.verify(
            signature.encoded,
            CadesVerificationOptions(
                requiredProfile = CadesProfile.B_T,
                trustAnchorResolver = resolverFor(ca.caCert),
                detachedPayload = payload,
            ),
        )
        assertTrue(result is Invalid.WrongProfile, "got $result")
    }

    @Test
    fun `garbage input yields Malformed`() = runBlocking {
        val result = verifier.verify(
            "not a CMS".toByteArray(),
            CadesVerificationOptions(
                requiredProfile = CadesProfile.B_B,
                trustAnchorResolver = resolverFor(ca.caCert),
            ),
        )
        assertTrue(result is Invalid.Malformed, "got $result")
    }

    @Test
    fun `detached signature without payload yields MissingDetachedPayload`() = runBlocking {
        val keyId = generateKey(Algorithm.P256)
        val chain = ca.issueChainBytes(kms.publicKey(keyId), "CN=Anyone")
        val payload = "x".toByteArray()

        val signature = DefaultCadesSigner(kms).sign(
            CadesSigningRequest(CadesProfile.B_B, keyId, payload, chain, detached = true),
        )
        val result = verifier.verify(
            signature.encoded,
            CadesVerificationOptions(
                requiredProfile = CadesProfile.B_B,
                trustAnchorResolver = resolverFor(ca.caCert),
                detachedPayload = null,
            ),
        )
        assertTrue(result is Invalid.MissingDetachedPayload, "got $result")
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

    private fun stampingDispatcher(tsa: TestTsa): Dispatcher = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val responseBytes = tsa.stamp(request.body.readByteArray())
            return MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/timestamp-reply")
                .setBody(Buffer().apply { write(responseBytes) })
        }
    }
}
