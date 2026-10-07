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
import org.trustweave.signatures.revocation.RevocationEvidence
import org.trustweave.signatures.revocation.RevocationPolicy
import org.trustweave.signatures.trustlists.QualifierUris
import org.trustweave.signatures.trustlists.TrustAnchorMatch
import org.trustweave.signatures.trustlists.TrustAnchorResolver
import org.trustweave.signatures.trustlists.TspService
import org.trustweave.signatures.trustlists.TspServiceStatus
import org.trustweave.signatures.trustlists.TspServiceType
import org.trustweave.signatures.tsa.TsaConfig
import java.security.cert.X509Certificate
import java.util.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/** What a JAdES verifier may and may not conclude from a time-stamp, a withdrawn service and embedded evidence. */
class JadesTrustTest {
    private lateinit var kms: TestKms
    private lateinit var ca: TestCa
    private lateinit var tsa: TestTsa
    private lateinit var otherTsa: TestTsa
    private lateinit var server: MockWebServer
    private val verifier = DefaultJadesVerifier()

    @BeforeEach
    fun setUp() {
        kms = TestKms()
        ca = TestCa()
        tsa = TestTsa.generate()
        otherTsa = TestTsa.generate()
        server = MockWebServer().apply { start() }
        server.dispatcher = dispatcher(tsa)
    }

    @AfterEach
    fun tearDown() = server.shutdown()

    private fun dispatcher(source: TestTsa) =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/timestamp-reply")
                    .setBody(Buffer().apply { write(source.stamp(request.body.readByteArray())) })
        }

    private fun resolver(match: () -> TrustAnchorMatch) =
        object : TrustAnchorResolver {
            override fun resolve(
                signerCert: X509Certificate,
                chain: List<X509Certificate>,
            ): TrustAnchorMatch = match()
        }

    private fun active() =
        TrustAnchorMatch.QualifiedActive(
            tspName = "Test TSP",
            territory = "EU",
            service =
                TspService(
                    serviceName = "Test CA",
                    serviceType = TspServiceType.CA_FOR_QUALIFIED_CERTIFICATES,
                    status = TspServiceStatus.GRANTED,
                    statusStartingTime = Clock.System.now(),
                    serviceCertificates = listOf(ca.caCert),
                    qualifierUris = listOf(QualifierUris.QC_WITH_SSCD),
                ),
            qcWithSscd = true,
            qcForESig = true,
        )

    private suspend fun keyId(): KeyId =
        when (val r = kms.generateKey(Algorithm.Ed25519, mapOf("keyId" to "trust-${System.nanoTime()}"))) {
            is GenerateKeyResult.Success -> r.keyHandle.id
            else -> error("KMS keygen failed: $r")
        }

    private suspend fun signed(
        profile: JadesProfile,
        validationData: ValidationData? = null,
    ): String {
        val id = keyId()
        val chain = ca.issueChainBytes(kms.publicKey(id), "CN=Trust Signer")
        return DefaultJadesSigner(kms)
            .sign(
                buildJsonObject { put("k", JsonPrimitive("v")) },
                JadesSigningRequest(
                    profile = profile,
                    keyId = id,
                    signerCertificateChain = chain,
                    tsaConfig = TsaConfig(endpointUrl = server.url("/tsa").toString()).takeIf { profile != JadesProfile.B_B },
                    validationData = validationData ?: ValidationData(completeCertificateChain = chain).takeIf { profile.atLeast(JadesProfile.B_LT) },
                ),
            ).serializedFlattened
    }

    private fun options(
        required: JadesProfile,
        match: () -> TrustAnchorMatch = ::active,
        anchors: List<X509Certificate> = listOf(tsa.cert),
        allowWithdrawn: Boolean = false,
    ) = JadesVerificationOptions(
        requiredProfile = required,
        trustAnchorResolver = resolver(match),
        timestampTrustAnchors = anchors,
        allowWithdrawnTrustWithoutAuthenticatedTime = allowWithdrawn,
    )

    // ------------------------------------------------------------------ time-stamps

    @Test
    fun `a time-stamp from a trusted TSA authenticates the time and yields B-T`() =
        runBlocking<Unit> {
            val result = verifier.verify(signed(JadesProfile.B_T), options(JadesProfile.B_T))
            assertTrue(result is Valid, "got $result")
            assertNotNull((result as Valid).signatureTimeStamp)
            assertEquals(JadesProfile.B_T, result.foundProfile)
        }

    @Test
    fun `a structurally valid time-stamp is not trusted when no TSA anchors are configured`() =
        runBlocking<Unit> {
            val envelope = signed(JadesProfile.B_T)
            val lenient = verifier.verify(envelope, options(JadesProfile.B_B, anchors = emptyList()))
            assertTrue(lenient is Valid, "got $lenient")
            assertNull((lenient as Valid).signatureTimeStamp, "an unverified time-stamp must not count")
            assertEquals(JadesProfile.B_B, lenient.foundProfile)
            val strict = verifier.verify(envelope, options(JadesProfile.B_T, anchors = emptyList()))
            assertTrue(strict is Invalid.WrongProfile, "got $strict")
        }

    @Test
    fun `a time-stamp from a TSA that is not trusted is rejected`() =
        runBlocking<Unit> {
            // The signature is stamped by `tsa`; the verifier only trusts a different TSA, i.e. an attacker
            // who can mint a token with a convenient genTime gets nothing.
            val result = verifier.verify(signed(JadesProfile.B_T), options(JadesProfile.B_T, anchors = listOf(otherTsa.cert)))
            assertTrue(result is Invalid.TimeStampMismatch, "got $result")
        }

    // ------------------------------------------------------------------ withdrawn trust

    private fun withdrawn(at: kotlin.time.Instant) = { TrustAnchorMatch.QualifiedWithdrawn("Test TSP", at) as TrustAnchorMatch }

    @Test
    fun `a withdrawn service is refused by default`() =
        runBlocking<Unit> {
            val result = verifier.verify(signed(JadesProfile.B_B), options(JadesProfile.B_B, withdrawn(Clock.System.now() + 1.days)))
            assertTrue(result is Invalid.TrustWithdrawn, "got $result")
        }

    @Test
    fun `a withdrawn service is accepted without a time-stamp only when allowed and the claimed time predates it`() =
        runBlocking<Unit> {
            val envelope = signed(JadesProfile.B_B)
            val ok = verifier.verify(envelope, options(JadesProfile.B_B, withdrawn(Clock.System.now() + 1.days), allowWithdrawn = true))
            assertTrue(ok is Valid, "got $ok")
            val late = verifier.verify(envelope, options(JadesProfile.B_B, withdrawn(Clock.System.now() - 1.days), allowWithdrawn = true))
            assertTrue(late is Invalid.TrustWithdrawn, "got $late")
        }

    @Test
    fun `a trusted time-stamp before the withdrawal keeps the signature valid, one after it does not`() =
        runBlocking<Unit> {
            val envelope = signed(JadesProfile.B_T)
            val before = verifier.verify(envelope, options(JadesProfile.B_T, withdrawn(Clock.System.now() + 1.days)))
            assertTrue(before is Valid, "got $before")
            val after = verifier.verify(envelope, options(JadesProfile.B_T, withdrawn(Clock.System.now() - 1.days)))
            assertTrue(after is Invalid.TrustWithdrawn, "got $after")
        }

    // ------------------------------------------------------------------ B-LT is proven, not claimed

    @Test
    fun `garbage rVals do not make a signature B-LT`() =
        runBlocking<Unit> {
            val id = keyId()
            val chain = ca.issueChainBytes(kms.publicKey(id), "CN=Garbage Evidence")
            val envelope =
                DefaultJadesSigner(kms)
                    .sign(
                        buildJsonObject { put("k", JsonPrimitive("v")) },
                        JadesSigningRequest(
                            profile = JadesProfile.B_LT,
                            keyId = id,
                            signerCertificateChain = chain,
                            tsaConfig = TsaConfig(endpointUrl = server.url("/tsa").toString()),
                            validationData =
                                ValidationData(
                                    completeCertificateChain = chain,
                                    revocationData =
                                        listOf(EncodedRevocationData("CRL", Base64.getEncoder().encodeToString(ByteArray(64) { it.toByte() }))),
                                ),
                        ),
                    ).serializedFlattened
            // Requiring B-LT implies REQUIRED revocation, and nothing in the garbage can satisfy it.
            val strict = verifier.verify(envelope, options(JadesProfile.B_LT))
            assertTrue(strict is Invalid.RevocationUnavailable, "got $strict")
            // Asking only for B-T accepts the signature but must not call it B-LT.
            val lenient = verifier.verify(envelope, options(JadesProfile.B_T))
            assertTrue(lenient is Valid, "got $lenient")
            assertEquals(JadesProfile.B_T, (lenient as Valid).foundProfile)
        }

    @Test
    fun `evidence supplied only by the caller does not upgrade a B-T signature to B-LT`() =
        runBlocking<Unit> {
            val envelope = signed(JadesProfile.B_T)
            val good = RevocationFixtures.crl(ca.caCert, ca.caPrivateKey(), thisUpdate = java.util.Date())
            val opts =
                options(JadesProfile.B_T).copy(
                    revocationPolicy = RevocationPolicy.REQUIRED,
                    revocationEvidence = RevocationEvidence(crls = listOf(good)),
                )
            val result = verifier.verify(envelope, opts)
            assertTrue(result is Valid, "got $result")
            assertEquals(JadesProfile.B_T, (result as Valid).foundProfile)
            assertTrue(result.revocationChecked)
        }
}
