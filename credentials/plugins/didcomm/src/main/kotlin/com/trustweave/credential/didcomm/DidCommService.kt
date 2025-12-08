package com.trustweave.credential.didcomm

import com.trustweave.credential.didcomm.models.DidCommMessage
import com.trustweave.credential.didcomm.packing.DidCommPacker
import com.trustweave.did.model.DidDocument
import com.trustweave.core.identifiers.KeyId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Service interface for DIDComm V2 messaging.
 *
 * Provides methods for:
 * - Sending messages (packing and delivery)
 * - Receiving messages (unpacking)
 * - Message storage and retrieval
 * - Protocol message handling
 */
interface DidCommService {
    /**
     * Sends a DIDComm message.
     *
     * @param message The message to send
     * @param fromDid Sender DID
     * @param fromKeyId Sender key ID
     * @param toDid Recipient DID
     * @param toKeyId Recipient key ID (from their DID document)
     * @param encrypt Whether to encrypt the message (default: true)
     * @return Message ID
     */
    suspend fun sendMessage(
        message: DidCommMessage,
        fromDid: String,
        fromKeyId: String,
        toDid: String,
        toKeyId: String,
        encrypt: Boolean = true
    ): String

    /**
     * Receives and processes a DIDComm message.
     *
     * @param packedMessage The packed message (JSON string)
     * @param recipientDid Recipient DID
     * @param recipientKeyId Recipient key ID
     * @param senderDid Expected sender DID (optional, for verification)
     * @return Unpacked message
     */
    suspend fun receiveMessage(
        packedMessage: String,
        recipientDid: String,
        recipientKeyId: String,
        senderDid: String? = null
    ): DidCommMessage

    /**
     * Stores a message (for later retrieval).
     *
     * @param message The message to store
     * @return Message ID
     */
    suspend fun storeMessage(message: DidCommMessage): String

    /**
     * Retrieves a stored message by ID.
     *
     * @param messageId The message ID
     * @return The message, or null if not found
     */
    suspend fun getMessage(messageId: String): DidCommMessage?

    /**
     * Gets all messages for a given DID (sent or received).
     *
     * @param did The DID
     * @return List of messages
     */
    suspend fun getMessagesForDid(did: String): List<DidCommMessage>

    /**
     * Gets messages in a thread.
     *
     * @param thid Thread ID
     * @return List of messages in the thread
     */
    suspend fun getThreadMessages(thid: String): List<DidCommMessage>
}

/**
 * In-memory implementation of DidCommService.
 *
 * Suitable for testing and simple use cases.
 * For production, use a persistent storage implementation.
 */
class InMemoryDidCommService(
    private val packer: DidCommPacker,
    private val resolveDid: suspend (String) -> DidDocument?,
    private val storage: com.trustweave.credential.didcomm.storage.DidCommMessageStorage? = null
) : DidCommService {
    private val inMemoryStorage = storage
        ?: com.trustweave.credential.didcomm.storage.InMemoryDidCommMessageStorage()

    override suspend fun sendMessage(
        message: DidCommMessage,
        fromDid: String,
        fromKeyId: String,
        toDid: String,
        toKeyId: String,
        encrypt: Boolean
    ): String = withContext(Dispatchers.IO) {
        // Pack the message
        val packed = packer.pack(
            message = message,
            fromDid = fromDid,
            fromKeyId = fromKeyId,
            toDid = toDid,
            toKeyId = toKeyId,
            encrypt = encrypt
        )

        // Store the message
        storeMessage(message)

        // In a real implementation, this would deliver the message via HTTP, WebSocket, etc.
        // For now, we just store it

        message.id
    }

    override suspend fun receiveMessage(
        packedMessage: String,
        recipientDid: String,
        recipientKeyId: String,
        senderDid: String?
    ): DidCommMessage = withContext(Dispatchers.IO) {
        // Unpack the message
        val message = packer.unpack(
            packedMessage = packedMessage,
            recipientDid = recipientDid,
            recipientKeyId = recipientKeyId,
            senderDid = senderDid
        )

        // Store the received message
        storeMessage(message)

        message
    }

    override suspend fun storeMessage(message: DidCommMessage): String {
        return inMemoryStorage.store(message)
    }

    override suspend fun getMessage(messageId: String): DidCommMessage? {
        return inMemoryStorage.get(messageId)
    }

    override suspend fun getMessagesForDid(did: String): List<DidCommMessage> {
        return inMemoryStorage.getMessagesForDid(did)
    }

    override suspend fun getThreadMessages(thid: String): List<DidCommMessage> {
        return inMemoryStorage.getThreadMessages(thid)
    }
}

