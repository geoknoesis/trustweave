package io.geoknoesis.vericore.kms

/**
 * Represents a key handle with metadata about the key.
 *
 * @param id Unique identifier for the key
 * @param algorithm The cryptographic algorithm (e.g., "Ed25519", "secp256k1")
 * @param publicKeyJwk Public key in JWK format (optional)
 * @param publicKeyMultibase Public key in multibase format (optional)
 */
data class KeyHandle(
    val id: String,
    val algorithm: String,
    val publicKeyJwk: Map<String, Any?>? = null,
    val publicKeyMultibase: String? = null
)

/**
 * Abstract interface for key management operations.
 * This interface is chain-agnostic and DID-method-agnostic.
 */
interface KeyManagementService {
    /**
     * Generates a new cryptographic key.
     *
     * @param algorithm The algorithm to use (e.g., "Ed25519", "secp256k1")
     * @param options Additional options for key generation
     * @return A KeyHandle for the newly generated key
     */
    suspend fun generateKey(
        algorithm: String,
        options: Map<String, Any?> = emptyMap()
    ): KeyHandle

    /**
     * Retrieves the public key information for a given key ID.
     *
     * @param keyId The identifier of the key
     * @return A KeyHandle containing the public key information
     * @throws KeyNotFoundException if the key does not exist
     */
    suspend fun getPublicKey(keyId: String): KeyHandle

    /**
     * Signs data using the specified key.
     *
     * @param keyId The identifier of the key to use for signing
     * @param data The data to sign
     * @param algorithm Optional algorithm override (if null, uses the key's default algorithm)
     * @return The signature bytes
     * @throws KeyNotFoundException if the key does not exist
     */
    suspend fun sign(
        keyId: String,
        data: ByteArray,
        algorithm: String? = null
    ): ByteArray

    /**
     * Deletes a key from the key management service.
     *
     * @param keyId The identifier of the key to delete
     * @return true if the key was deleted, false if it did not exist
     */
    suspend fun deleteKey(keyId: String): Boolean
}

/**
 * Exception thrown when a requested key is not found.
 */
class KeyNotFoundException(message: String, cause: Throwable? = null) : Exception(message, cause)

