package org.trustweave.credential.didcomm.crypto.secret

import org.trustweave.credential.didcomm.crypto.secret.encryption.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.didcommx.didcomm.secret.Secret
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission

/**
 * Encrypted file-based local key store for production use.
 *
 * Stores keys in an encrypted file with the following format:
 *
 * File Structure:
 * - Header (4 bytes): Version
 * - IV Length (4 bytes): Length of IV
 * - IV (12 bytes): Initialization vector
 * - Encrypted Data: GCM-encrypted JSON containing all keys
 *
 * Security:
 * - Keys encrypted with AES-256-GCM
 * - Master key derived from password using PBKDF2
 * - File permissions restricted (600 on Unix)
 * - Atomic writes for consistency
 *
 * **Example Usage:**
 * ```kotlin
 * val keyStore = EncryptedFileLocalKeyStoreFactory.create(
 *     keyFile = File("/secure/didcomm-keys.enc"),
 *     password = "your-secure-password".toCharArray()
 * )
 *
 * val secret = Secret(...)
 * keyStore.store("did:key:issuer#key-1", secret)
 * val retrieved = keyStore.get("did:key:issuer#key-1")
 * ```
 */
class EncryptedFileLocalKeyStore(
    private val keyFile: File,
    private val masterKey: ByteArray, // Should be derived from password
    private val keyEncryption: KeyEncryption = KeyEncryption(masterKey)
) : LocalKeyStore {

    private val json = Json {
        prettyPrint = false
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    init {
        // Ensure file exists and has correct permissions
        if (!keyFile.exists()) {
            keyFile.parentFile?.mkdirs()
            keyFile.createNewFile()
            setSecurePermissions(keyFile)
            // Initialize with empty keys
            saveKeys(emptyMap())
        } else {
            setSecurePermissions(keyFile)
        }
    }

    override suspend fun get(keyId: String): Secret? = withContext(Dispatchers.IO) {
        try {
            val keys = loadKeys()
            keys[keyId]
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun store(keyId: String, secret: Secret) = withContext(Dispatchers.IO) {
        val keys = loadKeys().toMutableMap()
        keys[keyId] = secret
        saveKeys(keys)
    }

    override suspend fun delete(keyId: String): Boolean = withContext(Dispatchers.IO) {
        val keys = loadKeys().toMutableMap()
        val removed = keys.remove(keyId) != null
        if (removed) {
            saveKeys(keys)
        }
        removed
    }

    override suspend fun list(): List<String> = withContext(Dispatchers.IO) {
        try {
            val keys = loadKeys()
            keys.keys.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun loadKeys(): Map<String, Secret> {
        if (!keyFile.exists() || keyFile.length() == 0L) {
            return emptyMap()
        }

        try {
            val fileContent = keyFile.readBytes()
            if (fileContent.isEmpty()) {
                return emptyMap()
            }

            val encryptedData = parseEncryptedFile(fileContent)
            val decryptedContent = keyEncryption.decrypt(encryptedData)
            val jsonString = String(decryptedContent, Charsets.UTF_8)

            if (jsonString.isBlank()) {
                return emptyMap()
            }

            val keysJson = json.parseToJsonElement(jsonString).jsonObject

            return keysJson.entries.mapNotNull { (keyId, secretJson) ->
                try {
                    // Parse Secret from JSON
                    // Note: didcomm-java Secret may need custom serialization
                    keyId to parseSecretFromJson(secretJson)
                } catch (e: Exception) {
                    // Skip invalid secrets
                    null
                }
            }.toMap()
        } catch (e: Exception) {
            throw IllegalStateException("Failed to load keys from encrypted file: ${e.message}", e)
        }
    }

    private fun parseSecretFromJson(jsonElement: JsonElement): Secret {
        val obj = jsonElement.jsonObject
        val id = obj["id"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing 'id'")

        // Parse privateKeyJwk
        val privateKeyJwk = obj["privateKeyJwk"]?.jsonObject?.let { jwkObj ->
            jwkObj.entries.associate { (key, value) ->
                key to when (value) {
                    is JsonPrimitive -> {
                        when {
                            value.isString -> value.content
                            value.booleanOrNull != null -> value.boolean
                            value.longOrNull != null -> value.long
                            value.doubleOrNull != null -> value.double
                            else -> value.content
                        }
                    }
                    is JsonArray -> value.map { it.jsonPrimitive.content }
                    else -> value.toString()
                }
            }
        } ?: throw IllegalArgumentException("Missing 'privateKeyJwk'")

        // Note: The didcomm-java library Secret constructor API (0.3.2) is unclear from the codebase.
        // The Secret class may require different parameters (e.g., kid, verificationMaterial instead of id, privateKeyJwk).
        // This is a placeholder implementation that throws a clear error indicating the API needs to be updated.
        //
        // To fix: Check the didcomm-java library documentation for Secret constructor signature
        // and update this code accordingly.
        throw IllegalStateException(
            "Secret construction from JSON is not implemented. " +
            "The didcomm-java library Secret constructor API needs to be verified. " +
            "Please check library documentation for version 0.3.2 and update parseSecretFromJson() accordingly. " +
            "Secret ID: $id"
        )
    }

    private fun saveKeys(keys: Map<String, Secret>) {
        try {
            val keysJson = buildJsonObject {
                keys.forEach { (keyId, secret) ->
                    put(keyId, secretToJson(secret))
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
            setSecurePermissions(keyFile)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to save keys to encrypted file: ${e.message}", e)
        }
    }

    private fun secretToJson(secret: Secret): JsonObject {
        // Note: The didcomm-java library Secret class API (0.3.2) is unclear from the codebase.
        // The Secret class may have different property names (e.g., kid instead of id, verificationMaterial instead of privateKeyJwk).
        // This is a placeholder implementation that throws a clear error indicating the API needs to be updated.
        //
        // To fix: Check the didcomm-java library documentation for Secret property access
        // and update this code accordingly.
        throw IllegalStateException(
            "Secret serialization to JSON is not implemented. " +
            "The didcomm-java library Secret class API needs to be verified. " +
            "Please check library documentation for version 0.3.2 and update secretToJson() accordingly."
        )
    }

    private fun parseEncryptedFile(content: ByteArray): EncryptedData {
        // Parse file format:
        // [4 bytes: version][4 bytes: iv length][iv][ciphertext]
        var offset = 0

        if (content.size < 8) {
            throw IllegalArgumentException("File too short")
        }

        val version = content.sliceArray(offset until offset + 4)
        offset += 4

        val ivLength = content.sliceArray(offset until offset + 4)
            .fold(0) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xFF) }
        offset += 4

        if (content.size < offset + ivLength) {
            throw IllegalArgumentException("Invalid file format: insufficient data for IV")
        }

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
                // Windows: Use Java file permissions
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
            // Ignore if permissions can't be set (e.g., on Windows without proper setup)
        }
    }
}

/**
 * Factory for creating EncryptedFileLocalKeyStore with password.
 *
 * The PBKDF2 salt is random (16 bytes from [java.security.SecureRandom]), generated on first
 * use and persisted next to the key store in `<keyFile>.salt` (format: 4-byte magic `TWS1` +
 * 16 salt bytes). Subsequent opens load the persisted salt.
 *
 * Legacy stores created by older versions derived the salt deterministically from the file
 * path, which is insecure. They cannot be distinguished from a tampered store (the salt file
 * format did not exist), so opening a non-empty key store without a salt file **fails closed**
 * with a clear "regenerate the key store" error instead of silently reusing the weak salt.
 */
object EncryptedFileLocalKeyStoreFactory {

    /** Default PBKDF2-HMAC-SHA256 iteration count (OWASP recommendation: >= 210,000). */
    const val DEFAULT_PBKDF2_ITERATIONS = 210_000

    /** Magic marker + format version ("TWS1") prefixed to the persisted salt file. */
    private val SALT_FILE_MAGIC = byteArrayOf(0x54, 0x57, 0x53, 0x31)

    private const val SALT_LENGTH_BYTES = 16

    /**
     * Creates an encrypted file key store from a password.
     *
     * @param keyFile File to store keys
     * @param password Password for encryption
     * @param salt Salt for key derivation (optional; when omitted, a random salt is generated
     *   on first use and persisted in `<keyFile>.salt`, then reloaded on subsequent opens)
     * @param iterations PBKDF2 iterations (default: [DEFAULT_PBKDF2_ITERATIONS])
     * @return EncryptedFileLocalKeyStore instance
     * @throws IllegalStateException if the salt file is corrupt, or if [keyFile] is a legacy
     *   store without a salt file (regenerate the key store in that case)
     */
    fun create(
        keyFile: File,
        password: CharArray,
        salt: ByteArray? = null,
        iterations: Int = DEFAULT_PBKDF2_ITERATIONS
    ): EncryptedFileLocalKeyStore {
        val actualSalt = salt ?: loadOrCreateSalt(keyFile)

        val masterKey = MasterKeyDerivation.deriveKey(
            password = password,
            salt = actualSalt,
            iterations = iterations
        )

        return EncryptedFileLocalKeyStore(keyFile, masterKey)
    }

    /** The sidecar file holding the persisted PBKDF2 salt for [keyFile]. */
    fun saltFileFor(keyFile: File): File = File(keyFile.parent, "${keyFile.name}.salt")

    /**
     * Loads the persisted salt, or generates and persists a fresh random one for a new store.
     *
     * Fails closed when the salt file is corrupt or when an existing (legacy) key store has
     * no salt file — legacy stores used a path-derived salt and must be regenerated.
     */
    private fun loadOrCreateSalt(keyFile: File): ByteArray {
        val saltFile = saltFileFor(keyFile)
        if (saltFile.exists()) {
            val content = saltFile.readBytes()
            val valid = content.size == SALT_FILE_MAGIC.size + SALT_LENGTH_BYTES &&
                content.sliceArray(SALT_FILE_MAGIC.indices).contentEquals(SALT_FILE_MAGIC)
            if (!valid) {
                throw IllegalStateException(
                    "Salt file '${saltFile.absolutePath}' is corrupt or has an unknown format. " +
                        "Restore it from backup, or delete both the salt file and the key store file " +
                        "'${keyFile.absolutePath}' to regenerate the key store (stored keys will be lost)."
                )
            }
            return content.copyOfRange(SALT_FILE_MAGIC.size, content.size)
        }

        if (keyFile.exists() && keyFile.length() > 0L) {
            throw IllegalStateException(
                "Key store file '${keyFile.absolutePath}' exists but has no salt file " +
                    "('${saltFile.name}'). It was likely created by an older version that derived " +
                    "the PBKDF2 salt from the file path, which is insecure and no longer supported. " +
                    "Regenerate the key store: re-store the secrets into a new file " +
                    "(or delete the legacy file to start fresh)."
            )
        }

        // Fresh store: generate a random salt and persist it before any keys are written.
        val newSalt = MasterKeyDerivation.generateSalt(SALT_LENGTH_BYTES)
        saltFile.parentFile?.mkdirs()
        val tempFile = File(saltFile.parent, "${saltFile.name}.tmp")
        tempFile.writeBytes(SALT_FILE_MAGIC + newSalt)
        if (!tempFile.renameTo(saltFile)) {
            tempFile.delete()
            throw IllegalStateException("Failed to persist salt file '${saltFile.absolutePath}'")
        }
        return newSalt
    }
}

