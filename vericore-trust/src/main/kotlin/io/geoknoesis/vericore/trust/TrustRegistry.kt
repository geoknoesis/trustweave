package io.geoknoesis.vericore.trust

import java.time.Instant

/**
 * Trust registry for managing trust anchors and discovering trust paths.
 *
 * A trust registry maintains a graph of trusted DIDs (trust anchors) and allows
 * verification of whether an issuer is trusted, either directly or through a trust path.
 *
 * **Example Usage**:
 * ```kotlin
 * val registry: TrustRegistry = InMemoryTrustRegistry()
 *
 * // Add a trust anchor
 * registry.addTrustAnchor(
 *     anchorDid = "did:key:trusted-issuer",
 *     metadata = TrustAnchorMetadata(
 *         credentialTypes = listOf("EducationCredential"),
 *         description = "Trusted university",
 *         addedAt = Instant.now()
 *     )
 * )
 *
 * // Check if issuer is trusted
 * val isTrusted = registry.isTrustedIssuer("did:key:trusted-issuer", "EducationCredential")
 *
 * // Find trust path
 * val path = registry.getTrustPath("did:key:verifier", "did:key:issuer")
 * ```
 */
interface TrustRegistry {
    /**
     * Checks if an issuer DID is trusted for a specific credential type.
     *
     * @param issuerDid The DID of the issuer to check
     * @param credentialType The credential type (null means check for any type)
     * @return true if the issuer is trusted, false otherwise
     */
    suspend fun isTrustedIssuer(issuerDid: String, credentialType: String?): Boolean

    /**
     * Adds a trust anchor to the registry.
     *
     * @param anchorDid The DID of the trust anchor
     * @param metadata Metadata about the trust anchor
     * @return true if the anchor was added successfully, false if it already exists
     */
    suspend fun addTrustAnchor(anchorDid: String, metadata: TrustAnchorMetadata): Boolean

    /**
     * Removes a trust anchor from the registry.
     *
     * @param anchorDid The DID of the trust anchor to remove
     * @return true if the anchor was removed, false if it didn't exist
     */
    suspend fun removeTrustAnchor(anchorDid: String): Boolean

    /**
     * Finds a trust path between two DIDs.
     *
     * @param fromDid The starting DID (typically the verifier)
     * @param toDid The target DID (typically the issuer)
     * @return TrustPathResult if a path exists, null otherwise
     */
    suspend fun getTrustPath(fromDid: String, toDid: String): TrustPathResult?

    /**
     * Gets all trusted issuers for a specific credential type.
     *
     * @param credentialType The credential type (null means all types)
     * @return List of trusted issuer DIDs
     */
    suspend fun getTrustedIssuers(credentialType: String?): List<String>
}

/**
 * Metadata about a trust anchor.
 *
 * @param credentialTypes List of credential types this anchor is trusted for (null means all types)
 * @param description Human-readable description of the trust anchor
 * @param addedAt Timestamp when the anchor was added to the registry
 */
data class TrustAnchorMetadata(
    val credentialTypes: List<String>? = null,
    val description: String? = null,
    val addedAt: Instant = Instant.now()
)

/**
 * Result of trust path discovery.
 *
 * @param path List of DIDs forming the trust path from source to target
 * @param trustScore Trust score between 0.0 and 1.0 (higher is more trusted)
 * @param valid Whether the trust path is valid
 */
data class TrustPathResult(
    val path: List<String>,
    val trustScore: Double,
    val valid: Boolean = true
) {
    init {
        require(trustScore in 0.0..1.0) { "Trust score must be between 0.0 and 1.0" }
    }
}


