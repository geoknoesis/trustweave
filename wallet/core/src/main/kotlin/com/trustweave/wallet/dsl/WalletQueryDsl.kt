package com.trustweave.wallet.dsl

import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.wallet.CredentialOrganization
import com.trustweave.wallet.CredentialQueryBuilder
import com.trustweave.wallet.Wallet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Enhanced Wallet Query DSL.
 * 
 * Provides enhanced query capabilities including tag and collection filtering.
 * 
 * **Example Usage**:
 * ```kotlin
 * val credentials = wallet.query {
 *     byType("CertificationCredential")
 *     notExpired()
 *     valid()
 *     byTag("cloud")
 *     byCollection("Certifications")
 * }
 * ```
 */

/**
 * Enhanced query builder that supports organization features.
 */
class EnhancedQueryBuilder(
    private val wallet: Wallet
) {
    private val queryBuilder = CredentialQueryBuilder()
    private var tagFilter: String? = null
    private var collectionFilter: String? = null
    
    /**
     * Filter by issuer DID.
     */
    fun byIssuer(issuerDid: String) {
        queryBuilder.byIssuer(issuerDid)
    }
    
    /**
     * Filter by credential type.
     */
    fun byType(type: String) {
        queryBuilder.byType(type)
    }
    
    /**
     * Filter by multiple credential types.
     */
    fun byTypes(vararg types: String) {
        queryBuilder.byTypes(*types)
    }
    
    /**
     * Filter by subject ID.
     */
    fun bySubject(subjectId: String) {
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
    fun byTag(tag: String) {
        tagFilter = tag
    }
    
    /**
     * Filter by collection (requires CredentialOrganization capability).
     */
    fun byCollection(collectionId: String) {
        collectionFilter = collectionId
    }
    
    /**
     * Execute the query.
     * 
     * @return List of matching credentials
     */
    suspend fun execute(): List<VerifiableCredential> = withContext(Dispatchers.IO) {
        // Use the query builder's predicate to filter credentials
        val predicate = queryBuilder.createPredicate()
        
        // Get all credentials and filter using predicate
        val allCredentials = wallet.list()
        val baseResults = allCredentials.filter(predicate)
        
        // Apply tag filter if specified and wallet supports organization
        val tagFiltered = if (tagFilter != null && wallet is CredentialOrganization) {
            val taggedCreds = wallet.findByTag(tagFilter!!)
            baseResults.filter { cred -> taggedCreds.any { it.id == cred.id } }
        } else {
            baseResults
        }
        
        // Apply collection filter if specified and wallet supports organization
        val finalResults = if (collectionFilter != null && wallet is CredentialOrganization) {
            val collectionCreds = wallet.getCredentialsInCollection(collectionFilter!!)
            tagFiltered.filter { cred -> collectionCreds.any { it.id == cred.id } }
        } else {
            tagFiltered
        }
        
        finalResults
    }
}

/**
 * Extension function for enhanced querying on wallets.
 */
suspend fun Wallet.queryEnhanced(block: EnhancedQueryBuilder.() -> Unit): List<VerifiableCredential> {
    val builder = EnhancedQueryBuilder(this)
    builder.block()
    return builder.execute()
}


