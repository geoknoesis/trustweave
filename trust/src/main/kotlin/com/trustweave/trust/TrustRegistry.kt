package com.trustweave.trust

import com.trustweave.credential.model.CredentialType
import com.trustweave.did.identifiers.Did
import com.trustweave.trust.types.IssuerIdentity
import com.trustweave.trust.types.TrustPath
import com.trustweave.trust.types.VerifierIdentity
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

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
 *         addedAt = Clock.System.now()
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
     * Checks if an issuer is trusted for a specific credential type (type-safe overload).
     *
     * @param issuer The issuer identity (type-safe)
     * @param credentialType The credential type (null means check for any type)
     * @return true if the issuer is trusted, false otherwise
     */
    suspend fun isTrustedIssuer(issuer: IssuerIdentity, credentialType: CredentialType?): Boolean {
        return isTrustedIssuer(issuer.value, credentialType?.value)
    }

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
     * Finds a trust path between two identities (first-class type).
     *
     * Returns a sealed TrustPath type that makes trust relationships explicit.
     *
     * @param from The starting identity (typically the verifier)
     * @param to The target identity (typically the issuer)
     * @return TrustPath.Verified if a path exists, TrustPath.NotFound otherwise
     */
    suspend fun findTrustPath(from: VerifierIdentity, to: IssuerIdentity): TrustPath

    /**
     * Gets all trusted issuers for a specific credential type.
     *
     * @param credentialType The credential type (null means all types)
     * @return List of trusted issuer DIDs
     */
    suspend fun getTrustedIssuers(credentialType: String?): List<String>

    /**
     * Gets all trusted issuers for a specific credential type (type-safe overload).
     *
     * Note: This returns DIDs only, not full IssuerIdentity objects since we don't have key IDs.
     * For full issuer identities with key IDs, use the string-based overload and construct IssuerIdentity manually.
     *
     * @param credentialType The credential type (null means all types)
     * @return List of trusted issuer DIDs (as Did objects)
     */
    suspend fun getTrustedIssuers(credentialType: CredentialType?): List<Did> {
        return getTrustedIssuers(credentialType?.value).map { didString ->
            Did(didString)
        }
    }
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
    val addedAt: Instant = Clock.System.now()
)

/**
 * Internal result of trust path discovery (used only for internal implementations).
 *
 * @param path List of DIDs forming the trust path from source to target
 * @param trustScore Trust score between 0.0 and 1.0 (higher is more trusted)
 * @param valid Whether the trust path is valid
 */
internal data class TrustPathResult(
    val path: List<String>,
    val trustScore: Double,
    val valid: Boolean = true
) {
    init {
        require(trustScore in 0.0..1.0) { "Trust score must be between 0.0 and 1.0" }
    }
}


