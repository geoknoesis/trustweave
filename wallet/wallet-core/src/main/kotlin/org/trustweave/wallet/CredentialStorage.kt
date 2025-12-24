package org.trustweave.wallet

import org.trustweave.credential.model.vc.VerifiableCredential

/**
 * Core credential storage interface.
 *
 * All wallets must implement this for basic credential operations.
 * This follows TrustWeave's pattern of small, focused interfaces.
 *
 * **Example Usage**:
 * ```kotlin
 * val storage: CredentialStorage = createWallet()
 *
 * // Store credential
 * val id = storage.store(credential)
 *
 * // Retrieve credential
 * val credential = storage.get(id)
 *
 * // Query credentials
 * val credentials = storage.query {
 *     byIssuer(issuerDid)
 *     byType("PersonCredential")
 *     notExpired()
 * }
 * ```
 */
interface CredentialStorage {
    /**
     * Store a credential.
     *
     * @param credential Credential to store
     * @return Credential ID (uses credential.id if present, otherwise generates one)
     */
    suspend fun store(credential: VerifiableCredential): String

    /**
     * Get a credential by ID.
     *
     * @param credentialId Credential ID
     * @return Credential, or null if not found
     */
    suspend fun get(credentialId: String): VerifiableCredential?

    /**
     * List credentials matching a filter.
     *
     * @param filter Optional filter criteria
     * @return List of matching credentials
     */
    suspend fun list(filter: CredentialFilter? = null): List<VerifiableCredential>

    /**
     * Delete a credential.
     *
     * @param credentialId Credential ID
     * @return true if credential was deleted, false if not found
     */
    suspend fun delete(credentialId: String): Boolean

    /**
     * Query credentials using a query builder.
     *
     * **Example**:
     * ```kotlin
     * val credentials = storage.query {
     *     byIssuer("did:key:issuer")
     *     byType("PersonCredential")
     *     notExpired()
     *     notRevoked()
     * }
     * ```
     *
     * @param query Query builder function
     * @return List of matching credentials
     */
    suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential>
}

/**
 * Credential filter criteria.
 */
data class CredentialFilter(
    val issuer: String? = null,
    val type: List<String>? = null,
    val subjectId: String? = null,
    val expired: Boolean? = null,
    val revoked: Boolean? = null
)

/**
 * Fluent query builder for credentials.
 *
 * **Example**:
 * ```kotlin
 * val query = CredentialQueryBuilder().apply {
 *     byIssuer("did:key:issuer")
 *     byType("PersonCredential")
 *     notExpired()
 *     valid()
 * }
 * ```
 */
class CredentialQueryBuilder {
    internal val filters = mutableListOf<(VerifiableCredential) -> Boolean>()

    /**
     * Get the query predicate.
     * This is the public API for getting the predicate function.
     */
    public fun toPredicate(): (VerifiableCredential) -> Boolean {
        return { credential ->
            filters.all { it(credential) }
        }
    }

    /**
     * Create predicate function from filters (public method for cross-module access).
     * This method is used by wallet implementations to get the query predicate.
     */
    public fun createPredicate(): (VerifiableCredential) -> Boolean {
        return { credential ->
            filters.all { it(credential) }
        }
    }

    /**
     * Filter by issuer DID.
     */
    fun byIssuer(issuerDid: String) {
        filters.add { it.issuer.id.value == issuerDid }
    }

    /**
     * Filter by credential type.
     */
    fun byType(type: String) {
        filters.add { it.type.any { ct -> ct.value == type } }
    }

    /**
     * Filter by multiple credential types.
     */
    fun byTypes(vararg types: String) {
        val typeSet = types.toSet()
        filters.add { credential -> credential.type.any { ct -> ct.value in typeSet } }
    }

    /**
     * Filter by subject ID.
     */
    fun bySubject(subjectId: String) {
        filters.add { credential ->
            credential.credentialSubject.id.value == subjectId
        }
    }

    /**
     * Filter to only non-expired credentials.
     */
    fun notExpired() {
        filters.add { credential ->
            credential.expirationDate?.let { expirationDate ->
                kotlinx.datetime.Clock.System.now() < expirationDate
            } ?: true // No expiration date means not expired
        }
    }

    /**
     * Filter to only expired credentials.
     */
    fun expired() {
        filters.add { credential ->
            credential.expirationDate?.let { expirationDate ->
                kotlinx.datetime.Clock.System.now() > expirationDate
            } ?: false
        }
    }

    /**
     * Filter to only non-revoked credentials.
     */
    fun notRevoked() {
        filters.add { credential ->
            credential.credentialStatus == null // TODO: Check actual revocation status
        }
    }

    /**
     * Filter to only revoked credentials.
     */
    fun revoked() {
        filters.add { credential ->
            credential.credentialStatus != null // TODO: Check actual revocation status
        }
    }

    /**
     * Filter to only valid credentials (not expired, not revoked, has proof).
     */
    fun valid() {
        filters.add { credential ->
            credential.proof != null &&
            (credential.expirationDate?.let { expirationDate ->
                kotlinx.datetime.Clock.System.now() < expirationDate
            } ?: true) &&
            credential.credentialStatus == null // TODO: Check actual revocation
        }
    }

    /**
     * Filter by tag (requires CredentialOrganization capability).
     * Note: This filter will only work if the wallet supports CredentialOrganization.
     * The actual filtering is done by the wallet implementation.
     */
    fun byTag(tag: String) {
        // Store tag filter - wallet implementation will handle this
        filters.add { credential ->
            // This is a placeholder - actual tag filtering is done by wallet
            // Wallet implementations should check tags separately
            true // Don't filter here, let wallet handle it
        }
        // Store tag in a way wallet can access it
        // Note: This requires wallet to check tags separately
    }

    /**
     * Filter by collection (requires CredentialOrganization capability).
     * Note: This filter will only work if the wallet supports CredentialOrganization.
     * The actual filtering is done by the wallet implementation.
     */
    fun byCollection(collectionId: String) {
        // Store collection filter - wallet implementation will handle this
        filters.add { credential ->
            // This is a placeholder - actual collection filtering is done by wallet
            // Wallet implementations should check collections separately
            true // Don't filter here, let wallet handle it
        }
        // Store collection ID in a way wallet can access it
        // Note: This requires wallet to check collections separately
    }
}

