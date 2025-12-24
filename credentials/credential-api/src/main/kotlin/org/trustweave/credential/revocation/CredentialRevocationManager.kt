package org.trustweave.credential.revocation

import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.StatusPurpose
import kotlinx.datetime.Instant

/**
 * Credential revocation manager interface for credential revocation and suspension.
 *
 * Provides operations for revoking and suspending credentials, and checking their status.
 * Uses Status List 2021 (W3C standard) as the underlying mechanism for efficient
 * revocation list management.
 *
 * **Example Usage**:
 * ```kotlin
 * val manager = RevocationManagers.default()
 *
 * // Create status list
 * val statusList = manager.createStatusList(
 *     issuerDid = issuerDid,
 *     purpose = StatusPurpose.REVOCATION
 * )
 *
 * // Revoke credential
 * manager.revokeCredential(
 *     credentialId = credentialId,
 *     statusListId = statusList.id
 * )
 *
 * // Check revocation status
 * val status = manager.checkRevocationStatus(envelope)
 * ```
 */
interface CredentialRevocationManager {
    /**
     * Create a new status list.
     *
     * @param issuerDid Issuer DID
     * @param purpose Status list purpose (revocation or suspension)
     * @param size Optional initial size (default: 131072 entries = 16KB bitstring)
     * @param customId Optional custom ID for the status list (generated if not provided)
     * @return Status list credential ID
     */
    suspend fun createStatusList(
        issuerDid: String,
        purpose: StatusPurpose,
        size: Int = 131072, // 16KB bitstring
        customId: String? = null
    ): StatusListId

    /**
     * Revoke a credential.
     *
     * @param credentialId Credential ID
     * @param statusListId Status list ID
     * @return true if revocation succeeded
     */
    suspend fun revokeCredential(
        credentialId: String,
        statusListId: StatusListId
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
        statusListId: StatusListId
    ): Boolean

    /**
     * Unrevoke a credential (remove from revocation list).
     *
     * @param credentialId Credential ID
     * @param statusListId Status list ID
     * @return true if unrevocation succeeded
     */
    suspend fun unrevokeCredential(
        credentialId: String,
        statusListId: StatusListId
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
        statusListId: StatusListId
    ): Boolean

    /**
     * Check revocation status of a Verifiable Credential.
     *
     * @param credential VerifiableCredential to check
     * @return Revocation status
     */
    suspend fun checkRevocationStatus(
        credential: org.trustweave.credential.model.vc.VerifiableCredential
    ): RevocationStatus

    /**
     * Check revocation status by status list ID and index.
     *
     * @param statusListId Status list ID
     * @param index Index in the status list
     * @return Revocation status
     */
    suspend fun checkStatusByIndex(
        statusListId: StatusListId,
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
        statusListId: StatusListId
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
        statusListId: StatusListId
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
        statusListId: StatusListId,
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
        statusListId: StatusListId
    ): Map<String, Boolean>

    /**
     * Update status list with multiple status updates in a batch operation.
     *
     * @param statusListId Status list ID
     * @param updates List of status updates
     */
    suspend fun updateStatusListBatch(
        statusListId: StatusListId,
        updates: List<StatusUpdate>
    )

    /**
     * Get statistics for a status list.
     *
     * @param statusListId Status list ID
     * @return Status list statistics, or null if not found
     */
    suspend fun getStatusListStatistics(
        statusListId: StatusListId
    ): StatusListStatistics?

    /**
     * Get status list by ID.
     *
     * @param statusListId Status list ID
     * @return Status list metadata, or null if not found
     */
    suspend fun getStatusList(statusListId: StatusListId): StatusListMetadata?

    /**
     * List all status lists, optionally filtered by issuer.
     *
     * @param issuerDid Optional issuer DID to filter by
     * @return List of status list metadata
     */
    suspend fun listStatusLists(issuerDid: String? = null): List<StatusListMetadata>

    /**
     * Delete a status list.
     *
     * @param statusListId Status list ID
     * @return true if deleted, false if not found
     */
    suspend fun deleteStatusList(statusListId: StatusListId): Boolean

    /**
     * Expand a status list to accommodate more credentials.
     *
     * @param statusListId Status list ID
     * @param additionalSize Additional size to add
     */
    suspend fun expandStatusList(
        statusListId: StatusListId,
        additionalSize: Int
    )
}

/**
 * Revocation status result.
 *
 * @param revoked Whether the credential is revoked
 * @param suspended Whether the credential is suspended
 * @param statusListId Status list ID if applicable
 * @param index Index in status list if applicable
 * @param reason Optional revocation/suspension reason
 */
data class RevocationStatus(
    val revoked: Boolean,
    val suspended: Boolean = false,
    val statusListId: StatusListId? = null,
    val index: Int? = null,
    val reason: String? = null
) {
    /**
     * True if credential is valid (not revoked and not suspended).
     */
    val isValid: Boolean
        get() = !revoked && !suspended
}

/**
 * Status list statistics.
 *
 * @param statusListId Status list ID
 * @param issuerDid Issuer DID
 * @param purpose Status list purpose
 * @param totalCapacity Total capacity of the status list
 * @param usedIndices Number of indices currently assigned to credentials
 * @param revokedCount Number of revoked credentials
 * @param suspendedCount Number of suspended credentials
 * @param availableIndices Number of available indices
 * @param lastUpdated Last update timestamp
 */
data class StatusListStatistics(
    val statusListId: StatusListId,
    val issuerDid: String,
    val purpose: StatusPurpose,
    val totalCapacity: Int,
    val usedIndices: Int,
    val revokedCount: Int,
    val suspendedCount: Int,
    val availableIndices: Int,
    val lastUpdated: Instant
)

/**
 * Status list metadata.
 */
data class StatusListMetadata(
    val id: StatusListId,
    val issuerDid: String,
    val purpose: StatusPurpose,
    val size: Int,
    val createdAt: Instant,
    val lastUpdated: Instant
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

