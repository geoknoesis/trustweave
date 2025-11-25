package com.trustweave.credential.didcomm.crypto.secret.encryption

import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encrypts/decrypts keys using AES-256-GCM.
 * 
 * Provides secure encryption for local key storage.
 * Uses AES-256-GCM for authenticated encryption.
 */
class KeyEncryption(
    private val masterKey: ByteArray // Derived from user password/master key
) {
    private val algorithm = "AES/GCM/NoPadding"
    private val keyLength = 256
    private val ivLength = 12 // 96 bits for GCM
    private val tagLength = 128 // 16 bytes
    
    /**
     * Encrypts plaintext data.
     * 
     * @param plaintext Data to encrypt
     * @return Encrypted data with IV
     */
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
    
    /**
     * Decrypts encrypted data.
     * 
     * @param encrypted Encrypted data with IV
     * @return Decrypted plaintext
     */
    fun decrypt(encrypted: EncryptedData): ByteArray {
        val secretKey = SecretKeySpec(masterKey, "AES")
        val parameterSpec = GCMParameterSpec(tagLength, encrypted.iv)
        
        val cipher = Cipher.getInstance(algorithm)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)
        
        return cipher.doFinal(encrypted.ciphertext)
    }
}

/**
 * Encrypted data structure.
 */
data class EncryptedData(
    val iv: ByteArray,
    val ciphertext: ByteArray,
    val algorithm: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as EncryptedData
        
        if (!iv.contentEquals(other.iv)) return false
        if (!ciphertext.contentEquals(other.ciphertext)) return false
        if (algorithm != other.algorithm) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = iv.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        result = 31 * result + algorithm.hashCode()
        return result
    }
}

/**
 * Derives master key from password using PBKDF2.
 * 
 * Uses PBKDF2 with HMAC-SHA256 for key derivation.
 * Recommended iterations: 100,000+ for production.
 */
object MasterKeyDerivation {
    /**
     * Derives a master key from a password.
     * 
     * @param password Password to derive key from
     * @param salt Salt for key derivation (should be random, 16+ bytes)
     * @param iterations Number of PBKDF2 iterations (recommended: 100,000+)
     * @return Derived key (256 bits / 32 bytes)
     */
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
    
    /**
     * Generates a random salt for key derivation.
     * 
     * @param length Salt length in bytes (default: 16)
     * @return Random salt
     */
    fun generateSalt(length: Int = 16): ByteArray {
        val salt = ByteArray(length)
        SecureRandom().nextBytes(salt)
        return salt
    }
}

