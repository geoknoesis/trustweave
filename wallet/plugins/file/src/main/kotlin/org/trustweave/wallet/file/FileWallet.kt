package org.trustweave.wallet.file

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.wallet.CredentialFilter
import org.trustweave.wallet.CredentialQueryBuilder
import org.trustweave.wallet.CredentialReadFailure
import org.trustweave.wallet.CredentialRecordStorage
import org.trustweave.wallet.CredentialRecovery
import org.trustweave.wallet.CredentialRecoveryResult
import org.trustweave.wallet.CredentialStorage
import org.trustweave.wallet.StoredCredentialRecord
import org.trustweave.wallet.StoredCredentialStatus
import org.trustweave.wallet.Wallet
import org.trustweave.wallet.WalletStatistics
import org.trustweave.wallet.WalletStatusResolver
import org.trustweave.wallet.exception.WalletException
import org.trustweave.wallet.resolveStoredStatus
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.withLock

/**
 * File-based wallet implementation.
 *
 * Stores credentials, collections, tags, and metadata in local filesystem.
 * Supports optional encryption for sensitive data.
 *
 * **Encryption format:** credentials and their metadata sidecars are encrypted with
 * AES/GCM/NoPadding using a
 * random 12-byte IV per write and a 128-bit authentication tag. The on-disk blob is
 * `[1-byte format version][12-byte IV][ciphertext + tag]`. Any tampering with the
 * stored bytes is detected during decryption and surfaces as a
 * [WalletException.StorageError] instead of returning corrupted data.
 *
 * **Record binding (format version 2).** New writes use format version 2, which authenticates
 * additional data (AAD): the wallet id, the kind of record (credential or metadata) and the SHA-256 of the
 * credential id, which is also the record's file name. Someone with write access to the wallet
 * directory therefore cannot swap the encrypted record of one credential for another's, move a
 * record between wallets, or put a metadata sidecar where a credential belongs: the GCM tag fails and
 * the read throws [WalletException.StorageError]. The wallet id is part of the binding, so a wallet
 * must be reopened with the same `walletId` it was written with.
 *
 * **Legacy records (format version 1, no AAD)** written by earlier versions are still read: a record's
 * version byte decides, and a version-2 record is never accepted without its AAD (no downgrade by
 * stripping it). Legacy records stay swappable until they are written again; storing a credential
 * again upgrades it to version 2.
 *
 * **Key material:** the key must be 16, 24, or 32 bytes of AES key material
 * (AES-128/192/256), given either Base64-encoded (`String` or `CharArray`) or raw
 * (`ByteArray`). Invalid keys are rejected at construction time. The `ByteArray` and
 * `CharArray` constructors copy the key, so the caller can — and should — zero its own
 * array right after construction; a `String` cannot be wiped.
 *
 * **Encryption is required.** Constructing a wallet without a key fails with
 * [WalletException.WalletCreationFailed]. Plaintext storage is available only through the
 * explicit [FileWallet.unencrypted] opt-in (tests, throwaway demos), which logs a warning.
 * Earlier versions silently fell back to plaintext when the key was omitted; existing
 * plaintext wallets can still be opened with [FileWallet.unencrypted].
 *
 * **File naming:** credential files are named after the SHA-256 hex digest of the
 * credential id (`<sha256(id)>.json`), never the raw id, so attacker-controlled ids
 * (e.g. containing `../`) cannot escape the wallet directory.
 *
 * **Breaking change:** wallets written by earlier versions (AES/ECB encryption,
 * raw-id filenames) are not readable by this implementation; re-import credentials
 * if migrating.
 *
 * **Example:**
 * ```kotlin
 * val wallet = FileWallet(
 *     walletId = "wallet-1",
 *     walletDid = "did:key:wallet-1",
 *     holderDid = "did:key:holder",
 *     walletDir = Paths.get("/path/to/wallet"),
 *     encryptionKey = keyBytes // ByteArray (zero it afterwards), CharArray or Base64 String
 * )
 * keyBytes.fill(0)
 * ```
 */
class FileWallet private constructor(
    override val walletId: String,
    val walletDid: String,
    val holderDid: String,
    walletDir: Path,
    /** AES key; null only for the explicit [unencrypted] opt-in. */
    private val secretKey: SecretKeySpec?,
    private val statusResolver: WalletStatusResolver?,
) : Wallet,
    CredentialStorage,
    CredentialRecordStorage,
    CredentialRecovery {
    /**
     * Opens an AES-GCM encrypted wallet with a Base64-encoded key.
     *
     * @param encryptionKey Base64 of 16, 24 or 32 key bytes. Required: `null` fails with
     *   [WalletException.WalletCreationFailed] (use [unencrypted] to opt into plaintext).
     */
    constructor(
        walletId: String,
        walletDid: String,
        holderDid: String,
        walletDir: Path,
        encryptionKey: String? = null,
        statusResolver: WalletStatusResolver? = null,
    ) : this(
        walletId,
        walletDid,
        holderDid,
        walletDir,
        keyFromBase64(requireKey(encryptionKey, walletId).toByteArray(Charsets.US_ASCII), walletId),
        statusResolver,
    )

    /**
     * Opens an AES-GCM encrypted wallet with raw key bytes. The bytes are copied; zero
     * [encryptionKey] after this returns.
     */
    constructor(
        walletId: String,
        walletDid: String,
        holderDid: String,
        walletDir: Path,
        encryptionKey: ByteArray,
        statusResolver: WalletStatusResolver? = null,
    ) : this(walletId, walletDid, holderDid, walletDir, keyFromBytes(encryptionKey, walletId), statusResolver)

    /**
     * Opens an AES-GCM encrypted wallet with a Base64-encoded key held in a [CharArray]. The
     * characters are copied and every intermediate buffer is zeroed; zero [encryptionKey] after
     * this returns.
     */
    constructor(
        walletId: String,
        walletDid: String,
        holderDid: String,
        walletDir: Path,
        encryptionKey: CharArray,
        statusResolver: WalletStatusResolver? = null,
    ) : this(walletId, walletDid, holderDid, walletDir, keyFromBase64Chars(encryptionKey, walletId), statusResolver)

    private val normalizedWalletDir = walletDir.toAbsolutePath().normalize()

    companion object {
        /**
         * Opens a wallet that stores credentials in **plaintext**. This is the only way to get an
         * unencrypted [FileWallet]; use it for tests, throwaway demos, or to read wallets written
         * by versions that defaulted to plaintext. A warning is logged on every open.
         */
        @JvmStatic
        @JvmOverloads
        fun unencrypted(
            walletId: String,
            walletDid: String,
            holderDid: String,
            walletDir: Path,
            statusResolver: WalletStatusResolver? = null,
        ): FileWallet = FileWallet(walletId, walletDid, holderDid, walletDir, null as SecretKeySpec?, statusResolver)

        private fun requireKey(
            encryptionKey: String?,
            walletId: String,
        ): String =
            encryptionKey ?: throw WalletException.WalletCreationFailed(
                reason =
                    "FileWallet requires an encryptionKey (Base64 of 16, 24 or 32 bytes) so credentials are " +
                        "encrypted at rest. To store plaintext deliberately, use FileWallet.unencrypted(...).",
                provider = "file",
                walletId = walletId,
            )

        /** Decodes Base64 key text given as ASCII bytes; [base64] is zeroed afterwards. */
        private fun keyFromBase64(
            base64: ByteArray,
            walletId: String,
        ): SecretKeySpec {
            val keyBytes =
                try {
                    Base64.getDecoder().decode(base64)
                } catch (e: IllegalArgumentException) {
                    throw WalletException.WalletCreationFailed(
                        reason = "encryptionKey must be valid Base64-encoded AES key material",
                        provider = "file",
                        walletId = walletId,
                    )
                } finally {
                    base64.fill(0)
                }
            return try {
                keyFromBytes(keyBytes, walletId)
            } finally {
                keyBytes.fill(0)
            }
        }

        private fun keyFromBase64Chars(
            base64: CharArray,
            walletId: String,
        ): SecretKeySpec = keyFromBase64(ByteArray(base64.size) { base64[it].code.toByte() }, walletId)

        /** Validates the length and copies the key into a [SecretKeySpec] (which keeps its own copy). */
        private fun keyFromBytes(
            keyBytes: ByteArray,
            walletId: String,
        ): SecretKeySpec {
            if (keyBytes.size !in VALID_AES_KEY_LENGTHS) {
                throw WalletException.WalletCreationFailed(
                    reason =
                        "encryptionKey must decode to 16, 24, or 32 bytes (AES-128/192/256), " +
                            "but decoded to ${keyBytes.size} bytes",
                    provider = "file",
                    walletId = walletId,
                )
            }
            return SecretKeySpec(keyBytes, "AES")
        }

        private val ioLocks =
            Array(64) {
                java.util.concurrent.locks
                    .ReentrantLock()
            }
        private val logger = LoggerFactory.getLogger(FileWallet::class.java)

        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"

        /** Legacy blob: AES-GCM without additional authenticated data. Read-only. */
        private const val FORMAT_VERSION_LEGACY: Byte = 1

        /** Current blob: AES-GCM with the record's wallet, kind and credential-id hash as AAD. */
        private const val FORMAT_VERSION: Byte = 2
        private const val KIND_CREDENTIAL = "credential"
        private const val KIND_METADATA = "metadata"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private val VALID_AES_KEY_LENGTHS = setOf(16, 24, 32)
    }

    private val secureRandom = SecureRandom()

    private val json =
        Json {
            prettyPrint = false
            encodeDefaults = false
            ignoreUnknownKeys = true
        }

    private val credentialsDir: Path = normalizedWalletDir.resolve("credentials")
    private val collectionsDir: Path = normalizedWalletDir.resolve("collections")
    private val metadataDir: Path = normalizedWalletDir.resolve("metadata")
    private val tagsDir: Path = normalizedWalletDir.resolve("tags")

    init {
        // Initialize directory structure
        initializeDirectories()
        if (secretKey == null) {
            logger.warn(
                "FileWallet '{}' was opened with FileWallet.unencrypted(): credentials are stored " +
                    "in PLAINTEXT under {}. Use an encryptionKey for anything but tests and demos.",
                walletId,
                walletDir,
            )
        }
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

    /**
     * Resolve the file used to store data for [credentialId] inside [dir].
     *
     * The filename is the SHA-256 hex digest of the credential id, so untrusted ids
     * (e.g. containing `../` or absolute paths) can never select a path outside the
     * wallet directory. As defense-in-depth the normalized result is verified to be
     * inside [dir]; escaping paths throw [WalletException.StorageError].
     */
    private fun resolveDataFile(
        dir: Path,
        credentialId: String,
    ): Path {
        val fileName = sha256Hex(credentialId) + ".json"
        val normalizedDir = dir.toAbsolutePath().normalize()
        val resolved = normalizedDir.resolve(fileName).normalize()
        if (!resolved.startsWith(normalizedDir)) {
            throw WalletException.StorageError(
                operation = "resolvePath",
                reason = "Resolved credential file path escapes the wallet directory: $resolved",
            )
        }
        return resolved
    }

    private fun sha256Hex(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    // CredentialStorage implementation
    override suspend fun store(credential: VerifiableCredential): String =
        withContext(Dispatchers.IO) {
            val credentialJson = json.encodeToString(VerifiableCredential.serializer(), credential)
            val id = credential.id?.value ?: "urn:trustweave:stored:${sha256Hex(credentialJson)}"

            val credentialFile = resolveDataFile(credentialsDir, id)
            val metadataFile = resolveDataFile(metadataDir, id)
            val recordHash = sha256Hex(id)
            withRecordLocks(credentialFile, metadataFile) {
                val content =
                    if (secretKey != null) {
                        encrypt(credentialJson, aad(KIND_CREDENTIAL, recordHash))
                    } else {
                        credentialJson.toByteArray(Charsets.UTF_8)
                    }

                // Initialize metadata if not exists. The sidecar embeds the raw credential id
                // (ids can be PII-bearing URNs), so it is protected with the same AES-GCM
                // scheme as the credential file whenever an encryption key is configured.
                //
                // Order: sidecar first, credential second. A crash between the two leaves at worst an
                // orphan sidecar, which nothing reads (records are enumerated from the credential
                // files) and which the next store of the same id reuses; the reverse order would leave a
                // credential without a sidecar, which makes listing fail. A failure of the credential
                // write rolls back a sidecar created by THIS call, so a failed store leaves nothing behind.
                var createdMetadata = false
                if (!Files.exists(metadataFile)) {
                    val metadata =
                        buildJsonObject {
                            put("credentialId", id)
                            put("createdAt", Clock.System.now().toString())
                            put("updatedAt", Clock.System.now().toString())
                            put("notes", JsonNull)
                            put("tags", buildJsonArray { })
                            put("metadata", buildJsonObject { })
                        }
                    val metadataJson = json.encodeToString(JsonObject.serializer(), metadata)
                    val metadataContent =
                        if (secretKey != null) {
                            encrypt(metadataJson, aad(KIND_METADATA, recordHash))
                        } else {
                            metadataJson.toByteArray(Charsets.UTF_8)
                        }
                    atomicWrite(metadataFile, metadataContent)
                    createdMetadata = true
                }

                try {
                    atomicWrite(credentialFile, content)
                } catch (e: Throwable) {
                    if (createdMetadata) {
                        try {
                            Files.deleteIfExists(metadataFile)
                        } catch (cleanup: Exception) {
                            e.addSuppressed(cleanup)
                        }
                    }
                    throw e
                }
                id
            }
        }

    override suspend fun get(credentialId: String): VerifiableCredential? =
        withContext(Dispatchers.IO) {
            val credentialFile = resolveDataFile(credentialsDir, credentialId)
            if (!Files.exists(credentialFile)) {
                return@withContext null
            }

            val content = readBytes(credentialFile)
            val credentialJson =
                if (secretKey != null) {
                    decrypt(content, aad(KIND_CREDENTIAL, sha256Hex(credentialId)))
                } else {
                    String(content, Charsets.UTF_8)
                }

            json.decodeFromString(VerifiableCredential.serializer(), credentialJson)
        }

    override suspend fun list(filter: CredentialFilter?): List<VerifiableCredential> = listRecords(filter).map { it.credential }

    override suspend fun delete(credentialId: String): Boolean =
        withContext(Dispatchers.IO) {
            val credentialFile = resolveDataFile(credentialsDir, credentialId)
            withRecordLocks(credentialFile, resolveDataFile(metadataDir, credentialId)) {
                val deleted = Files.deleteIfExists(credentialFile)

                if (deleted) {
                    // Clean up related files
                    Files.deleteIfExists(resolveDataFile(metadataDir, credentialId))
                    Files.deleteIfExists(resolveDataFile(tagsDir, credentialId))
                }

                deleted
            }
        }

    override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> =
        withContext(Dispatchers.IO) {
            val builder = CredentialQueryBuilder()
            builder.query()

            // FileWallet stores no queryable tag/collection data, so byTag/byCollection
            // cannot be honored. Failing loudly is required by the CredentialQueryBuilder
            // contract — silently returning unfiltered credentials would feed wrong
            // candidates into presentation selection.
            if (builder.requestedTags.isNotEmpty() || builder.requestedCollections.isNotEmpty()) {
                throw UnsupportedOperationException(
                    "FileWallet does not support byTag/byCollection query filters " +
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
    private suspend fun matchesFilter(
        credential: VerifiableCredential,
        filter: CredentialFilter,
    ): Boolean {
        if (filter.issuer != null && credential.issuer.id.value != filter.issuer) return false
        if (filter.type != null) {
            val filterTypes = filter.type
            if (filterTypes != null && !filterTypes.any { type -> credential.type.any { ct -> ct.value == type } }) return false
        }
        if (filter.subjectId != null) {
            val subjectId = credential.credentialSubject.id?.value
            if (subjectId != filter.subjectId) return false
        }
        if (filter.expired != null) {
            val isExpired =
                (credential.validUntil ?: credential.expirationDate)?.let {
                    Clock.System.now() > it
                } ?: false
            if (isExpired != filter.expired) return false
        }
        if (filter.hasStatusEntry != null && (credential.credentialStatus != null) != filter.hasStatusEntry) return false
        if (filter.revoked != null) {
            val status = resolveStoredStatus(credential, statusResolver)
            if (status == StoredCredentialStatus.UNKNOWN) return false
            if ((status == StoredCredentialStatus.REVOKED) != filter.revoked) return false
        }
        return true
    }

    private inline fun <T> withRecordLocks(
        credential: Path,
        metadata: Path,
        block: () -> T,
    ): T {
        val indexes =
            listOf(credential, metadata)
                .map {
                    (it.toAbsolutePath().normalize().hashCode() and Int.MAX_VALUE) % ioLocks.size
                }.distinct()
                .sorted()
        indexes.forEach { ioLocks[it].lock() }
        try {
            return block()
        } finally {
            indexes.asReversed().forEach { ioLocks[it].unlock() }
        }
    }

    private fun ioLock(path: Path) = ioLocks[(path.toAbsolutePath().normalize().hashCode() and Int.MAX_VALUE) % ioLocks.size]

    private fun readBytes(path: Path): ByteArray = ioLock(path).withLock { Files.readAllBytes(path) }

    /** Atomically replace a record. Unsupported filesystems fail without truncating the old record. */
    private fun atomicWrite(
        target: Path,
        content: ByteArray,
    ) {
        val temporary = Files.createTempFile(target.parent, ".wallet-", ".tmp")
        try {
            java.nio.channels.FileChannel.open(temporary, StandardOpenOption.WRITE).use { channel ->
                val bytes = java.nio.ByteBuffer.wrap(content)
                while (bytes.hasRemaining()) channel.write(bytes)
                channel.force(true)
            }
            ioLock(target).withLock {
                // Windows indexers/antivirus may briefly hold a handle without delete sharing.
                // Retry only access-denied, with a fixed bound; never fall back to truncating writes.
                for (attempt in 0..8) {
                    try {
                        Files.move(
                            temporary,
                            target,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        )
                        break
                    } catch (error: java.nio.file.AccessDeniedException) {
                        if (attempt == 8) throw error
                        Thread.sleep(25L * (attempt + 1))
                    }
                }
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun recordPaths(): List<Path> =
        Files.list(credentialsDir).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".json") }.sorted().toList()
        }

    private fun readRecord(path: Path): StoredCredentialRecord =
        withRecordLocks(path, metadataDir.resolve(path.fileName)) {
            // The record's identity is its file name (the SHA-256 of the credential id); it is the AAD
            // of both blobs, so a record moved or swapped under another name fails authentication.
            val recordHash = path.fileName.toString().removeSuffix(".json")

            fun decode(
                bytes: ByteArray,
                kind: String,
            ) = if (secretKey != null) decrypt(bytes, aad(kind, recordHash)) else String(bytes, Charsets.UTF_8)
            val credential = json.decodeFromString(VerifiableCredential.serializer(), decode(readBytes(path), KIND_CREDENTIAL))
            val metadata =
                json
                    .parseToJsonElement(decode(readBytes(metadataDir.resolve(path.fileName)), KIND_METADATA))
                    .jsonObject
            val handle = metadata.getValue("credentialId").jsonPrimitive.content
            check(resolveDataFile(credentialsDir, handle) == path) { "Metadata handle does not match credential file" }
            StoredCredentialRecord(handle, credential)
        }

    override suspend fun listRecords(filter: CredentialFilter?): List<StoredCredentialRecord> =
        withContext(Dispatchers.IO) {
            recordPaths().map { readRecord(it) }.filter { filter == null || matchesFilter(it.credential, filter) }
        }

    override suspend fun recoverRecords(): CredentialRecoveryResult =
        withContext(Dispatchers.IO) {
            val records = mutableListOf<StoredCredentialRecord>()
            val failures = mutableListOf<CredentialReadFailure>()
            for (path in recordPaths()) {
                try {
                    records.add(readRecord(path))
                } catch (
                    error: CancellationException,
                ) {
                    throw error
                } catch (
                    error: Exception,
                ) {
                    failures.add(CredentialReadFailure(path.fileName.toString(), error.javaClass.simpleName))
                }
            }
            CredentialRecoveryResult(records, failures)
        }

    override suspend fun getStatistics(): WalletStatistics =
        withContext(Dispatchers.IO) {
            val credentials = list(null)
            val statuses = credentials.map { resolveStoredStatus(it, statusResolver) }
            val now = Clock.System.now()
            WalletStatistics(
                totalCredentials = credentials.size,
                validCredentials =
                    credentials.indices.count { i ->
                        credentials[i].proof != null &&
                            statuses[i] == StoredCredentialStatus.ACTIVE &&
                            (credentials[i].expirationDate ?: credentials[i].validUntil)?.let { it > now } != false
                    },
                expiredCredentials = credentials.count { (it.expirationDate ?: it.validUntil)?.let { expiry -> expiry <= now } == true },
                revokedCredentials = statuses.count { it == StoredCredentialStatus.REVOKED },
                unknownStatusCredentials = statuses.count { it == StoredCredentialStatus.UNKNOWN },
            )
        }

    /** AAD of one record: wallet scope, record kind and the SHA-256 hex of the credential id. */
    private fun aad(
        kind: String,
        recordHash: String,
    ): ByteArray = "trustweave-filewallet/v2\u0000$walletId\u0000$kind\u0000$recordHash".toByteArray(Charsets.UTF_8)

    /**
     * Encrypt data using AES/GCM/NoPadding with a fresh random 12-byte IV, authenticating [aad].
     *
     * Output layout: `[1-byte format version = 2][12-byte IV][ciphertext + 128-bit tag]`.
     */
    private fun encrypt(
        data: String,
        aad: ByteArray,
    ): ByteArray {
        val key =
            secretKey
                ?: throw IllegalStateException("Encryption key not provided")

        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        cipher.updateAAD(aad)
        val ciphertext = cipher.doFinal(data.toByteArray(Charsets.UTF_8))

        val blob = ByteArray(1 + iv.size + ciphertext.size)
        blob[0] = FORMAT_VERSION
        iv.copyInto(blob, destinationOffset = 1)
        ciphertext.copyInto(blob, destinationOffset = 1 + iv.size)
        return blob
    }

    /**
     * Decrypt an AES-GCM blob produced by [encrypt] (version 2, [aad] authenticated) or by an earlier
     * version (version 1, no AAD). The version byte alone selects the scheme: a version-2 blob is
     * never tried without its AAD, and a blob without a known version marker is rejected.
     *
     * Tampered, swapped or malformed data fails GCM authentication and surfaces as a
     * [WalletException.StorageError] — corrupted plaintext is never returned.
     */
    private fun decrypt(
        encryptedData: ByteArray,
        aad: ByteArray,
    ): String {
        val key =
            secretKey
                ?: throw IllegalStateException("Encryption key not provided")

        val minLength = 1 + GCM_IV_LENGTH_BYTES + GCM_TAG_LENGTH_BITS / 8
        val version = encryptedData.firstOrNull()
        if (encryptedData.size < minLength || (version != FORMAT_VERSION && version != FORMAT_VERSION_LEGACY)) {
            throw WalletException.StorageError(
                operation = "decrypt",
                reason =
                    "Credential file is not in the expected AES-GCM format (version $FORMAT_VERSION); " +
                        "it may be corrupted, tampered with, or written by an older FileWallet version",
            )
        }

        val iv = encryptedData.copyOfRange(1, 1 + GCM_IV_LENGTH_BYTES)
        val ciphertext = encryptedData.copyOfRange(1 + GCM_IV_LENGTH_BYTES, encryptedData.size)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        if (version == FORMAT_VERSION) cipher.updateAAD(aad)

        val decrypted =
            try {
                cipher.doFinal(ciphertext)
            } catch (e: GeneralSecurityException) {
                throw WalletException.StorageError(
                    operation = "decrypt",
                    reason = "Credential decryption failed: data is corrupted, tampered with, or does not belong to this record",
                    cause = e,
                )
            }
        return String(decrypted, Charsets.UTF_8)
    }
}
