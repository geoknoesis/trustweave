package org.trustweave.wallet.cloud

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.wallet.CredentialFilter
import org.trustweave.wallet.CredentialQueryBuilder
import org.trustweave.wallet.CredentialReadFailure
import org.trustweave.wallet.CredentialRecordStorage
import org.trustweave.wallet.CredentialRecovery
import org.trustweave.wallet.CredentialRecoveryResult
import org.trustweave.wallet.CredentialStorage
import org.trustweave.wallet.StoredCredentialRecord
import org.trustweave.wallet.Wallet
import org.trustweave.wallet.WalletStatistics
import java.util.UUID
import kotlin.time.Clock

/**
 * Cloud storage-backed wallet implementation.
 *
 * Stores credentials in cloud storage (AWS S3, Azure Blob Storage, or Google Cloud Storage).
 * Enables multi-device wallet synchronization.
 *
 * **No encryption:** this class does not encrypt credentials; objects are written as plain JSON.
 * Confidentiality relies entirely on the storage provider's own at-rest encryption (e.g. S3
 * SSE, Azure Storage encryption) and access control configured outside this library.
 *
 * Credential ids are percent-encoded into a single object-key segment, so an id can never
 * traverse or alias another key.
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
    protected val basePath: String,
) : Wallet,
    CredentialStorage,
    CredentialRecordStorage,
    CredentialRecovery {
    protected val json =
        Json {
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
    protected abstract suspend fun upload(
        key: String,
        data: ByteArray,
    )

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

    private fun credentialKey(credentialId: String): String = "$credentialsPath/${encodeKeySegment(credentialId)}.json"

    /**
     * The key objects were stored under before ids were percent-encoded (`{credentials}/{id}.json`),
     * or `null` when that raw key could address anything outside the credentials prefix (empty, `.`
     * or `..` segments, backslashes, control characters) or is identical to [credentialKey]. Such
     * objects stay reachable; an id that could traverse never gets a fallback.
     */
    private fun legacyCredentialKey(credentialId: String): String? {
        if (credentialId.isEmpty() || credentialId.any { it == '\\' || it.isISOControl() }) return null
        if (credentialId.split('/').any { it == "." || it == ".." }) return null
        val key = "$credentialsPath/$credentialId.json"
        return key.takeIf { it != credentialKey(credentialId) }
    }

    private suspend fun downloadWithLegacyFallback(credentialId: String): ByteArray? =
        download(credentialKey(credentialId))
            ?: legacyCredentialKey(credentialId)?.let { download(it) }

    /**
     * Percent-encodes a credential id for use as exactly one object-key segment, so an id can
     * never add path separators, climb out of the wallet prefix (`../`), or collide with another
     * id. Letters, digits and `-_.~:` are kept as-is (so ordinary ids such as `urn:uuid:...` keep
     * their historical keys); everything else, and a dots-only id, is encoded. Reversible via
     * [decodeKeySegment].
     */
    private fun encodeKeySegment(id: String): String {
        val out = StringBuilder()
        for (byte in id.toByteArray(Charsets.UTF_8)) {
            val c = (byte.toInt() and 0xFF).toChar()
            if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in "-_~:" || (c == '.' && id.any { it != '.' })) {
                out.append(c)
            } else {
                out.append('%').append("%02X".format(byte.toInt() and 0xFF))
            }
        }
        return out.toString()
    }

    private fun decodeKeySegment(segment: String): String {
        val bytes = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < segment.length) {
            val c = segment[i]
            val hex = if (c == '%' && i + 2 < segment.length) segment.substring(i + 1, i + 3).toIntOrNull(16) else null
            if (hex != null) {
                bytes.write(hex)
                i += 3
            } else {
                bytes.write(c.toString().toByteArray(Charsets.UTF_8))
                i++
            }
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }

    // CredentialStorage implementation
    override suspend fun store(credential: VerifiableCredential): String =
        withContext(Dispatchers.IO) {
            val id = credential.id?.value ?: UUID.randomUUID().toString()
            val credentialJson = json.encodeToString(VerifiableCredential.serializer(), credential)

            val key = credentialKey(id)
            upload(key, credentialJson.toByteArray(Charsets.UTF_8))

            // Create metadata on first store; on a re-store keep it but refresh `updatedAt`.
            val metadataKey = "$metadataPath/${encodeKeySegment(id)}.json"
            val now = Clock.System.now().toString()
            val existing =
                download(metadataKey)?.let {
                    runCatching { json.parseToJsonElement(String(it, Charsets.UTF_8)) as? JsonObject }.getOrNull()
                }
            val metadata =
                if (existing != null) {
                    JsonObject(existing + ("updatedAt" to JsonPrimitive(now)))
                } else {
                    buildJsonObject {
                        put("credentialId", id)
                        put("createdAt", now)
                        put("updatedAt", now)
                        put("notes", JsonNull)
                        putJsonArray("tags") { }
                        putJsonObject("metadata") { }
                    }
                }
            upload(metadataKey, json.encodeToString(JsonObject.serializer(), metadata).toByteArray(Charsets.UTF_8))

            id
        }

    override suspend fun get(credentialId: String): VerifiableCredential? =
        withContext(Dispatchers.IO) {
            val content = downloadWithLegacyFallback(credentialId) ?: return@withContext null

            val credentialJson = String(content, Charsets.UTF_8)
            json.decodeFromString(VerifiableCredential.serializer(), credentialJson)
        }

    override suspend fun list(filter: CredentialFilter?): List<VerifiableCredential> = listRecords(filter).map { it.credential }

    override suspend fun listRecords(filter: CredentialFilter?): List<StoredCredentialRecord> =
        withContext(Dispatchers.IO) {
            listKeys("$credentialsPath/")
                .filter { it.endsWith(".json") }
                .sorted()
                .mapNotNull { key -> readRecordOrSkip(key) }
                .distinctBy { it.storageId }
                .filter { filter == null || matchesFilter(it.credential, filter) }
        }

    private suspend fun readRecord(key: String): StoredCredentialRecord? {
        require(key.startsWith("$credentialsPath/") && key.endsWith(".json")) { "Unexpected credential key" }
        val content = download(key) ?: return null // Concurrent deletion is a normal object-store race.
        val credential = json.decodeFromString(VerifiableCredential.serializer(), String(content, Charsets.UTF_8))
        return StoredCredentialRecord(handleFor(key), credential)
    }

    /**
     * The handle for an object key: the decoded id when that id maps back to exactly this key,
     * otherwise (an object written before ids were encoded) the raw id, which [get] and [delete]
     * reach through the legacy-key fallback. Either way the handle round-trips to the same key.
     */
    private fun handleFor(key: String): String {
        val segment = key.removePrefix("$credentialsPath/").removeSuffix(".json")
        val decoded = decodeKeySegment(segment)
        return if (credentialKey(decoded) == key) decoded else segment
    }

    /**
     * Like [readRecord] but an object that cannot be parsed is skipped rather than failing the
     * whole scan ([recoverRecords] is the call that reports it). Storage failures still propagate.
     */
    private suspend fun readRecordOrSkip(key: String): StoredCredentialRecord? =
        try {
            readRecord(key)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (corrupt: IllegalArgumentException) {
            null // includes kotlinx.serialization.SerializationException
        }

    override suspend fun recoverRecords(): CredentialRecoveryResult =
        withContext(Dispatchers.IO) {
            val records = mutableListOf<StoredCredentialRecord>()
            val failures = mutableListOf<CredentialReadFailure>()
            for (key in listKeys("$credentialsPath/").filter { it.endsWith(".json") }.sorted()) {
                try {
                    readRecord(key)?.let { records.add(it) }
                } catch (
                    error: kotlinx.coroutines.CancellationException,
                ) {
                    throw error
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    failures.add(CredentialReadFailure(key, error.javaClass.simpleName))
                }
            }
            CredentialRecoveryResult(records, failures)
        }

    override suspend fun delete(credentialId: String): Boolean =
        withContext(Dispatchers.IO) {
            val segment = encodeKeySegment(credentialId)
            val deleted =
                deleteFromStorage(credentialKey(credentialId)) ||
                    (legacyCredentialKey(credentialId)?.let { deleteFromStorage(it) } ?: false)

            if (deleted) {
                // Clean up related files
                deleteFromStorage("$metadataPath/$segment.json")
                deleteFromStorage("$tagsPath/$segment.json")
            }

            deleted
        }

    override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> =
        withContext(Dispatchers.IO) {
            val builder = CredentialQueryBuilder()
            builder.query()

            // CloudWallet stores no queryable tag/collection data, so byTag/byCollection
            // cannot be honored. Failing loudly is required by the CredentialQueryBuilder
            // contract — silently returning unfiltered credentials would feed wrong
            // candidates into presentation selection.
            if (builder.requestedTags.isNotEmpty() || builder.requestedCollections.isNotEmpty()) {
                throw UnsupportedOperationException(
                    "CloudWallet does not support byTag/byCollection query filters " +
                        "(requested tags=${builder.requestedTags}, collections=${builder.requestedCollections}). " +
                        "Use a wallet with CredentialTagging/CredentialCollections support instead.",
                )
            }

            val predicate = builder.toPredicate()
            val allCredentials = list(null)
            allCredentials.filter(predicate)
        }

    /**
     * Check if credential matches filter criteria.
     */
    private fun matchesFilter(
        credential: VerifiableCredential,
        filter: CredentialFilter,
    ): Boolean {
        if (filter.issuer != null && credential.issuer.id.value != filter.issuer) return false
        if (filter.type != null) {
            val filterTypes = filter.type
            if (filterTypes != null && !filterTypes.any { filterType -> credential.type.any { it.value == filterType } }) return false
        }
        if (filter.subjectId != null) {
            val subjectId = credential.credentialSubject.id?.value
            if (subjectId != filter.subjectId) return false
        }
        if (filter.expired != null) {
            val isExpired =
                credential.expirationDate?.let {
                    Clock.System.now() > it
                } ?: false
            if (isExpired != filter.expired) return false
        }
        if (!org.trustweave.wallet.matchesOfflineStatusFilter(credential, filter)) return false
        return true
    }

    /**
     * Scan one object at a time. Remote status remains UNKNOWN without a resolver.
     * Object listing is a point-in-time key set; concurrent deletions are skipped.
     */
    override suspend fun getStatistics(): WalletStatistics =
        withContext(Dispatchers.IO) {
            val now = Clock.System.now()
            var total = 0
            var valid = 0
            var expired = 0
            var unknown = 0
            for (key in listKeys("$credentialsPath/").filter { it.endsWith(".json") }) {
                val credential = readRecordOrSkip(key)?.credential ?: continue
                total++
                val expiry =
                    if (credential.isVc2 &&
                        !credential.isVc1
                    ) {
                        credential.validUntil
                    } else {
                        credential.validUntil ?: credential.expirationDate
                    }
                val isExpired = expiry?.let { it <= now } == true
                if (isExpired) expired++
                if (credential.credentialStatus != null) unknown++
                if (credential.proof != null && credential.credentialStatus == null && !isExpired) valid++
            }
            WalletStatistics(
                totalCredentials = total,
                validCredentials = valid,
                expiredCredentials = expired,
                revokedCredentials = 0,
                unknownStatusCredentials = unknown,
            )
        }
}
