package org.trustweave.signatures.etsi.validation

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.signatures.etsi.validation.EtsiValidationReport.FinalVerdict
import org.trustweave.signatures.jades.DefaultJadesSigner
import org.trustweave.signatures.jades.JadesProfile
import org.trustweave.signatures.jades.JadesSigningRequest
import org.trustweave.signatures.jades.JadesValidationResult
import org.trustweave.signatures.jades.JadesValidationResult.Invalid
import org.trustweave.signatures.jades.JadesVerificationOptions
import org.trustweave.signatures.jades.JadesVerifier
import org.trustweave.signatures.revocation.RevocationPolicy
import org.trustweave.signatures.trustlists.DefaultTrustAnchorResolver
import org.trustweave.signatures.trustlists.MemberStateTsl
import org.trustweave.signatures.trustlists.TrustList
import kotlin.time.Clock

/**
 * Drives the validator with a stub [JadesVerifier] so every [Invalid] subtype reaches the per-step mapping, which the
 * real verifier cannot do for all of them on one envelope. The point is that no verifier outcome is silently treated
 * as a pass: a rejected signature never yields TOTAL_PASSED, and each step reports what it actually evaluated.
 */
class EtsiInvalidResultMappingTest {
    private lateinit var kms: TestKms
    private lateinit var ca: TestCa
    private lateinit var envelope: String

    @BeforeEach
    fun setUp() =
        runBlocking<Unit> {
            kms = TestKms()
            ca = TestCa()
            val keyId =
                when (val r = kms.generateKey(Algorithm.Ed25519, mapOf("keyId" to "mapping-key"))) {
                    is GenerateKeyResult.Success -> r.keyHandle.id
                    else -> error("KMS keygen failed: $r")
                }
            val chain = ca.issueChainBytes(kms.publicKey(keyId), "CN=Mapping Signer")
            envelope =
                DefaultJadesSigner(kms)
                    .sign(
                        payloadJson = buildJsonObject { put("hello", JsonPrimitive("world")) },
                        request = JadesSigningRequest(profile = JadesProfile.B_B, keyId = keyId, signerCertificateChain = chain),
                    ).serializedFlattened
        }

    private fun report(result: JadesValidationResult): EtsiValidationReport {
        val stub =
            object : JadesVerifier {
                override suspend fun verify(
                    jadesSerialized: String,
                    options: JadesVerificationOptions,
                ) = result
            }
        val policy = EtsiSignaturePolicy(revocationPolicy = RevocationPolicy.CHECK_IF_AVAILABLE)
        val resolver =
            DefaultTrustAnchorResolver(
                TrustList(
                    schemeOperator = "Test",
                    sequenceNumber = 1,
                    issuedAt = Clock.System.now(),
                    nextUpdateAt = null,
                    memberStateLists = emptyList<MemberStateTsl>(),
                ),
            )
        return runBlocking { DefaultEtsiSignatureValidator(stub).validate(envelope, policy, resolver) }
    }

    private fun outcome(
        r: EtsiValidationReport,
        step: EtsiValidationStep,
    ) = r.steps.getValue(step)

    private val someCert get() = ca.caCert
    private val now get() = Clock.System.now()

    @Test
    fun `results rejected after the signature was checked keep the crypto step passed`() {
        listOf(
            Invalid.UntrustedSigner(someCert),
            Invalid.TrustWithdrawn(someCert, now, "withdrawn"),
            Invalid.SignerCertificateInvalid("not yet valid"),
            Invalid.TimeStampMismatch("bad imprint"),
            Invalid.MissingTimeStamp("none"),
            Invalid.CertificateExpired(now),
            Invalid.CertificateRevoked(someCert, now, "revoked"),
            Invalid.RevocationUnavailable("no evidence"),
        ).forEach { result ->
            val r = report(result)
            assertTrue(outcome(r, EtsiValidationStep.SIGNATURE_ACCEPTANCE) is StepOutcome.Passed, "$result")
            assertNotEquals(FinalVerdict.TOTAL_PASSED, r.finalVerdict, "$result")
        }
    }

    @Test
    fun `a wrong profile leaves the crypto step inconclusive rather than passed`() {
        val r = report(Invalid.WrongProfile(found = JadesProfile.B_B, required = JadesProfile.B_LT))
        assertTrue(outcome(r, EtsiValidationStep.SIGNATURE_ACCEPTANCE) is StepOutcome.Inconclusive)
        assertNotEquals(FinalVerdict.TOTAL_PASSED, r.finalVerdict)
    }

    @Test
    fun `bad signature and malformed fail the crypto step and skip the later ones`() {
        listOf(Invalid.BadSignature("bad"), Invalid.Malformed("broken")).forEach { result ->
            val r = report(result)
            assertTrue(outcome(r, EtsiValidationStep.SIGNATURE_ACCEPTANCE) is StepOutcome.Failed, "$result")
            assertTrue(outcome(r, EtsiValidationStep.X509_CERT_PATH) is StepOutcome.Inconclusive, "$result")
            assertTrue(outcome(r, EtsiValidationStep.REVOCATION) is StepOutcome.Inconclusive, "$result")
            assertTrue(outcome(r, EtsiValidationStep.SIGNING_TIME_VALIDITY) is StepOutcome.Inconclusive, "$result")
        }
    }

    @Test
    fun `revocation step fails for revoked and unavailable and is inconclusive for other rejections`() {
        assertTrue(outcome(report(Invalid.CertificateRevoked(someCert, now, "r")), EtsiValidationStep.REVOCATION) is StepOutcome.Failed)
        assertTrue(outcome(report(Invalid.RevocationUnavailable("u")), EtsiValidationStep.REVOCATION) is StepOutcome.Failed)
        listOf(
            Invalid.UntrustedSigner(someCert),
            Invalid.WrongProfile(JadesProfile.B_B, JadesProfile.B_LT),
            Invalid.MissingTimeStamp("m"),
            Invalid.TimeStampMismatch("t"),
            Invalid.CertificateExpired(now),
            Invalid.SignerCertificateInvalid("s"),
            Invalid.TrustWithdrawn(someCert, now, "w"),
        ).forEach { result ->
            assertTrue(outcome(report(result), EtsiValidationStep.REVOCATION) is StepOutcome.Inconclusive, "$result")
        }
    }

    @Test
    fun `signing time step fails only for an expired certificate`() {
        assertTrue(outcome(report(Invalid.CertificateExpired(now)), EtsiValidationStep.SIGNING_TIME_VALIDITY) is StepOutcome.Failed)
        listOf(
            Invalid.UntrustedSigner(someCert),
            Invalid.SignerCertificateInvalid("s"),
            Invalid.TrustWithdrawn(someCert, now, "w"),
            Invalid.CertificateRevoked(someCert, now, "r"),
            Invalid.RevocationUnavailable("u"),
            Invalid.WrongProfile(JadesProfile.B_B, JadesProfile.B_LT),
        ).forEach { result ->
            assertTrue(outcome(report(result), EtsiValidationStep.SIGNING_TIME_VALIDITY) is StepOutcome.Inconclusive, "$result")
        }
    }

    @Test
    fun `time stamp problems fail the time stamp step`() {
        assertTrue(outcome(report(Invalid.TimeStampMismatch("t")), EtsiValidationStep.TIME_STAMP_TOKEN) is StepOutcome.Failed)
        assertTrue(outcome(report(Invalid.MissingTimeStamp("m")), EtsiValidationStep.TIME_STAMP_TOKEN) is StepOutcome.Failed)
    }

    @Test
    fun `the cert path step passes for revocation outcomes and fails for trust problems`() {
        assertTrue(outcome(report(Invalid.CertificateRevoked(someCert, now, "r")), EtsiValidationStep.X509_CERT_PATH) is StepOutcome.Passed)
        assertTrue(outcome(report(Invalid.RevocationUnavailable("u")), EtsiValidationStep.X509_CERT_PATH) is StepOutcome.Passed)
        assertTrue(outcome(report(Invalid.UntrustedSigner(someCert)), EtsiValidationStep.X509_CERT_PATH) is StepOutcome.Failed)
        assertEquals(
            true,
            outcome(report(Invalid.TrustWithdrawn(someCert, now, "w")), EtsiValidationStep.X509_CERT_PATH) is StepOutcome.Failed,
        )
    }
}
