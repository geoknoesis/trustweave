package com.geoknoesis.vericore.multiparty

import com.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Issuance participant.
 */
@Serializable
data class IssuanceParticipant(
    val did: String,
    val role: ParticipantRole,
    val keyId: String,
    val signatureRequired: Boolean = true
)

/**
 * Participant role.
 */
enum class ParticipantRole {
    ISSUER,      // Primary issuer
    CO_ISSUER,   // Co-issuer
    APPROVER,    // Approver (must approve before issuance)
    WITNESS      // Witness (signs but doesn't issue)
}

/**
 * Multi-party issuance request.
 */
@Serializable
data class MultiPartyIssuanceRequest(
    val requestId: String,
    val credential: VerifiableCredential,
    val participants: List<IssuanceParticipant>,
    val consensusType: ConsensusType = ConsensusType.ALL,
    val expiresAt: String? = null // ISO 8601
)

/**
 * Consensus type.
 */
enum class ConsensusType {
    ALL,         // All participants must sign
    MAJORITY,    // Majority must sign
    QUORUM,      // Quorum (e.g., 2/3) must sign
    ANY          // Any participant can sign
}

/**
 * Issuance signature.
 */
@Serializable
data class IssuanceSignature(
    val participantDid: String,
    val signature: String,
    val timestamp: String, // ISO 8601
    val approved: Boolean = true
)

/**
 * Multi-party issuance status.
 */
enum class IssuanceStatus {
    PENDING,
    APPROVED,
    REJECTED,
    COMPLETED,
    EXPIRED
}

/**
 * Multi-party issuance result.
 */
@Serializable
data class MultiPartyIssuanceResult(
    val requestId: String,
    val status: IssuanceStatus,
    val signatures: List<IssuanceSignature>,
    val credential: VerifiableCredential? = null,
    val createdAt: String, // ISO 8601
    val completedAt: String? = null
)

/**
 * Multi-party credential issuance service.
 * 
 * Enables collaborative credential issuance with multiple participants.
 * 
 * **Example Usage:**
 * ```kotlin
 * val service = MultiPartyIssuanceService()
 * 
 * // Create issuance request
 * val request = MultiPartyIssuanceRequest(
 *     requestId = UUID.randomUUID().toString(),
 *     credential = credential,
 *     participants = listOf(
 *         IssuanceParticipant("did:key:issuer1", ParticipantRole.ISSUER, "key1"),
 *         IssuanceParticipant("did:key:issuer2", ParticipantRole.CO_ISSUER, "key2")
 *     ),
 *     consensusType = ConsensusType.ALL
 * )
 * 
 * // Submit request
 * val result = service.submitRequest(request)
 * 
 * // Add signature
 * service.addSignature(result.requestId, participantDid, signature)
 * 
 * // Complete issuance when consensus reached
 * val issuedCredential = service.completeIssuance(result.requestId)
 * ```
 */
interface MultiPartyIssuanceService {
    /**
     * Submit a multi-party issuance request.
     * 
     * @param request Issuance request
     * @return Issuance result
     */
    suspend fun submitRequest(request: MultiPartyIssuanceRequest): MultiPartyIssuanceResult
    
    /**
     * Add a signature from a participant.
     * 
     * @param requestId Request ID
     * @param participantDid Participant DID
     * @param signature Signature
     * @param approved Whether participant approves
     * @return Updated issuance result
     */
    suspend fun addSignature(
        requestId: String,
        participantDid: String,
        signature: String,
        approved: Boolean = true
    ): MultiPartyIssuanceResult?
    
    /**
     * Get issuance request status.
     * 
     * @param requestId Request ID
     * @return Issuance result, or null if not found
     */
    suspend fun getRequest(requestId: String): MultiPartyIssuanceResult?
    
    /**
     * Complete issuance when consensus is reached.
     * 
     * @param requestId Request ID
     * @return Issued credential, or null if consensus not reached
     */
    suspend fun completeIssuance(requestId: String): VerifiableCredential?
    
    /**
     * Reject issuance request.
     * 
     * @param requestId Request ID
     * @param participantDid Participant who rejected
     * @return Updated issuance result
     */
    suspend fun rejectRequest(requestId: String, participantDid: String): MultiPartyIssuanceResult?
}

/**
 * Simple multi-party issuance service implementation.
 */
class SimpleMultiPartyIssuanceService : MultiPartyIssuanceService {
    private val requests = ConcurrentHashMap<String, MultiPartyIssuanceRequest>()
    private val results = ConcurrentHashMap<String, MultiPartyIssuanceResult>()
    private val lock = Any()
    
    override suspend fun submitRequest(request: MultiPartyIssuanceRequest): MultiPartyIssuanceResult = withContext(Dispatchers.IO) {
        synchronized(lock) {
            requests[request.requestId] = request
            
            val result = MultiPartyIssuanceResult(
                requestId = request.requestId,
                status = IssuanceStatus.PENDING,
                signatures = emptyList(),
                credential = null,
                createdAt = Instant.now().toString(),
                completedAt = null
            )
            
            results[request.requestId] = result
            result
        }
    }
    
    override suspend fun addSignature(
        requestId: String,
        participantDid: String,
        signature: String,
        approved: Boolean
    ): MultiPartyIssuanceResult? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val request = requests[requestId] ?: return@withContext null
            val currentResult = results[requestId] ?: return@withContext null
            
            if (currentResult.status != IssuanceStatus.PENDING) {
                return@withContext currentResult
            }
            
            val newSignature = IssuanceSignature(
                participantDid = participantDid,
                signature = signature,
                timestamp = Instant.now().toString(),
                approved = approved
            )
            
            val updatedSignatures = currentResult.signatures + newSignature
            val newStatus = if (!approved) {
                IssuanceStatus.REJECTED
            } else {
                checkConsensus(request, updatedSignatures)
            }
            
            val updatedResult = currentResult.copy(
                status = newStatus,
                signatures = updatedSignatures,
                completedAt = if (newStatus == IssuanceStatus.COMPLETED) Instant.now().toString() else null
            )
            
            results[requestId] = updatedResult
            updatedResult
        }
    }
    
    override suspend fun getRequest(requestId: String): MultiPartyIssuanceResult? = withContext(Dispatchers.IO) {
        results[requestId]
    }
    
    override suspend fun completeIssuance(requestId: String): VerifiableCredential? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val result = results[requestId] ?: return@withContext null
            val request = requests[requestId] ?: return@withContext null
            
            if (result.status != IssuanceStatus.COMPLETED) {
                return@withContext null
            }
            
            // In a real implementation, combine signatures into the credential proof
            request.credential
        }
    }
    
    override suspend fun rejectRequest(requestId: String, participantDid: String): MultiPartyIssuanceResult? = withContext(Dispatchers.IO) {
        addSignature(requestId, participantDid, "", approved = false)
    }
    
    private fun checkConsensus(
        request: MultiPartyIssuanceRequest,
        signatures: List<IssuanceSignature>
    ): IssuanceStatus {
        val approvedSignatures = signatures.filter { it.approved }
        val requiredParticipants = request.participants.filter { it.signatureRequired }
        
        return when (request.consensusType) {
            ConsensusType.ALL -> {
                if (approvedSignatures.size >= requiredParticipants.size) {
                    IssuanceStatus.COMPLETED
                } else {
                    IssuanceStatus.PENDING
                }
            }
            ConsensusType.MAJORITY -> {
                val majority = (requiredParticipants.size / 2) + 1
                if (approvedSignatures.size >= majority) {
                    IssuanceStatus.COMPLETED
                } else {
                    IssuanceStatus.PENDING
                }
            }
            ConsensusType.QUORUM -> {
                val quorum = (requiredParticipants.size * 2) / 3
                if (approvedSignatures.size >= quorum) {
                    IssuanceStatus.COMPLETED
                } else {
                    IssuanceStatus.PENDING
                }
            }
            ConsensusType.ANY -> {
                if (approvedSignatures.isNotEmpty()) {
                    IssuanceStatus.COMPLETED
                } else {
                    IssuanceStatus.PENDING
                }
            }
        }
    }
}

