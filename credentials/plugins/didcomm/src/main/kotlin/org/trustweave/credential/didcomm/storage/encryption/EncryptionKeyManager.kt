package org.trustweave.credential.didcomm.storage.encryption

import java.util.concurrent.ConcurrentHashMap

/**
 * Manages encryption keys with rotation support.
 *
 * Provides key versioning and rotation capabilities for message encryption.
 *
 * **Example Usage:**
 * ```kotlin
 * val keyManager = InMemoryEncryptionKeyManager()
 * val currentKey = keyManager.getCurrentKey()
 * val newVersion = keyManager.rotateKey()
 * ```
 */
interface EncryptionKeyManager {
    /**
     * Gets the current encryption key.
     *
     * @return Current encryption key
     */
    suspend fun getCurrentKey(): ByteArray

    /**
     * Gets a specific key version.
     *
     * @param version Key version
     * @return Key, or null if version not found
     */
    suspend fun getKey(version: Int): ByteArray?

    /**
     * Rotates to a new encryption key.
     *
     * @return New key version
     */
    suspend fun rotateKey(): Int

    /**
     * Gets all available key versions.
     *
     * @return List of key versions
     */
    suspend fun getKeyVersions(): List<Int>
}

/**
 * In-memory implementation of EncryptionKeyManager.
 *
 * ⚠️ **WARNING**: Keys stored in memory. For production, use a secure
 * key management system (HSM, cloud KMS, etc.).
 */
class InMemoryEncryptionKeyManager(
    private val keyGenerator: () -> ByteArray = { generateRandomKey() }
) : EncryptionKeyManager {

    private val keys = ConcurrentHashMap<Int, ByteArray>()
    private var currentVersion = 1

    init {
        // Initialize with first key
        keys[1] = keyGenerator()
    }

    override suspend fun getCurrentKey(): ByteArray {
        return keys[currentVersion] ?: throw IllegalStateException("No current key available")
    }

    override suspend fun getKey(version: Int): ByteArray? {
        return keys[version]
    }

    override suspend fun rotateKey(): Int {
        val newVersion = currentVersion + 1
        keys[newVersion] = keyGenerator()
        currentVersion = newVersion
        return newVersion
    }

    override suspend fun getKeyVersions(): List<Int> {
        return keys.keys.sorted()
    }

    companion object {
        /**
         * Generates a random 256-bit (32-byte) key.
         */
        fun generateRandomKey(): ByteArray {
            val key = ByteArray(32) // 256 bits
            java.security.SecureRandom().nextBytes(key)
            return key
        }
    }
}

