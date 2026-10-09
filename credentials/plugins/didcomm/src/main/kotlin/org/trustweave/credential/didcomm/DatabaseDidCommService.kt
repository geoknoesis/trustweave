package org.trustweave.credential.didcomm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.trustweave.credential.didcomm.models.DidCommMessage
import org.trustweave.credential.didcomm.packing.DidCommPacker
import org.trustweave.credential.didcomm.storage.DidCommMessageStorage
import org.trustweave.did.model.DidDocument

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
class DatabaseDidCommService
    @JvmOverloads
    constructor(
        private val packer: DidCommPacker,
        private val resolveDid: suspend (String) -> DidDocument?,
        private val storage: DidCommMessageStorage,
        /**
         * Where accepted message ids are remembered for replay protection. When omitted, the
         * storage's own durable store is used ([DidCommMessageStorage.replayStore], e.g. a table in
         * the same PostgreSQL database), which survives restarts and is shared by all replicas. Only
         * a storage that offers none (e.g. MongoDB) falls back to a per-process
         * [InMemoryDidCommReplayStore], with a warning: that guarantee does not hold across restarts
         * or replicas, so supply a shared [DidCommReplayStore] there.
         */
        replayStore: DidCommReplayStore? = null,
    ) : DidCommService {
        private val receiveGuards = DidCommReceiveGuards(replayStore ?: defaultReplayStore(storage))

        override suspend fun sendMessage(
            message: DidCommMessage,
            fromDid: String,
            fromKeyId: String,
            toDid: String,
            toKeyId: String,
            encrypt: Boolean,
        ): String =
            withContext(Dispatchers.IO) {
                // Pack the message
                val packed =
                    packer.pack(
                        message = message,
                        fromDid = fromDid,
                        fromKeyId = fromKeyId,
                        toDid = toDid,
                        toKeyId = toKeyId,
                        encrypt = encrypt,
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
            senderDid: String?,
            requireSigned: Boolean,
        ): DidCommMessage =
            withContext(Dispatchers.IO) {
                // Unpack the message
                val unpacked =
                    packer.unpackToResult(
                        packedMessage = packedMessage,
                        recipientDid = recipientDid,
                        recipientKeyId = recipientKeyId,
                        senderDid = senderDid,
                        requireSigned = requireSigned,
                    )

                val message = unpacked.message
                receiveGuards.check(message, unpacked.authenticatedSenderDid ?: unpacked.verifiedSignerDid)

                // Store the received message
                storage.store(message)

                message
            }

        override suspend fun storeMessage(message: DidCommMessage): String = storage.store(message)

        override suspend fun getMessage(messageId: String): DidCommMessage? = storage.get(messageId)

        override suspend fun getMessagesForDid(did: String): List<DidCommMessage> = storage.getMessagesForDid(did)

        override suspend fun getThreadMessages(thid: String): List<DidCommMessage> = storage.getThreadMessages(thid)

        private companion object {
            private val logger = LoggerFactory.getLogger(DatabaseDidCommService::class.java)

            fun defaultReplayStore(storage: DidCommMessageStorage): DidCommReplayStore =
                storage.replayStore()
                    ?: InMemoryDidCommReplayStore().also {
                        logger.warn(
                            "{} offers no durable replay store; DIDComm replay protection is per-process and is lost on " +
                                "restart / not shared between replicas. Pass a DatabaseDidCommReplayStore explicitly.",
                            storage::class.java.simpleName,
                        )
                    }
        }
    }
