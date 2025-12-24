package org.trustweave.trust.dsl.credential

import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.revocation.RevocationStatus
import org.trustweave.credential.revocation.StatusListMetadata
import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.credential.identifiers.StatusListId
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
    private val revocationManager: CredentialRevocationManager?
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
     * Get the revocation manager.
     *
     * @throws IllegalStateException if revocation manager is not configured
     */
    private fun getRevocationManager(): CredentialRevocationManager {
        return revocationManager
            ?: throw IllegalStateException(
                "CredentialRevocationManager is required for revocation operations."
            )
    }

    /**
     * Create a new status list.
     *
     * @return Status list ID
     */
    suspend fun createStatusList(): StatusListId = withContext(Dispatchers.IO) {
        val issuer = issuerDid ?: throw IllegalStateException(
            "Issuer DID is required. Use forIssuer(\"did:key:...\")"
        )

        val manager = getRevocationManager()
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

        val manager = getRevocationManager()
        manager.revokeCredential(credId, StatusListId(listId))
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

        val manager = getRevocationManager()
        manager.suspendCredential(credId, StatusListId(listId))
    }

    /**
     * Check revocation status of a credential.
     *
     * @param credential Credential to check
     * @return Revocation status
     */
    suspend fun check(credential: VerifiableCredential): RevocationStatus = withContext(Dispatchers.IO) {
        val manager = getRevocationManager()
        manager.checkRevocationStatus(credential)
    }

    /**
     * Get status list by ID.
     *
     * @return Status list metadata, or null if not found
     */
    suspend fun getStatusList(): StatusListMetadata? = withContext(Dispatchers.IO) {
        val listId = statusListId ?: throw IllegalStateException(
            "Status list ID is required. Use statusList(\"list-id\")"
        )

        val manager = getRevocationManager()
        manager.getStatusList(StatusListId(listId))
    }
}

/**
 * Extension function to access revocation operations using CredentialDslProvider.
 */
fun CredentialDslProvider.revocation(block: RevocationBuilder.() -> Unit): RevocationBuilder {
    return RevocationBuilder(getRevocationManager()).apply(block)
}

/**
 * Extension function to revoke a credential directly using CredentialDslProvider.
 */
suspend fun CredentialDslProvider.revoke(block: RevocationBuilder.() -> Unit): Boolean {
    val builder = RevocationBuilder(getRevocationManager())
    builder.block()
    return builder.revoke()
}


