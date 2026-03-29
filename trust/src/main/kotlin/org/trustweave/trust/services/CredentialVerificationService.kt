package org.trustweave.trust.services

import org.trustweave.credential.CredentialService
import org.trustweave.credential.results.VerificationResult
import org.trustweave.trust.dsl.credential.VerificationBuilder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Use case for credential verification.
 *
 * Orchestrates [CredentialService] to verify credentials. Depends only on abstractions.
 */
class CredentialVerificationService(
    private val credentialService: CredentialService,
    private val ioDispatcher: CoroutineDispatcher
) {
    /**
     * Verify a verifiable credential.
     *
     * @param timeout Maximum time to wait for verification (default: 10 seconds)
     * @param block DSL block for specifying verification parameters
     * @return The credential verification result (sealed type for exhaustive error handling)
     */
    suspend fun verify(
        timeout: Duration = 10.seconds,
        block: VerificationBuilder.() -> Unit
    ): VerificationResult = withTimeout(timeout) {
        withContext(ioDispatcher) {
            val builder = VerificationBuilder(
                credentialService = credentialService,
                ioDispatcher = ioDispatcher
            )
            builder.block()
            builder.build()
        }
    }
}
