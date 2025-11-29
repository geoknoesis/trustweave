package com.trustweave.credential.didcomm.storage.encryption

import com.trustweave.credential.didcomm.models.DidCommMessage
import kotlinx.serialization.json.*
import java.security.SecureRandom
import javax.crypto.Cipher
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
}

/**
 * Encrypted message structure.
 */
data class EncryptedMessage(
    val keyVersion: Int,
    val encryptedData: ByteArray,
    val iv: ByteArray,
    val algorithm: String = "AES-256-GCM"
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
 */
class AesMessageEncryption(
    private val encryptionKey: ByteArray,
    private val keyVersion: Int = 1
) : MessageEncryption {

    private val algorithm = "AES/GCM/NoPadding"
    private val ivLength = 12 // 96 bits for GCM
    private val tagLength = 128 // 16 bytes
    private val json = Json {
        prettyPrint = false
        encodeDefaults = false
    }

    override suspend fun encrypt(message: DidCommMessage): EncryptedMessage {
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

