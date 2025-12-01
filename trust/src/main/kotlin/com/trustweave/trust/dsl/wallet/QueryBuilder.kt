package com.trustweave.trust.dsl.wallet

import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.wallet.CredentialOrganization
import com.trustweave.wallet.CredentialQueryBuilder
import com.trustweave.wallet.Wallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wallet Query DSL.
 *
 * Provides query capabilities including tag and collection filtering.
 *
 * **Example Usage**:
 * ```kotlin
 * val credentials = wallet.query {
 *     type("CertificationCredential")
 *     notExpired()
 *     valid()
 *     tag("cloud")
 *     collection("Certifications")
 * }
 * ```
 */

/**
 * Query builder that supports organization features.
 */
class QueryBuilder(
    private val wallet: Wallet
) {
    private val queryBuilder = CredentialQueryBuilder()
    private var tagFilter: String? = null
    private var collectionFilter: String? = null

    /**
     * Filter by issuer DID.
     * 
     * @param issuerDid Must be a valid DID starting with "did:"
     * @throws IllegalArgumentException if issuerDid is blank or doesn't start with "did:"
     */
    fun issuer(issuerDid: String) {
        require(issuerDid.isNotBlank()) { "Issuer DID cannot be blank" }
        require(issuerDid.startsWith("did:")) { 
            "Issuer DID must start with 'did:'. Got: $issuerDid" 
        }
        queryBuilder.byIssuer(issuerDid)
    }

    /**
     * Filter by credential type.
     * 
     * @param type Credential type string (e.g., "PersonCredential", "EducationCredential")
     * @throws IllegalArgumentException if type is blank
     */
    fun type(type: String) {
        require(type.isNotBlank()) { "Credential type cannot be blank" }
        queryBuilder.byType(type)
    }

    /**
     * Filter by multiple credential types.
     */
    fun types(vararg types: String) {
        queryBuilder.byTypes(*types)
    }

    /**
     * Filter by subject ID.
     * 
     * @param subjectId Must be a valid DID starting with "did:" or a valid URI
     * @throws IllegalArgumentException if subjectId is blank or doesn't match DID/URI format
     */
    fun subject(subjectId: String) {
        require(subjectId.isNotBlank()) { "Subject ID cannot be blank" }
        require(subjectId.startsWith("did:") || subjectId.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:.*"))) { 
            "Subject ID must be a valid DID (starting with 'did:') or URI. Got: $subjectId" 
        }
        queryBuilder.bySubject(subjectId)
    }

    /**
     * Filter to only non-expired credentials.
     */
    fun notExpired() {
        queryBuilder.notExpired()
    }

    /**
     * Filter to only expired credentials.
     */
    fun expired() {
        queryBuilder.expired()
    }

    /**
     * Filter to only non-revoked credentials.
     */
    fun notRevoked() {
        queryBuilder.notRevoked()
    }

    /**
     * Filter to only revoked credentials.
     */
    fun revoked() {
        queryBuilder.revoked()
    }

    /**
     * Filter to only valid credentials.
     */
    fun valid() {
        queryBuilder.valid()
    }

    /**
     * Filter by tag (requires CredentialOrganization capability).
     */
    fun tag(tag: String) {
        tagFilter = tag
    }

    /**
     * Filter by collection (requires CredentialOrganization capability).
     */
    fun collection(collectionId: String) {
        collectionFilter = collectionId
    }

    /**
     * Execute the query.
     *
     * @return List of matching credentials
     */
    suspend fun execute(): List<VerifiableCredential> = withContext(Dispatchers.IO) {
        val predicate = queryBuilder.createPredicate()
        val allCredentials = wallet.list()

        // Apply base predicate filter
        val baseResults = allCredentials.filter(predicate)

        // Apply tag filter if specified and wallet supports organization
        val tagFiltered = tagFilter
            ?.takeIf { wallet is CredentialOrganization }
            ?.let { tag ->
                val taggedCredIds = (wallet as CredentialOrganization)
                    .findByTag(tag)
                    .mapTo(mutableSetOf()) { it.id }
                baseResults.filter { it.id in taggedCredIds }
            }
            ?: baseResults

        // Apply collection filter if specified and wallet supports organization
        collectionFilter
            ?.takeIf { wallet is CredentialOrganization }
            ?.let { collectionId ->
                val collectionCredIds = (wallet as CredentialOrganization)
                    .getCredentialsInCollection(collectionId)
                    .mapTo(mutableSetOf()) { it.id }
                tagFiltered.filter { it.id in collectionCredIds }
            }
            ?: tagFiltered
    }
}

/**
 * Extension function for querying credentials in a wallet.
 */
suspend fun Wallet.query(block: QueryBuilder.() -> Unit): List<VerifiableCredential> {
    val builder = QueryBuilder(this)
    builder.block()
    return builder.execute()
}


