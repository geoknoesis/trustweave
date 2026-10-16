package org.trustweave.signatures.etsi.validation

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
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
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** Accepting the WITHDRAWN status in the policy must not by itself trust a back-datable claimed signing time. */
class EtsiWithdrawnTrustTest {
    private val kms = TestKms()
    private val ca = TestCa()
    private val validator = DefaultEtsiSignatureValidator()

    private fun resolver(withdrawnAt: Instant): DefaultTrustAnchorResolver {
        val service =
            TspService(
                serviceName = "Test CA",
                serviceType = TspServiceType.CA_FOR_QUALIFIED_CERTIFICATES,
                status = TspServiceStatus.WITHDRAWN,
                statusStartingTime = withdrawnAt,
                serviceCertificates = listOf(ca.caCert),
                qualifierUris = listOf(QualifierUris.QC_WITH_SSCD, QualifierUris.QC_FOR_ESIG),
            )
        val tsp = TrustedTSP(name = "Test TSP", tradeName = null, services = listOf(service))
        val now = Clock.System.now()
        val member = MemberStateTsl("EU", "Test", 1, now, listOf(tsp))
        return DefaultTrustAnchorResolver(TrustList("Test", 1, now, null, listOf(member)))
    }

    @Test
    fun `accepting WITHDRAWN status still needs an authenticated time unless explicitly relaxed`() =
        runBlocking<Unit> {
            val keyId =
                when (val r = kms.generateKey(Algorithm.Ed25519, mapOf("keyId" to "withdrawn-test"))) {
                    is GenerateKeyResult.Success -> r.keyHandle.id
                    else -> error("KMS keygen failed: $r")
                }
            val cert = ca.issueChainBytes(kms.publicKey(keyId), "CN=Withdrawn Later")
            val signature =
                DefaultJadesSigner(kms).sign(
                    payloadJson = buildJsonObject { put("a", JsonPrimitive(1)) },
                    request = JadesSigningRequest(JadesProfile.B_B, keyId, cert),
                )
            // The withdrawal lies after the claimed signing time, but no trusted time-stamp backs that claim.
            val trust = resolver(Clock.System.now() + Duration.parse("PT1H"))
            val acceptsWithdrawn =
                EtsiSignaturePolicy(
                    allowedTrustStatusUris = setOf(TspServiceStatus.GRANTED.uri, TspServiceStatus.WITHDRAWN.uri),
                )

            val strict = validator.validate(signature.serializedFlattened, acceptsWithdrawn, trust)
            assertEquals(FinalVerdict.TOTAL_FAILED, strict.finalVerdict, "${strict.steps}")
            assertTrue(strict.steps[EtsiValidationStep.X509_CERT_PATH] is StepOutcome.Failed, "${strict.steps}")

            val relaxed =
                validator.validate(
                    signature.serializedFlattened,
                    acceptsWithdrawn.copy(allowWithdrawnTrustWithoutAuthenticatedTime = true),
                    trust,
                )
            assertEquals(FinalVerdict.TOTAL_PASSED, relaxed.finalVerdict, "${relaxed.steps}")
        }
}
