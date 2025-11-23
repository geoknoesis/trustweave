package com.trustweave.wallet.file

import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.wallet.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

/**
 * File-based wallet implementation.
 * 
 * Stores credentials, collections, tags, and metadata in local filesystem.
 * Supports optional encryption for sensitive data.
 * 
 * **Example:**
 * ```kotlin
 * val wallet = FileWallet(
 *     walletId = "wallet-1",
 *     walletDid = "did:key:wallet-1",
 *     holderDid = "did:key:holder",
 *     walletDir = Paths.get("/path/to/wallet"),
 *     encryptionKey = "base64-encoded-key" // optional
 * )
 * ```
 */
class FileWallet(
    override val walletId: String,
    val walletDid: String,
    val holderDid: String,
    private val walletDir: Path,
    private val encryptionKey: String? = null
) : Wallet, CredentialStorage {
    
    private val json = Json {
        prettyPrint = false
        encodeDefaults = false
        ignoreUnknownKeys = true
    }
    
    private val credentialsDir: Path = walletDir.resolve("credentials")
    private val collectionsDir: Path = walletDir.resolve("collections")
    private val metadataDir: Path = walletDir.resolve("metadata")
    private val tagsDir: Path = walletDir.resolve("tags")
    
    init {
        // Initialize directory structure
        initializeDirectories()
    }
    
    /**
     * Initialize directory structure for wallet data.
     */
    private fun initializeDirectories() {
        Files.createDirectories(credentialsDir)
        Files.createDirectories(collectionsDir)
        Files.createDirectories(metadataDir)
        Files.createDirectories(tagsDir)
    }
    
    // CredentialStorage implementation
    override suspend fun store(credential: VerifiableCredential): String = withContext(Dispatchers.IO) {
        val id = credential.id ?: UUID.randomUUID().toString()
        val credentialJson = json.encodeToString(VerifiableCredential.serializer(), credential)
        
        val credentialFile = credentialsDir.resolve("$id.json")
        val content = if (encryptionKey != null) {
            encrypt(credentialJson)
        } else {
            credentialJson.toByteArray(Charsets.UTF_8)
        }
        
        Files.write(credentialFile, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
        
        // Initialize metadata if not exists
        val metadataFile = metadataDir.resolve("$id.json")
        if (!Files.exists(metadataFile)) {
            val metadata = buildJsonObject {
                put("credentialId", id)
                put("createdAt", Instant.now().toString())
                put("updatedAt", Instant.now().toString())
                put("notes", JsonNull)
                putJsonArray("tags") { }
                putJsonObject("metadata") { }
            }
            Files.write(metadataFile, json.encodeToString(JsonObject.serializer(), metadata).toByteArray(Charsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        }
        
        id
    }
    
    override suspend fun get(credentialId: String): VerifiableCredential? = withContext(Dispatchers.IO) {
        val credentialFile = credentialsDir.resolve("$credentialId.json")
        if (!Files.exists(credentialFile)) {
            return@withContext null
        }
        
        val content = Files.readAllBytes(credentialFile)
        val credentialJson = if (encryptionKey != null) {
            decrypt(content)
        } else {
            String(content, Charsets.UTF_8)
        }
        
        json.decodeFromString(VerifiableCredential.serializer(), credentialJson)
    }
    
    override suspend fun list(filter: CredentialFilter?): List<VerifiableCredential> = withContext(Dispatchers.IO) {
        val credentials = mutableListOf<VerifiableCredential>()
        
        if (!Files.exists(credentialsDir)) {
            return@withContext emptyList()
        }
        
        Files.list(credentialsDir).use { stream ->
            stream.filter { it.toString().endsWith(".json") }
                .forEach { file ->
                    try {
                        val credentialId = file.fileName.toString().removeSuffix(".json")
                        val credentialFile = credentialsDir.resolve("$credentialId.json")
                        if (Files.exists(credentialFile)) {
                            val content = Files.readAllBytes(credentialFile)
                            val credentialJson = if (encryptionKey != null) {
                                decrypt(content)
                            } else {
                                String(content, Charsets.UTF_8)
                            }
                            val credential = json.decodeFromString(VerifiableCredential.serializer(), credentialJson)
                            if (filter == null || matchesFilter(credential, filter)) {
                                credentials.add(credential)
                            }
                        }
                    } catch (e: Exception) {
                        // Skip corrupted files
                    }
                }
        }
        
        credentials
    }
    
    override suspend fun delete(credentialId: String): Boolean = withContext(Dispatchers.IO) {
        val credentialFile = credentialsDir.resolve("$credentialId.json")
        val deleted = Files.deleteIfExists(credentialFile)
        
        if (deleted) {
            // Clean up related files
            Files.deleteIfExists(metadataDir.resolve("$credentialId.json"))
            Files.deleteIfExists(tagsDir.resolve("$credentialId.json"))
        }
        
        deleted
    }
    
    override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> = withContext(Dispatchers.IO) {
        val builder = CredentialQueryBuilder()
        builder.query()
        
        // Use reflection to call createPredicate()
        val predicateMethod = builder::class.java.getMethod("createPredicate")
        @Suppress("UNCHECKED_CAST")
        val predicate = predicateMethod.invoke(builder) as (VerifiableCredential) -> Boolean
        
        // Get all credentials and filter
        val allCredentials = list(null)
        allCredentials.filter(predicate)
    }
    
    /**
     * Check if credential matches filter criteria.
     */
    private fun matchesFilter(credential: VerifiableCredential, filter: CredentialFilter): Boolean {
        if (filter.issuer != null && credential.issuer != filter.issuer) return false
        if (filter.type != null) {
            val filterTypes = filter.type
            if (filterTypes != null && !filterTypes.any { credential.type.contains(it) }) return false
        }
        if (filter.subjectId != null) {
            val subjectId = try {
                credential.credentialSubject.jsonObject["id"]?.jsonPrimitive?.content
            } catch (e: Exception) {
                null
            }
            if (subjectId != filter.subjectId) return false
        }
        if (filter.expired != null) {
            val isExpired = credential.expirationDate?.let {
                try {
                    Instant.now().isAfter(Instant.parse(it))
                } catch (e: Exception) {
                    false
                }
            } ?: false
            if (isExpired != filter.expired) return false
        }
        if (filter.revoked != null) {
            val isRevoked = credential.credentialStatus != null
            if (isRevoked != filter.revoked) return false
        }
        return true
    }
    
    /**
     * Get wallet statistics.
     */
    override suspend fun getStatistics(): WalletStatistics = withContext(Dispatchers.IO) {
        val allCredentials = list(null)
        val now = Instant.now()
        
        WalletStatistics(
            totalCredentials = allCredentials.size,
            validCredentials = allCredentials.count { credential ->
                credential.proof != null &&
                (credential.expirationDate?.let { expirationDate ->
                    try {
                        val expiration = Instant.parse(expirationDate)
                        now.isBefore(expiration)
                    } catch (e: Exception) {
                        false
                    }
                } ?: true) &&
                credential.credentialStatus == null
            },
            expiredCredentials = allCredentials.count { credential ->
                credential.expirationDate?.let { expirationDate ->
                    try {
                        val expiration = Instant.parse(expirationDate)
                        now.isAfter(expiration)
                    } catch (e: Exception) {
                        false
                    }
                } ?: false
            },
            revokedCredentials = allCredentials.count { it.credentialStatus != null },
            collectionsCount = 0, // Would require collection implementation
            tagsCount = 0, // Would require tag implementation
            archivedCount = 0 // Would require archive implementation
        )
    }
    
    /**
     * Encrypt data using AES.
     */
    private fun encrypt(data: String): ByteArray {
        if (encryptionKey == null) {
            throw IllegalStateException("Encryption key not provided")
        }
        
        val keyBytes = Base64.getDecoder().decode(encryptionKey)
        val key = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        
        return cipher.doFinal(data.toByteArray(Charsets.UTF_8))
    }
    
    /**
     * Decrypt data using AES.
     */
    private fun decrypt(encryptedData: ByteArray): String {
        if (encryptionKey == null) {
            throw IllegalStateException("Encryption key not provided")
        }
        
        val keyBytes = Base64.getDecoder().decode(encryptionKey)
        val key = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.DECRYPT_MODE, key)
        
        val decrypted = cipher.doFinal(encryptedData)
        return String(decrypted, Charsets.UTF_8)
    }
}

