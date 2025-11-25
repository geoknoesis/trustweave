package com.trustweave.credential.storage.encryption

import kotlinx.serialization.Serializable
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts/decrypts protocol messages at rest.
 * 
 * Generic encryption interface that works with any serializable message type.
 * 
 * **Example Usage:**
 * ```kotlin
 * val encryptionKey = deriveEncryptionKey()
 * val encryption = AesMessageEncryption(encryptionKey, keyVersion = 1)
 * 
 * val encrypted = encryption.encrypt(messageJson)
 * val decrypted = encryption.decrypt(encrypted)
 * ```
 */
interface MessageEncryption {
    /**
     * Encrypts message data for storage.
     * 
     * @param data Message data as byte array
     * @return Encrypted message
     */
    suspend fun encrypt(data: ByteArray): EncryptedMessage
    
    /**
     * Decrypts message data from storage.
     * 
     * @param encrypted Encrypted message
     * @return Decrypted message data
     */
    suspend fun decrypt(encrypted: EncryptedMessage): ByteArray
    
    /**
     * Gets the current encryption key version.
     */
    suspend fun getKeyVersion(): Int
}

/**
 * Encrypted message structure.
 */
@Serializable
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
    
    override suspend fun encrypt(data: ByteArray): EncryptedMessage {
        val iv = ByteArray(ivLength).apply {
            SecureRandom().nextBytes(this)
        }
        
        val secretKey = SecretKeySpec(encryptionKey, "AES")
        val parameterSpec = GCMParameterSpec(tagLength, iv)
        
        val cipher = Cipher.getInstance(algorithm)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
        
        val ciphertext = cipher.doFinal(data)
        
        return EncryptedMessage(
            keyVersion = keyVersion,
            encryptedData = ciphertext,
            iv = iv,
            algorithm = algorithm
        )
    }
    
    override suspend fun decrypt(encrypted: EncryptedMessage): ByteArray {
        val secretKey = SecretKeySpec(encryptionKey, "AES")
        val parameterSpec = GCMParameterSpec(tagLength, encrypted.iv)
        
        val cipher = Cipher.getInstance(algorithm)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)
        
        return cipher.doFinal(encrypted.encryptedData)
    }
    
    override suspend fun getKeyVersion(): Int = keyVersion
}

