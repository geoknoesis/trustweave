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
    /**
     * When true, returns only credentials that have a [credentialStatus] entry.
     * Note: this does NOT perform a live revocation check — it only filters by
     * the presence of a status entry. Credentials may be suspended or valid even
     * when this filter returns them. Use a [CredentialRevocationManager] for
     * definitive revocation status.
     */
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

    /** Tags requested via [byTag]. Wallet implementations must use these for tag-based filtering. */
    val requestedTags: MutableSet<String> = mutableSetOf()

    /** Collection IDs requested via [byCollection]. Wallet implementations must use these for collection-based filtering. */
    val requestedCollections: MutableSet<String> = mutableSetOf()

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
            credential.credentialSubject.id?.value == subjectId
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
     *
     * **Revocation checking:** Credentials without [credentialStatus] are included (no revocation
     * mechanism). Credentials with [credentialStatus] are excluded because revocation status cannot
     * be determined without [CredentialRevocationManager.checkRevocationStatus]. Wallet implementations
     * needing accurate filtering for credentials with status should integrate with CredentialRevocationManager.
     */
    fun notRevoked() {
        filters.add { credential ->
            credential.credentialStatus == null
        }
    }

    /**
     * Filter to only revoked credentials.
     *
     * **Limitation:** Accurate revocation status requires [CredentialRevocationManager.checkRevocationStatus].
     * This filter cannot determine revocation from [credentialStatus] alone. Credentials with
     * [credentialStatus] are excluded (we cannot confirm they are revoked without checking the list).
     * Wallet implementations should use CredentialRevocationManager for accurate revocation filtering.
     */
    fun revoked() {
        filters.add { credential ->
            // Without CredentialRevocationManager, we cannot reliably identify revoked credentials.
            // Exclude all - caller should use CredentialRevocationManager for accurate filtering.
            false
        }
    }

    /**
     * Filter to only valid credentials (not expired, not revoked, has proof).
     *
     * **Revocation:** Same limitation as [notRevoked]—credentials with [credentialStatus] are
     * included optimistically. Use [CredentialRevocationManager] for accurate revocation checks.
     */
    fun valid() {
        filters.add { credential ->
            credential.proof != null &&
                (credential.expirationDate?.let { expirationDate ->
                    kotlinx.datetime.Clock.System.now() < expirationDate
                } ?: true) &&
                credential.credentialStatus == null
        }
    }

    /**
     * Filter by tag (requires CredentialTagging capability).
     *
     * Tags are wallet-level metadata not embedded in the credential itself.
     * Wallet implementations that support CredentialTagging must check
     * [requestedTags] after applying the standard predicate.
     */
    fun byTag(tag: String) {
        requestedTags.add(tag)
    }

    /**
     * Filter by collection (requires CredentialCollections capability).
     *
     * Collections are wallet-level metadata not embedded in the credential itself.
     * Wallet implementations that support CredentialCollections must check
     * [requestedCollections] after applying the standard predicate.
     */
    fun byCollection(collectionId: String) {
        requestedCollections.add(collectionId)
    }
}

