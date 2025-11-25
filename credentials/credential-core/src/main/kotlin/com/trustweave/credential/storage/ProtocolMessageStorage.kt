package com.trustweave.credential.storage

import com.trustweave.credential.storage.encryption.MessageEncryption

/**
 * Generic storage interface for protocol messages.
 * 
 * Provides abstraction for message persistence across different protocols,
 * allowing protocols to share storage implementations (PostgreSQL, MongoDB, etc.).
 * 
 * **Example Usage:**
 * ```kotlin
 * // DIDComm
 * val didCommStorage: ProtocolMessageStorage<DidCommMessage> = 
 *     PostgresMessageStorage(serializer, dataSource)
 * 
 * // OIDC4VCI
 * val oidcStorage: ProtocolMessageStorage<Oidc4VciOffer> = 
 *     PostgresMessageStorage(serializer, dataSource)
 * ```
 */
interface ProtocolMessageStorage<T : ProtocolMessage> {
    /**
     * Stores a message.
     * 
     * @param message The message to store
     * @return Message ID
     */
    suspend fun store(message: T): String
    
    /**
     * Retrieves a message by ID.
     * 
     * @param messageId Message ID
     * @return Message, or null if not found
     */
    suspend fun get(messageId: String): T?
    
    /**
     * Gets all messages for a participant (sent or received).
     * 
     * @param participantId Participant identifier (DID, URL, etc.)
     * @param limit Maximum number of messages to return
     * @param offset Pagination offset
     * @return List of messages, ordered by creation time (newest first)
     */
    suspend fun getMessagesForParticipant(
        participantId: String,
        limit: Int = 100,
        offset: Int = 0
    ): List<T>
    
    /**
     * Gets messages in a thread.
     * 
     * @param threadId Thread ID
     * @return List of messages in the thread, ordered by creation time
     */
    suspend fun getThreadMessages(threadId: String): List<T>
    
    /**
     * Deletes a message.
     * 
     * @param messageId Message ID
     * @return true if deleted, false if not found
     */
    suspend fun delete(messageId: String): Boolean
    
    /**
     * Deletes all messages for a participant.
     * 
     * @param participantId Participant identifier
     * @return Number of messages deleted
     */
    suspend fun deleteMessagesForParticipant(participantId: String): Int
    
    /**
     * Deletes all messages in a thread.
     * 
     * @param threadId Thread ID
     * @return Number of messages deleted
     */
    suspend fun deleteThreadMessages(threadId: String): Int
    
    /**
     * Counts messages for a participant.
     * 
     * @param participantId Participant identifier
     * @return Number of messages
     */
    suspend fun countMessagesForParticipant(participantId: String): Int
    
    /**
     * Searches messages with filters.
     * 
     * @param filter Search filters
     * @param limit Maximum number of results
     * @param offset Pagination offset
     * @return List of matching messages
     */
    suspend fun search(
        filter: MessageFilter,
        limit: Int = 100,
        offset: Int = 0
    ): List<T>
    
    /**
     * Sets message encryption (optional).
     * 
     * @param encryption Message encryption service
     */
    fun setEncryption(encryption: MessageEncryption?)
    
    /**
     * Marks messages as archived.
     * 
     * @param messageIds List of message IDs to mark as archived
     * @param archiveId Archive ID
     */
    suspend fun markAsArchived(messageIds: List<String>, archiveId: String)
    
    /**
     * Gets archived status of a message.
     * 
     * @param messageId Message ID
     * @return true if archived, false otherwise
     */
    suspend fun isArchived(messageId: String): Boolean
}

/**
 * Message search filter.
 */
data class MessageFilter(
    val fromParticipant: String? = null,
    val toParticipant: String? = null,
    val type: String? = null,
    val threadId: String? = null,
    val createdAfter: String? = null, // ISO 8601 timestamp
    val createdBefore: String? = null, // ISO 8601 timestamp
    val hasAttachments: Boolean? = null
)

