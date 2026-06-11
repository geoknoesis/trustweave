package org.trustweave.trust.services

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.trust.dsl.credential.RevocationBuilder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Domain service for credential revocation operations.
 *
 * Extracted from [org.trustweave.trust.TrustWeave] to separate the revocation
 * responsibility into a focused service class.
 */
class CredentialRevocationService(
    private val revocationManager: CredentialRevocationManager?,
    private val ioDispatcher: CoroutineDispatcher
) {
    /**
     * Revoke a credential.
     *
     * **Timeout semantics:** the `Boolean` return type cannot honestly express a timeout —
     * returning `false` would silently misreport an *unknown* outcome as "not revoked".
     * A revocation that exceeds [timeout] therefore throws
     * [TrustWeaveException.OperationTimedOut] (with the underlying
     * [TimeoutCancellationException] as cause) instead of leaking the raw cancellation
     * exception; real coroutine cancellation of the caller still propagates.
     *
     * @param timeout Maximum time to wait for revocation (default: 10 seconds)
     * @param block DSL block for specifying revocation parameters
     * @return true if revocation succeeded
     * @throws TrustWeaveException.OperationTimedOut if the operation exceeds [timeout];
     *   the revocation outcome is unknown and callers should re-check status
     */
    suspend fun revoke(
        timeout: Duration = 10.seconds,
        block: RevocationBuilder.() -> Unit
    ): Boolean = try {
        withTimeout(timeout) {
            val builder = RevocationBuilder(revocationManager)
            builder.block()
            withContext(ioDispatcher) {
                builder.revoke()
            }
        }
    } catch (e: TimeoutCancellationException) {
        // Propagate real cancellation (parent cancellation / enclosing timeout) untouched.
        currentCoroutineContext().ensureActive()
        throw TrustWeaveException.OperationTimedOut(
            operation = "credential revocation",
            timeout = timeout.toString(),
            cause = e
        )
    }
}
