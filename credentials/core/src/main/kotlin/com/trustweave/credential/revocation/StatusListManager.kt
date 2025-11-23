package com.trustweave.credential.revocation

import com.trustweave.credential.models.CredentialStatus
import com.trustweave.credential.models.VerifiableCredential
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Status list credential for managing credential revocation.
 * 
 * Implements W3C Status List 2021 specification.
 * 
 * @param id Status list credential ID
 * @param type Credential types
 * @param issuer Issuer DID
 * @param credentialSubject Status list data
 * @param issuanceDate Issuance date
 * @param proof Proof for the status list credential
 */
@Serializable
data class StatusListCredential(
    val id: String,
    val type: List<String>,
    val issuer: String,
    val credentialSubject: StatusListSubject,
    val issuanceDate: String,
    val proof: com.trustweave.credential.models.Proof? = null
)

/**
 * Status list subject containing the encoded status list.
 * 
 * @param id Status list ID
 * @param type Status list type
 * @param statusPurpose Purpose of the status list (revocation or suspension)
 * @param encodedList Base64-encoded bitstring of revocation statuses
 */
@Serializable
data class StatusListSubject(
    val id: String,
    val type: String = "StatusList2021",
    val statusPurpose: String = "revocation",
    val encodedList: String // Base64-encoded bitstring
)

/**
 * Revocation status result.
 * 
 * @param revoked Whether the credential is revoked
 * @param suspended Whether the credential is suspended
 * @param statusListId Status list ID if applicable
 * @param reason Optional revocation reason
 */
data class RevocationStatus(
    val revoked: Boolean,
    val suspended: Boolean = false,
    val statusListId: String? = null,
    val reason: String? = null
)

/**
 * Status purpose enumeration.
 */
enum class StatusPurpose {
    REVOCATION,
    SUSPENSION
}

/**
 * Status list statistics.
 * 
 * @param statusListId Status list ID
 * @param totalCapacity Total capacity of the status list
 * @param usedIndices Number of indices currently assigned to credentials
 * @param revokedCount Number of revoked credentials
 * @param suspendedCount Number of suspended credentials
 * @param availableIndices Number of available indices
 * @param lastUpdated Last update timestamp
 */
data class StatusListStatistics(
    val statusListId: String,
    val totalCapacity: Int,
    val usedIndices: Int,
    val revokedCount: Int,
    val suspendedCount: Int,
    val availableIndices: Int,
    val lastUpdated: java.time.Instant
)

/**
 * Status update for batch operations.
 * 
 * @param index Index in the status list
 * @param revoked Whether to set revoked status (null = no change)
 * @param suspended Whether to set suspended status (null = no change)
 * @param reason Optional reason for the status change
 */
data class StatusUpdate(
    val index: Int,
    val revoked: Boolean? = null,
    val suspended: Boolean? = null,
    val reason: String? = null
)

/**
 * Status list manager interface.
 * 
 * Provides operations for creating and managing credential status lists.
 * 
 * **Example Usage**:
 * ```kotlin
 * val manager = InMemoryStatusListManager()
 * 
 * // Create status list
 * val statusList = manager.createStatusList(
 *     issuerDid = "did:key:...",
 *     purpose = StatusPurpose.REVOCATION
 * )
 * 
 * // Revoke credential
 * manager.revokeCredential(
 *     credentialId = "cred-123",
 *     statusListId = statusList.id
 * )
 * 
 * // Check revocation status
 * val status = manager.checkRevocationStatus(credential)
 * ```
 */
interface StatusListManager {
    /**
     * Create a new status list.
     * 
     * @param issuerDid Issuer DID
     * @param purpose Status list purpose (revocation or suspension)
     * @param size Optional initial size (default: 131072 entries)
     * @param customId Optional custom ID for the status list (generated if not provided)
     * @return Status list credential
     */
    suspend fun createStatusList(
        issuerDid: String,
        purpose: StatusPurpose,
        size: Int = 131072, // 16KB bitstring
        customId: String? = null
    ): StatusListCredential
    
    /**
     * Revoke a credential.
     * 
     * @param credentialId Credential ID
     * @param statusListId Status list ID
     * @return true if revocation succeeded
     */
    suspend fun revokeCredential(
        credentialId: String,
        statusListId: String
    ): Boolean
    
    /**
     * Suspend a credential.
     * 
     * @param credentialId Credential ID
     * @param statusListId Status list ID
     * @return true if suspension succeeded
     */
    suspend fun suspendCredential(
        credentialId: String,
        statusListId: String
    ): Boolean
    
    /**
     * Check revocation status of a credential.
     * 
     * @param credential Credential to check
     * @return Revocation status
     */
    suspend fun checkRevocationStatus(
        credential: VerifiableCredential
    ): RevocationStatus
    
    /**
     * Update status list with revoked indices.
     * 
     * @param statusListId Status list ID
     * @param revokedIndices List of revoked credential indices
     * @return Updated status list credential
     */
    suspend fun updateStatusList(
        statusListId: String,
        revokedIndices: List<Int>
    ): StatusListCredential
    
    /**
     * Get status list by ID.
     * 
     * @param statusListId Status list ID
     * @return Status list credential, or null if not found
     */
    suspend fun getStatusList(statusListId: String): StatusListCredential?
    
    /**
     * List all status lists, optionally filtered by issuer.
     * 
     * @param issuerDid Optional issuer DID to filter by
     * @return List of status list credentials
     */
    suspend fun listStatusLists(issuerDid: String? = null): List<StatusListCredential>
    
    /**
     * Delete a status list.
     * 
     * @param statusListId Status list ID
     * @return true if deleted, false if not found
     */
    suspend fun deleteStatusList(statusListId: String): Boolean
    
    /**
     * Get statistics for a status list.
     * 
     * @param statusListId Status list ID
     * @return Status list statistics, or null if not found
     */
    suspend fun getStatusListStatistics(statusListId: String): StatusListStatistics?
    
    /**
     * Unrevoke a credential (remove from revocation list).
     * 
     * @param credentialId Credential ID
     * @param statusListId Status list ID
     * @return true if unrevocation succeeded
     */
    suspend fun unrevokeCredential(
        credentialId: String,
        statusListId: String
    ): Boolean
    
    /**
     * Unsuspend a credential (remove from suspension list).
     * 
     * @param credentialId Credential ID
     * @param statusListId Status list ID
     * @return true if unsuspension succeeded
     */
    suspend fun unsuspendCredential(
        credentialId: String,
        statusListId: String
    ): Boolean
    
    /**
     * Check revocation status by status list ID and index.
     * 
     * @param statusListId Status list ID
     * @param index Index in the status list
     * @return Revocation status
     */
    suspend fun checkStatusByIndex(
        statusListId: String,
        index: Int
    ): RevocationStatus
    
    /**
     * Check revocation status by credential ID and status list ID.
     * 
     * @param credentialId Credential ID
     * @param statusListId Status list ID
     * @return Revocation status
     */
    suspend fun checkStatusByCredentialId(
        credentialId: String,
        statusListId: String
    ): RevocationStatus
    
    /**
     * Get the index assigned to a credential in a status list.
     * 
     * @param credentialId Credential ID
     * @param statusListId Status list ID
     * @return Index, or null if not assigned
     */
    suspend fun getCredentialIndex(
        credentialId: String,
        statusListId: String
    ): Int?
    
    /**
     * Assign an index to a credential in a status list.
     * 
     * @param credentialId Credential ID
     * @param statusListId Status list ID
     * @param index Optional specific index to assign (auto-assigned if null)
     * @return The assigned index
     */
    suspend fun assignCredentialIndex(
        credentialId: String,
        statusListId: String,
        index: Int? = null
    ): Int
    
    /**
     * Revoke multiple credentials in a batch operation.
     * 
     * @param credentialIds List of credential IDs to revoke
     * @param statusListId Status list ID
     * @return Map of credential ID to success status
     */
    suspend fun revokeCredentials(
        credentialIds: List<String>,
        statusListId: String
    ): Map<String, Boolean>
    
    /**
     * Update status list with multiple status updates in a batch operation.
     * 
     * @param statusListId Status list ID
     * @param updates List of status updates
     * @return Updated status list credential
     */
    suspend fun updateStatusListBatch(
        statusListId: String,
        updates: List<StatusUpdate>
    ): StatusListCredential
    
    /**
     * Expand a status list to accommodate more credentials.
     * 
     * @param statusListId Status list ID
     * @param additionalSize Additional size to add
     * @return Updated status list credential with expanded capacity
     */
    suspend fun expandStatusList(
        statusListId: String,
        additionalSize: Int
    ): StatusListCredential
}

