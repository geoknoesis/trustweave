package org.trustweave.credential.didcomm.storage

import org.trustweave.credential.didcomm.models.DidCommMessage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory implementation of DidCommMessageStorage.
 *
 * Suitable for testing and simple use cases.
 * Messages are stored in memory and will be lost on restart.
 */
class InMemoryDidCommMessageStorage : DidCommMessageStorage {
    private val messages = ConcurrentHashMap<String, DidCommMessage>()
    private val messagesByDid = ConcurrentHashMap<String, CopyOnWriteArrayList<String>>()
    private val messagesByThread = ConcurrentHashMap<String, CopyOnWriteArrayList<String>>()

    /** Distinct DIDs a message is indexed under (a self-addressed message is indexed once). */
    private fun participants(message: DidCommMessage): List<String> = (listOfNotNull(message.from) + message.to).distinct()

    private fun index(message: DidCommMessage) {
        participants(message).forEach { did ->
            messagesByDid.getOrPut(did) { CopyOnWriteArrayList() }.add(message.id)
        }
        message.thid?.let { thid ->
            messagesByThread.getOrPut(thid) { CopyOnWriteArrayList() }.add(message.id)
        }
    }

    private fun unindex(message: DidCommMessage) {
        participants(message).forEach { messagesByDid[it]?.remove(message.id) }
        message.thid?.let { messagesByThread[it]?.remove(message.id) }
    }

    override suspend fun store(message: DidCommMessage): String {
        // Storing an id again replaces the message; its old index entries must go, or counts and
        // listings would report it twice (or under a DID/thread it no longer belongs to).
        messages.put(message.id, message)?.let { unindex(it) }
        index(message)
        return message.id
    }

    override suspend fun get(messageId: String): DidCommMessage? = messages[messageId]

    override suspend fun getMessagesForDid(
        did: String,
        limit: Int,
        offset: Int,
    ): List<DidCommMessage> {
        val messageIds =
            messagesByDid[did]?.takeLast(limit + offset)?.takeLast(limit)
                ?: return emptyList()
        return messageIds.mapNotNull { messages[it] }
    }

    override suspend fun getThreadMessages(thid: String): List<DidCommMessage> {
        val messageIds = messagesByThread[thid] ?: return emptyList()
        return messageIds.mapNotNull { messages[it] }
    }

    override suspend fun delete(messageId: String): Boolean {
        val message = messages.remove(messageId) ?: return false
        unindex(message)
        return true
    }

    override suspend fun deleteMessagesForDid(did: String): Int {
        // Through delete(), so the message also leaves the other participants' and the thread's indexes.
        val messageIds = messagesByDid[did]?.toList() ?: return 0
        return messageIds.count { delete(it) }
    }

    override suspend fun deleteThreadMessages(thid: String): Int {
        val messageIds = messagesByThread[thid]?.toList() ?: return 0
        return messageIds.count { delete(it) }
    }

    override suspend fun countMessagesForDid(did: String): Int = messagesByDid[did]?.size ?: 0

    override suspend fun search(
        filter: MessageFilter,
        limit: Int,
        offset: Int,
    ): List<DidCommMessage> =
        messages.values
            .filter { message ->
                (filter.fromDid == null || filter.fromDid == message.from) &&
                    filter.toDid?.let { message.to.contains(it) } ?: true &&
                    (filter.type == null || filter.type == message.type) &&
                    (filter.thid == null || filter.thid == message.thid) &&
                    filter.createdAfter?.let {
                        (message.created ?: "") >= it
                    } ?: true &&
                    filter.createdBefore?.let {
                        (message.created ?: "") <= it
                    } ?: true &&
                    filter.hasAttachments?.let {
                        if (it) {
                            message.attachments.isNotEmpty()
                        } else {
                            message.attachments.isEmpty()
                        }
                    } ?: true
            }.sortedByDescending { it.created }
            .drop(offset)
            .take(limit)

    override fun setEncryption(encryption: org.trustweave.credential.didcomm.storage.encryption.MessageEncryption?) {
        // Fail closed: silently ignoring the request would keep messages in plaintext while the
        // caller believes they are encrypted at rest.
        if (encryption != null) {
            throw UnsupportedOperationException(
                "Message encryption at rest is not supported by InMemoryDidCommMessageStorage; " +
                    "use a database-backed storage with encryption support.",
            )
        }
    }

    private val archivedMessages: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val archiveIds = ConcurrentHashMap<String, String>() // messageId -> archiveId

    override suspend fun markAsArchived(
        messageIds: List<String>,
        archiveId: String,
    ) {
        messageIds.forEach { id ->
            archivedMessages.add(id)
            archiveIds[id] = archiveId
        }
    }

    override suspend fun isArchived(messageId: String): Boolean = archivedMessages.contains(messageId)
}
