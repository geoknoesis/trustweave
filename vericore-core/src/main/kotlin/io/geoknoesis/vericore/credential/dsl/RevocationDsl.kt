package io.geoknoesis.vericore.credential.dsl

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.revocation.RevocationStatus
import io.geoknoesis.vericore.credential.revocation.StatusListCredential
import io.geoknoesis.vericore.credential.revocation.StatusListManager
import io.geoknoesis.vericore.credential.revocation.StatusPurpose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Revocation Builder DSL.
 * 
 * Provides a fluent API for managing credential revocation using trust layer configuration.
 * 
 * **Example Usage**:
 * ```kotlin
 * // Create status list
 * val statusList = trustLayer.revocation {
 *     forIssuer("did:key:university")
 *     purpose(StatusPurpose.REVOCATION)
 * }.createStatusList()
 * 
 * // Revoke credential
 * trustLayer.revoke {
 *     credential("cred-123")
 *     statusList(statusList.id)
 * }
 * 
 * // Check revocation status
 * val status = trustLayer.revocation {
 *     statusList(statusList.id)
 * }.check(credential)
 * ```
 */
class RevocationBuilder(
    private val context: TrustLayerContext
) {
    private var issuerDid: String? = null
    private var credentialId: String? = null
    private var statusListId: String? = null
    private var purpose: StatusPurpose = StatusPurpose.REVOCATION
    private var size: Int = 131072 // Default size
    
    /**
     * Set issuer DID for status list creation.
     */
    fun forIssuer(did: String) {
        this.issuerDid = did
    }
    
    /**
     * Set credential ID for revocation.
     */
    fun credential(credentialId: String) {
        this.credentialId = credentialId
    }
    
    /**
     * Set status list ID.
     */
    fun statusList(statusListId: String) {
        this.statusListId = statusListId
    }
    
    /**
     * Set status list purpose (revocation or suspension).
     */
    fun purpose(purpose: StatusPurpose) {
        this.purpose = purpose
    }
    
    /**
     * Set status list size (for creation).
     */
    fun size(size: Int) {
        this.size = size
    }
    
    /**
     * Get the status list manager from context.
     * 
     * @throws IllegalStateException if status list manager is not configured
     */
    private fun getStatusListManager(): StatusListManager {
        return context.getStatusListManager()
            ?: throw IllegalStateException(
                "StatusListManager is not configured in trust layer. " +
                "Configure it in trustLayer { revocation { provider(\"inMemory\") } }"
            )
    }
    
    /**
     * Create a new status list.
     * 
     * @return Status list credential
     */
    suspend fun createStatusList(): StatusListCredential = withContext(Dispatchers.IO) {
        val issuer = issuerDid ?: throw IllegalStateException(
            "Issuer DID is required. Use forIssuer(\"did:key:...\")"
        )
        
        val manager = getStatusListManager()
        manager.createStatusList(issuer, purpose, size)
    }
    
    /**
     * Revoke a credential.
     * 
     * @return true if revocation succeeded
     */
    suspend fun revoke(): Boolean = withContext(Dispatchers.IO) {
        val credId = credentialId ?: throw IllegalStateException(
            "Credential ID is required. Use credential(\"cred-123\")"
        )
        val listId = statusListId ?: throw IllegalStateException(
            "Status list ID is required. Use statusList(\"list-id\")"
        )
        
        val manager = getStatusListManager()
        manager.revokeCredential(credId, listId)
    }
    
    /**
     * Suspend a credential.
     * 
     * @return true if suspension succeeded
     */
    suspend fun suspend(): Boolean = withContext(Dispatchers.IO) {
        val credId = credentialId ?: throw IllegalStateException(
            "Credential ID is required. Use credential(\"cred-123\")"
        )
        val listId = statusListId ?: throw IllegalStateException(
            "Status list ID is required. Use statusList(\"list-id\")"
        )
        
        val manager = getStatusListManager()
        manager.suspendCredential(credId, listId)
    }
    
    /**
     * Check revocation status of a credential.
     * 
     * @param credential Credential to check
     * @return Revocation status
     */
    suspend fun check(credential: VerifiableCredential): RevocationStatus = withContext(Dispatchers.IO) {
        val manager = getStatusListManager()
        manager.checkRevocationStatus(credential)
    }
    
    /**
     * Get status list by ID.
     * 
     * @return Status list credential, or null if not found
     */
    suspend fun getStatusList(): StatusListCredential? = withContext(Dispatchers.IO) {
        val listId = statusListId ?: throw IllegalStateException(
            "Status list ID is required. Use statusList(\"list-id\")"
        )
        
        val manager = getStatusListManager()
        manager.getStatusList(listId)
    }
}

/**
 * Extension function to access revocation operations using trust layer configuration.
 */
fun TrustLayerContext.revocation(block: RevocationBuilder.() -> Unit): RevocationBuilder {
    return RevocationBuilder(this).apply(block)
}

/**
 * Extension function for direct revocation on trust layer config.
 */
fun TrustLayerConfig.revocation(block: RevocationBuilder.() -> Unit): RevocationBuilder {
    return dsl().revocation(block)
}

/**
 * Extension function to revoke a credential directly.
 */
suspend fun TrustLayerContext.revoke(block: RevocationBuilder.() -> Unit): Boolean {
    val builder = RevocationBuilder(this)
    builder.block()
    return builder.revoke()
}

/**
 * Extension function for direct revocation on trust layer config.
 */
suspend fun TrustLayerConfig.revoke(block: RevocationBuilder.() -> Unit): Boolean {
    return dsl().revoke(block)
}


