package org.trustweave.trust.services

import org.trustweave.trust.TrustRegistry
import org.trustweave.trust.dsl.TrustBuilder
import org.trustweave.trust.types.IssuerIdentity
import org.trustweave.trust.types.TrustPath
import org.trustweave.trust.types.VerifierIdentity
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Domain service for trust registry operations.
 *
 * Extracted from [org.trustweave.trust.TrustWeave] to separate the trust management
 * responsibility into a focused service class.
 */
class TrustManagementService(
    private val trustRegistry: TrustRegistry
) {
    /**
     * Find a trust path between a verifier and an issuer.
     *
     * @param verifier The verifier identity
     * @param issuer The issuer identity
     * @param timeout Maximum time to wait (default: 10 seconds)
     * @return [TrustPath.Verified] if a path exists, [TrustPath.NotFound] otherwise
     */
    suspend fun findTrustPath(
        verifier: VerifierIdentity,
        issuer: IssuerIdentity,
        timeout: Duration = 10.seconds
    ): TrustPath = withTimeout(timeout) {
        trustRegistry.findTrustPath(verifier, issuer)
    }

    /**
     * Perform trust operations using the trust DSL.
     *
     * @param block DSL block for trust operations
     */
    suspend fun trust(block: suspend TrustBuilder.() -> Unit) {
        val builder = TrustBuilder(trustRegistry)
        builder.block()
    }
}
