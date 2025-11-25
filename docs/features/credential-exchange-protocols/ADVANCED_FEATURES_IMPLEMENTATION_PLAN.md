# Advanced Features Implementation Plan

## Overview

This document outlines the implementation plan for advanced features to enhance the DIDComm message storage and SecretResolver system for production use.

## Implementation Phases

### Phase 1: Core Enhancements (Priority: High)
1. EncryptedFileLocalKeyStore implementation
2. Message encryption at rest
3. MongoDB storage implementation

### Phase 2: Scalability & Reliability (Priority: High)
4. Message archiving to cold storage
5. Message replication for high availability

### Phase 3: Advanced Features (Priority: Medium)
6. Advanced search capabilities
7. Message analytics and reporting
8. Key rotation automation

---

## 1. EncryptedFileLocalKeyStore Implementation

### Overview
Implement encrypted file-based storage for DIDComm keys, providing secure local key storage for production use.

### Architecture

```
EncryptedFileLocalKeyStore
    │
    ├── Key Encryption (AES-256-GCM)
    │   └── Master Key Derivation (PBKDF2/Argon2)
    │
    ├── File Format
    │   ├── Header (metadata, version, salt)
    │   └── Encrypted Key Blocks (one per key)
    │
    └── Key Management
        ├── Key Rotation
        ├── Backup/Restore
        └── Access Control
```

### Implementation Steps

#### Step 1: Create Encryption Utilities
**File**: `credentials/plugins/didcomm/src/main/kotlin/com/trustweave/credential/didcomm/crypto/secret/encryption/KeyEncryption.kt`

```kotlin
package com.trustweave.credential.didcomm.crypto.secret.encryption

import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.util.Base64

/**
 * Encrypts/decrypts keys using AES-256-GCM.
 */
class KeyEncryption(
    private val masterKey: ByteArray // Derived from user password/master key
) {
    private val algorithm = "AES/GCM/NoPadding"
    private val keyLength = 256
    private val ivLength = 12 // 96 bits for GCM
    private val tagLength = 128 // 16 bytes
    
    fun encrypt(plaintext: ByteArray): EncryptedData {
        val iv = ByteArray(ivLength).apply {
            SecureRandom().nextBytes(this)
        }
        
        val secretKey = SecretKeySpec(masterKey, "AES")
        val parameterSpec = GCMParameterSpec(tagLength, iv)
        
        val cipher = Cipher.getInstance(algorithm)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
        
        val ciphertext = cipher.doFinal(plaintext)
        
        return EncryptedData(
            iv = iv,
            ciphertext = ciphertext,
            algorithm = algorithm
        )
    }
    
    fun decrypt(encrypted: EncryptedData): ByteArray {
        val secretKey = SecretKeySpec(masterKey, "AES")
        val parameterSpec = GCMParameterSpec(tagLength, encrypted.iv)
        
        val cipher = Cipher.getInstance(algorithm)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)
        
        return cipher.doFinal(encrypted.ciphertext)
    }
}

data class EncryptedData(
    val iv: ByteArray,
    val ciphertext: ByteArray,
    val algorithm: String
)

/**
 * Derives master key from password using PBKDF2.
 */
object MasterKeyDerivation {
    fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        iterations: Int = 100000
    ): ByteArray {
        val keySpec: KeySpec = PBEKeySpec(
            password,
            salt,
            iterations,
            256 // Key length in bits
        )
        val keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val secretKey = keyFactory.generateSecret(keySpec)
        return secretKey.encoded
    }
}
```

#### Step 2: Implement File Format
**File**: `credentials/plugins/didcomm/src/main/kotlin/com/trustweave/credential/didcomm/crypto/secret/EncryptedFileLocalKeyStore.kt`

```kotlin
package com.trustweave.credential.didcomm.crypto.secret

import com.trustweave.credential.didcomm.crypto.secret.encryption.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.didcommx.didcomm.secret.Secret
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Encrypted file-based local key store for production use.
 * 
 * Stores keys in an encrypted file with the following format:
 * 
 * File Structure:
 * - Header (JSON): version, salt, algorithm, metadata
 * - Key Blocks (encrypted): Each key stored as encrypted JSON
 * 
 * Security:
 * - Keys encrypted with AES-256-GCM
 * - Master key derived from password using PBKDF2
 * - File permissions restricted (600 on Unix)
 * - Atomic writes for consistency
 */
class EncryptedFileLocalKeyStore(
    private val keyFile: File,
    private val masterKey: ByteArray, // Should be derived from password
    private val keyEncryption: KeyEncryption = KeyEncryption(masterKey)
) : LocalKeyStore {
    
    private val lock = ReentrantReadWriteLock()
    private val json = Json { prettyPrint = false; encodeDefaults = false }
    
    init {
        // Ensure file exists and has correct permissions
        if (!keyFile.exists()) {
            keyFile.createNewFile()
            setSecurePermissions(keyFile)
        } else {
            setSecurePermissions(keyFile)
        }
    }
    
    override suspend fun get(keyId: String): Secret? = withContext(Dispatchers.IO) {
        lock.read {
            val keys = loadKeys()
            keys[keyId]
        }
    }
    
    override suspend fun store(keyId: String, secret: Secret) = withContext(Dispatchers.IO) {
        lock.write {
            val keys = loadKeys().toMutableMap()
            keys[keyId] = secret
            saveKeys(keys)
        }
    }
    
    override suspend fun delete(keyId: String): Boolean = withContext(Dispatchers.IO) {
        lock.write {
            val keys = loadKeys().toMutableMap()
            val removed = keys.remove(keyId) != null
            if (removed) {
                saveKeys(keys)
            }
            removed
        }
    }
    
    override suspend fun list(): List<String> = withContext(Dispatchers.IO) {
        lock.read {
            val keys = loadKeys()
            keys.keys.toList()
        }
    }
    
    private fun loadKeys(): Map<String, Secret> {
        if (!keyFile.exists() || keyFile.length() == 0L) {
            return emptyMap()
        }
        
        try {
            val fileContent = keyFile.readBytes()
            val encryptedData = parseEncryptedFile(fileContent)
            val decryptedContent = keyEncryption.decrypt(encryptedData)
            val jsonString = String(decryptedContent, Charsets.UTF_8)
            val keysJson = json.parseToJsonElement(jsonString).jsonObject
            
            return keysJson.entries.associate { (keyId, secretJson) ->
                keyId to json.decodeFromJsonElement(Secret.serializer(), secretJson)
            }
        } catch (e: Exception) {
            throw IllegalStateException("Failed to load keys from encrypted file", e)
        }
    }
    
    private fun saveKeys(keys: Map<String, Secret>) {
        try {
            val keysJson = buildJsonObject {
                keys.forEach { (keyId, secret) ->
                    put(keyId, json.encodeToJsonElement(Secret.serializer(), secret))
                }
            }
            val jsonString = json.encodeToString(JsonObject.serializer(), keysJson)
            val plaintext = jsonString.toByteArray(Charsets.UTF_8)
            val encryptedData = keyEncryption.encrypt(plaintext)
            
            val fileContent = serializeEncryptedFile(encryptedData)
            
            // Atomic write: write to temp file, then rename
            val tempFile = File(keyFile.parent, "${keyFile.name}.tmp")
            tempFile.writeBytes(fileContent)
            setSecurePermissions(tempFile)
            
            // Atomic rename
            if (keyFile.exists()) {
                keyFile.delete()
            }
            tempFile.renameTo(keyFile)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to save keys to encrypted file", e)
        }
    }
    
    private fun parseEncryptedFile(content: ByteArray): EncryptedData {
        // Parse file format:
        // [4 bytes: version][4 bytes: iv length][iv][ciphertext]
        var offset = 0
        
        val version = content.sliceArray(offset until offset + 4)
        offset += 4
        
        val ivLength = content.sliceArray(offset until offset + 4)
            .fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xFF) }
        offset += 4
        
        val iv = content.sliceArray(offset until offset + ivLength)
        offset += ivLength
        
        val ciphertext = content.sliceArray(offset until content.size)
        
        return EncryptedData(
            iv = iv,
            ciphertext = ciphertext,
            algorithm = "AES/GCM/NoPadding"
        )
    }
    
    private fun serializeEncryptedFile(encrypted: EncryptedData): ByteArray {
        val version = byteArrayOf(0x01, 0x00, 0x00, 0x00) // Version 1
        val ivLength = byteArrayOf(
            ((encrypted.iv.size shr 24) and 0xFF).toByte(),
            ((encrypted.iv.size shr 16) and 0xFF).toByte(),
            ((encrypted.iv.size shr 8) and 0xFF).toByte(),
            (encrypted.iv.size and 0xFF).toByte()
        )
        
        return version + ivLength + encrypted.iv + encrypted.ciphertext
    }
    
    private fun setSecurePermissions(file: File) {
        try {
            // Set permissions to 600 (owner read/write only)
            if (System.getProperty("os.name").lowercase().contains("win")) {
                // Windows: Use Java NIO
                file.setReadable(false, false)
                file.setWritable(false, false)
                file.setReadable(true, true)
                file.setWritable(true, true)
            } else {
                // Unix: Use POSIX permissions
                val perms = setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE
                )
                Files.setPosixFilePermissions(file.toPath(), perms)
            }
        } catch (e: Exception) {
            // Ignore if permissions can't be set
        }
    }
}

/**
 * Factory for creating EncryptedFileLocalKeyStore with password.
 */
object EncryptedFileLocalKeyStoreFactory {
    /**
     * Creates an encrypted file key store from a password.
     * 
     * @param keyFile File to store keys
     * @param password Password for encryption
     * @param salt Salt for key derivation (optional, will be generated)
     * @return EncryptedFileLocalKeyStore instance
     */
    fun create(
        keyFile: File,
        password: CharArray,
        salt: ByteArray? = null
    ): EncryptedFileLocalKeyStore {
        val actualSalt = salt ?: ByteArray(16).apply {
            java.security.SecureRandom().nextBytes(this)
        }
        
        val masterKey = MasterKeyDerivation.deriveKey(
            password = password,
            salt = actualSalt,
            iterations = 100000
        )
        
        return EncryptedFileLocalKeyStore(keyFile, masterKey)
    }
}
```

### Testing Strategy
- Unit tests for encryption/decryption
- Integration tests with file system
- Security tests for key access
- Performance tests for large key sets

### Dependencies
- BouncyCastle (already included)
- Kotlinx Serialization (already included)

---

## 2. Message Encryption at Rest

### Overview
Encrypt message JSON in the database to protect sensitive data.

### Architecture

```
PostgresDidCommMessageStorage
    │
    ├── Encryption Layer
    │   ├── Field-level encryption (selective fields)
    │   └── Full message encryption (all messages)
    │
    └── Key Management
        ├── Encryption Key Rotation
        └── Key Versioning
```

### Implementation Steps

#### Step 1: Create Message Encryption Service
**File**: `credentials/plugins/didcomm/src/main/kotlin/com/trustweave/credential/didcomm/storage/encryption/MessageEncryption.kt`

```kotlin
package com.trustweave.credential.didcomm.storage.encryption

import com.trustweave.credential.didcomm.models.DidCommMessage
import kotlinx.serialization.json.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import java.util.Base64

/**
 * Encrypts/decrypts DIDComm messages at rest.
 * 
 * Supports:
 * - Full message encryption
 * - Field-level encryption (selective fields)
 * - Key rotation
 */
interface MessageEncryption {
    /**
     * Encrypts a message for storage.
     */
    suspend fun encrypt(message: DidCommMessage): EncryptedMessage
    
    /**
     * Decrypts a message from storage.
     */
    suspend fun decrypt(encrypted: EncryptedMessage): DidCommMessage
    
    /**
     * Gets the current encryption key version.
     */
    suspend fun getKeyVersion(): Int
}

data class EncryptedMessage(
    val keyVersion: Int,
    val encryptedData: ByteArray,
    val iv: ByteArray,
    val algorithm: String = "AES-256-GCM"
)

/**
 * AES-256-GCM implementation of message encryption.
 */
class AesMessageEncryption(
    private val encryptionKey: ByteArray,
    private val keyVersion: Int = 1
) : MessageEncryption {
    
    private val algorithm = "AES/GCM/NoPadding"
    private val ivLength = 12
    private val tagLength = 128
    
    override suspend fun encrypt(message: DidCommMessage): EncryptedMessage {
        val json = Json { prettyPrint = false; encodeDefaults = false }
        val messageJson = json.encodeToString(
            DidCommMessage.serializer(),
            message
        )
        val plaintext = messageJson.toByteArray(Charsets.UTF_8)
        
        val iv = ByteArray(ivLength).apply {
            SecureRandom().nextBytes(this)
        }
        
        val secretKey = SecretKeySpec(encryptionKey, "AES")
        val parameterSpec = GCMParameterSpec(tagLength, iv)
        
        val cipher = Cipher.getInstance(algorithm)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
        
        val ciphertext = cipher.doFinal(plaintext)
        
        return EncryptedMessage(
            keyVersion = keyVersion,
            encryptedData = ciphertext,
            iv = iv,
            algorithm = algorithm
        )
    }
    
    override suspend fun decrypt(encrypted: EncryptedMessage): DidCommMessage {
        val secretKey = SecretKeySpec(encryptionKey, "AES")
        val parameterSpec = GCMParameterSpec(tagLength, encrypted.iv)
        
        val cipher = Cipher.getInstance(algorithm)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)
        
        val plaintext = cipher.doFinal(encrypted.encryptedData)
        val jsonString = String(plaintext, Charsets.UTF_8)
        
        val json = Json { ignoreUnknownKeys = true }
        return json.decodeFromString(DidCommMessage.serializer(), jsonString)
    }
    
    override suspend fun getKeyVersion(): Int = keyVersion
}
```

#### Step 2: Update Storage Interface
**File**: `credentials/plugins/didcomm/src/main/kotlin/com/trustweave/credential/didcomm/storage/DidCommMessageStorage.kt`

Add optional encryption parameter:

```kotlin
interface DidCommMessageStorage {
    // ... existing methods ...
    
    /**
     * Sets message encryption (optional).
     * 
     * @param encryption Message encryption service
     */
    fun setEncryption(encryption: MessageEncryption?)
}
```

#### Step 3: Update PostgreSQL Storage
**File**: `credentials/plugins/didcomm/src/main/kotlin/com/trustweave/credential/didcomm/storage/database/PostgresDidCommMessageStorage.kt`

Add encryption support:

```kotlin
class PostgresDidCommMessageStorage(
    private val dataSource: DataSource,
    private val encryption: MessageEncryption? = null
) : DidCommMessageStorage {
    
    override fun setEncryption(encryption: MessageEncryption?) {
        // Update encryption instance
    }
    
    override suspend fun store(message: DidCommMessage): String = withContext(Dispatchers.IO) {
        val messageToStore = if (encryption != null) {
            // Encrypt message
            val encrypted = encryption.encrypt(message)
            // Store encrypted data
            storeEncrypted(encrypted, message.id)
            return message.id
        } else {
            // Store unencrypted (existing logic)
            storeUnencrypted(message)
            return message.id
        }
    }
    
    private suspend fun storeEncrypted(
        encrypted: EncryptedMessage,
        messageId: String
    ) {
        // Store encrypted data in database
        // Add columns: encrypted_data BYTEA, key_version INT, iv BYTEA
    }
}
```

### Database Schema Updates

```sql
-- Add encryption columns to messages table
ALTER TABLE didcomm_messages ADD COLUMN IF NOT EXISTS encrypted_data BYTEA;
ALTER TABLE didcomm_messages ADD COLUMN IF NOT EXISTS key_version INT;
ALTER TABLE didcomm_messages ADD COLUMN IF NOT EXISTS iv BYTEA;
ALTER TABLE didcomm_messages ADD COLUMN IF NOT EXISTS is_encrypted BOOLEAN DEFAULT FALSE;

-- Create index for key version (for key rotation queries)
CREATE INDEX IF NOT EXISTS idx_messages_key_version ON didcomm_messages(key_version);
```

### Key Management

**File**: `credentials/plugins/didcomm/src/main/kotlin/com/trustweave/credential/didcomm/storage/encryption/EncryptionKeyManager.kt`

```kotlin
/**
 * Manages encryption keys with rotation support.
 */
interface EncryptionKeyManager {
    suspend fun getCurrentKey(): ByteArray
    suspend fun getKey(version: Int): ByteArray?
    suspend fun rotateKey(): Int // Returns new key version
    suspend fun getKeyVersions(): List<Int>
}
```

---

## 3. MongoDB Storage Implementation

### Overview
Implement MongoDB backend for message storage, providing NoSQL alternative to PostgreSQL.

### Architecture

```
MongoDidCommMessageStorage
    │
    ├── MongoDB Connection
    │   └── Connection Pooling
    │
    ├── Document Structure
    │   └── Optimized for MongoDB queries
    │
    └── Indexes
        ├── DID indexes
        ├── Thread indexes
        └── Time-based indexes
```

### Implementation Steps

#### Step 1: Add MongoDB Dependencies
**File**: `credentials/plugins/didcomm/build.gradle.kts`

```kotlin
// MongoDB driver
implementation("org.mongodb:mongodb-driver-kotlin-coroutine:4.11.0")
```

#### Step 2: Create MongoDB Storage
**File**: `credentials/plugins/didcomm/src/main/kotlin/com/trustweave/credential/didcomm/storage/database/MongoDidCommMessageStorage.kt`

```kotlin
package com.trustweave.credential.didcomm.storage.database

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Sorts
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import com.trustweave.credential.didcomm.models.DidCommMessage
import com.trustweave.credential.didcomm.storage.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.bson.Document

/**
 * MongoDB-backed message storage.
 * 
 * Uses MongoDB for flexible document storage with efficient queries.
 */
class MongoDidCommMessageStorage(
    private val mongoClient: MongoClient,
    private val databaseName: String = "trustweave",
    private val collectionName: String = "didcomm_messages"
) : DidCommMessageStorage {
    
    private val database: MongoDatabase = mongoClient.getDatabase(databaseName)
    private val collection: MongoCollection<Document> = database.getCollection(collectionName)
    
    init {
        createIndexes()
    }
    
    override suspend fun store(message: DidCommMessage): String = withContext(Dispatchers.IO) {
        val json = Json { prettyPrint = false; encodeDefaults = false }
        val messageJson = json.encodeToString(
            DidCommMessage.serializer(),
            message
        )
        
        val document = Document.parse(messageJson).apply {
            put("_id", message.id)
            put("from_did", message.from)
            put("to_dids", message.to)
            put("type", message.type)
            put("thid", message.thid)
            put("created_time", message.created)
            put("expires_time", message.expiresTime)
        }
        
        collection.insertOne(document)
        message.id
    }
    
    override suspend fun get(messageId: String): DidCommMessage? = withContext(Dispatchers.IO) {
        val document = collection.find(Filters.eq("_id", messageId)).first()
            ?: return@withContext null
        
        val json = Json { ignoreUnknownKeys = true }
        json.decodeFromString(
            DidCommMessage.serializer(),
            document.toJson()
        )
    }
    
    override suspend fun getMessagesForDid(
        did: String,
        limit: Int,
        offset: Int
    ): List<DidCommMessage> = withContext(Dispatchers.IO) {
        val filter = Filters.or(
            Filters.eq("from_did", did),
            Filters.in("to_dids", did)
        )
        
        collection.find(filter)
            .sort(Sorts.descending("created_time"))
            .skip(offset)
            .limit(limit)
            .toList()
            .map { document ->
                val json = Json { ignoreUnknownKeys = true }
                json.decodeFromString(
                    DidCommMessage.serializer(),
                    document.toJson()
                )
            }
    }
    
    override suspend fun getThreadMessages(thid: String): List<DidCommMessage> = withContext(Dispatchers.IO) {
        collection.find(Filters.eq("thid", thid))
            .sort(Sorts.ascending("created_time"))
            .toList()
            .map { document ->
                val json = Json { ignoreUnknownKeys = true }
                json.decodeFromString(
                    DidCommMessage.serializer(),
                    document.toJson()
                )
            }
    }
    
    override suspend fun delete(messageId: String): Boolean = withContext(Dispatchers.IO) {
        val result = collection.deleteOne(Filters.eq("_id", messageId))
        result.deletedCount > 0
    }
    
    override suspend fun deleteMessagesForDid(did: String): Int = withContext(Dispatchers.IO) {
        val filter = Filters.or(
            Filters.eq("from_did", did),
            Filters.in("to_dids", did)
        )
        val result = collection.deleteMany(filter)
        result.deletedCount.toInt()
    }
    
    override suspend fun deleteThreadMessages(thid: String): Int = withContext(Dispatchers.IO) {
        val result = collection.deleteMany(Filters.eq("thid", thid))
        result.deletedCount.toInt()
    }
    
    override suspend fun countMessagesForDid(did: String): Int = withContext(Dispatchers.IO) {
        val filter = Filters.or(
            Filters.eq("from_did", did),
            Filters.in("to_dids", did)
        )
        collection.countDocuments(filter).toInt()
    }
    
    override suspend fun search(
        filter: MessageFilter,
        limit: Int,
        offset: Int
    ): List<DidCommMessage> = withContext(Dispatchers.IO) {
        val mongoFilter = buildMongoFilter(filter)
        
        collection.find(mongoFilter)
            .sort(Sorts.descending("created_time"))
            .skip(offset)
            .limit(limit)
            .toList()
            .map { document ->
                val json = Json { ignoreUnknownKeys = true }
                json.decodeFromString(
                    DidCommMessage.serializer(),
                    document.toJson()
                )
            }
    }
    
    private fun buildMongoFilter(filter: MessageFilter): Document {
        val conditions = mutableListOf<Document>()
        
        filter.fromDid?.let {
            conditions.add(Document("from_did", it))
        }
        filter.toDid?.let {
            conditions.add(Document("to_dids", it))
        }
        filter.type?.let {
            conditions.add(Document("type", it))
        }
        filter.thid?.let {
            conditions.add(Document("thid", it))
        }
        filter.createdAfter?.let {
            conditions.add(Document("created_time", Document("\$gte", it)))
        }
        filter.createdBefore?.let {
            conditions.add(Document("created_time", Document("\$lte", it)))
        }
        
        return if (conditions.isEmpty()) {
            Document()
        } else {
            Document("\$and", conditions)
        }
    }
    
    private fun createIndexes() {
        // Create indexes for performance
        collection.createIndex(Indexes.ascending("from_did"))
        collection.createIndex(Indexes.ascending("to_dids"))
        collection.createIndex(Indexes.ascending("thid"))
        collection.createIndex(Indexes.ascending("created_time"))
        collection.createIndex(Indexes.ascending("type"))
        
        // Compound indexes
        collection.createIndex(Indexes.compoundIndex(
            Indexes.ascending("from_did"),
            Indexes.descending("created_time")
        ))
    }
}
```

---

## 4. Message Archiving to Cold Storage

### Overview
Archive old messages to cold storage (S3, Azure Blob, etc.) to reduce database size and costs.

### Architecture

```
Message Archiver
    │
    ├── Archive Policy
    │   ├── Age-based (e.g., >90 days)
    │   ├── Size-based (e.g., >1GB)
    │   └── Custom rules
    │
    ├── Archive Format
    │   ├── Compressed (gzip)
    │   └── Batch files (JSONL)
    │
    └── Storage Backends
        ├── AWS S3
        ├── Azure Blob
        └── Google Cloud Storage
```

### Implementation Steps

#### Step 1: Create Archive Policy
**File**: `credentials/plugins/didcomm/src/main/kotlin/com/trustweave/credential/didcomm/storage/archive/ArchivePolicy.kt`

```kotlin
package com.trustweave.credential.didcomm.storage.archive

import com.trustweave.credential.didcomm.models.DidCommMessage
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Defines when messages should be archived.
 */
interface ArchivePolicy {
    suspend fun shouldArchive(message: DidCommMessage): Boolean
}

/**
 * Age-based archive policy.
 */
class AgeBasedArchivePolicy(
    private val maxAgeDays: Int = 90
) : ArchivePolicy {
    
    override suspend fun shouldArchive(message: DidCommMessage): Boolean {
        val created = message.created?.let {
            try {
                Instant.parse(it)
            } catch (e: Exception) {
                null
            }
        } ?: return false
        
        val age = ChronoUnit.DAYS.between(created, Instant.now())
        return age > maxAgeDays
    }
}

/**
 * Composite archive policy.
 */
class CompositeArchivePolicy(
    private val policies: List<ArchivePolicy>
) : ArchivePolicy {
    
    override suspend fun shouldArchive(message: DidCommMessage): Boolean {
        return policies.any { it.shouldArchive(message) }
    }
}
```

#### Step 2: Create Archive Service
**File**: `credentials/plugins/didcomm/src/main/kotlin/com/trustweave/credential/didcomm/storage/archive/MessageArchiver.kt`

```kotlin
package com.trustweave.credential.didcomm.storage.archive

import com.trustweave.credential.didcomm.models.DidCommMessage
import com.trustweave.credential.didcomm.storage.DidCommMessageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * Archives messages to cold storage.
 */
interface MessageArchiver {
    /**
     * Archives messages matching the policy.
     */
    suspend fun archiveMessages(policy: ArchivePolicy): ArchiveResult
    
    /**
     * Restores archived messages.
     */
    suspend fun restoreMessages(archiveId: String): RestoreResult
}

data class ArchiveResult(
    val archiveId: String,
    val messageCount: Int,
    val archiveSize: Long,
    val storageLocation: String
)

data class RestoreResult(
    val messageCount: Int,
    val restoredIds: List<String>
)

/**
 * S3-based message archiver.
 */
class S3MessageArchiver(
    private val storage: DidCommMessageStorage,
    private val s3Client: Any, // AWS S3 client
    private val bucketName: String,
    private val prefix: String = "archives/"
) : MessageArchiver {
    
    override suspend fun archiveMessages(policy: ArchivePolicy): ArchiveResult = withContext(Dispatchers.IO) {
        // Find messages to archive
        val messagesToArchive = findMessagesToArchive(policy)
        
        if (messagesToArchive.isEmpty()) {
            return@withContext ArchiveResult(
                archiveId = "",
                messageCount = 0,
                archiveSize = 0,
                storageLocation = ""
            )
        }
        
        // Create archive file (compressed JSONL)
        val archiveId = generateArchiveId()
        val archiveData = createArchiveFile(messagesToArchive)
        
        // Upload to S3
        val s3Key = "$prefix$archiveId.jsonl.gz"
        uploadToS3(s3Key, archiveData)
        
        // Mark messages as archived in database
        markAsArchived(messagesToArchive.map { it.id })
        
        ArchiveResult(
            archiveId = archiveId,
            messageCount = messagesToArchive.size,
            archiveSize = archiveData.size.toLong(),
            storageLocation = "s3://$bucketName/$s3Key"
        )
    }
    
    private suspend fun findMessagesToArchive(policy: ArchivePolicy): List<DidCommMessage> {
        // Query all messages and filter by policy
        // In production, use efficient query based on policy
        return emptyList() // Implementation
    }
    
    private fun createArchiveFile(messages: List<DidCommMessage>): ByteArray {
        val json = Json { prettyPrint = false; encodeDefaults = false }
        val output = ByteArrayOutputStream()
        
        GZIPOutputStream(output).use { gzip ->
            messages.forEach { message ->
                val line = json.encodeToString(
                    DidCommMessage.serializer(),
                    message
                ) + "\n"
                gzip.write(line.toByteArray(Charsets.UTF_8))
            }
        }
        
        return output.toByteArray()
    }
    
    private suspend fun uploadToS3(key: String, data: ByteArray) {
        // Upload to S3
        // Implementation depends on S3 client
    }
    
    private suspend fun markAsArchived(messageIds: List<String>) {
        // Update database to mark messages as archived
        // Add 'archived' flag to messages table
    }
    
    private fun generateArchiveId(): String {
        return java.util.UUID.randomUUID().toString()
    }
}
```

### Database Schema Updates

```sql
-- Add archive tracking
ALTER TABLE didcomm_messages ADD COLUMN IF NOT EXISTS archived BOOLEAN DEFAULT FALSE;
ALTER TABLE didcomm_messages ADD COLUMN IF NOT EXISTS archive_id VARCHAR(255);
ALTER TABLE didcomm_messages ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_messages_archived ON didcomm_messages(archived);
```

---

## 5. Message Replication for High Availability

### Overview
Implement message replication across multiple database instances for high availability and disaster recovery.

### Architecture

```
Primary Database
    │
    ├── Replication Manager
    │   ├── Write to Primary
    │   ├── Async Replication
    │   └── Conflict Resolution
    │
    └── Replica Databases
        ├── Replica 1
        ├── Replica 2
        └── Replica N
```

### Implementation Steps

#### Step 1: Create Replication Manager
**File**: `credentials/plugins/didcomm/src/main/kotlin/com/trustweave/credential/didcomm/storage/replication/ReplicationManager.kt`

```kotlin
package com.trustweave.credential.didcomm.storage.replication

import com.trustweave.credential.didcomm.models.DidCommMessage
import com.trustweave.credential.didcomm.storage.DidCommMessageStorage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Manages message replication across multiple storage backends.
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
    
    // Implement other methods with replication logic...
}
```

#### Step 2: Health Checks
**File**: `credentials/plugins/didcomm/src/main/kotlin/com/trustweave/credential/didcomm/storage/replication/ReplicaHealthCheck.kt`

```kotlin
/**
 * Health check for replica databases.
 */
interface ReplicaHealthCheck {
    suspend fun checkHealth(storage: DidCommMessageStorage): HealthStatus
    suspend fun getHealthyReplicas(): List<DidCommMessageStorage>
}

data class HealthStatus(
    val isHealthy: Boolean,
    val latency: Long, // milliseconds
    val lastCheck: Instant
)
```

---

## 6. Advanced Search Capabilities

### Overview
Implement full-text search, faceted search, and complex query capabilities.

### Architecture

```
Advanced Search
    │
    ├── Full-Text Search
    │   └── Elasticsearch/PostgreSQL FTS
    │
    ├── Faceted Search
    │   └── Aggregations
    │
    └── Complex Queries
        ├── Boolean operators
        ├── Range queries
        └── Regex queries
```

### Implementation Steps

#### Step 1: Create Search Interface
**File**: `credentials/plugins/didcomm/src/main/kotlin/com/trustweave/credential/didcomm/storage/search/AdvancedSearch.kt`

```kotlin
package com.trustweave.credential.didcomm.storage.search

import com.trustweave.credential.didcomm.models.DidCommMessage

/**
 * Advanced search interface.
 */
interface AdvancedSearch {
    /**
     * Full-text search across message content.
     */
    suspend fun fullTextSearch(
        query: String,
        limit: Int = 100,
        offset: Int = 0
    ): List<DidCommMessage>
    
    /**
     * Faceted search with aggregations.
     */
    suspend fun facetedSearch(
        query: SearchQuery,
        facets: List<Facet>
    ): FacetedSearchResult
    
    /**
     * Complex query with boolean operators.
     */
    suspend fun complexQuery(
        query: ComplexQuery,
        limit: Int = 100,
        offset: Int = 0
    ): List<DidCommMessage>
}

data class SearchQuery(
    val text: String? = null,
    val filters: Map<String, Any> = emptyMap(),
    val sort: SortOrder? = null
)

data class Facet(
    val field: String,
    val type: FacetType
)

enum class FacetType {
    TERMS,  // Count distinct values
    RANGE,  // Range aggregations
    DATE    // Date range aggregations
}

data class FacetedSearchResult(
    val results: List<DidCommMessage>,
    val facets: Map<String, FacetResult>
)

data class ComplexQuery(
    val conditions: List<QueryCondition>,
    val operator: BooleanOperator = BooleanOperator.AND
)

enum class BooleanOperator {
    AND, OR, NOT
}

data class QueryCondition(
    val field: String,
    val operator: ComparisonOperator,
    val value: Any
)

enum class ComparisonOperator {
    EQ, NE, GT, GTE, LT, LTE, LIKE, IN, BETWEEN
}
```

#### Step 2: PostgreSQL Full-Text Search
**File**: `credentials/plugins/didcomm/src/main/kotlin/com/trustweave/credential/didcomm/storage/search/PostgresFullTextSearch.kt`

```kotlin
/**
 * PostgreSQL full-text search implementation.
 */
class PostgresFullTextSearch(
    private val storage: PostgresDidCommMessageStorage
) : AdvancedSearch {
    
    override suspend fun fullTextSearch(
        query: String,
        limit: Int,
        offset: Int
    ): List<DidCommMessage> {
        // Use PostgreSQL tsvector/tsquery for full-text search
        // Add GIN index on searchable fields
        return emptyList() // Implementation
    }
    
    // Implement other methods...
}
```

### Database Schema Updates

```sql
-- Add full-text search column
ALTER TABLE didcomm_messages ADD COLUMN IF NOT EXISTS search_vector tsvector;

-- Create GIN index for full-text search
CREATE INDEX IF NOT EXISTS idx_messages_search_vector 
ON didcomm_messages USING GIN(search_vector);

-- Create trigger to update search vector
CREATE OR REPLACE FUNCTION update_message_search_vector()
RETURNS TRIGGER AS $$
BEGIN
    NEW.search_vector :=
        to_tsvector('english', COALESCE(NEW.type, '')) ||
        to_tsvector('english', COALESCE(NEW.body::text, ''));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER message_search_vector_update
BEFORE INSERT OR UPDATE ON didcomm_messages
FOR EACH ROW EXECUTE FUNCTION update_message_search_vector();
```

---

## 7. Message Analytics and Reporting

### Overview
Provide analytics and reporting capabilities for message traffic, patterns, and metrics.

### Architecture

```
Analytics Engine
    │
    ├── Metrics Collection
    │   ├── Message counts
    │   ├── Traffic patterns
    │   └── Error rates
    │
    ├── Aggregations
    │   ├── Time-based
    │   ├── DID-based
    │   └── Type-based
    │
    └── Reporting
        ├── Dashboards
        └── Exports
```

### Implementation Steps

#### Step 1: Create Analytics Service
**File**: `credentials/plugins/didcomm/src/main/kotlin/com/trustweave/credential/didcomm/storage/analytics/MessageAnalytics.kt`

```kotlin
package com.trustweave.credential.didcomm.storage.analytics

import com.trustweave.credential.didcomm.models.DidCommMessage
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Message analytics and reporting.
 */
interface MessageAnalytics {
    /**
     * Gets message statistics for a time period.
     */
    suspend fun getStatistics(
        startTime: Instant,
        endTime: Instant,
        groupBy: GroupBy = GroupBy.HOUR
    ): MessageStatistics
    
    /**
     * Gets traffic patterns.
     */
    suspend fun getTrafficPatterns(
        startTime: Instant,
        endTime: Instant
    ): TrafficPatterns
    
    /**
     * Gets top DIDs by message count.
     */
    suspend fun getTopDids(
        limit: Int = 10,
        startTime: Instant? = null,
        endTime: Instant? = null
    ): List<DidStatistics>
    
    /**
     * Gets message type distribution.
     */
    suspend fun getTypeDistribution(
        startTime: Instant? = null,
        endTime: Instant? = null
    ): Map<String, Int>
}

enum class GroupBy {
    HOUR, DAY, WEEK, MONTH
}

data class MessageStatistics(
    val totalMessages: Int,
    val sentMessages: Int,
    val receivedMessages: Int,
    val averageMessageSize: Long,
    val timeSeries: List<TimeSeriesPoint>
)

data class TimeSeriesPoint(
    val timestamp: Instant,
    val count: Int
)

data class TrafficPatterns(
    val peakHours: List<Int>,
    val averageMessagesPerHour: Double,
    val busiestDay: String
)

data class DidStatistics(
    val did: String,
    val messageCount: Int,
    val sentCount: Int,
    val receivedCount: Int
)
```

#### Step 2: Implement Analytics
**File**: `credentials/plugins/didcomm/src/main/kotlin/com/trustweave/credential/didcomm/storage/analytics/PostgresMessageAnalytics.kt`

```kotlin
/**
 * PostgreSQL-based analytics implementation.
 */
class PostgresMessageAnalytics(
    private val storage: PostgresDidCommMessageStorage
) : MessageAnalytics {
    
    override suspend fun getStatistics(
        startTime: Instant,
        endTime: Instant,
        groupBy: GroupBy
    ): MessageStatistics {
        // Query database for statistics
        // Use SQL aggregations and GROUP BY
        return MessageStatistics(
            totalMessages = 0,
            sentMessages = 0,
            receivedMessages = 0,
            averageMessageSize = 0,
            timeSeries = emptyList()
        )
    }
    
    // Implement other methods...
}
```

---

## 8. Key Rotation Automation

### Overview
Automate key rotation for DIDComm keys to maintain security.

### Architecture

```
Key Rotation Manager
    │
    ├── Rotation Policy
    │   ├── Time-based (e.g., every 90 days)
    │   ├── Usage-based (e.g., after N uses)
    │   └── Manual trigger
    │
    ├── Rotation Process
    │   ├── Generate new key
    │   ├── Update DID document
    │   ├── Migrate messages
    │   └── Archive old key
    │
    └── Key Lifecycle
        ├── Active
        ├── Rotating
        └── Archived
```

### Implementation Steps

#### Step 1: Create Rotation Policy
**File**: `credentials/plugins/didcomm/src/main/kotlin/com/trustweave/credential/didcomm/crypto/rotation/KeyRotationPolicy.kt`

```kotlin
package com.trustweave.credential.didcomm.crypto.rotation

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Defines when keys should be rotated.
 */
interface KeyRotationPolicy {
    suspend fun shouldRotate(keyId: String, keyMetadata: KeyMetadata): Boolean
}

data class KeyMetadata(
    val keyId: String,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
    val usageCount: Int = 0
)

/**
 * Time-based rotation policy.
 */
class TimeBasedRotationPolicy(
    private val maxAgeDays: Int = 90
) : KeyRotationPolicy {
    
    override suspend fun shouldRotate(
        keyId: String,
        keyMetadata: KeyMetadata
    ): Boolean {
        val age = ChronoUnit.DAYS.between(
            keyMetadata.createdAt,
            Instant.now()
        )
        return age >= maxAgeDays
    }
}

/**
 * Usage-based rotation policy.
 */
class UsageBasedRotationPolicy(
    private val maxUsageCount: Int = 10000
) : KeyRotationPolicy {
    
    override suspend fun shouldRotate(
        keyId: String,
        keyMetadata: KeyMetadata
    ): Boolean {
        return keyMetadata.usageCount >= maxUsageCount
    }
}
```

#### Step 2: Create Rotation Manager
**File**: `credentials/plugins/didcomm/src/main/kotlin/com/trustweave/credential/didcomm/crypto/rotation/KeyRotationManager.kt`

```kotlin
package com.trustweave.credential.didcomm.crypto.rotation

import com.trustweave.credential.didcomm.crypto.secret.LocalKeyStore
import com.trustweave.kms.KeyManagementService
import org.didcommx.didcomm.secret.Secret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages key rotation for DIDComm keys.
 */
class KeyRotationManager(
    private val keyStore: LocalKeyStore,
    private val kms: KeyManagementService,
    private val policy: KeyRotationPolicy
) {
    
    /**
     * Checks and rotates keys if needed.
     */
    suspend fun checkAndRotate(): RotationResult = withContext(Dispatchers.IO) {
        val keysToRotate = findKeysToRotate()
        
        val results = keysToRotate.map { keyId ->
            rotateKey(keyId)
        }
        
        RotationResult(
            rotatedCount = results.size,
            results = results
        )
    }
    
    /**
     * Rotates a specific key.
     */
    suspend fun rotateKey(keyId: String): KeyRotationResult = withContext(Dispatchers.IO) {
        // 1. Get current key
        val oldKey = keyStore.get(keyId)
            ?: throw IllegalArgumentException("Key not found: $keyId")
        
        // 2. Generate new key
        val newKeyId = generateNewKeyId(keyId)
        val newKey = generateNewKey(newKeyId)
        
        // 3. Store new key
        keyStore.store(newKeyId, newKey)
        
        // 4. Update DID document (if applicable)
        updateDidDocument(keyId, newKeyId)
        
        // 5. Archive old key
        archiveOldKey(keyId, oldKey)
        
        KeyRotationResult(
            oldKeyId = keyId,
            newKeyId = newKeyId,
            success = true
        )
    }
    
    private suspend fun findKeysToRotate(): List<String> {
        val allKeys = keyStore.list()
        return allKeys.filter { keyId ->
            val metadata = getKeyMetadata(keyId)
            policy.shouldRotate(keyId, metadata)
        }
    }
    
    private suspend fun getKeyMetadata(keyId: String): KeyMetadata {
        // Get metadata from key store or separate metadata store
        return KeyMetadata(
            keyId = keyId,
            createdAt = Instant.now().minus(100, ChronoUnit.DAYS),
            lastUsedAt = null,
            usageCount = 0
        )
    }
    
    private fun generateNewKeyId(oldKeyId: String): String {
        // Generate new key ID (e.g., increment version)
        return "$oldKeyId-v2"
    }
    
    private suspend fun generateNewKey(keyId: String): Secret {
        // Generate new key using KMS
        // Implementation depends on key type
        throw NotImplementedError("Key generation to be implemented")
    }
    
    private suspend fun updateDidDocument(oldKeyId: String, newKeyId: String) {
        // Update DID document with new key
        // Implementation depends on DID method
    }
    
    private suspend fun archiveOldKey(keyId: String, key: Secret) {
        // Archive old key (don't delete immediately)
        // Keep for decryption of old messages
    }
}

data class RotationResult(
    val rotatedCount: Int,
    val results: List<KeyRotationResult>
)

data class KeyRotationResult(
    val oldKeyId: String,
    val newKeyId: String,
    val success: Boolean,
    val error: String? = null
)
```

#### Step 3: Scheduled Rotation
**File**: `credentials/plugins/didcomm/src/main/kotlin/com/trustweave/credential/didcomm/crypto/rotation/ScheduledKeyRotation.kt`

```kotlin
/**
 * Scheduled key rotation service.
 */
class ScheduledKeyRotation(
    private val rotationManager: KeyRotationManager,
    private val interval: java.time.Duration = java.time.Duration.ofDays(1)
) {
    private var rotationJob: kotlinx.coroutines.Job? = null
    
    fun start() {
        rotationJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            while (true) {
                try {
                    rotationManager.checkAndRotate()
                } catch (e: Exception) {
                    // Log error, continue
                }
                kotlinx.coroutines.delay(interval.toMillis())
            }
        }
    }
    
    fun stop() {
        rotationJob?.cancel()
    }
}
```

---

## Implementation Timeline

### Phase 1: Core Enhancements (Weeks 1-4)
- **Week 1-2**: EncryptedFileLocalKeyStore
- **Week 2-3**: Message encryption at rest
- **Week 3-4**: MongoDB storage

### Phase 2: Scalability (Weeks 5-8)
- **Week 5-6**: Message archiving
- **Week 7-8**: Message replication

### Phase 3: Advanced Features (Weeks 9-12)
- **Week 9-10**: Advanced search
- **Week 11**: Analytics and reporting
- **Week 12**: Key rotation automation

## Dependencies

### New Dependencies Required

```kotlin
// MongoDB
implementation("org.mongodb:mongodb-driver-kotlin-coroutine:4.11.0")

// AWS S3 (for archiving)
implementation("software.amazon.awssdk:s3:2.20.0")

// Elasticsearch (optional, for advanced search)
implementation("co.elastic.clients:elasticsearch-java:8.11.0")
```

## Testing Strategy

### Unit Tests
- Encryption/decryption tests
- Storage operation tests
- Search functionality tests
- Analytics calculation tests

### Integration Tests
- Database integration tests
- Cloud storage integration tests
- Replication tests
- Performance tests

### Security Tests
- Encryption strength tests
- Key access control tests
- Audit logging tests

## Monitoring & Observability

### Metrics to Track
- Message storage latency
- Archive operation duration
- Replication lag
- Search query performance
- Key rotation success rate
- Encryption/decryption performance

### Alerts
- Replication failures
- Archive failures
- Key rotation failures
- High storage usage
- Search performance degradation

## Security Considerations

1. **Key Management**: Master keys must be stored securely (HSM, cloud KMS)
2. **Access Control**: Implement RBAC for storage operations
3. **Audit Logging**: Log all key access and rotation operations
4. **Encryption**: Use strong encryption (AES-256-GCM)
5. **Key Rotation**: Regular rotation schedule
6. **Backup Encryption**: Encrypt backups

## Performance Considerations

1. **Indexing**: Proper indexes for all query patterns
2. **Caching**: Cache frequently accessed data
3. **Batch Operations**: Batch archive and replication operations
4. **Connection Pooling**: Use connection pools for databases
5. **Async Operations**: Use async for non-critical operations

## Migration Strategy

1. **Gradual Rollout**: Implement features incrementally
2. **Backward Compatibility**: Maintain compatibility with existing data
3. **Data Migration**: Scripts for migrating existing data
4. **Rollback Plan**: Ability to rollback if issues occur

