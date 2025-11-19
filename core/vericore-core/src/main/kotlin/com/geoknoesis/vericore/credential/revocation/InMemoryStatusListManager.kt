package com.geoknoesis.vericore.credential.revocation

import com.geoknoesis.vericore.credential.models.VerifiableCredential
import java.util.Base64
import java.util.BitSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-memory status list manager implementation.
 * 
 * Provides basic status list management for testing and development.
 * For production use, consider implementing persistent storage.
 * 
 * **Example Usage**:
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
    private val statusLists = ConcurrentHashMap<String, StatusListCredential>()
    private val statusListData = ConcurrentHashMap<String, BitSet>()
    private val credentialToIndex = ConcurrentHashMap<String, Int>()
    private val indexToCredential = ConcurrentHashMap<Int, String>()
    private var nextIndex = 0
    
    override suspend fun createStatusList(
        issuerDid: String,
        purpose: StatusPurpose,
        size: Int
    ): StatusListCredential = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
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
            issuanceDate = java.time.format.DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.now())
        )
        
        statusLists[id] = statusList
        statusListData[id] = bitSet
        
        statusList
    }
    
    override suspend fun revokeCredential(
        credentialId: String,
        statusListId: String
    ): Boolean = withContext(Dispatchers.IO) {
        val bitSet = statusListData[statusListId] ?: return@withContext false
        
        val index = credentialToIndex.getOrPut(credentialId) {
            val idx = nextIndex++
            indexToCredential[idx] = credentialId
            idx
        }
        
        bitSet.set(index, true)
        
        // Update encoded list in status list credential
        val statusList = statusLists[statusListId] ?: return@withContext false
        val updatedStatusList = statusList.copy(
            credentialSubject = statusList.credentialSubject.copy(
                encodedList = encodeBitSet(bitSet, bitSet.size())
            )
        )
        statusLists[statusListId] = updatedStatusList
        
        true
    }
    
    override suspend fun suspendCredential(
        credentialId: String,
        statusListId: String
    ): Boolean {
        // For now, suspension uses the same mechanism as revocation
        // In a full implementation, separate suspension lists would be used
        return revokeCredential(credentialId, statusListId)
    }
    
    override suspend fun checkRevocationStatus(
        credential: VerifiableCredential
    ): RevocationStatus = withContext(Dispatchers.IO) {
        val credentialStatus = credential.credentialStatus ?: return@withContext RevocationStatus(
            revoked = false,
            suspended = false
        )
        
        val statusListId = credentialStatus.statusListCredential ?: credentialStatus.id
        val statusList = statusLists[statusListId] ?: return@withContext RevocationStatus(
            revoked = false,
            suspended = false,
            statusListId = statusListId
        )
        
        val index = credentialToIndex[credential.id] ?: return@withContext RevocationStatus(
            revoked = false,
            suspended = false,
            statusListId = statusListId
        )
        
        val bitSet = statusListData[statusListId] ?: return@withContext RevocationStatus(
            revoked = false,
            suspended = false,
            statusListId = statusListId
        )
        
        val isRevoked = bitSet.get(index)
        val purpose = statusList.credentialSubject.statusPurpose
        
        RevocationStatus(
            revoked = isRevoked && purpose == "revocation",
            suspended = isRevoked && purpose == "suspension",
            statusListId = statusListId
        )
    }
    
    override suspend fun updateStatusList(
        statusListId: String,
        revokedIndices: List<Int>
    ): StatusListCredential = withContext(Dispatchers.IO) {
        val bitSet = statusListData[statusListId] ?: throw IllegalArgumentException("Status list not found: $statusListId")
        
        for (index in revokedIndices) {
            bitSet.set(index, true)
        }
        
        val statusList = statusLists[statusListId] ?: throw IllegalArgumentException("Status list not found: $statusListId")
        val updatedStatusList = statusList.copy(
            credentialSubject = statusList.credentialSubject.copy(
                encodedList = encodeBitSet(bitSet, bitSet.size())
            )
        )
        
        statusLists[statusListId] = updatedStatusList
        updatedStatusList
    }
    
    override suspend fun getStatusList(statusListId: String): StatusListCredential? {
        return statusLists[statusListId]
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

