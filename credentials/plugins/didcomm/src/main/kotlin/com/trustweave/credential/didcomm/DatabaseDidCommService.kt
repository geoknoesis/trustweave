package com.trustweave.credential.didcomm

import com.trustweave.credential.didcomm.models.DidCommMessage
import com.trustweave.credential.didcomm.packing.DidCommPacker
import com.trustweave.credential.didcomm.storage.DidCommMessageStorage
import com.trustweave.did.model.DidDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Database-backed DIDComm service implementation.
 *
 * Uses persistent storage for message persistence across restarts.
 * Suitable for production deployments.
 *
 * **Example Usage:**
 * ```kotlin
 * val dataSource = // Your DataSource
 * val storage = PostgresDidCommMessageStorage(dataSource)
 * val service = DatabaseDidCommService(packer, resolveDid, storage)
 * ```
 */
class DatabaseDidCommService(
    private val packer: DidCommPacker,
    private val resolveDid: suspend (String) -> DidDocument?,
    private val storage: DidCommMessageStorage
) : DidCommService {

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
        storage.store(message)

        // In a real implementation, deliver via HTTP, WebSocket, etc.
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
        storage.store(message)

        message
    }

    override suspend fun storeMessage(message: DidCommMessage): String {
        return storage.store(message)
    }

    override suspend fun getMessage(messageId: String): DidCommMessage? {
        return storage.get(messageId)
    }

    override suspend fun getMessagesForDid(did: String): List<DidCommMessage> {
        return storage.getMessagesForDid(did)
    }

    override suspend fun getThreadMessages(thid: String): List<DidCommMessage> {
        return storage.getThreadMessages(thid)
    }
}

