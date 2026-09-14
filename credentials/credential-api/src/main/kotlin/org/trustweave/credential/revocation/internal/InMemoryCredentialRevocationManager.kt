package org.trustweave.credential.revocation.internal

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import org.trustweave.credential.identifiers.StatusListId
import org.trustweave.credential.model.StatusPurpose
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.revocation.CredentialRevocationManager
import org.trustweave.credential.revocation.RevocationStatus
import org.trustweave.credential.revocation.StatusListMetadata
import org.trustweave.credential.revocation.StatusListStatistics
import org.trustweave.credential.revocation.StatusUpdate
import java.util.BitSet
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory credential revocation manager implementation.
 *
 * Provides comprehensive revocation and suspension management for testing and development.
 * For production use, consider implementing persistent storage.
 *
 * ## A status list this manager does not hold
 *
 * Asked about a status list it has never seen, this manager **throws** rather than answering.
 *
 * That is a deliberate change from returning "not revoked". This manager's state is one JVM's
 * heap, so "I have no such list" is overwhelmingly likely to mean the list lives somewhere else —
 * not that the credential is in good standing. Answering "not revoked" quietly passed the
 * credential and, worse, did so *around* `RevocationFailurePolicy`: the host had configured how
 * an unanswerable revocation check should be treated, and never got asked.
 *
 * Throwing routes the decision back to that policy. `RevocationChecker` catches it and applies
 * FAIL_CLOSED, FAIL_WITH_WARNING or FAIL_OPEN as the host configured, and
 * `CredentialService.status` treats it as revoked, which is what its own fail-closed comment
 * always said it would do. A host that genuinely wants unknown lists to pass can still say so —
 * it just has to say it.
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

    // Per-status-list mutex to serialize BitSet mutations and index allocation
    private val listMutexes = ConcurrentHashMap<StatusListId, Mutex>()

    override suspend fun createStatusList(
        issuerDid: String,
        purpose: StatusPurpose,
        size: Int,
        customId: String?,
    ): StatusListId {
        val id = StatusListId(customId ?: UUID.randomUUID().toString())
        val bitSet = BitSet(size)
        val now = Clock.System.now()

        val metadata =
            StatusListMetadata(
                id = id,
                issuerDid = issuerDid,
                purpose = purpose,
                size = size,
                createdAt = now,
                lastUpdated = now,
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
        listMutexes[id] = Mutex()

        return id
    }

    override suspend fun revokeCredential(
        credentialId: String,
        statusListId: StatusListId,
    ): Boolean {
        val metadata = statusLists[statusListId] ?: return false

        // Ensure it's a revocation list
        if (metadata.purpose != StatusPurpose.REVOCATION) {
            return false
        }

        val bitSet = revocationData[statusListId] ?: return false
        val mutex = listMutexes.getOrPut(statusListId) { Mutex() }
        mutex.withLock {
            val index = getOrAssignIndex(credentialId, statusListId)
            bitSet.set(index, true)
        }
        updateMetadata(statusListId)

        return true
    }

    override suspend fun suspendCredential(
        credentialId: String,
        statusListId: StatusListId,
    ): Boolean {
        val metadata = statusLists[statusListId] ?: return false

        // Ensure it's a suspension list
        if (metadata.purpose != StatusPurpose.SUSPENSION) {
            return false
        }

        val bitSet = suspensionData[statusListId] ?: return false
        val mutex = listMutexes.getOrPut(statusListId) { Mutex() }
        mutex.withLock {
            val index = getOrAssignIndex(credentialId, statusListId)
            bitSet.set(index, true)
        }
        updateMetadata(statusListId)

        return true
    }

    override suspend fun unrevokeCredential(
        credentialId: String,
        statusListId: StatusListId,
    ): Boolean {
        val metadata = statusLists[statusListId] ?: return false

        if (metadata.purpose != StatusPurpose.REVOCATION) {
            return false
        }

        val bitSet = revocationData[statusListId] ?: return false
        val mutex = listMutexes.getOrPut(statusListId) { Mutex() }
        val index = getCredentialIndex(credentialId, statusListId) ?: return false
        mutex.withLock { bitSet.set(index, false) }
        updateMetadata(statusListId)

        return true
    }

    override suspend fun unsuspendCredential(
        credentialId: String,
        statusListId: StatusListId,
    ): Boolean {
        val metadata = statusLists[statusListId] ?: return false

        if (metadata.purpose != StatusPurpose.SUSPENSION) {
            return false
        }

        val bitSet = suspensionData[statusListId] ?: return false
        val mutex = listMutexes.getOrPut(statusListId) { Mutex() }
        val index = getCredentialIndex(credentialId, statusListId) ?: return false
        mutex.withLock { bitSet.set(index, false) }
        updateMetadata(statusListId)

        return true
    }

    override suspend fun checkRevocationStatus(credential: VerifiableCredential): RevocationStatus {
        val credentialStatus =
            credential.credentialStatus ?: return RevocationStatus(
                revoked = false,
                suspended = false,
            )

        val statusListId = credentialStatus.statusListCredential ?: credentialStatus.id
        if (!statusLists.containsKey(statusListId)) throw unknownStatusList(statusListId)
        val index =
            credentialStatus.statusListIndex?.toIntOrNull()
                ?: credential.id?.value?.let { getCredentialIndex(it, statusListId) }

        return if (index != null) {
            checkStatusByIndex(statusListId, index)
        } else {
            RevocationStatus(
                revoked = false,
                suspended = false,
                statusListId = statusListId,
            )
        }
    }

    override suspend fun checkStatusByIndex(
        statusListId: StatusListId,
        index: Int,
    ): RevocationStatus {
        val metadata = statusLists[statusListId] ?: throw unknownStatusList(statusListId)

        val isRevoked = revocationData[statusListId]?.get(index) == true
        val isSuspended = suspensionData[statusListId]?.get(index) == true

        return RevocationStatus(
            revoked = isRevoked && metadata.purpose == StatusPurpose.REVOCATION,
            suspended = isSuspended && metadata.purpose == StatusPurpose.SUSPENSION,
            statusListId = statusListId,
            index = index,
        )
    }

    override suspend fun checkStatusByCredentialId(
        credentialId: String,
        statusListId: StatusListId,
    ): RevocationStatus {
        if (!statusLists.containsKey(statusListId)) throw unknownStatusList(statusListId)
        // A known list with no index for this credential is a real answer: the credential was
        // never assigned a position on it, so nothing on it can have revoked the credential.
        val index =
            getCredentialIndex(credentialId, statusListId)
                ?: return RevocationStatus(
                    revoked = false,
                    suspended = false,
                    statusListId = statusListId,
                )

        return checkStatusByIndex(statusListId, index)
    }

    override suspend fun getCredentialIndex(
        credentialId: String,
        statusListId: StatusListId,
    ): Int? = credentialToIndex[statusListId]?.get(credentialId)

    override suspend fun assignCredentialIndex(
        credentialId: String,
        statusListId: StatusListId,
        index: Int?,
    ): Int {
        val indices = credentialToIndex.getOrPut(statusListId) { ConcurrentHashMap() }
        val reverseIndices = indexToCredential.getOrPut(statusListId) { ConcurrentHashMap() }
        val mutex = listMutexes.getOrPut(statusListId) { Mutex() }

        return mutex.withLock {
            if (index != null) {
                if (reverseIndices.containsKey(index)) {
                    throw IllegalArgumentException(
                        "Index $index is already assigned in status list ${statusListId.value}",
                    )
                }
                indices[credentialId] = index
                reverseIndices[index] = credentialId
                index
            } else {
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
    }

    override suspend fun revokeCredentials(
        credentialIds: List<String>,
        statusListId: StatusListId,
    ): Map<String, Boolean> {
        val metadata = statusLists[statusListId] ?: return credentialIds.associateWith { false }

        if (metadata.purpose != StatusPurpose.REVOCATION) {
            return credentialIds.associateWith { false }
        }

        val bitSet = revocationData[statusListId] ?: return credentialIds.associateWith { false }
        val mutex = listMutexes.getOrPut(statusListId) { Mutex() }

        val results =
            mutex.withLock {
                credentialIds.associateWith { credentialId ->
                    val index = getOrAssignIndex(credentialId, statusListId)
                    bitSet.set(index, true)
                    true
                }
            }

        updateMetadata(statusListId)
        return results
    }

    override suspend fun updateStatusListBatch(
        statusListId: StatusListId,
        updates: List<StatusUpdate>,
    ) {
        val metadata =
            statusLists[statusListId]
                ?: throw IllegalArgumentException("Status list not found: ${statusListId.value}")

        val revocationBitSet = revocationData[statusListId]
        val suspensionBitSet = suspensionData[statusListId]
        val mutex = listMutexes.getOrPut(statusListId) { Mutex() }

        mutex.withLock {
            for (update in updates) {
                // Copy nullable properties to locals — cross-module smart cast not possible.
                val revoked = update.revoked
                val suspended = update.suspended
                if (revoked != null && metadata.purpose == StatusPurpose.REVOCATION) {
                    revocationBitSet?.set(update.index, revoked)
                }
                if (suspended != null && metadata.purpose == StatusPurpose.SUSPENSION) {
                    suspensionBitSet?.set(update.index, suspended)
                }
            }
        }

        updateMetadata(statusListId)
    }

    override suspend fun getStatusListStatistics(statusListId: StatusListId): StatusListStatistics? {
        val metadata = statusLists[statusListId] ?: return null

        val bitSet =
            when (metadata.purpose) {
                StatusPurpose.REVOCATION -> revocationData[statusListId]
                StatusPurpose.SUSPENSION -> suspensionData[statusListId]
            } ?: return null

        val indices = credentialToIndex[statusListId] ?: emptyMap()
        val usedIndices = indices.size
        val totalCapacity = metadata.size
        val revokedCount =
            if (metadata.purpose == StatusPurpose.REVOCATION) {
                bitSet.cardinality()
            } else {
                0
            }
        val suspendedCount =
            if (metadata.purpose == StatusPurpose.SUSPENSION) {
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
            lastUpdated = metadata.lastUpdated,
        )
    }

    override suspend fun getStatusList(statusListId: StatusListId): StatusListMetadata? = statusLists[statusListId]

    override suspend fun listStatusLists(issuerDid: String?): List<StatusListMetadata> =
        if (issuerDid != null) {
            statusLists.values.filter { it.issuerDid == issuerDid }
        } else {
            statusLists.values.toList()
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
        additionalSize: Int,
    ) {
        val metadata =
            statusLists[statusListId]
                ?: throw IllegalArgumentException("Status list not found: ${statusListId.value}")

        val currentBitSet =
            when (metadata.purpose) {
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
        statusLists[statusListId] =
            metadata.copy(
                size = newSize,
                lastUpdated = Clock.System.now(),
            )
    }

    private fun getOrAssignIndex(
        credentialId: String,
        statusListId: StatusListId,
    ): Int {
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
     * The credential names a status list this manager does not hold, so it cannot answer.
     *
     * [IllegalStateException] rather than a "not revoked" result, and rather than
     * [IllegalArgumentException]: the caller's request is well-formed, this manager's state simply
     * does not cover it. `RevocationChecker` maps it to "Revocation manager error" and then
     * applies the host's configured failure policy.
     */
    private fun unknownStatusList(statusListId: StatusListId) =
        IllegalStateException(
            "Status list '${statusListId.value}' is not held by this in-memory revocation manager, so the " +
                "credential's revocation status cannot be determined. Register the list, or configure a manager " +
                "that can resolve it.",
        )

    private fun updateMetadata(statusListId: StatusListId) {
        val metadata = statusLists[statusListId] ?: return
        statusLists[statusListId] = metadata.copy(lastUpdated = Clock.System.now())
    }
}
