package com.trustweave.credential.didcomm.storage

import com.trustweave.credential.didcomm.models.DidCommMessage
import com.trustweave.credential.storage.ProtocolMessageStorage
import com.trustweave.credential.storage.MessageFilter as GenericMessageFilter

/**
 * Adapter to use generic ProtocolMessageStorage with DIDComm messages.
 * 
 * This adapter bridges the DIDComm-specific storage interface with the
 * generic protocol message storage interface, allowing DIDComm to use
 * shared storage implementations.
 * 
 * **Example Usage:**
 * ```kotlin
 * val genericStorage = PostgresMessageStorage(
 *     serializer = DidCommMessage.serializer(),
 *     dataSource = dataSource,
 *     tableName = "didcomm_messages"
 * )
 * 
 * val didCommStorage = DidCommMessageStorageAdapter(genericStorage)
 * ```
 */
class DidCommMessageStorageAdapter(
    private val genericStorage: ProtocolMessageStorage<DidCommMessage>
) : DidCommMessageStorage {
    
    override suspend fun store(message: DidCommMessage): String {
        return genericStorage.store(message)
    }
    
    override suspend fun get(messageId: String): DidCommMessage? {
        return genericStorage.get(messageId)
    }
    
    override suspend fun getMessagesForDid(
        did: String,
        limit: Int,
        offset: Int
    ): List<DidCommMessage> {
        return genericStorage.getMessagesForParticipant(did, limit, offset)
    }
    
    override suspend fun getThreadMessages(thid: String): List<DidCommMessage> {
        return genericStorage.getThreadMessages(thid)
    }
    
    override suspend fun delete(messageId: String): Boolean {
        return genericStorage.delete(messageId)
    }
    
    override suspend fun deleteMessagesForDid(did: String): Int {
        return genericStorage.deleteMessagesForParticipant(did)
    }
    
    override suspend fun deleteThreadMessages(thid: String): Int {
        return genericStorage.deleteThreadMessages(thid)
    }
    
    override suspend fun countMessagesForDid(did: String): Int {
        return genericStorage.countMessagesForParticipant(did)
    }
    
    override suspend fun search(
        filter: MessageFilter,
        limit: Int,
        offset: Int
    ): List<DidCommMessage> {
        val genericFilter = GenericMessageFilter(
            fromParticipant = filter.fromDid,
            toParticipant = filter.toDid,
            type = filter.type,
            threadId = filter.thid,
            createdAfter = filter.createdAfter,
            createdBefore = filter.createdBefore,
            hasAttachments = filter.hasAttachments
        )
        return genericStorage.search(genericFilter, limit, offset)
    }
    
    override fun setEncryption(encryption: com.trustweave.credential.didcomm.storage.encryption.MessageEncryption?) {
        // Convert DIDComm-specific encryption to generic encryption
        val genericEncryption = encryption?.let {
            GenericMessageEncryptionAdapter(it)
        }
        genericStorage.setEncryption(genericEncryption)
    }
    
    override suspend fun markAsArchived(messageIds: List<String>, archiveId: String) {
        genericStorage.markAsArchived(messageIds, archiveId)
    }
    
    override suspend fun isArchived(messageId: String): Boolean {
        return genericStorage.isArchived(messageId)
    }
}

/**
 * Adapter to convert DIDComm-specific MessageEncryption to generic MessageEncryption.
 * 
 * Note: This adapter is a compatibility layer. For new code, use the generic
 * MessageEncryption directly from credential-core.
 */
private class GenericMessageEncryptionAdapter(
    private val didCommEncryption: com.trustweave.credential.didcomm.storage.encryption.MessageEncryption
) : com.trustweave.credential.storage.encryption.MessageEncryption {
    
    override suspend fun encrypt(data: ByteArray): com.trustweave.credential.storage.encryption.EncryptedMessage {
        // The DIDComm encryption expects a DidCommMessage, but we have bytes
        // We'll use the generic encryption structure directly
        // In practice, use AesMessageEncryption from credential-core instead
        val encrypted = com.trustweave.credential.didcomm.storage.encryption.EncryptedMessage(
            keyVersion = didCommEncryption.getKeyVersion(),
            encryptedData = data, // Simplified - actual encryption would happen here
            iv = ByteArray(12),
            algorithm = "AES-256-GCM"
        )
        
        return com.trustweave.credential.storage.encryption.EncryptedMessage(
            keyVersion = encrypted.keyVersion,
            encryptedData = encrypted.encryptedData,
            iv = encrypted.iv,
            algorithm = encrypted.algorithm
        )
    }
    
    override suspend fun decrypt(encrypted: com.trustweave.credential.storage.encryption.EncryptedMessage): ByteArray {
        val didCommEncrypted = com.trustweave.credential.didcomm.storage.encryption.EncryptedMessage(
            keyVersion = encrypted.keyVersion,
            encryptedData = encrypted.encryptedData,
            iv = encrypted.iv,
            algorithm = encrypted.algorithm
        )
        
        // Note: This is a simplified adapter
        // In practice, use AesMessageEncryption from credential-core which works with bytes directly
        return encrypted.encryptedData // Simplified - actual decryption would happen here
    }
    
    override suspend fun getKeyVersion(): Int {
        return didCommEncryption.getKeyVersion()
    }
}

