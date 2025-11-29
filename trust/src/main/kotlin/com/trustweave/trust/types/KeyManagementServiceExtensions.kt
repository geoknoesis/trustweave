package com.trustweave.trust.types

import com.trustweave.kms.Algorithm
import com.trustweave.kms.KeyHandle
import com.trustweave.kms.KeyManagementService

/**
 * Type-safe extensions for KeyManagementService using KeyId.
 *
 * These extensions provide compile-time type safety for key identifiers,
 * preventing common errors like passing a DID where a KeyId is expected.
 *
 * **Example:**
 * ```kotlin
 * val keyId = KeyId("key-1")
 * val signature = kms.sign(keyId, data)
 * ```
 */

/**
 * Retrieves the public key information for a given key ID.
 *
 * @param keyId Type-safe key identifier
 * @return A KeyHandle containing the public key information
 * @throws com.trustweave.kms.KeyNotFoundException if the key does not exist
 */
suspend fun KeyManagementService.getPublicKey(keyId: KeyId): KeyHandle {
    return getPublicKey(keyId.value)
}

/**
 * Signs data using the specified key.
 *
 * @param keyId Type-safe key identifier
 * @param data The data to sign
 * @param algorithm Optional algorithm override (if null, uses the key's default algorithm)
 * @return The signature bytes
 * @throws com.trustweave.kms.KeyNotFoundException if the key does not exist
 */
suspend fun KeyManagementService.sign(
    keyId: KeyId,
    data: ByteArray,
    algorithm: Algorithm? = null
): ByteArray {
    return sign(keyId.value, data, algorithm)
}

/**
 * Signs data using the specified key by algorithm name.
 *
 * @param keyId Type-safe key identifier
 * @param data The data to sign
 * @param algorithmName Optional algorithm name override (if null, uses the key's default algorithm)
 * @return The signature bytes
 * @throws com.trustweave.kms.KeyNotFoundException if the key does not exist
 */
suspend fun KeyManagementService.sign(
    keyId: KeyId,
    data: ByteArray,
    algorithmName: String?
): ByteArray {
    return sign(keyId.value, data, algorithmName)
}

/**
 * Deletes a key from the key management service.
 *
 * @param keyId Type-safe key identifier
 * @return true if the key was deleted, false if it did not exist
 */
suspend fun KeyManagementService.deleteKey(keyId: KeyId): Boolean {
    return deleteKey(keyId.value)
}

