package com.trustweave.wallet.cloud

import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.wallet.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.time.Instant
import java.util.UUID

/**
 * Cloud storage-backed wallet implementation.
 *
 * Stores credentials in cloud storage (AWS S3, Azure Blob Storage, or Google Cloud Storage).
 * Enables multi-device wallet synchronization.
 *
 * **Example:**
 * ```kotlin
 * val wallet = CloudWallet(
 *     walletId = "wallet-1",
 *     walletDid = "did:key:wallet-1",
 *     holderDid = "did:key:holder",
 *     storageProvider = "aws",
 *     bucketName = "my-bucket",
 *     basePath = "wallets/wallet-1"
 * )
 * ```
 */
abstract class CloudWallet(
    override val walletId: String,
    val walletDid: String,
    val holderDid: String,
    protected val bucketName: String,
    protected val basePath: String
) : Wallet, CredentialStorage {

    protected val json = Json {
        prettyPrint = false
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    protected val credentialsPath: String = "$basePath/credentials"
    protected val collectionsPath: String = "$basePath/collections"
    protected val metadataPath: String = "$basePath/metadata"
    protected val tagsPath: String = "$basePath/tags"

    /**
     * Upload data to cloud storage.
     */
    protected abstract suspend fun upload(key: String, data: ByteArray)

    /**
     * Download data from cloud storage.
     */
    protected abstract suspend fun download(key: String): ByteArray?

    /**
     * Delete data from cloud storage.
     */
    protected abstract suspend fun deleteFromStorage(key: String): Boolean

    /**
     * List all keys with a given prefix.
     */
    protected abstract suspend fun listKeys(prefix: String): List<String>

    // CredentialStorage implementation
    override suspend fun store(credential: VerifiableCredential): String = withContext(Dispatchers.IO) {
        val id = credential.id ?: UUID.randomUUID().toString()
        val credentialJson = json.encodeToString(VerifiableCredential.serializer(), credential)

        val key = "$credentialsPath/$id.json"
        upload(key, credentialJson.toByteArray(Charsets.UTF_8))

        // Initialize metadata if not exists
        val metadataKey = "$metadataPath/$id.json"
        val existingMetadata = download(metadataKey)
        if (existingMetadata == null) {
            val metadata = buildJsonObject {
                put("credentialId", id)
                put("createdAt", Instant.now().toString())
                put("updatedAt", Instant.now().toString())
                put("notes", JsonNull)
                putJsonArray("tags") { }
                putJsonObject("metadata") { }
            }
            upload(metadataKey, json.encodeToString(JsonObject.serializer(), metadata).toByteArray(Charsets.UTF_8))
        }

        id
    }

    override suspend fun get(credentialId: String): VerifiableCredential? = withContext(Dispatchers.IO) {
        val key = "$credentialsPath/$credentialId.json"
        val content = download(key) ?: return@withContext null

        val credentialJson = String(content, Charsets.UTF_8)
        json.decodeFromString(VerifiableCredential.serializer(), credentialJson)
    }

    override suspend fun list(filter: CredentialFilter?): List<VerifiableCredential> = withContext(Dispatchers.IO) {
        val credentials = mutableListOf<VerifiableCredential>()

        val keys = listKeys(credentialsPath)
        keys.filter { it.endsWith(".json") }
            .forEach { key ->
                try {
                    val content = download(key) ?: return@forEach
                    val credentialJson = String(content, Charsets.UTF_8)
                    val credential = json.decodeFromString(VerifiableCredential.serializer(), credentialJson)

                    if (filter == null || matchesFilter(credential, filter)) {
                        credentials.add(credential)
                    }
                } catch (e: Exception) {
                    // Skip corrupted files
                }
            }

        credentials
    }

    override suspend fun delete(credentialId: String): Boolean = withContext(Dispatchers.IO) {
        val credentialKey = "$credentialsPath/$credentialId.json"
        val deleted = deleteFromStorage(credentialKey)

        if (deleted) {
            // Clean up related files
            deleteFromStorage("$metadataPath/$credentialId.json")
            deleteFromStorage("$tagsPath/$credentialId.json")
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
}

