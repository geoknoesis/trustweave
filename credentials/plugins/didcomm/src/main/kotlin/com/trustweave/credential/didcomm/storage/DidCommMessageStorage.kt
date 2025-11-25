package com.trustweave.credential.didcomm.storage

import com.trustweave.credential.didcomm.models.DidCommMessage

/**
 * Storage interface for DIDComm messages.
 * 
 * Provides abstraction for message persistence, allowing different
 * storage backends (database, file, cloud, etc.).
 * 
 * **Example Usage:**
 * ```kotlin
 * val storage: DidCommMessageStorage = PostgresDidCommMessageStorage(dataSource)
 * 
 * // Store message
 * val messageId = storage.store(message)
 * 
 * // Retrieve message
 * val message = storage.getMessage(messageId)
 * 
 * // Query messages
 * val messages = storage.getMessagesForDid("did:key:holder")
 * ```
 */
interface DidCommMessageStorage {
    /**
     * Stores a message.
     * 
     * @param message The message to store
     * @return Message ID
     */
    suspend fun store(message: DidCommMessage): String
    
    /**
     * Retrieves a message by ID.
     * 
     * @param messageId Message ID
     * @return Message, or null if not found
     */
    suspend fun get(messageId: String): DidCommMessage?
    
    /**
     * Gets all messages for a DID (sent or received).
     * 
     * @param did The DID
     * @param limit Maximum number of messages to return
     * @param offset Pagination offset
     * @return List of messages, ordered by creation time (newest first)
     */
    suspend fun getMessagesForDid(
        did: String,
        limit: Int = 100,
        offset: Int = 0
    ): List<DidCommMessage>
    
    /**
     * Gets messages in a thread.
     * 
     * @param thid Thread ID
     * @return List of messages in the thread, ordered by creation time
     */
    suspend fun getThreadMessages(thid: String): List<DidCommMessage>
    
    /**
     * Deletes a message.
     * 
     * @param messageId Message ID
     * @return true if deleted, false if not found
     */
    suspend fun delete(messageId: String): Boolean
    
    /**
     * Deletes all messages for a DID.
     * 
     * @param did The DID
     * @return Number of messages deleted
     */
    suspend fun deleteMessagesForDid(did: String): Int
    
    /**
     * Deletes all messages in a thread.
     * 
     * @param thid Thread ID
     * @return Number of messages deleted
     */
    suspend fun deleteThreadMessages(thid: String): Int
    
    /**
     * Counts messages for a DID.
     * 
     * @param did The DID
     * @return Number of messages
     */
    suspend fun countMessagesForDid(did: String): Int
    
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
    ): List<DidCommMessage>
    
    /**
     * Sets message encryption (optional).
     * 
     * @param encryption Message encryption service
     */
    fun setEncryption(encryption: com.trustweave.credential.didcomm.storage.encryption.MessageEncryption?)
    
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
    val fromDid: String? = null,
    val toDid: String? = null,
    val type: String? = null,
    val thid: String? = null,
    val createdAfter: String? = null, // ISO 8601 timestamp
    val createdBefore: String? = null, // ISO 8601 timestamp
    val hasAttachments: Boolean? = null
)

