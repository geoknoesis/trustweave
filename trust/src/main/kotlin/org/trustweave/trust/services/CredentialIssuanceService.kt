package org.trustweave.trust.services

import org.trustweave.credential.CredentialService
import org.trustweave.credential.extensions.toProofSuiteId
import org.trustweave.credential.model.ProofType
import org.trustweave.credential.results.IssuanceResult
import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.did.resolver.DidResolver
import org.trustweave.trust.dsl.credential.IssuanceBuilder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Use case for credential issuance.
 *
 * Orchestrates [CredentialService] to issue verifiable credentials.
 * Depends only on abstractions (ports)—follows Dependency Inversion Principle.
 */
class CredentialIssuanceService(
    private val credentialService: CredentialService,
    private val revocationManager: CredentialRevocationManager?,
    private val didResolver: DidResolver?,
    private val defaultProofType: ProofType,
    private val ioDispatcher: CoroutineDispatcher
) {
    /**
     * Issue a verifiable credential using the configured service.
     *
     * @param timeout Maximum time to wait for issuance (default: 30 seconds)
     * @param block DSL block for building the credential and specifying issuance parameters
     * @return Sealed result type with success or detailed failure information
     */
    suspend fun issue(
        timeout: Duration = 30.seconds,
        block: IssuanceBuilder.() -> Unit
    ): IssuanceResult = withTimeout(timeout) {
        withContext(ioDispatcher) {
            val builder = IssuanceBuilder(
                credentialService = credentialService,
                revocationManager = revocationManager,
                defaultProofSuite = defaultProofType.toProofSuiteId(),
                ioDispatcher = ioDispatcher,
                didResolver = didResolver
            )
            builder.block()
            builder.build()
        }
    }
}
