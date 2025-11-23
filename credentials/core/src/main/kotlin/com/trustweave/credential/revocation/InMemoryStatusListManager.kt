package com.trustweave.credential.revocation

import com.trustweave.credential.models.VerifiableCredential
import java.util.Base64
import java.util.BitSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * In-memory status list manager implementation.
 * 
 * Provides comprehensive status list management for testing and development.
 * For production use, consider implementing persistent storage.
 * 
 * **Example Usage:**
 * ```kotlin
 * val manager = InMemoryStatusListManager()
 * 
 * val statusList = manager.createStatusList(
 *     issuerDid = "did:key:...",
 *     purpose = StatusPurpose.REVOCATION
 * )
 * 
 * manager.revokeCredential("cred-123", statusList.id)
 * val status = manager.checkRevocationStatus(credential)
 * ```
 */
class InMemoryStatusListManager : StatusListManager {
    // Status list credentials
    private val statusLists = ConcurrentHashMap<String, StatusListCredential>()
    
    // Separate BitSets for revocation and suspension per status list
    private val revocationData = ConcurrentHashMap<String, BitSet>()
    private val suspensionData = ConcurrentHashMap<String, BitSet>()
    
    // Per-status-list index mapping: statusListId -> (credentialId -> index)
    private val credentialToIndex = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()
    // Per-status-list reverse mapping: statusListId -> (index -> credentialId)
    private val indexToCredential = ConcurrentHashMap<String, ConcurrentHashMap<Int, String>>()
    // Per-status-list next available index
    private val nextIndex = ConcurrentHashMap<String, Int>()
    
    override suspend fun createStatusList(
        issuerDid: String,
        purpose: StatusPurpose,
        size: Int,
        customId: String?
    ): StatusListCredential = withContext(Dispatchers.IO) {
        val id = customId ?: UUID.randomUUID().toString()
        val bitSet = BitSet(size)
        
        val statusList = StatusListCredential(
            id = id,
            type = listOf("VerifiableCredential", "StatusList2021Credential"),
            issuer = issuerDid,
            credentialSubject = StatusListSubject(
                id = id,
                type = "StatusList2021",
                statusPurpose = purpose.name.lowercase(),
                encodedList = encodeBitSet(bitSet, size)
            ),
            issuanceDate = Instant.now().toString()
        )
        
        statusLists[id] = statusList
        if (purpose == StatusPurpose.REVOCATION) {
            revocationData[id] = bitSet
        } else {
            suspensionData[id] = bitSet
        }
        credentialToIndex[id] = ConcurrentHashMap()
        indexToCredential[id] = ConcurrentHashMap()
        nextIndex[id] = 0
        
        statusList
    }
    
    override suspend fun revokeCredential(
        credentialId: String,
        statusListId: String
    ): Boolean = withContext(Dispatchers.IO) {
        val statusList = statusLists[statusListId] ?: return@withContext false
        
        // Ensure it's a revocation list
        if (statusList.credentialSubject.statusPurpose != "revocation") {
            return@withContext false
        }
        
        val bitSet = revocationData[statusListId] ?: return@withContext false
        val index = getOrAssignIndex(credentialId, statusListId)
        
        bitSet.set(index, true)
        updateStatusListEncodedList(statusListId, bitSet)
        
        true
    }
    
    override suspend fun suspendCredential(
        credentialId: String,
        statusListId: String
    ): Boolean = withContext(Dispatchers.IO) {
        val statusList = statusLists[statusListId] ?: return@withContext false
        
        // Ensure it's a suspension list
        if (statusList.credentialSubject.statusPurpose != "suspension") {
            return@withContext false
        }
        
        val bitSet = suspensionData[statusListId] ?: return@withContext false
        val index = getOrAssignIndex(credentialId, statusListId)
        
        bitSet.set(index, true)
        updateStatusListEncodedList(statusListId, bitSet)
        
        true
    }
    
    override suspend fun unrevokeCredential(
        credentialId: String,
        statusListId: String
    ): Boolean = withContext(Dispatchers.IO) {
        val statusList = statusLists[statusListId] ?: return@withContext false
        
        if (statusList.credentialSubject.statusPurpose != "revocation") {
            return@withContext false
        }
        
        val bitSet = revocationData[statusListId] ?: return@withContext false
        val index = getCredentialIndex(credentialId, statusListId) ?: return@withContext false
        
        bitSet.set(index, false)
        updateStatusListEncodedList(statusListId, bitSet)
        
        true
    }
    
    override suspend fun unsuspendCredential(
        credentialId: String,
        statusListId: String
    ): Boolean = withContext(Dispatchers.IO) {
        val statusList = statusLists[statusListId] ?: return@withContext false
        
        if (statusList.credentialSubject.statusPurpose != "suspension") {
            return@withContext false
        }
        
        val bitSet = suspensionData[statusListId] ?: return@withContext false
        val index = getCredentialIndex(credentialId, statusListId) ?: return@withContext false
        
        bitSet.set(index, false)
        updateStatusListEncodedList(statusListId, bitSet)
        
        true
    }
    
    override suspend fun checkRevocationStatus(
        credential: VerifiableCredential
    ): RevocationStatus = withContext(Dispatchers.IO) {
        val credentialStatus = credential.credentialStatus ?: return@withContext RevocationStatus(
            revoked = false,
            suspended = false
        )
        
        val statusListId = credentialStatus.statusListCredential 
            ?: credentialStatus.id 
            ?: return@withContext RevocationStatus(revoked = false, suspended = false)
        
        // Use statusListIndex if provided, otherwise look up by credential ID
        val index = credentialStatus.statusListIndex?.toIntOrNull()
            ?: getCredentialIndex(credential.id ?: return@withContext RevocationStatus(
                revoked = false,
                suspended = false,
                statusListId = statusListId
            ), statusListId)
            ?: return@withContext RevocationStatus(
                revoked = false,
                suspended = false,
                statusListId = statusListId
            )
        
        return@withContext checkStatusByIndex(statusListId, index)
    }
    
    override suspend fun checkStatusByIndex(
        statusListId: String,
        index: Int
    ): RevocationStatus = withContext(Dispatchers.IO) {
        val statusList = statusLists[statusListId] ?: return@withContext RevocationStatus(
            revoked = false,
            suspended = false,
            statusListId = statusListId
        )
        
        val purpose = statusList.credentialSubject.statusPurpose
        val isRevoked = revocationData[statusListId]?.get(index) == true
        val isSuspended = suspensionData[statusListId]?.get(index) == true
        
        RevocationStatus(
            revoked = isRevoked && purpose == "revocation",
            suspended = isSuspended && purpose == "suspension",
            statusListId = statusListId
        )
    }
    
    override suspend fun checkStatusByCredentialId(
        credentialId: String,
        statusListId: String
    ): RevocationStatus = withContext(Dispatchers.IO) {
        val index = getCredentialIndex(credentialId, statusListId)
            ?: return@withContext RevocationStatus(
                revoked = false,
                suspended = false,
                statusListId = statusListId
            )
        
        checkStatusByIndex(statusListId, index)
    }
    
    override suspend fun getCredentialIndex(
        credentialId: String,
        statusListId: String
    ): Int? = withContext(Dispatchers.IO) {
        credentialToIndex[statusListId]?.get(credentialId)
    }
    
    override suspend fun assignCredentialIndex(
        credentialId: String,
        statusListId: String,
        index: Int?
    ): Int = withContext(Dispatchers.IO) {
        val indices = credentialToIndex.getOrPut(statusListId) { ConcurrentHashMap() }
        val reverseIndices = indexToCredential.getOrPut(statusListId) { ConcurrentHashMap() }
        
        if (index != null) {
            // Check if index is already assigned
            if (reverseIndices.containsKey(index)) {
                throw IllegalArgumentException("Index $index is already assigned in status list $statusListId")
            }
            indices[credentialId] = index
            reverseIndices[index] = credentialId
            index
        } else {
            // Auto-assign next available index
            val next = nextIndex.getOrPut(statusListId) { 0 }
            var candidate = next
            while (reverseIndices.containsKey(candidate)) {
                candidate++
            }
            indices[credentialId] = candidate
            reverseIndices[candidate] = credentialId
            nextIndex[statusListId] = candidate + 1
            candidate
        }
    }
    
    override suspend fun updateStatusList(
        statusListId: String,
        revokedIndices: List<Int>
    ): StatusListCredential = withContext(Dispatchers.IO) {
        val statusList = statusLists[statusListId] 
            ?: throw IllegalArgumentException("Status list not found: $statusListId")
        
        val purpose = statusList.credentialSubject.statusPurpose
        val bitSet = if (purpose == "revocation") {
            revocationData[statusListId] ?: throw IllegalArgumentException("Revocation data not found for: $statusListId")
        } else {
            suspensionData[statusListId] ?: throw IllegalArgumentException("Suspension data not found for: $statusListId")
        }
        
        for (index in revokedIndices) {
            bitSet.set(index, true)
        }
        
        updateStatusListEncodedList(statusListId, bitSet)
        statusLists[statusListId]!!
    }
    
    override suspend fun updateStatusListBatch(
        statusListId: String,
        updates: List<StatusUpdate>
    ): StatusListCredential = withContext(Dispatchers.IO) {
        val statusList = statusLists[statusListId] 
            ?: throw IllegalArgumentException("Status list not found: $statusListId")
        
        val purpose = statusList.credentialSubject.statusPurpose
        val revocationBitSet = revocationData[statusListId]
        val suspensionBitSet = suspensionData[statusListId]
        
        for (update in updates) {
            if (update.revoked != null && purpose == "revocation") {
                revocationBitSet?.set(update.index, update.revoked)
            }
            if (update.suspended != null && purpose == "suspension") {
                suspensionBitSet?.set(update.index, update.suspended)
            }
        }
        
        val bitSet = if (purpose == "revocation") revocationBitSet else suspensionBitSet
        if (bitSet != null) {
            updateStatusListEncodedList(statusListId, bitSet)
        }
        
        statusLists[statusListId]!!
    }
    
    override suspend fun getStatusList(statusListId: String): StatusListCredential? {
        return statusLists[statusListId]
    }
    
    override suspend fun listStatusLists(issuerDid: String?): List<StatusListCredential> = withContext(Dispatchers.IO) {
        if (issuerDid != null) {
            statusLists.values.filter { it.issuer == issuerDid }
        } else {
            statusLists.values.toList()
        }
    }
    
    override suspend fun deleteStatusList(statusListId: String): Boolean = withContext(Dispatchers.IO) {
        val removed = statusLists.remove(statusListId) != null
        if (removed) {
            revocationData.remove(statusListId)
            suspensionData.remove(statusListId)
            credentialToIndex.remove(statusListId)
            indexToCredential.remove(statusListId)
            nextIndex.remove(statusListId)
        }
        removed
    }
    
    override suspend fun getStatusListStatistics(statusListId: String): StatusListStatistics? = withContext(Dispatchers.IO) {
        val statusList = statusLists[statusListId] ?: return@withContext null
        
        val purpose = statusList.credentialSubject.statusPurpose
        val bitSet = if (purpose == "revocation") {
            revocationData[statusListId]
        } else {
            suspensionData[statusListId]
        } ?: return@withContext null
        
        val indices = credentialToIndex[statusListId] ?: emptyMap()
        val usedIndices = indices.size
        val totalCapacity = bitSet.size()
        val revokedCount = if (purpose == "revocation") {
            bitSet.cardinality()
        } else {
            0
        }
        val suspendedCount = if (purpose == "suspension") {
            bitSet.cardinality()
        } else {
            0
        }
        val availableIndices = totalCapacity - usedIndices
        
        StatusListStatistics(
            statusListId = statusListId,
            totalCapacity = totalCapacity,
            usedIndices = usedIndices,
            revokedCount = revokedCount,
            suspendedCount = suspendedCount,
            availableIndices = availableIndices,
            lastUpdated = Instant.now()
        )
    }
    
    override suspend fun revokeCredentials(
        credentialIds: List<String>,
        statusListId: String
    ): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val statusList = statusLists[statusListId] ?: return@withContext credentialIds.associateWith { false }
        
        if (statusList.credentialSubject.statusPurpose != "revocation") {
            return@withContext credentialIds.associateWith { false }
        }
        
        val bitSet = revocationData[statusListId] ?: return@withContext credentialIds.associateWith { false }
        
        val results = credentialIds.associateWith { credentialId ->
            val index = getOrAssignIndex(credentialId, statusListId)
            bitSet.set(index, true)
            true
        }
        
        updateStatusListEncodedList(statusListId, bitSet)
        results
    }
    
    override suspend fun expandStatusList(
        statusListId: String,
        additionalSize: Int
    ): StatusListCredential = withContext(Dispatchers.IO) {
        val statusList = statusLists[statusListId] 
            ?: throw IllegalArgumentException("Status list not found: $statusListId")
        
        val purpose = statusList.credentialSubject.statusPurpose
        val currentBitSet = if (purpose == "revocation") {
            revocationData[statusListId]
        } else {
            suspensionData[statusListId]
        } ?: throw IllegalArgumentException("Status list data not found: $statusListId")
        
        val currentSize = currentBitSet.size()
        val newSize = currentSize + additionalSize
        val newBitSet = BitSet(newSize)
        
        // Copy existing bits
        for (i in 0 until currentSize) {
            if (currentBitSet.get(i)) {
                newBitSet.set(i, true)
            }
        }
        
        // Update storage
        if (purpose == "revocation") {
            revocationData[statusListId] = newBitSet
        } else {
            suspensionData[statusListId] = newBitSet
        }
        
        val updatedStatusList = statusList.copy(
            credentialSubject = statusList.credentialSubject.copy(
                encodedList = encodeBitSet(newBitSet, newSize)
            )
        )
        statusLists[statusListId] = updatedStatusList
        
        updatedStatusList
    }
    
    /**
     * Get or assign an index for a credential in a status list.
     */
    private fun getOrAssignIndex(credentialId: String, statusListId: String): Int {
        val indices = credentialToIndex.getOrPut(statusListId) { ConcurrentHashMap() }
        val reverseIndices = indexToCredential.getOrPut(statusListId) { ConcurrentHashMap() }
        
        return indices.getOrPut(credentialId) {
            val next = nextIndex.getOrPut(statusListId) { 0 }
            var candidate = next
            while (reverseIndices.containsKey(candidate)) {
                candidate++
            }
            reverseIndices[candidate] = credentialId
            nextIndex[statusListId] = candidate + 1
            candidate
        }
    }
    
    /**
     * Update the encoded list in a status list credential.
     */
    private fun updateStatusListEncodedList(statusListId: String, bitSet: BitSet) {
        val statusList = statusLists[statusListId] ?: return
        val size = bitSet.size()
        val updatedStatusList = statusList.copy(
            credentialSubject = statusList.credentialSubject.copy(
                encodedList = encodeBitSet(bitSet, size)
            )
        )
        statusLists[statusListId] = updatedStatusList
    }
    
    /**
     * Encode BitSet to Base64 string.
     */
    private fun encodeBitSet(bitSet: BitSet, size: Int): String {
        val bytes = ByteArray((size + 7) / 8)
        for (i in 0 until size) {
            if (bitSet.get(i)) {
                val byteIndex = i / 8
                val bitIndex = i % 8
                bytes[byteIndex] = (bytes[byteIndex].toInt() or (1 shl bitIndex)).toByte()
            }
        }
        return Base64.getEncoder().encodeToString(bytes)
    }
    
    /**
     * Decode Base64 string to BitSet.
     */
    private fun decodeBitSet(encoded: String): BitSet {
        val bytes = Base64.getDecoder().decode(encoded)
        val bitSet = BitSet(bytes.size * 8)
        for (i in bytes.indices) {
            for (j in 0..7) {
                if ((bytes[i].toInt() and (1 shl j)) != 0) {
                    bitSet.set(i * 8 + j)
                }
            }
        }
        return bitSet
    }
}
