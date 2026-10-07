package org.trustweave.signatures.cades

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.signatures.cades.CadesValidationResult.Invalid
import org.trustweave.signatures.cades.CadesValidationResult.Valid
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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/** What a CAdES verifier may conclude from a time-stamp, a withdrawn service and revocation evidence. */
class CadesTrustTest {
    private lateinit var kms: TestKms
    private lateinit var ca: TestCa
    private lateinit var tsa: TestTsa
    private lateinit var otherTsa: TestTsa
    private lateinit var server: MockWebServer
    private val verifier = DefaultCadesVerifier()
    private val payload = "payload".toByteArray()

    @BeforeEach
    fun setUp() {
        kms = TestKms()
        ca = TestCa()
        tsa = TestTsa.generate()
        otherTsa = TestTsa.generate()
        server = MockWebServer().apply { start() }
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse =
                    MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/timestamp-reply")
                        .setBody(Buffer().apply { write(tsa.stamp(request.body.readByteArray())) })
            }
    }

    @AfterEach
    fun tearDown() = server.shutdown()

    private fun active() =
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

    private fun resolver(match: () -> TrustAnchorMatch) =
        object : TrustAnchorResolver {
            override fun resolve(
                signerCert: X509Certificate,
                chain: List<X509Certificate>,
            ): TrustAnchorMatch = match()
        }

    private suspend fun keyId(): KeyId =
        when (val r = kms.generateKey(Algorithm.P256, mapOf("keyId" to "cades-trust-${System.nanoTime()}"))) {
            is GenerateKeyResult.Success -> r.keyHandle.id
            else -> error("KMS keygen failed: $r")
        }

    private data class Signed(
        val cms: ByteArray,
        val signer: X509Certificate,
    )

    private suspend fun signed(profile: CadesProfile): Signed {
        val id = keyId()
        val chain = ca.issueChainBytes(kms.publicKey(id), "CN=CAdES Trust Signer")
        val signature =
            DefaultCadesSigner(kms).sign(
                CadesSigningRequest(
                    profile = profile,
                    keyId = id,
                    payload = payload,
                    signerCertificateChain = chain,
                    tsaConfig = TsaConfig(endpointUrl = server.url("/tsa").toString()).takeIf { profile == CadesProfile.B_T },
                    detached = true,
                ),
            )
        val signer =
            java.security.cert.CertificateFactory
                .getInstance(
                    "X.509",
                ).generateCertificate(chain.first().inputStream()) as X509Certificate
        return Signed(signature.encoded, signer)
    }

    private fun options(
        required: CadesProfile,
        match: () -> TrustAnchorMatch = ::active,
        anchors: List<X509Certificate> = listOf(tsa.cert),
        allowWithdrawn: Boolean = false,
    ) = CadesVerificationOptions(
        requiredProfile = required,
        trustAnchorResolver = resolver(match),
        detachedPayload = payload,
        timestampTrustAnchors = anchors,
        allowWithdrawnTrustWithoutAuthenticatedTime = allowWithdrawn,
    )

    // ------------------------------------------------------------------ time-stamps

    @Test
    fun `a time-stamp from a trusted TSA authenticates the time and yields B-T`() =
        runBlocking<Unit> {
            val result = verifier.verify(signed(CadesProfile.B_T).cms, options(CadesProfile.B_T))
            assertTrue(result is Valid, "got $result")
            assertEquals(CadesProfile.B_T, (result as Valid).profile)
            assertTrue(result.signatureTimeStamp != null)
        }

    @Test
    fun `a time-stamp is not trusted when no TSA anchors are configured`() =
        runBlocking<Unit> {
            val s = signed(CadesProfile.B_T)
            val lenient = verifier.verify(s.cms, options(CadesProfile.B_B, anchors = emptyList()))
            assertTrue(lenient is Valid, "got $lenient")
            assertNull((lenient as Valid).signatureTimeStamp, "an unverified time-stamp must not count")
            assertEquals(CadesProfile.B_B, lenient.profile)
            val strict = verifier.verify(s.cms, options(CadesProfile.B_T, anchors = emptyList()))
            assertTrue(strict is Invalid.WrongProfile, "got $strict")
        }

    @Test
    fun `a time-stamp from a TSA that is not trusted is rejected`() =
        runBlocking<Unit> {
            val result = verifier.verify(signed(CadesProfile.B_T).cms, options(CadesProfile.B_T, anchors = listOf(otherTsa.cert)))
            assertTrue(result is Invalid.TimeStampMismatch, "got $result")
        }

    // ------------------------------------------------------------------ withdrawn trust

    private fun withdrawn(at: kotlin.time.Instant) = { TrustAnchorMatch.QualifiedWithdrawn("Test TSP", at) as TrustAnchorMatch }

    @Test
    fun `a withdrawn service is refused by default and accepted only when allowed and the claimed time predates it`() =
        runBlocking<Unit> {
            val s = signed(CadesProfile.B_B)
            val future = withdrawn(Clock.System.now() + 1.days)
            assertTrue(verifier.verify(s.cms, options(CadesProfile.B_B, future)) is Invalid.TrustWithdrawn)
            assertTrue(verifier.verify(s.cms, options(CadesProfile.B_B, future, allowWithdrawn = true)) is Valid)
            val past = withdrawn(Clock.System.now() - 1.days)
            assertTrue(verifier.verify(s.cms, options(CadesProfile.B_B, past, allowWithdrawn = true)) is Invalid.TrustWithdrawn)
        }

    @Test
    fun `a trusted time-stamp before the withdrawal keeps the signature valid, one after it does not`() =
        runBlocking<Unit> {
            val s = signed(CadesProfile.B_T)
            assertTrue(verifier.verify(s.cms, options(CadesProfile.B_T, withdrawn(Clock.System.now() + 1.days))) is Valid)
            assertTrue(verifier.verify(s.cms, options(CadesProfile.B_T, withdrawn(Clock.System.now() - 1.days))) is Invalid.TrustWithdrawn)
        }

    // ------------------------------------------------------------------ revocation

    @Test
    fun `caller-supplied revocation evidence is enforced`() =
        runBlocking<Unit> {
            val s = signed(CadesProfile.B_B)
            val good = RevocationFixtures.crl(ca.caCert, ca.caPrivateKey())
            val revoked = RevocationFixtures.crl(ca.caCert, ca.caPrivateKey(), listOf(s.signer.serialNumber))

            fun withEvidence(
                policy: RevocationPolicy,
                evidence: RevocationEvidence,
            ) = options(CadesProfile.B_B).copy(revocationPolicy = policy, revocationEvidence = evidence)

            val ok = verifier.verify(s.cms, withEvidence(RevocationPolicy.REQUIRED, RevocationEvidence(crls = listOf(good))))
            assertTrue(ok is Valid && ok.revocationChecked, "got $ok")
            assertTrue(
                verifier.verify(
                    s.cms,
                    withEvidence(RevocationPolicy.REQUIRED, RevocationEvidence(crls = listOf(revoked))),
                ) is Invalid.CertificateRevoked,
            )
            assertTrue(
                verifier.verify(s.cms, withEvidence(RevocationPolicy.REQUIRED, RevocationEvidence.NONE)) is Invalid.RevocationUnavailable,
            )
            val lenient = verifier.verify(s.cms, withEvidence(RevocationPolicy.CHECK_IF_AVAILABLE, RevocationEvidence.NONE))
            assertTrue(lenient is Valid && !lenient.revocationChecked, "got $lenient")
            assertFalse(verifier.verify(s.cms, options(CadesProfile.B_B)).let { it is Valid && it.revocationChecked })
        }
}
