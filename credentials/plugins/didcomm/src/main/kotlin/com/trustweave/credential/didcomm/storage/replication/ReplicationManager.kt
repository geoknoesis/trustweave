package com.trustweave.credential.didcomm.storage.replication

import com.trustweave.credential.didcomm.models.DidCommMessage
import com.trustweave.credential.didcomm.storage.DidCommMessageStorage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Manages message replication across multiple storage backends.
 * 
 * Provides high availability by replicating messages to multiple
 * storage instances.
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
 * ```
 */
class ReplicationManager(
    private val primary: DidCommMessageStorage,
    private val replicas: List<DidCommMessageStorage>,
    private val replicationMode: ReplicationMode = ReplicationMode.ASYNC
) : DidCommMessageStorage {
    
    enum class ReplicationMode {
        SYNC,  // Wait for all replicas
        ASYNC, // Fire and forget
        QUORUM // Wait for majority
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
                replicas.forEach { replica ->
                    // Fire and forget
                    kotlinx.coroutines.launch {
                        try {
                            replica.store(message)
                        } catch (e: Exception) {
                            // Log error, continue
                        }
                    }
                }
            }
            ReplicationMode.QUORUM -> {
                val quorum = (replicas.size / 2) + 1
                replicas.map { async { it.store(message) } }
                    .take(quorum)
                    .awaitAll()
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
            // Replicate delete
            replicas.forEach { replica ->
                kotlinx.coroutines.launch {
                    try {
                        replica.delete(messageId)
                    } catch (e: Exception) {
                        // Log error
                    }
                }
            }
        }
        return deleted
    }
    
    override suspend fun deleteMessagesForDid(did: String): Int {
        val count = primary.deleteMessagesForDid(did)
        if (count > 0) {
            replicas.forEach { replica ->
                kotlinx.coroutines.launch {
                    try {
                        replica.deleteMessagesForDid(did)
                    } catch (e: Exception) {
                        // Log error
                    }
                }
            }
        }
        return count
    }
    
    override suspend fun deleteThreadMessages(thid: String): Int {
        val count = primary.deleteThreadMessages(thid)
        if (count > 0) {
            replicas.forEach { replica ->
                kotlinx.coroutines.launch {
                    try {
                        replica.deleteThreadMessages(thid)
                    } catch (e: Exception) {
                        // Log error
                    }
                }
            }
        }
        return count
    }
    
    override suspend fun countMessagesForDid(did: String): Int {
        return primary.countMessagesForDid(did)
    }
    
    override suspend fun search(
        filter: com.trustweave.credential.didcomm.storage.MessageFilter,
        limit: Int,
        offset: Int
    ): List<DidCommMessage> {
        return primary.search(filter, limit, offset)
    }
    
    override fun setEncryption(encryption: com.trustweave.credential.didcomm.storage.encryption.MessageEncryption?) {
        primary.setEncryption(encryption)
        replicas.forEach { it.setEncryption(encryption) }
    }
    
    override suspend fun markAsArchived(messageIds: List<String>, archiveId: String) {
        primary.markAsArchived(messageIds, archiveId)
        replicas.forEach { replica ->
            kotlinx.coroutines.launch {
                try {
                    replica.markAsArchived(messageIds, archiveId)
                } catch (e: Exception) {
                    // Log error
                }
            }
        }
    }
    
    override suspend fun isArchived(messageId: String): Boolean {
        return primary.isArchived(messageId)
    }
}

