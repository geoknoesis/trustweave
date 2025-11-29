package com.trustweave.credential.didcomm.storage

import com.trustweave.credential.didcomm.models.DidCommMessage
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory implementation of DidCommMessageStorage.
 *
 * Suitable for testing and simple use cases.
 * Messages are stored in memory and will be lost on restart.
 */
class InMemoryDidCommMessageStorage : DidCommMessageStorage {
    private val messages = ConcurrentHashMap<String, DidCommMessage>()
    private val messagesByDid = ConcurrentHashMap<String, MutableList<String>>()
    private val messagesByThread = ConcurrentHashMap<String, MutableList<String>>()

    override suspend fun store(message: DidCommMessage): String {
        messages[message.id] = message

        // Index by DID
        message.from?.let { from ->
            messagesByDid.getOrPut(from) { mutableListOf() }.add(message.id)
        }
        message.to.forEach { to ->
            messagesByDid.getOrPut(to) { mutableListOf() }.add(message.id)
        }

        // Index by thread
        message.thid?.let { thid ->
            messagesByThread.getOrPut(thid) { mutableListOf() }.add(message.id)
        }

        return message.id
    }

    override suspend fun get(messageId: String): DidCommMessage? {
        return messages[messageId]
    }

    override suspend fun getMessagesForDid(
        did: String,
        limit: Int,
        offset: Int
    ): List<DidCommMessage> {
        val messageIds = messagesByDid[did]?.takeLast(limit + offset)?.takeLast(limit)
            ?: return emptyList()
        return messageIds.mapNotNull { messages[it] }
    }

    override suspend fun getThreadMessages(thid: String): List<DidCommMessage> {
        val messageIds = messagesByThread[thid] ?: return emptyList()
        return messageIds.mapNotNull { messages[it] }
    }

    override suspend fun delete(messageId: String): Boolean {
        val message = messages.remove(messageId) ?: return false

        // Remove from indexes
        message.from?.let { messagesByDid[it]?.remove(messageId) }
        message.to.forEach { messagesByDid[it]?.remove(messageId) }
        message.thid?.let { messagesByThread[it]?.remove(messageId) }

        return true
    }

    override suspend fun deleteMessagesForDid(did: String): Int {
        val messageIds = messagesByDid.remove(did) ?: return 0
        messageIds.forEach { messages.remove(it) }
        return messageIds.size
    }

    override suspend fun deleteThreadMessages(thid: String): Int {
        val messageIds = messagesByThread.remove(thid) ?: return 0
        messageIds.forEach { messages.remove(it) }
        return messageIds.size
    }

    override suspend fun countMessagesForDid(did: String): Int {
        return messagesByDid[did]?.size ?: 0
    }

    override suspend fun search(
        filter: MessageFilter,
        limit: Int,
        offset: Int
    ): List<DidCommMessage> {
        return messages.values
            .filter { message ->
                filter.fromDid?.let { it == message.from } ?: true &&
                filter.toDid?.let { message.to.contains(it) } ?: true &&
                filter.type?.let { it == message.type } ?: true &&
                filter.thid?.let { it == message.thid } ?: true &&
                filter.createdAfter?.let {
                    (message.created ?: "") >= it
                } ?: true &&
                filter.createdBefore?.let {
                    (message.created ?: "") <= it
                } ?: true &&
                filter.hasAttachments?.let {
                    if (it) message.attachments.isNotEmpty()
                    else message.attachments.isEmpty()
                } ?: true
            }
            .sortedByDescending { it.created }
            .drop(offset)
            .take(limit)
    }

    override fun setEncryption(encryption: com.trustweave.credential.didcomm.storage.encryption.MessageEncryption?) {
        // In-memory storage doesn't support encryption
        // Encryption should be handled at database level
    }

    private val archivedMessages = mutableSetOf<String>()
    private val archiveIds = mutableMapOf<String, String>() // messageId -> archiveId

    override suspend fun markAsArchived(messageIds: List<String>, archiveId: String) {
        messageIds.forEach { id ->
            archivedMessages.add(id)
            archiveIds[id] = archiveId
        }
    }

    override suspend fun isArchived(messageId: String): Boolean {
        return archivedMessages.contains(messageId)
    }
}

