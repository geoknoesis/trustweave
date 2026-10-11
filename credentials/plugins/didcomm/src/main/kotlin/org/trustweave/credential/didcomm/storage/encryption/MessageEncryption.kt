package org.trustweave.credential.didcomm.storage.encryption

import kotlinx.serialization.json.Json
import org.trustweave.credential.didcomm.models.DidCommMessage
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts/decrypts DIDComm messages at rest.
 *
 * Supports:
 * - Full message encryption
 * - Field-level encryption (selective fields)
 * - Key rotation
 *
 * **Example Usage:**
 * ```kotlin
 * val encryptionKey = deriveEncryptionKey()
 * val encryption = AesMessageEncryption(encryptionKey, keyVersion = 1)
 *
 * val encrypted = encryption.encrypt(message)
 * val decrypted = encryption.decrypt(encrypted)
 * ```
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

    /**
     * Decrypts a message read from the row identified by [messageId]. Implementations that bind the
     * row id into the ciphertext (see [AesMessageEncryption]) verify it here; the default simply
     * decrypts and checks that the plaintext carries the same id, so a ciphertext copied onto
     * another row is rejected.
     */
    suspend fun decrypt(
        encrypted: EncryptedMessage,
        messageId: String,
    ): DidCommMessage {
        val message = decrypt(encrypted)
        require(message.id == messageId) { "Decrypted message id does not match the row it was read from" }
        return message
    }

    /**
     * A keyed, deterministic, non-reversible token for [value] under [purpose] (`"did"`, `"thread"`,
     * `"type"`), used so storage can look rows up by sender / recipient / thread without writing the
     * identifier itself in plaintext. Returns `null` (the default) when unsupported, in which case
     * storage leaves the indexed columns empty and lookups by those fields find nothing.
     */
    suspend fun blindIndex(
        purpose: String,
        value: String,
    ): String? = null
}

/**
 * Encrypted message structure.
 */
data class EncryptedMessage(
    val keyVersion: Int,
    val encryptedData: ByteArray,
    val iv: ByteArray,
    val algorithm: String = "AES-256-GCM",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedMessage

        if (keyVersion != other.keyVersion) return false
        if (!encryptedData.contentEquals(other.encryptedData)) return false
        if (!iv.contentEquals(other.iv)) return false
        if (algorithm != other.algorithm) return false

        return true
    }

    override fun hashCode(): Int {
        var result = keyVersion
        result = 31 * result + encryptedData.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + algorithm.hashCode()
        return result
    }
}

/**
 * AES-256-GCM implementation of message encryption.
 *
 * **Formats** (distinguished by the length of the stored `iv` field):
 * - legacy: `iv` is the 12-byte GCM nonce, no associated data. Still decrypted for existing rows.
 * - v1 (written now): `iv` is `0x01 || 12-byte nonce` and the GCM associated data is
 *   `"trustweave.didcomm.message.v1" || 0x00 || messageId || 0x00 || keyVersion`, so a ciphertext
 *   cannot be moved to another row or key version. v1 data must be decrypted through
 *   [decrypt] with the row id.
 */
class AesMessageEncryption(
    private val encryptionKey: ByteArray,
    private val keyVersion: Int = 1,
) : MessageEncryption {
    private val algorithm = "AES/GCM/NoPadding"
    private val ivLength = 12 // 96 bits for GCM
    private val tagLength = 128 // 16 bytes
    private val json =
        Json {
            prettyPrint = false
            encodeDefaults = false
        }

    private fun aad(
        messageId: String,
        version: Int,
    ): ByteArray = "trustweave.didcomm.message.v1\u0000$messageId\u0000$version".toByteArray(Charsets.UTF_8)

    override suspend fun encrypt(message: DidCommMessage): EncryptedMessage {
        val messageJson =
            json.encodeToString(
                DidCommMessage.serializer(),
                message,
            )
        val plaintext = messageJson.toByteArray(Charsets.UTF_8)

        val nonce =
            ByteArray(ivLength).apply {
                SecureRandom().nextBytes(this)
            }

        val secretKey = SecretKeySpec(encryptionKey, "AES")
        val parameterSpec = GCMParameterSpec(tagLength, nonce)

        val cipher = Cipher.getInstance(algorithm)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
        cipher.updateAAD(aad(message.id, keyVersion))

        val ciphertext = cipher.doFinal(plaintext)

        return EncryptedMessage(
            keyVersion = keyVersion,
            encryptedData = ciphertext,
            iv = byteArrayOf(FORMAT_V1) + nonce,
            algorithm = algorithm,
        )
    }

    override suspend fun decrypt(encrypted: EncryptedMessage): DidCommMessage {
        if (encrypted.iv.size != ivLength) {
            throw IllegalStateException(
                "This ciphertext is bound to its row id; decrypt it with decrypt(encrypted, messageId)",
            )
        }
        return decryptWith(encrypted.iv, encrypted, null)
    }

    override suspend fun decrypt(
        encrypted: EncryptedMessage,
        messageId: String,
    ): DidCommMessage {
        val message =
            if (encrypted.iv.size == ivLength + 1 && encrypted.iv[0] == FORMAT_V1) {
                decryptWith(encrypted.iv.copyOfRange(1, encrypted.iv.size), encrypted, aad(messageId, encrypted.keyVersion))
            } else {
                decryptWith(encrypted.iv, encrypted, null)
            }
        require(message.id == messageId) { "Decrypted message id does not match the row it was read from" }
        return message
    }

    private fun decryptWith(
        nonce: ByteArray,
        encrypted: EncryptedMessage,
        aad: ByteArray?,
    ): DidCommMessage {
        val secretKey = SecretKeySpec(encryptionKey, "AES")
        val parameterSpec = GCMParameterSpec(tagLength, nonce)

        val cipher = Cipher.getInstance(algorithm)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)
        if (aad != null) cipher.updateAAD(aad)

        val plaintext = cipher.doFinal(encrypted.encryptedData)
        val jsonString = String(plaintext, Charsets.UTF_8)

        val json = Json { ignoreUnknownKeys = true }
        return json.decodeFromString(DidCommMessage.serializer(), jsonString)
    }

    private val blindKey: ByteArray by lazy {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(encryptionKey, "HmacSHA256"))
        mac.doFinal("trustweave.didcomm.blind-index.v1".toByteArray(Charsets.UTF_8))
    }

    override suspend fun blindIndex(
        purpose: String,
        value: String,
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(blindKey, "HmacSHA256"))
        return mac
            .doFinal("$purpose\u0000$value".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    override suspend fun getKeyVersion(): Int = keyVersion

    private companion object {
        const val FORMAT_V1: Byte = 0x01
    }
}
