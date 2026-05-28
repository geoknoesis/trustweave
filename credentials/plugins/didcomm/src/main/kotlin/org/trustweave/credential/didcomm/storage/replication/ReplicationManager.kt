package org.trustweave.credential.didcomm.storage.replication

import org.trustweave.credential.didcomm.models.DidCommMessage
import org.trustweave.credential.didcomm.storage.DidCommMessageStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.io.Closeable

/**
 * Manages message replication across multiple storage backends.
 *
 * Provides high availability by replicating messages to multiple
 * storage instances.
 *
 * **Lifecycle:** This manager owns a long-lived [replicationScope] used for
 * fire-and-forget background replication (ASYNC mode and best-effort delete /
 * archive propagation). Callers **must** [close] the manager when it is no
 * longer needed so the background scope is cancelled and does not leak
 * coroutines.
 *
 * **Example Usage:**
 * ```kotlin
 * val primary = PostgresDidCommMessageStorage(dataSource1)
 * val replica1 = PostgresDidCommMessageStorage(dataSource2)
 * val replica2 = PostgresDidCommMessageStorage(dataSource3)
 *
 * val replicationManager = ReplicationManager(
 *     primary = primary,
 *     replicas = listOf(replica1, replica2),
 *     replicationMode = ReplicationMode.ASYNC
 * )
 *
 * try {
 *     replicationManager.store(message)
 * } finally {
 *     replicationManager.close()
 * }
 * ```
 */
class ReplicationManager(
    private val primary: DidCommMessageStorage,
    private val replicas: List<DidCommMessageStorage>,
    private val replicationMode: ReplicationMode = ReplicationMode.ASYNC
) : DidCommMessageStorage, Closeable {

    private val logger = LoggerFactory.getLogger(ReplicationManager::class.java)

    /**
     * Supervised scope for fire-and-forget background replication.
     *
     * A [SupervisorJob] ensures a single failing replica write does not cancel
     * sibling replica writes or the scope itself. Callers must [close] this
     * manager to cancel the scope and avoid leaking coroutines.
     */
    private val replicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    enum class ReplicationMode {
        SYNC,  // Wait for all replicas
        ASYNC, // Fire and forget
        QUORUM // Wait for majority
    }

    /**
     * Cancels the background [replicationScope]. Must be called when this
     * manager is no longer needed to avoid leaking background coroutines.
     */
    override fun close() {
        replicationScope.cancel()
    }

    override suspend fun store(message: DidCommMessage): String = coroutineScope {
        // Write to primary
        val messageId = primary.store(message)

        // Replicate to replicas
        when (replicationMode) {
            ReplicationMode.SYNC -> {
                replicas.map { async { it.store(message) } }.awaitAll()
            }
            ReplicationMode.ASYNC -> {
                // Fire and forget: launch on the long-lived replication scope so
                // store() does not block on replica writes, and a failure on one
                // replica neither cancels store() nor sibling replica writes.
                replicas.forEach { replica ->
                    replicationScope.launch {
                        runCatching { replica.store(message) }
                            .onFailure { logger.warn("Async replica store failed for message {}", messageId, it) }
                    }
                }
            }
            ReplicationMode.QUORUM -> {
                // Quorum write: start ALL replica writes, then return as soon as at
                // least `quorum` of them complete successfully. Completions are
                // observed in finish order (not list position) via a channel, so a
                // slow/failing replica early in the list does not block the quorum.
                // Stragglers keep running on the background replicationScope after
                // the quorum is met. Throw only if every replica has finished and
                // fewer than `quorum` of them succeeded.
                val quorum = (replicas.size / 2) + 1
                if (replicas.size < quorum) {
                    throw IllegalStateException(
                        "Cannot satisfy quorum of $quorum with only ${replicas.size} replica(s)"
                    )
                }
                // Capacity == replicas.size so background sends never suspend, even
                // after this method has returned and stopped receiving.
                val results = Channel<Boolean>(capacity = replicas.size)
                replicas.forEach { replica ->
                    // Run on the long-lived scope (with SupervisorJob) so a single
                    // failing replica neither cancels its siblings nor the parent
                    // store() coroutine, and stragglers survive early return.
                    replicationScope.launch {
                        val ok = runCatching { replica.store(message) }
                            .onFailure { logger.warn("Quorum replica store failed for message {}", messageId, it) }
                            .isSuccess
                        results.send(ok)
                    }
                }

                var succeeded = 0
                var completed = 0
                while (completed < replicas.size && succeeded < quorum) {
                    if (results.receive()) succeeded++
                    completed++
                }

                if (succeeded < quorum) {
                    throw IllegalStateException(
                        "Quorum write failed: only $succeeded of ${replicas.size} replica writes " +
                            "succeeded, quorum of $quorum required"
                    )
                }
            }
        }

        messageId
    }

    override suspend fun get(messageId: String): DidCommMessage? {
        // Try primary first
        return primary.get(messageId) ?: run {
            // If not found, try replicas
            replicas.firstNotNullOfOrNull { it.get(messageId) }
        }
    }

    override suspend fun getMessagesForDid(
        did: String,
        limit: Int,
        offset: Int
    ): List<DidCommMessage> {
        // Read from primary
        return primary.getMessagesForDid(did, limit, offset)
    }

    override suspend fun getThreadMessages(thid: String): List<DidCommMessage> {
        return primary.getThreadMessages(thid)
    }

    override suspend fun delete(messageId: String): Boolean {
        val deleted = primary.delete(messageId)
        if (deleted) {
            // Best-effort replicate delete on the background scope.
            replicas.forEach { replica ->
                replicationScope.launch {
                    runCatching { replica.delete(messageId) }
                        .onFailure { logger.warn("Replica delete failed for message {}", messageId, it) }
                }
            }
        }
        return deleted
    }

    override suspend fun deleteMessagesForDid(did: String): Int {
        val count = primary.deleteMessagesForDid(did)
        if (count > 0) {
            replicas.forEach { replica ->
                replicationScope.launch {
                    runCatching { replica.deleteMessagesForDid(did) }
                        .onFailure { logger.warn("Replica deleteMessagesForDid failed for did {}", did, it) }
                }
            }
        }
        return count
    }

    override suspend fun deleteThreadMessages(thid: String): Int {
        val count = primary.deleteThreadMessages(thid)
        if (count > 0) {
            replicas.forEach { replica ->
                replicationScope.launch {
                    runCatching { replica.deleteThreadMessages(thid) }
                        .onFailure { logger.warn("Replica deleteThreadMessages failed for thread {}", thid, it) }
                }
            }
        }
        return count
    }

    override suspend fun countMessagesForDid(did: String): Int {
        return primary.countMessagesForDid(did)
    }

    override suspend fun search(
        filter: org.trustweave.credential.didcomm.storage.MessageFilter,
        limit: Int,
        offset: Int
    ): List<DidCommMessage> {
        return primary.search(filter, limit, offset)
    }

    override fun setEncryption(encryption: org.trustweave.credential.didcomm.storage.encryption.MessageEncryption?) {
        primary.setEncryption(encryption)
        replicas.forEach { it.setEncryption(encryption) }
    }

    override suspend fun markAsArchived(messageIds: List<String>, archiveId: String) {
        primary.markAsArchived(messageIds, archiveId)
        replicas.forEach { replica ->
            replicationScope.launch {
                runCatching { replica.markAsArchived(messageIds, archiveId) }
                    .onFailure { logger.warn("Replica markAsArchived failed for archive {}", archiveId, it) }
            }
        }
    }

    override suspend fun isArchived(messageId: String): Boolean {
        return primary.isArchived(messageId)
    }
}

