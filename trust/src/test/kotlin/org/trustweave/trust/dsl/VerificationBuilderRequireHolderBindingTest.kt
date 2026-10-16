package org.trustweave.trust.dsl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.credential.CredentialService
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
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
import org.trustweave.did.identifiers.Did
import org.trustweave.trust.dsl.credential.VerificationBuilder
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration

/** The DSL forwards `requireHolderBinding` to the credential service and leaves it off by default. */
class VerificationBuilderRequireHolderBindingTest {
    private class RecordingService : CredentialService {
        var seen: VerificationOptions? = null

        override suspend fun verify(
            credential: VerifiableCredential,
            trustPolicy: TrustEvaluator?,
            options: VerificationOptions,
        ): VerificationResult {
            seen = options
            return VerificationResult.Invalid.InvalidProof(credential = credential, reason = "recorded", errors = emptyList())
        }

        override suspend fun issue(request: IssuanceRequest): IssuanceResult = error("unused")

        override suspend fun createPresentation(
            credentials: List<VerifiableCredential>,
            request: PresentationRequest,
        ): VerifiablePresentation = error("unused")

        override suspend fun verifyPresentation(
            presentation: VerifiablePresentation,
            trustPolicy: TrustEvaluator?,
            options: VerificationOptions,
        ): VerificationResult = error("unused")

        override suspend fun status(
            credential: VerifiableCredential,
            clockSkewTolerance: Duration,
        ): CredentialStatusInfo = error("unused")

        override fun supports(format: ProofSuiteId): Boolean = false

        override fun supportedFormats(): List<ProofSuiteId> = emptyList()

        override fun supportsCapability(
            format: ProofSuiteId,
            capability: ProofEngineCapabilities.() -> Boolean,
        ): Boolean = false
    }

    private fun credential() =
        VerifiableCredential(
            id = CredentialId("urn:uuid:1"),
            type = listOf(CredentialType.Custom("TestCredential")),
            issuer = Issuer.fromDid(Did("did:key:z6MkIssuer")),
            credentialSubject = CredentialSubject.fromIri("did:key:z6MkSubject"),
            issuanceDate = Clock.System.now(),
            proof = null,
        )

    @Test
    fun `defaults to false and forwards true when requested`() =
        runBlocking<Unit> {
            val service = RecordingService()
            VerificationBuilder(service).apply { credential(credential()) }.build()
            assertEquals(false, service.seen?.requireHolderBinding)

            VerificationBuilder(service)
                .apply {
                    credential(credential())
                    requireHolderBinding(true)
                }.build()
            assertEquals(true, service.seen?.requireHolderBinding)
        }
}
