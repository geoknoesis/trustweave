package org.trustweave.trust.services

import org.trustweave.credential.CredentialService
import org.trustweave.credential.results.VerificationResult
import org.trustweave.trust.dsl.credential.VerificationBuilder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
     * A verification that exceeds [timeout] is mapped to
     * [VerificationResult.Invalid.InvalidProof] (the proof could not be verified within the
     * allotted time) instead of leaking a raw [TimeoutCancellationException]; real coroutine
     * cancellation of the caller still propagates.
     *
     * @param timeout Maximum time to wait for verification (default: 10 seconds)
     * @param block DSL block for specifying verification parameters
     * @return The credential verification result (sealed type for exhaustive error handling)
     */
    suspend fun verify(
        timeout: Duration = 10.seconds,
        block: VerificationBuilder.() -> Unit
    ): VerificationResult {
        // Configure the builder outside the timeout so the request credential is retained
        // and can be reported in a timeout failure result.
        val builder = VerificationBuilder(
            credentialService = credentialService,
            ioDispatcher = ioDispatcher
        )
        builder.block()
        return try {
            withTimeout(timeout) {
                withContext(ioDispatcher) {
                    builder.build()
                }
            }
        } catch (e: TimeoutCancellationException) {
            // Map OUR timeout to the sealed failure contract. If the surrounding coroutine
            // was itself cancelled (parent cancellation / enclosing timeout), propagate it.
            currentCoroutineContext().ensureActive()
            val reason = "Credential verification timed out after $timeout"
            VerificationResult.Invalid.InvalidProof(
                credential = builder.credential,
                reason = reason,
                errors = listOf(reason)
            )
        }
    }
}
