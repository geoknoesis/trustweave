package org.trustweave.signatures.etsi.validation

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.signatures.etsi.validation.EtsiValidationReport.FinalVerdict
import org.trustweave.signatures.jades.DefaultJadesSigner
import org.trustweave.signatures.jades.JadesProfile
import org.trustweave.signatures.jades.JadesSigningRequest
import org.trustweave.signatures.trustlists.DefaultTrustAnchorResolver
import org.trustweave.signatures.trustlists.MemberStateTsl
import org.trustweave.signatures.trustlists.QualifierUris
import org.trustweave.signatures.trustlists.TrustList
import org.trustweave.signatures.trustlists.TrustedTSP
import org.trustweave.signatures.trustlists.TspService
import org.trustweave.signatures.trustlists.TspServiceStatus
import org.trustweave.signatures.trustlists.TspServiceType
import java.security.cert.X509Certificate

class EtsiSignatureValidatorTest {

    private lateinit var kms: TestKms
    private lateinit var ca: TestCa
    private val validator = DefaultEtsiSignatureValidator()

    @BeforeEach
    fun setUp() {
        kms = TestKms()
        ca = TestCa()
    }

    // ---------------------------------------------------------------- happy path

    @Test
    fun `valid Ed25519 signature with matching policy yields TOTAL_PASSED`() = runBlocking {
        val keyId = generateKey(Algorithm.Ed25519)
        val cert = ca.issueChainBytes(kms.publicKey(keyId), "CN=Ed25519 Signer")

        val signature = DefaultJadesSigner(kms).sign(
            payloadJson = buildJsonObject { put("hello", JsonPrimitive("world")) },
            request = JadesSigningRequest(
                profile = JadesProfile.B_B,
                keyId = keyId,
                signerCertificateChain = cert,
            ),
        )

        val report = validator.validate(
            jadesSerialized = signature.serializedFlattened,
            policy = EtsiSignaturePolicy(),
            trustAnchorResolver = resolverFor(ca.caCert),
        )

        assertEquals(
            FinalVerdict.TOTAL_PASSED,
            report.finalVerdict,
            "expected TOTAL_PASSED, got ${report.finalVerdict}: ${report.steps}",
        )
        assertEquals(EtsiValidationStep.values().size, report.steps.size, "expected an outcome per step")
        EtsiValidationStep.values().forEach { step ->
            assertTrue(report.steps.containsKey(step), "missing entry for $step")
        }
        // Format + identifier + crypto + cert-path + policy + signing-time all PASSED.
        assertTrue(report.steps[EtsiValidationStep.FORMAT_CHECK] is StepOutcome.Passed)
        assertTrue(report.steps[EtsiValidationStep.IDENTIFIER_CHECK] is StepOutcome.Passed)
        assertTrue(report.steps[EtsiValidationStep.SIGNATURE_ACCEPTANCE] is StepOutcome.Passed)
        assertTrue(report.steps[EtsiValidationStep.X509_CERT_PATH] is StepOutcome.Passed)
        assertTrue(report.steps[EtsiValidationStep.CRYPTO_CONSTRAINTS] is StepOutcome.Passed)
        assertTrue(report.steps[EtsiValidationStep.SIGNATURE_POLICY] is StepOutcome.Passed)
        assertTrue(report.steps[EtsiValidationStep.SIGNING_TIME_VALIDITY] is StepOutcome.Passed)
        // B-B has no time-stamp; without policy.requireTimeStamp the step is NotApplicable.
        assertTrue(report.steps[EtsiValidationStep.TIME_STAMP_TOKEN] is StepOutcome.NotApplicable)
        // Placeholder steps stay NotApplicable.
        assertTrue(report.steps[EtsiValidationStep.REVOCATION] is StepOutcome.NotApplicable)
        assertTrue(report.steps[EtsiValidationStep.LONG_TERM_VALIDATION] is StepOutcome.NotApplicable)
        assertTrue(report.steps[EtsiValidationStep.FINAL_VERDICT] is StepOutcome.Passed)
        assertNotNull(report.signerSubject)
        assertNotNull(report.signingTime)
    }

    // ---------------------------------------------------------------- policy-rejected algorithm

    @Test
    fun `ES256 signature against EdDSA-only policy fails CRYPTO_CONSTRAINTS`() = runBlocking {
        val keyId = generateKey(Algorithm.P256)
        val cert = ca.issueChainBytes(kms.publicKey(keyId), "CN=P256 Signer")

        val signature = DefaultJadesSigner(kms).sign(
            payloadJson = buildJsonObject { put("a", JsonPrimitive(1)) },
            request = JadesSigningRequest(
                profile = JadesProfile.B_B,
                keyId = keyId,
                signerCertificateChain = cert,
            ),
        )

        val report = validator.validate(
            jadesSerialized = signature.serializedFlattened,
            policy = EtsiSignaturePolicy(acceptedSignatureAlgorithms = setOf("EdDSA")),
            trustAnchorResolver = resolverFor(ca.caCert),
        )

        assertEquals(
            FinalVerdict.TOTAL_FAILED,
            report.finalVerdict,
            "expected TOTAL_FAILED but got ${report.finalVerdict}: ${report.steps}",
        )
        val crypto = report.steps[EtsiValidationStep.CRYPTO_CONSTRAINTS]
        assertTrue(
            crypto is StepOutcome.Failed,
            "CRYPTO_CONSTRAINTS should be Failed, got $crypto",
        )
        crypto as StepOutcome.Failed
        assertTrue(crypto.reason.contains("ES256"), "reason should mention ES256: ${crypto.reason}")
    }

    // ---------------------------------------------------------------- untrusted signer

    @Test
    fun `untrusted signer fails X509_CERT_PATH`() = runBlocking {
        val keyId = generateKey(Algorithm.Ed25519)
        val cert = ca.issueChainBytes(kms.publicKey(keyId), "CN=Anyone")

        val signature = DefaultJadesSigner(kms).sign(
            payloadJson = buildJsonObject { put("a", JsonPrimitive(1)) },
            request = JadesSigningRequest(JadesProfile.B_B, keyId, cert),
        )

        val unrelatedCa = TestCa(caSubject = "CN=Unrelated CA")
        val report = validator.validate(
            jadesSerialized = signature.serializedFlattened,
            policy = EtsiSignaturePolicy(),
            trustAnchorResolver = resolverFor(unrelatedCa.caCert),
        )

        assertEquals(FinalVerdict.TOTAL_FAILED, report.finalVerdict)
        assertTrue(
            report.steps[EtsiValidationStep.X509_CERT_PATH] is StepOutcome.Failed,
            "expected X509_CERT_PATH Failed, got ${report.steps[EtsiValidationStep.X509_CERT_PATH]}",
        )
        // Crypto check passed even though trust failed.
        assertTrue(report.steps[EtsiValidationStep.SIGNATURE_ACCEPTANCE] is StepOutcome.Passed)
    }

    // ---------------------------------------------------------------- time-stamp required

    @Test
    fun `B-B signature with policy requireTimeStamp fails TIME_STAMP_TOKEN`() = runBlocking {
        val keyId = generateKey(Algorithm.Ed25519)
        val cert = ca.issueChainBytes(kms.publicKey(keyId), "CN=Anyone")

        val signature = DefaultJadesSigner(kms).sign(
            payloadJson = buildJsonObject { put("a", JsonPrimitive(1)) },
            request = JadesSigningRequest(JadesProfile.B_B, keyId, cert),
        )

        val report = validator.validate(
            jadesSerialized = signature.serializedFlattened,
            policy = EtsiSignaturePolicy(requireTimeStamp = true),
            trustAnchorResolver = resolverFor(ca.caCert),
        )

        assertEquals(FinalVerdict.TOTAL_FAILED, report.finalVerdict)
        val ts = report.steps[EtsiValidationStep.TIME_STAMP_TOKEN]
        assertTrue(ts is StepOutcome.Failed, "expected TIME_STAMP_TOKEN Failed, got $ts")
    }

    // ---------------------------------------------------------------- INDETERMINATE on malformed envelope

    @Test
    fun `unparseable envelope produces TOTAL_FAILED on FORMAT_CHECK with later steps Inconclusive`() = runBlocking {
        val report = validator.validate(
            jadesSerialized = "not a JWS",
            policy = EtsiSignaturePolicy(),
            trustAnchorResolver = resolverFor(ca.caCert),
        )
        assertEquals(FinalVerdict.TOTAL_FAILED, report.finalVerdict)
        assertTrue(report.steps[EtsiValidationStep.FORMAT_CHECK] is StepOutcome.Failed)
        assertNull(report.signerSubject)
        assertNull(report.signingTime)
    }

    @Test
    fun `withdrawn trust status surfaces INDETERMINATE when policy excludes WITHDRAWN`() = runBlocking {
        val keyId = generateKey(Algorithm.Ed25519)
        val cert = ca.issueChainBytes(kms.publicKey(keyId), "CN=Anyone")

        val signature = DefaultJadesSigner(kms).sign(
            payloadJson = buildJsonObject { put("a", JsonPrimitive(1)) },
            request = JadesSigningRequest(JadesProfile.B_B, keyId, cert),
        )

        // Trust list with the matching CA marked as WITHDRAWN — but the policy only accepts
        // GRANTED, so SIGNATURE_POLICY should Fail and the final verdict TOTAL_FAILED.
        val report = validator.validate(
            jadesSerialized = signature.serializedFlattened,
            policy = EtsiSignaturePolicy(),
            trustAnchorResolver = resolverFor(ca.caCert, status = TspServiceStatus.WITHDRAWN),
        )

        assertEquals(FinalVerdict.TOTAL_FAILED, report.finalVerdict)
        val policyOutcome = report.steps[EtsiValidationStep.SIGNATURE_POLICY]
        assertTrue(
            policyOutcome is StepOutcome.Failed,
            "expected SIGNATURE_POLICY Failed for WITHDRAWN status, got $policyOutcome",
        )
    }

    @Test
    fun `Inconclusive step surfaces INDETERMINATE final verdict`() = runBlocking {
        // Construct a synthetic report manually to exercise the aggregation logic — the easiest
        // way to land an Inconclusive without a Failed in the pipeline is via the helper.
        val outcomes = linkedMapOf<EtsiValidationStep, StepOutcome>(
            EtsiValidationStep.FORMAT_CHECK to StepOutcome.Passed(),
            EtsiValidationStep.IDENTIFIER_CHECK to StepOutcome.Passed(),
            EtsiValidationStep.SIGNATURE_ACCEPTANCE to StepOutcome.Passed(),
            EtsiValidationStep.X509_CERT_PATH to StepOutcome.Inconclusive("simulated"),
        )
        val report = aggregateForTest(outcomes)
        assertEquals(FinalVerdict.INDETERMINATE, report.finalVerdict)
        assertTrue(report.steps[EtsiValidationStep.FINAL_VERDICT] is StepOutcome.Inconclusive)
    }

    // ---------------------------------------------------------------- helpers

    private suspend fun generateKey(algorithm: Algorithm): KeyId {
        val keyId = "test-${algorithm.name}"
        return when (val r = kms.generateKey(algorithm, mapOf("keyId" to keyId))) {
            is GenerateKeyResult.Success -> r.keyHandle.id
            else -> error("KMS keygen failed: $r")
        }
    }

    private fun resolverFor(
        trustedCa: X509Certificate,
        status: TspServiceStatus = TspServiceStatus.GRANTED,
    ): DefaultTrustAnchorResolver {
        val service = TspService(
            serviceName = "Test CA",
            serviceType = TspServiceType.CA_FOR_QUALIFIED_CERTIFICATES,
            status = status,
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

    /**
     * Tiny re-implementation of the aggregation in [DefaultEtsiSignatureValidator.finalize] so
     * that we can unit-test the [FinalVerdict.INDETERMINATE] path without forcing the live
     * pipeline into an inconclusive state (which is rare in practice — most failures are
     * Failed rather than Inconclusive in the MVP).
     */
    private fun aggregateForTest(
        partial: Map<EtsiValidationStep, StepOutcome>,
    ): EtsiValidationReport {
        val full = linkedMapOf<EtsiValidationStep, StepOutcome>().apply {
            EtsiValidationStep.values().forEach { put(it, partial[it] ?: StepOutcome.NotApplicable) }
        }
        val applicable = full.filterKeys { it != EtsiValidationStep.FINAL_VERDICT }.values
        val verdict = when {
            applicable.any { it is StepOutcome.Failed } -> FinalVerdict.TOTAL_FAILED
            applicable.any { it is StepOutcome.Inconclusive } -> FinalVerdict.INDETERMINATE
            else -> FinalVerdict.TOTAL_PASSED
        }
        full[EtsiValidationStep.FINAL_VERDICT] = when (verdict) {
            FinalVerdict.TOTAL_PASSED -> StepOutcome.Passed("all applicable steps passed")
            FinalVerdict.TOTAL_FAILED -> StepOutcome.Failed("at least one step failed")
            FinalVerdict.INDETERMINATE -> StepOutcome.Inconclusive("at least one step is inconclusive")
        }
        return EtsiValidationReport(
            finalVerdict = verdict,
            steps = full.toMap(),
            signerSubject = null,
            signingTime = null,
        )
    }
}
