package com.trustweave.trust.types

import com.trustweave.trust.TrustAnchorMetadata
import java.time.Instant

/**
 * Trust anchor in a trust path.
 *
 * Represents a single trust anchor node in a trust path from verifier to issuer.
 *
 * @param did The DID of the trust anchor
 * @param metadata Metadata about the trust anchor
 */
data class TrustAnchor(
    val did: Did,
    val metadata: TrustAnchorMetadata
)

/**
 * Trust path from verifier to issuer.
 *
 * Represents a verified path through the trust graph from a verifier to an issuer.
 * This is a first-class type that makes trust relationships explicit and verifiable.
 *
 * **Example Usage:**
 * ```kotlin
 * when (val path = trustWeave.findTrustPath(verifier, issuer)) {
 *     is TrustPath.Verified -> {
 *         println("Trusted via path: ${path.anchors.map { it.did }}")
 *         println("Path length: ${path.anchors.size}")
 *         println("Verified at: ${path.verifiedAt}")
 *     }
 *     is TrustPath.NotFound -> {
 *         println("No trust path found")
 *     }
 * }
 * ```
 */
sealed class TrustPath {
    /**
     * Verified trust path from verifier to issuer.
     *
     * @param from The verifier identity
     * @param to The issuer identity
     * @param anchors List of trust anchors forming the path (excluding endpoints)
     * @param verified Whether the path has been verified
     * @param verifiedAt Timestamp when the path was verified
     * @param trustScore Trust score between 0.0 and 1.0 (higher is more trusted)
     */
    data class Verified(
        val from: VerifierIdentity,
        val to: IssuerIdentity,
        val anchors: List<TrustAnchor>,
        val verified: Boolean = true,
        val verifiedAt: Instant = Instant.now(),
        val trustScore: Double = 1.0
    ) : TrustPath() {
        init {
            require(trustScore in 0.0..1.0) { "Trust score must be between 0.0 and 1.0" }
        }

        /**
         * Get the full path including endpoints.
         */
        val fullPath: List<Did>
            get() = listOf(from.did) + anchors.map { anchor -> anchor.did } + listOf(to.did)

        /**
         * Get the path length (number of hops).
         */
        val length: Int
            get() = anchors.size + 1
    }

    /**
     * No trust path found between verifier and issuer.
     *
     * @param from The verifier identity
     * @param to The issuer identity
     * @param reason Optional reason why no path was found
     */
    data class NotFound(
        val from: VerifierIdentity,
        val to: IssuerIdentity,
        val reason: String? = null
    ) : TrustPath()
}

