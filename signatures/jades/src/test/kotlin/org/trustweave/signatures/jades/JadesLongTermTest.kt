package org.trustweave.signatures.jades

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.signatures.jades.JadesValidationResult.Invalid
import org.trustweave.signatures.jades.JadesValidationResult.Valid
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
import java.util.Base64

class JadesLongTermTest {

    private lateinit var kms: TestKms
    private lateinit var ca: TestCa
    private lateinit var tsa: TestTsa
    private lateinit var server: MockWebServer
    private val verifier = DefaultJadesVerifier()

    @BeforeEach
    fun setUp() {
        kms = TestKms()
        ca = TestCa()
        tsa = TestTsa.generate()
        server = MockWebServer().apply { start() }
        server.dispatcher = stampingDispatcher(tsa)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `signing request rejects B_LT without validationData`() = runBlocking {
        val keyId = generateKey(Algorithm.Ed25519)
        val chain = ca.issueChainBytes(kms.publicKey(keyId), "CN=Anyone")
        val failed = runCatching {
            JadesSigningRequest(
                profile = JadesProfile.B_LT,
                keyId = keyId,
                signerCertificateChain = chain,
                tsaConfig = TsaConfig(endpointUrl = server.url("/tsa").toString()),
                validationData = null, // missing
            )
        }
        assertTrue(failed.isFailure)
        assertTrue(failed.exceptionOrNull()!!.message!!.contains("validationData is required"))
    }

    @Test
    fun `signing request rejects B_LT with empty cert chain in validationData`() = runBlocking {
        val keyId = generateKey(Algorithm.Ed25519)
        val chain = ca.issueChainBytes(kms.publicKey(keyId), "CN=Anyone")
        val failed = runCatching {
            JadesSigningRequest(
                profile = JadesProfile.B_LT,
                keyId = keyId,
                signerCertificateChain = chain,
                tsaConfig = TsaConfig(endpointUrl = server.url("/tsa").toString()),
                validationData = ValidationData(completeCertificateChain = emptyList()),
            )
        }
        assertTrue(failed.isFailure)
        assertTrue(failed.exceptionOrNull()!!.message!!.contains("completeCertificateChain"))
    }

    @Test
    fun `roundtrips a JAdES B-LT signature`() = runBlocking {
        val keyId = generateKey(Algorithm.Ed25519)
        val chain = ca.issueChainBytes(kms.publicKey(keyId), "CN=B-LT Signer")
        val signature = DefaultJadesSigner(kms).sign(
            payloadJson = buildJsonObject { put("profile", JsonPrimitive("B-LT")) },
            request = JadesSigningRequest(
                profile = JadesProfile.B_LT,
                keyId = keyId,
                signerCertificateChain = chain,
                tsaConfig = TsaConfig(endpointUrl = server.url("/tsa").toString()),
                validationData = ValidationData(
                    completeCertificateChain = chain, // re-using the chain for the LT material
                    revocationData = listOf(
                        EncodedRevocationData(
                            type = "CRL",
                            dataB64 = Base64.getEncoder().encodeToString(makeDummyCrlBytes()),
                        ),
                    ),
                ),
            ),
        )

        assertTrue(signature.unsigned.sigTst.isNotEmpty(), "B-LT carries sigTst")
        assertTrue(signature.unsigned.xVals.isNotEmpty(), "B-LT carries xVals")
        assertTrue(signature.unsigned.rVals.isNotEmpty(), "B-LT carries rVals")
        assertTrue(signature.unsigned.arcTst.isEmpty(), "B-LT does NOT carry arcTst")

        val result = verifier.verify(
            signature.serializedFlattened,
            JadesVerificationOptions(
                requiredProfile = JadesProfile.B_LT,
                trustAnchorResolver = resolverFor(ca.caCert),
            ),
        )
        assertTrue(result is Valid, "got $result")
        result as Valid
        assertEquals(JadesProfile.B_LT, result.foundProfile)
        assertTrue(result.xValsCount > 0)
        assertTrue(result.rValsCount > 0)
        assertNotNull(result.signatureTimeStamp)
        assertNull(result.archivalTimeStamp, "B-LT has no arcTst")
    }

    @Test
    fun `roundtrips a JAdES B-LTA signature including archival time-stamp`() = runBlocking {
        val keyId = generateKey(Algorithm.Ed25519)
        val chain = ca.issueChainBytes(kms.publicKey(keyId), "CN=B-LTA Signer")
        val signature = DefaultJadesSigner(kms).sign(
            payloadJson = buildJsonObject { put("profile", JsonPrimitive("B-LTA")) },
            request = JadesSigningRequest(
                profile = JadesProfile.B_LTA,
                keyId = keyId,
                signerCertificateChain = chain,
                tsaConfig = TsaConfig(endpointUrl = server.url("/tsa").toString()),
                validationData = ValidationData(
                    completeCertificateChain = chain,
                    revocationData = listOf(
                        EncodedRevocationData(
                            type = "OCSP",
                            dataB64 = Base64.getEncoder().encodeToString(makeDummyOcspBytes()),
                            producedAt = "2026-05-01T00:00:00Z",
                        ),
                    ),
                ),
            ),
        )

        assertTrue(signature.unsigned.arcTst.isNotEmpty(), "B-LTA carries arcTst")

        val result = verifier.verify(
            signature.serializedFlattened,
            JadesVerificationOptions(
                requiredProfile = JadesProfile.B_LTA,
                trustAnchorResolver = resolverFor(ca.caCert),
            ),
        )
        assertTrue(result is Valid, "got $result")
        result as Valid
        assertEquals(JadesProfile.B_LTA, result.foundProfile)
        assertNotNull(result.archivalTimeStamp, "B-LTA must surface archival time-stamp")
    }

    @Test
    fun `requiring B-LT but receiving only B-T yields WrongProfile`() = runBlocking {
        val keyId = generateKey(Algorithm.Ed25519)
        val chain = ca.issueChainBytes(kms.publicKey(keyId), "CN=B-T Signer")
        val signature = DefaultJadesSigner(kms).sign(
            buildJsonObject { put("k", JsonPrimitive("v")) },
            JadesSigningRequest(
                profile = JadesProfile.B_T,
                keyId = keyId,
                signerCertificateChain = chain,
                tsaConfig = TsaConfig(endpointUrl = server.url("/tsa").toString()),
            ),
        )
        val result = verifier.verify(
            signature.serializedFlattened,
            JadesVerificationOptions(
                requiredProfile = JadesProfile.B_LT,
                trustAnchorResolver = resolverFor(ca.caCert),
            ),
        )
        assertTrue(result is Invalid.WrongProfile, "got $result")
        result as Invalid.WrongProfile
        assertEquals(JadesProfile.B_T, result.found)
        assertEquals(JadesProfile.B_LT, result.required)
    }

    @Test
    fun `B-LTA signature is accepted when verifier only requires B-T (strict-superset rule)`() = runBlocking {
        val keyId = generateKey(Algorithm.Ed25519)
        val chain = ca.issueChainBytes(kms.publicKey(keyId), "CN=B-LTA Signer")
        val signature = DefaultJadesSigner(kms).sign(
            buildJsonObject { put("k", JsonPrimitive("v")) },
            JadesSigningRequest(
                profile = JadesProfile.B_LTA,
                keyId = keyId,
                signerCertificateChain = chain,
                tsaConfig = TsaConfig(endpointUrl = server.url("/tsa").toString()),
                validationData = ValidationData(completeCertificateChain = chain),
            ),
        )
        val result = verifier.verify(
            signature.serializedFlattened,
            JadesVerificationOptions(
                requiredProfile = JadesProfile.B_T,
                trustAnchorResolver = resolverFor(ca.caCert),
            ),
        )
        assertTrue(result is Valid, "got $result")
        assertEquals(JadesProfile.B_LTA, (result as Valid).foundProfile)
    }

    // ---------------------------------------------------------------- helpers

    private suspend fun generateKey(algorithm: Algorithm): KeyId {
        val keyId = "test-${algorithm.name}-${System.nanoTime()}"
        val result = kms.generateKey(algorithm, mapOf("keyId" to keyId))
        return when (result) {
            is GenerateKeyResult.Success -> result.keyHandle.id
            else -> error("KMS keygen failed: $result")
        }
    }

    // Synthetic byte blobs — the verifier only does base64 + type checks for MVP, so the
    // contents don't need to be real CRL / OCSP structures.
    private fun makeDummyCrlBytes(): ByteArray = ByteArray(64) { it.toByte() }
    private fun makeDummyOcspBytes(): ByteArray = ByteArray(96) { (255 - it).toByte() }

    private fun resolverFor(trustedCa: X509Certificate): DefaultTrustAnchorResolver {
        val service = TspService(
            serviceName = "Test CA",
            serviceType = TspServiceType.CA_FOR_QUALIFIED_CERTIFICATES,
            status = TspServiceStatus.GRANTED,
            statusStartingTime = kotlinx.datetime.Clock.System.now(),
            serviceCertificates = listOf(trustedCa),
            qualifierUris = listOf(QualifierUris.QC_WITH_SSCD, QualifierUris.QC_FOR_ESIG),
        )
        val trustList = TrustList(
            schemeOperator = "Test",
            sequenceNumber = 1,
            issuedAt = kotlinx.datetime.Clock.System.now(),
            nextUpdateAt = null,
            memberStateLists = listOf(
                MemberStateTsl(
                    territory = "EU",
                    schemeOperator = "Test",
                    sequenceNumber = 1,
                    issuedAt = kotlinx.datetime.Clock.System.now(),
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
