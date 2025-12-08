package com.trustweave.credential.revocation.internal

import com.trustweave.credential.identifiers.StatusListId
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.StatusPurpose
import com.trustweave.credential.revocation.RevocationStatus
import com.trustweave.credential.revocation.CredentialRevocationManager
import com.trustweave.credential.revocation.StatusListMetadata
import com.trustweave.credential.revocation.StatusListStatistics
import com.trustweave.credential.revocation.StatusUpdate
import java.util.Base64
import java.util.BitSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

/**
 * In-memory credential revocation manager implementation.
 *
 * Provides comprehensive revocation and suspension management for testing and development.
 * For production use, consider implementing persistent storage.
 */
internal class InMemoryCredentialRevocationManager : CredentialRevocationManager {
    // Status list metadata
    private val statusLists = ConcurrentHashMap<StatusListId, StatusListMetadata>()

    // Separate BitSets for revocation and suspension per status list
    private val revocationData = ConcurrentHashMap<StatusListId, BitSet>()
    private val suspensionData = ConcurrentHashMap<StatusListId, BitSet>()

    // Per-status-list index mapping: statusListId -> (credentialId -> index)
    private val credentialToIndex = ConcurrentHashMap<StatusListId, ConcurrentHashMap<String, Int>>()
    // Per-status-list reverse mapping: statusListId -> (index -> credentialId)
    private val indexToCredential = ConcurrentHashMap<StatusListId, ConcurrentHashMap<Int, String>>()
    // Per-status-list next available index
    private val nextIndex = ConcurrentHashMap<StatusListId, Int>()

    override suspend fun createStatusList(
        issuerDid: String,
        purpose: StatusPurpose,
        size: Int,
        customId: String?
    ): StatusListId {
        val id = StatusListId(customId ?: UUID.randomUUID().toString())
        val bitSet = BitSet(size)
        val now = Clock.System.now()

        val metadata = StatusListMetadata(
            id = id,
            issuerDid = issuerDid,
            purpose = purpose,
            size = size,
            createdAt = now,
            lastUpdated = now
        )

        statusLists[id] = metadata
        if (purpose == StatusPurpose.REVOCATION) {
            revocationData[id] = bitSet
        } else {
            suspensionData[id] = bitSet
        }
        credentialToIndex[id] = ConcurrentHashMap()
        indexToCredential[id] = ConcurrentHashMap()
        nextIndex[id] = 0

        return id
    }

    override suspend fun revokeCredential(
        credentialId: String,
        statusListId: StatusListId
    ): Boolean {
        val metadata = statusLists[statusListId] ?: return false

        // Ensure it's a revocation list
        if (metadata.purpose != StatusPurpose.REVOCATION) {
            return false
        }

        val bitSet = revocationData[statusListId] ?: return false
        val index = getOrAssignIndex(credentialId, statusListId)

        bitSet.set(index, true)
        updateMetadata(statusListId)

        return true
    }

    override suspend fun suspendCredential(
        credentialId: String,
        statusListId: StatusListId
    ): Boolean {
        val metadata = statusLists[statusListId] ?: return false

        // Ensure it's a suspension list
        if (metadata.purpose != StatusPurpose.SUSPENSION) {
            return false
        }

        val bitSet = suspensionData[statusListId] ?: return false
        val index = getOrAssignIndex(credentialId, statusListId)

        bitSet.set(index, true)
        updateMetadata(statusListId)

        return true
    }

    override suspend fun unrevokeCredential(
        credentialId: String,
        statusListId: StatusListId
    ): Boolean {
        val metadata = statusLists[statusListId] ?: return false

        if (metadata.purpose != StatusPurpose.REVOCATION) {
            return false
        }

        val bitSet = revocationData[statusListId] ?: return false
        val index = getCredentialIndex(credentialId, statusListId) ?: return false

        bitSet.set(index, false)
        updateMetadata(statusListId)

        return true
    }

    override suspend fun unsuspendCredential(
        credentialId: String,
        statusListId: StatusListId
    ): Boolean {
        val metadata = statusLists[statusListId] ?: return false

        if (metadata.purpose != StatusPurpose.SUSPENSION) {
            return false
        }

        val bitSet = suspensionData[statusListId] ?: return false
        val index = getCredentialIndex(credentialId, statusListId) ?: return false

        bitSet.set(index, false)
        updateMetadata(statusListId)

        return true
    }

    override suspend fun checkRevocationStatus(
        credential: VerifiableCredential
    ): RevocationStatus {
        val credentialStatus = credential.credentialStatus ?: return RevocationStatus(
            revoked = false,
            suspended = false
        )

        val statusListId = credentialStatus.statusListCredential ?: credentialStatus.id
        val index = credentialStatus.statusListIndex?.toIntOrNull()
            ?: credential.id?.value?.let { getCredentialIndex(it, statusListId) }

        return if (index != null) {
            checkStatusByIndex(statusListId, index)
        } else {
            RevocationStatus(
                revoked = false,
                suspended = false,
                statusListId = statusListId
            )
        }
    }

    override suspend fun checkStatusByIndex(
        statusListId: StatusListId,
        index: Int
    ): RevocationStatus {
        val metadata = statusLists[statusListId] ?: return RevocationStatus(
            revoked = false,
            suspended = false,
            statusListId = statusListId,
            index = index
        )

        val isRevoked = revocationData[statusListId]?.get(index) == true
        val isSuspended = suspensionData[statusListId]?.get(index) == true

        return RevocationStatus(
            revoked = isRevoked && metadata.purpose == StatusPurpose.REVOCATION,
            suspended = isSuspended && metadata.purpose == StatusPurpose.SUSPENSION,
            statusListId = statusListId,
            index = index
        )
    }

    override suspend fun checkStatusByCredentialId(
        credentialId: String,
        statusListId: StatusListId
    ): RevocationStatus {
        val index = getCredentialIndex(credentialId, statusListId)
            ?: return RevocationStatus(
                revoked = false,
                suspended = false,
                statusListId = statusListId
            )

        return checkStatusByIndex(statusListId, index)
    }

    override suspend fun getCredentialIndex(
        credentialId: String,
        statusListId: StatusListId
    ): Int? {
        return credentialToIndex[statusListId]?.get(credentialId)
    }

    override suspend fun assignCredentialIndex(
        credentialId: String,
        statusListId: StatusListId,
        index: Int?
    ): Int {
        val indices = credentialToIndex.getOrPut(statusListId) { ConcurrentHashMap() }
        val reverseIndices = indexToCredential.getOrPut(statusListId) { ConcurrentHashMap() }

        if (index != null) {
            // Check if index is already assigned
            if (reverseIndices.containsKey(index)) {
                throw IllegalArgumentException("Index $index is already assigned in status list ${statusListId.value}")
            }
            indices[credentialId] = index
            reverseIndices[index] = credentialId
            return index
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
            updateMetadata(statusListId)
            return candidate
        }
    }

    override suspend fun revokeCredentials(
        credentialIds: List<String>,
        statusListId: StatusListId
    ): Map<String, Boolean> {
        val metadata = statusLists[statusListId] ?: return credentialIds.associateWith { false }

        if (metadata.purpose != StatusPurpose.REVOCATION) {
            return credentialIds.associateWith { false }
        }

        val bitSet = revocationData[statusListId] ?: return credentialIds.associateWith { false }

        val results = credentialIds.associateWith { credentialId ->
            val index = getOrAssignIndex(credentialId, statusListId)
            bitSet.set(index, true)
            true
        }

        updateMetadata(statusListId)
        return results
    }

    override suspend fun updateStatusListBatch(
        statusListId: StatusListId,
        updates: List<StatusUpdate>
    ) {
        val metadata = statusLists[statusListId]
            ?: throw IllegalArgumentException("Status list not found: ${statusListId.value}")

        val revocationBitSet = revocationData[statusListId]
        val suspensionBitSet = suspensionData[statusListId]

        for (update in updates) {
            if (update.revoked != null && metadata.purpose == StatusPurpose.REVOCATION) {
                revocationBitSet?.set(update.index, update.revoked)
            }
            if (update.suspended != null && metadata.purpose == StatusPurpose.SUSPENSION) {
                suspensionBitSet?.set(update.index, update.suspended)
            }
        }

        updateMetadata(statusListId)
    }

    override suspend fun getStatusListStatistics(
        statusListId: StatusListId
    ): StatusListStatistics? {
        val metadata = statusLists[statusListId] ?: return null

        val bitSet = when (metadata.purpose) {
            StatusPurpose.REVOCATION -> revocationData[statusListId]
            StatusPurpose.SUSPENSION -> suspensionData[statusListId]
        } ?: return null

        val indices = credentialToIndex[statusListId] ?: emptyMap()
        val usedIndices = indices.size
        val totalCapacity = metadata.size
        val revokedCount = if (metadata.purpose == StatusPurpose.REVOCATION) {
            bitSet.cardinality()
        } else {
            0
        }
        val suspendedCount = if (metadata.purpose == StatusPurpose.SUSPENSION) {
            bitSet.cardinality()
        } else {
            0
        }
        val availableIndices = totalCapacity - usedIndices

        return StatusListStatistics(
            statusListId = statusListId,
            issuerDid = metadata.issuerDid,
            purpose = metadata.purpose,
            totalCapacity = totalCapacity,
            usedIndices = usedIndices,
            revokedCount = revokedCount,
            suspendedCount = suspendedCount,
            availableIndices = availableIndices,
            lastUpdated = metadata.lastUpdated
        )
    }

    override suspend fun getStatusList(statusListId: StatusListId): StatusListMetadata? {
        return statusLists[statusListId]
    }

    override suspend fun listStatusLists(issuerDid: String?): List<StatusListMetadata> {
        return if (issuerDid != null) {
            statusLists.values.filter { it.issuerDid == issuerDid }
        } else {
            statusLists.values.toList()
        }
    }

    override suspend fun deleteStatusList(statusListId: StatusListId): Boolean {
        val removed = statusLists.remove(statusListId) != null
        if (removed) {
            revocationData.remove(statusListId)
            suspensionData.remove(statusListId)
            credentialToIndex.remove(statusListId)
            indexToCredential.remove(statusListId)
            nextIndex.remove(statusListId)
        }
        return removed
    }

    override suspend fun expandStatusList(
        statusListId: StatusListId,
        additionalSize: Int
    ) {
        val metadata = statusLists[statusListId]
            ?: throw IllegalArgumentException("Status list not found: ${statusListId.value}")

        val currentBitSet = when (metadata.purpose) {
            StatusPurpose.REVOCATION -> revocationData[statusListId]
            StatusPurpose.SUSPENSION -> suspensionData[statusListId]
        } ?: throw IllegalArgumentException("Status list data not found: ${statusListId.value}")

        val newSize = metadata.size + additionalSize
        val newBitSet = BitSet(newSize)

        // Copy existing bits
        val currentSize = currentBitSet.size()
        for (i in 0 until currentSize) {
            if (currentBitSet.get(i)) {
                newBitSet.set(i, true)
            }
        }

        // Update storage
        when (metadata.purpose) {
            StatusPurpose.REVOCATION -> revocationData[statusListId] = newBitSet
            StatusPurpose.SUSPENSION -> suspensionData[statusListId] = newBitSet
        }

        // Update metadata
        statusLists[statusListId] = metadata.copy(
            size = newSize,
            lastUpdated = Clock.System.now()
        )
    }

    private fun getOrAssignIndex(credentialId: String, statusListId: StatusListId): Int {
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

    private fun updateMetadata(statusListId: StatusListId) {
        val metadata = statusLists[statusListId] ?: return
        statusLists[statusListId] = metadata.copy(lastUpdated = Clock.System.now())
    }
}

