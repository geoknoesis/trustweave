package org.trustweave.wallet.file

import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.wallet.*
import org.trustweave.wallet.exception.WalletException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.datetime.Clock
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

/**
 * File-based wallet implementation.
 *
 * Stores credentials, collections, tags, and metadata in local filesystem.
 * Supports optional encryption for sensitive data.
 *
 * **Encryption format:** credentials are encrypted with AES/GCM/NoPadding using a
 * random 12-byte IV per write and a 128-bit authentication tag. The on-disk blob is
 * `[1-byte format version][12-byte IV][ciphertext + tag]`. Any tampering with the
 * stored bytes is detected during decryption and surfaces as a
 * [WalletException.StorageError] instead of returning corrupted data.
 *
 * **Key material:** [encryptionKey] must be a Base64-encoded AES key that decodes to
 * exactly 16, 24, or 32 bytes (AES-128/192/256). Invalid keys are rejected at
 * construction time. When no key is provided, credentials are stored in **plaintext**
 * and a warning is logged — provide a key for any sensitive deployment.
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
 *     encryptionKey = "base64-encoded-16/24/32-byte-key" // optional but strongly recommended
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

    private companion object {
        private val logger = LoggerFactory.getLogger(FileWallet::class.java)

        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val FORMAT_VERSION: Byte = 1
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private val VALID_AES_KEY_LENGTHS = setOf(16, 24, 32)
    }

    private val secureRandom = SecureRandom()

    /**
     * AES key derived from [encryptionKey], validated at construction.
     * Null means plaintext storage (legacy/default behavior — discouraged).
     */
    private val secretKey: SecretKeySpec? = encryptionKey?.let { key ->
        val keyBytes = try {
            Base64.getDecoder().decode(key)
        } catch (e: IllegalArgumentException) {
            throw WalletException.WalletCreationFailed(
                reason = "encryptionKey must be valid Base64-encoded AES key material",
                provider = "file",
                walletId = walletId
            )
        }
        if (keyBytes.size !in VALID_AES_KEY_LENGTHS) {
            throw WalletException.WalletCreationFailed(
                reason = "encryptionKey must decode to 16, 24, or 32 bytes (AES-128/192/256), but decoded to ${keyBytes.size} bytes",
                provider = "file",
                walletId = walletId
            )
        }
        SecretKeySpec(keyBytes, "AES")
    }

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
        if (secretKey == null) {
            logger.warn(
                "FileWallet '{}' was created WITHOUT an encryption key: credentials will be stored " +
                    "in plaintext under {}. Provide a Base64-encoded 16/24/32-byte 'encryptionKey' " +
                    "to enable AES-GCM encryption at rest.",
                walletId,
                walletDir
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
    private fun resolveDataFile(dir: Path, credentialId: String): Path {
        val fileName = sha256Hex(credentialId) + ".json"
        val normalizedDir = dir.toAbsolutePath().normalize()
        val resolved = normalizedDir.resolve(fileName).normalize()
        if (!resolved.startsWith(normalizedDir)) {
            throw WalletException.StorageError(
                operation = "resolvePath",
                reason = "Resolved credential file path escapes the wallet directory: $resolved"
            )
        }
        return resolved
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    // CredentialStorage implementation
    override suspend fun store(credential: VerifiableCredential): String = withContext(Dispatchers.IO) {
        val id = credential.id?.value ?: UUID.randomUUID().toString()
        val credentialJson = json.encodeToString(VerifiableCredential.serializer(), credential)

        val credentialFile = resolveDataFile(credentialsDir, id)
        val content = if (secretKey != null) {
            encrypt(credentialJson)
        } else {
            credentialJson.toByteArray(Charsets.UTF_8)
        }

        Files.write(credentialFile, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)

        // Initialize metadata if not exists
        val metadataFile = resolveDataFile(metadataDir, id)
        if (!Files.exists(metadataFile)) {
            val metadata = buildJsonObject {
                put("credentialId", id)
                put("createdAt", Clock.System.now().toString())
                put("updatedAt", Clock.System.now().toString())
                put("notes", JsonNull)
                put("tags", buildJsonArray { })
                put("metadata", buildJsonObject { })
            }
            Files.write(metadataFile, json.encodeToString(JsonObject.serializer(), metadata).toByteArray(Charsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        }

        id
    }

    override suspend fun get(credentialId: String): VerifiableCredential? = withContext(Dispatchers.IO) {
        val credentialFile = resolveDataFile(credentialsDir, credentialId)
        if (!Files.exists(credentialFile)) {
            return@withContext null
        }

        val content = Files.readAllBytes(credentialFile)
        val credentialJson = if (secretKey != null) {
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
            stream.filter { it.fileName.toString().endsWith(".json") }
                .forEach { file ->
                    try {
                        // Filenames are SHA-256 digests of the credential id; the id itself
                        // is part of the (decrypted) JSON content, so read the file directly.
                        val content = Files.readAllBytes(file)
                        val credentialJson = if (secretKey != null) {
                            decrypt(content)
                        } else {
                            String(content, Charsets.UTF_8)
                        }
                        val credential = json.decodeFromString(VerifiableCredential.serializer(), credentialJson)
                        if (filter == null || matchesFilter(credential, filter)) {
                            credentials.add(credential)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Skip corrupted/unreadable files, but record why so failures are diagnosable.
                        logger.warn("Skipping credential file that could not be read: {}", file, e)
                    }
                }
        }

        credentials
    }

    override suspend fun delete(credentialId: String): Boolean = withContext(Dispatchers.IO) {
        val credentialFile = resolveDataFile(credentialsDir, credentialId)
        val deleted = Files.deleteIfExists(credentialFile)

        if (deleted) {
            // Clean up related files
            Files.deleteIfExists(resolveDataFile(metadataDir, credentialId))
            Files.deleteIfExists(resolveDataFile(tagsDir, credentialId))
        }

        deleted
    }

    override suspend fun query(query: CredentialQueryBuilder.() -> Unit): List<VerifiableCredential> = withContext(Dispatchers.IO) {
        val builder = CredentialQueryBuilder()
        builder.query()

        val predicate = builder.toPredicate()
        val allCredentials = list(null)
        allCredentials.filter(predicate)
    }

    /**
     * Check if credential matches filter criteria.
     */
    private fun matchesFilter(credential: VerifiableCredential, filter: CredentialFilter): Boolean {
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
            val isExpired = credential.expirationDate?.let {
                Clock.System.now() > it
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
        val now = Clock.System.now()

        WalletStatistics(
            totalCredentials = allCredentials.size,
            validCredentials = allCredentials.count { credential ->
                credential.proof != null &&
                (credential.expirationDate?.let { expirationDate ->
                    now < expirationDate
                } ?: true) &&
                credential.credentialStatus == null
            },
            expiredCredentials = allCredentials.count { credential ->
                credential.expirationDate?.let { expirationDate ->
                    now > expirationDate
                } ?: false
            },
            revokedCredentials = allCredentials.count { it.credentialStatus != null },
            collectionsCount = 0, // Would require collection implementation
            tagsCount = 0, // Would require tag implementation
            archivedCount = 0 // Would require archive implementation
        )
    }

    /**
     * Encrypt data using AES/GCM/NoPadding with a fresh random 12-byte IV.
     *
     * Output layout: `[1-byte format version][12-byte IV][ciphertext + 128-bit tag]`.
     */
    private fun encrypt(data: String): ByteArray {
        val key = secretKey
            ?: throw IllegalStateException("Encryption key not provided")

        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(data.toByteArray(Charsets.UTF_8))

        val blob = ByteArray(1 + iv.size + ciphertext.size)
        blob[0] = FORMAT_VERSION
        iv.copyInto(blob, destinationOffset = 1)
        ciphertext.copyInto(blob, destinationOffset = 1 + iv.size)
        return blob
    }

    /**
     * Decrypt an AES-GCM blob produced by [encrypt].
     *
     * Tampered or malformed data fails GCM authentication and surfaces as a
     * [WalletException.StorageError] — corrupted plaintext is never returned.
     */
    private fun decrypt(encryptedData: ByteArray): String {
        val key = secretKey
            ?: throw IllegalStateException("Encryption key not provided")

        val minLength = 1 + GCM_IV_LENGTH_BYTES + GCM_TAG_LENGTH_BITS / 8
        if (encryptedData.size < minLength || encryptedData[0] != FORMAT_VERSION) {
            throw WalletException.StorageError(
                operation = "decrypt",
                reason = "Credential file is not in the expected AES-GCM format (version $FORMAT_VERSION); " +
                    "it may be corrupted, tampered with, or written by an older FileWallet version"
            )
        }

        val iv = encryptedData.copyOfRange(1, 1 + GCM_IV_LENGTH_BYTES)
        val ciphertext = encryptedData.copyOfRange(1 + GCM_IV_LENGTH_BYTES, encryptedData.size)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        val decrypted = try {
            cipher.doFinal(ciphertext)
        } catch (e: GeneralSecurityException) {
            throw WalletException.StorageError(
                operation = "decrypt",
                reason = "Credential decryption failed: data is corrupted or has been tampered with",
                cause = e
            )
        }
        return String(decrypted, Charsets.UTF_8)
    }
}

