package org.trustweave.trust.services

import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.trust.dsl.credential.RevocationBuilder
import kotlinx.coroutines.CoroutineDispatcher
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
     * @param timeout Maximum time to wait for revocation (default: 10 seconds)
     * @param block DSL block for specifying revocation parameters
     * @return true if revocation succeeded
     */
    suspend fun revoke(
        timeout: Duration = 10.seconds,
        block: RevocationBuilder.() -> Unit
    ): Boolean = withTimeout(timeout) {
        val builder = RevocationBuilder(revocationManager)
        builder.block()
        withContext(ioDispatcher) {
            builder.revoke()
        }
    }
}
