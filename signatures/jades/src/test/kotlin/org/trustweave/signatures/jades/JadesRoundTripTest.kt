package org.trustweave.signatures.jades

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
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

class JadesRoundTripTest {
    private lateinit var kms: TestKms
    private lateinit var ca: TestCa
    private val verifier = DefaultJadesVerifier()

    @BeforeEach
    fun setUp() {
        kms = TestKms()
        ca = TestCa()
    }

    // ---------------------------------------------------------------- B-B happy paths

    @Test
    fun `roundtrips a JAdES B-B signature with Ed25519`() =
        runBlocking {
            val keyId = generateKey(Algorithm.Ed25519)
            val cert = ca.issueChainBytes(kms.publicKey(keyId), "CN=Ed25519 Signer")

            val signature =
                DefaultJadesSigner(kms).sign(
                    payloadJson = buildJsonObject { put("hello", JsonPrimitive("world")) },
                    request =
                        JadesSigningRequest(
                            profile = JadesProfile.B_B,
                            keyId = keyId,
                            signerCertificateChain = cert,
                        ),
                )

            assertTrue(signature.unsigned.sigTst.isEmpty(), "B-B should have empty sigTst")
            assertNotNull(signature.compact(), "B-B should expose compact serialization")

            val result =
                verifier.verify(
                    signature.serializedFlattened,
                    JadesVerificationOptions(
                        requiredProfile = JadesProfile.B_B,
                        trustAnchorResolver = resolverFor(ca.caCert),
                    ),
                )
            assertTrue(result is Valid, "expected Valid, got $result")
            result as Valid
            assertEquals("EdDSA", result.header.alg)
            assertEquals(keyId.value, result.header.kid)
            assertNull(result.signatureTimeStamp, "B-B should not have a signature time-stamp")
            val payloadObj = result.payload as JsonObject
            assertEquals("world", payloadObj["hello"]!!.jsonPrimitive.content)
        }

    @Test
    fun `a signature naming an unsupported critical extension is rejected`() =
        runBlocking {
            // RFC 7515 4.1.11: crit lists extensions the verifier MUST understand and process, and a
            // verifier that does not understand one MUST reject the signature. This verifier supports
            // no critical extensions, so any crit at all has to be refused - otherwise a signer's
            // critical semantics are silently dropped while the signature still reports Valid.
            val keyId = generateKey(Algorithm.Ed25519)
            val cert = ca.issueChainBytes(kms.publicKey(keyId), "CN=Crit Signer")

            val signature =
                DefaultJadesSigner(kms).sign(
                    payloadJson = buildJsonObject { put("hello", JsonPrimitive("world")) },
                    request =
                        JadesSigningRequest(
                            profile = JadesProfile.B_B,
                            keyId = keyId,
                            signerCertificateChain = cert,
                            additionalHeaders =
                                mapOf(
                                    "crit" to
                                        kotlinx.serialization.json.JsonArray(
                                            listOf(JsonPrimitive("urn:example:unsupported-constraint")),
                                        ),
                                    "urn:example:unsupported-constraint" to JsonPrimitive("must-be-honoured"),
                                ),
                        ),
                )

            val result =
                verifier.verify(
                    signature.serializedFlattened,
                    JadesVerificationOptions(
                        requiredProfile = JadesProfile.B_B,
                        trustAnchorResolver = resolverFor(ca.caCert),
                    ),
                )

            assertTrue(result !is Valid, "An unsupported crit extension must not verify, got $result")
        }

    @Test
    fun `a signature whose x5t#S256 does not match the certificate is rejected`() =
        runBlocking {
            // x5t#S256 is the SHA-256 of the signing certificate and is covered by the signature. It
            // exists to pin which certificate signed; a verifier that requires it to be present but
            // never compares it to the certificate in x5c is not using the binding at all.
            val keyId = generateKey(Algorithm.Ed25519)
            val cert = ca.issueChainBytes(kms.publicKey(keyId), "CN=Thumbprint Signer")

            val signature =
                DefaultJadesSigner(kms).sign(
                    payloadJson = buildJsonObject { put("hello", JsonPrimitive("world")) },
                    request =
                        JadesSigningRequest(
                            profile = JadesProfile.B_B,
                            keyId = keyId,
                            signerCertificateChain = cert,
                            // additionalHeaders are written last, so this replaces the computed thumbprint.
                            // The signature over the header is still perfectly valid.
                            additionalHeaders =
                                mapOf(
                                    "x5t#S256" to JsonPrimitive("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
                                ),
                        ),
                )

            val result =
                verifier.verify(
                    signature.serializedFlattened,
                    JadesVerificationOptions(
                        requiredProfile = JadesProfile.B_B,
                        trustAnchorResolver = resolverFor(ca.caCert),
                    ),
                )

            assertTrue(
                result !is Valid,
                "A thumbprint that does not match the signing certificate must not verify, got $result",
            )
        }

    @Test
    fun `roundtrips a JAdES B-B signature with ES256 (P-256)`() =
        runBlocking {
            val keyId = generateKey(Algorithm.P256)
            val cert = ca.issueChainBytes(kms.publicKey(keyId), "CN=P256 Signer")

            val signature =
                DefaultJadesSigner(kms).sign(
                    payloadJson = buildJsonObject { put("k", JsonPrimitive("v")) },
                    request =
                        JadesSigningRequest(
                            profile = JadesProfile.B_B,
                            keyId = keyId,
                            signerCertificateChain = cert,
                        ),
                )
            val result =
                verifier.verify(
                    signature.serializedFlattened,
                    JadesVerificationOptions(
                        requiredProfile = JadesProfile.B_B,
                        trustAnchorResolver = resolverFor(ca.caCert),
                    ),
                )
            assertTrue(result is Valid, "got $result")
            assertEquals("ES256", (result as Valid).header.alg)
        }

    @Test
    fun `JWS compact serialization round-trips for B-B`() =
        runBlocking {
            val keyId = generateKey(Algorithm.Ed25519)
            val cert = ca.issueChainBytes(kms.publicKey(keyId), "CN=Ed25519 Signer")

            val signature =
                DefaultJadesSigner(kms).sign(
                    buildJsonObject { put("x", JsonPrimitive(42)) },
                    JadesSigningRequest(JadesProfile.B_B, keyId, cert),
                )
            val compact = signature.compact()!!
            val result =
                verifier.verify(
                    compact,
                    JadesVerificationOptions(
                        requiredProfile = JadesProfile.B_B,
                        trustAnchorResolver = resolverFor(ca.caCert),
                    ),
                )
            assertTrue(result is Valid, "got $result")
        }

    // ---------------------------------------------------------------- B-T happy path

    @Test
    fun `roundtrips a JAdES B-T signature using an in-process TSA`() =
        runBlocking {
            val tsa = TestTsa.generate()
            val server = MockWebServer().apply { start() }
            try {
                server.dispatcher = stampingDispatcher(tsa)

                val keyId = generateKey(Algorithm.Ed25519)
                val cert = ca.issueChainBytes(kms.publicKey(keyId), "CN=B-T Signer")

                val signature =
                    DefaultJadesSigner(kms).sign(
                        payloadJson = buildJsonObject { put("profile", JsonPrimitive("B-T")) },
                        request =
                            JadesSigningRequest(
                                profile = JadesProfile.B_T,
                                keyId = keyId,
                                signerCertificateChain = cert,
                                tsaConfig = TsaConfig(endpointUrl = server.url("/tsa").toString()),
                            ),
                    )
                assertTrue(signature.unsigned.sigTst.isNotEmpty(), "B-T must embed at least one sigTst")
                assertNull(signature.compact(), "B-T cannot be expressed as JWS Compact")

                val result =
                    verifier.verify(
                        signature.serializedFlattened,
                        JadesVerificationOptions(
                            requiredProfile = JadesProfile.B_T,
                            trustAnchorResolver = resolverFor(ca.caCert),
                        ),
                    )
                assertTrue(result is Valid, "got $result")
                assertNotNull((result as Valid).signatureTimeStamp)
            } finally {
                server.shutdown()
            }
        }

    // ---------------------------------------------------------------- error paths

    @Test
    fun `untrusted signer resolves to UntrustedSigner`() =
        runBlocking {
            val keyId = generateKey(Algorithm.Ed25519)
            val cert = ca.issueChainBytes(kms.publicKey(keyId), "CN=Anyone")

            val signature =
                DefaultJadesSigner(kms).sign(
                    buildJsonObject { put("x", JsonPrimitive(1)) },
                    JadesSigningRequest(JadesProfile.B_B, keyId, cert),
                )
            val otherCa = TestCa(caSubject = "CN=Unrelated CA")
            val result =
                verifier.verify(
                    signature.serializedFlattened,
                    JadesVerificationOptions(
                        requiredProfile = JadesProfile.B_B,
                        trustAnchorResolver = resolverFor(otherCa.caCert),
                    ),
                )
            assertTrue(result is Invalid.UntrustedSigner, "got $result")
        }

    @Test
    fun `tampered payload fails BadSignature`() =
        runBlocking {
            val keyId = generateKey(Algorithm.Ed25519)
            val cert = ca.issueChainBytes(kms.publicKey(keyId), "CN=Anyone")

            val signature =
                DefaultJadesSigner(kms).sign(
                    buildJsonObject { put("a", JsonPrimitive(1)) },
                    JadesSigningRequest(JadesProfile.B_B, keyId, cert),
                )
            val tampered =
                signature.serializedFlattened.replace(
                    "\"payload\":\"${signature.payloadB64u}\"",
                    "\"payload\":\"" +
                        java.util.Base64
                            .getUrlEncoder()
                            .withoutPadding()
                            .encodeToString("{\"a\":2}".toByteArray()) + "\"",
                )
            val result =
                verifier.verify(
                    tampered,
                    JadesVerificationOptions(
                        requiredProfile = JadesProfile.B_B,
                        trustAnchorResolver = resolverFor(ca.caCert),
                    ),
                )
            assertTrue(result is Invalid.BadSignature, "got $result")
        }

    @Test
    fun `requiring B-T but receiving B-B yields WrongProfile`() =
        runBlocking {
            val keyId = generateKey(Algorithm.Ed25519)
            val cert = ca.issueChainBytes(kms.publicKey(keyId), "CN=Anyone")

            val signature =
                DefaultJadesSigner(kms).sign(
                    buildJsonObject { put("a", JsonPrimitive(1)) },
                    JadesSigningRequest(JadesProfile.B_B, keyId, cert),
                )
            val result =
                verifier.verify(
                    signature.serializedFlattened,
                    JadesVerificationOptions(
                        requiredProfile = JadesProfile.B_T,
                        trustAnchorResolver = resolverFor(ca.caCert),
                    ),
                )
            assertTrue(result is Invalid.WrongProfile, "got $result")
            result as Invalid.WrongProfile
            assertEquals(JadesProfile.B_B, result.found)
            assertEquals(JadesProfile.B_T, result.required)
        }

    @Test
    fun `garbage input yields Malformed`() =
        runBlocking {
            val result =
                verifier.verify(
                    "not a jws",
                    JadesVerificationOptions(
                        requiredProfile = JadesProfile.B_B,
                        trustAnchorResolver = resolverFor(ca.caCert),
                    ),
                )
            assertTrue(result is Invalid.Malformed, "got $result")
        }

    @Test
    fun `disallowed algorithm is rejected as BadSignature`() =
        runBlocking {
            val keyId = generateKey(Algorithm.Ed25519)
            val cert = ca.issueChainBytes(kms.publicKey(keyId), "CN=Anyone")
            val signature =
                DefaultJadesSigner(kms).sign(
                    buildJsonObject { put("a", JsonPrimitive(1)) },
                    JadesSigningRequest(JadesProfile.B_B, keyId, cert),
                )
            val result =
                verifier.verify(
                    signature.serializedFlattened,
                    JadesVerificationOptions(
                        requiredProfile = JadesProfile.B_B,
                        trustAnchorResolver = resolverFor(ca.caCert),
                        acceptedAlgorithms = setOf("ES256"),
                    ),
                )
            assertTrue(result is Invalid.BadSignature, "got $result")
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
        val service =
            TspService(
                serviceName = "Test CA",
                serviceType = TspServiceType.CA_FOR_QUALIFIED_CERTIFICATES,
                status = TspServiceStatus.GRANTED,
                statusStartingTime = Clock.System.now(),
                serviceCertificates = listOf(trustedCa),
                qualifierUris = listOf(QualifierUris.QC_WITH_SSCD, QualifierUris.QC_FOR_ESIG),
            )
        val trustList =
            TrustList(
                schemeOperator = "Test",
                sequenceNumber = 1,
                issuedAt = Clock.System.now(),
                nextUpdateAt = null,
                memberStateLists =
                    listOf(
                        MemberStateTsl(
                            territory = "EU",
                            schemeOperator = "Test",
                            sequenceNumber = 1,
                            issuedAt = Clock.System.now(),
                            trustedTsps =
                                listOf(
                                    TrustedTSP(name = "Test TSP", tradeName = null, services = listOf(service)),
                                ),
                        ),
                    ),
            )
        return DefaultTrustAnchorResolver(trustList)
    }

    private fun stampingDispatcher(tsa: TestTsa): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val responseBytes = tsa.stamp(request.body.readByteArray())
                return MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/timestamp-reply")
                    .setBody(Buffer().apply { write(responseBytes) })
            }
        }
}
