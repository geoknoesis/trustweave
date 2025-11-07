package io.geoknoesis.vericore.credential.revocation

import io.geoknoesis.vericore.credential.models.CredentialStatus
import io.geoknoesis.vericore.credential.models.VerifiableCredential
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
    val proof: io.geoknoesis.vericore.credential.models.Proof? = null
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
     * @return Status list credential
     */
    suspend fun createStatusList(
        issuerDid: String,
        purpose: StatusPurpose,
        size: Int = 131072 // 16KB bitstring
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
}

