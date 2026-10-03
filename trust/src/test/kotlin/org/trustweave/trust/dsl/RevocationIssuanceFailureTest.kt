package org.trustweave.trust.dsl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.credential.CredentialService
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.requests.IssuanceRequest
import org.trustweave.credential.requests.PresentationRequest
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.CredentialStatusInfo
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.spi.proof.ProofEngineCapabilities
import org.trustweave.credential.trust.TrustEvaluator
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.trust.TrustWeave
import org.trustweave.trust.dsl.credential.withWarning
import org.trustweave.trust.types.getOrThrowDid
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * When issuance fails after withRevocation() assigned a status-list index, the caller must get the
 * original failure (subtype, reason) back — not a generic AdapterError — plus a warning naming the
 * index that could not be released.
 */
class RevocationIssuanceFailureTest {
    /** Rejects every issuance with a specific, non-adapter failure. */
    private class RejectingCredentialService : CredentialService {
        override suspend fun issue(request: IssuanceRequest): IssuanceResult =
            IssuanceResult.Failure.InvalidRequest(field = "credentialSubject", reason = "rejected by policy")

        override suspend fun verify(
            credential: VerifiableCredential,
            trustPolicy: TrustEvaluator?,
            options: VerificationOptions,
        ): VerificationResult = error("not used")

        override suspend fun createPresentation(
            credentials: List<VerifiableCredential>,
            request: PresentationRequest,
        ): VerifiablePresentation = error("not used")

        override suspend fun verifyPresentation(
            presentation: VerifiablePresentation,
            trustPolicy: TrustEvaluator?,
            options: VerificationOptions,
        ): VerificationResult = error("not used")

        override suspend fun status(
            credential: VerifiableCredential,
            clockSkewTolerance: Duration,
        ): CredentialStatusInfo = error("not used")

        override fun supports(format: ProofSuiteId): Boolean = true

        override fun supportedFormats(): List<ProofSuiteId> = listOf(ProofSuiteId.VC_LD)

        override fun supportsCapability(
            format: ProofSuiteId,
            capability: ProofEngineCapabilities.() -> Boolean,
        ): Boolean = false
    }

    @Test
    fun `a failed issuance keeps its failure subtype and reports the orphaned status index`() =
        runBlocking<Unit> {
            val kms = InMemoryKeyManagementService()
            val sdk =
                TrustWeave.build {
                    keys { custom(kms) }
                    did { method("key") { algorithm("Ed25519") } }
                    revocation { provider("inMemory") }
                    credentialService(RejectingCredentialService())
                }
            try {
                val issuer = sdk.createDid().getOrThrowDid()
                val result =
                    sdk.issue {
                        credential {
                            type("TrainingCredential")
                            issuer(issuer)
                            subject(issuer.value) { "course" to "training" }
                        }
                        signedBy(issuer)
                        withRevocation()
                    }

                val failure = assertIs<IssuanceResult.Failure.InvalidRequest>(result)
                assertEquals("rejected by policy", failure.reason)
                assertTrue(
                    failure.warnings.any { it.contains("status-list index") && it.contains("unused") },
                    "warnings: ${failure.warnings}",
                )
            } finally {
                sdk.close()
            }
        }

    @Test
    fun `withWarning preserves subtype, reason and cause`() {
        val adapter = IssuanceResult.Failure.AdapterError(ProofSuiteId.VC_LD, "boom", IllegalStateException("cause"))
        val warned = assertIs<IssuanceResult.Failure.AdapterError>(adapter.withWarning("w"))
        assertEquals("boom", warned.reason)
        assertIs<IllegalStateException>(warned.cause)
        assertEquals(listOf("w"), warned.warnings)
    }
}
