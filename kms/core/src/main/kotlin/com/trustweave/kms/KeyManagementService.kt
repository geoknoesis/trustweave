package com.trustweave.kms

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
 * 
 * **All implementations MUST advertise their supported algorithms.**
 */
interface KeyManagementService {
    /**
     * Returns the set of algorithms supported by this KMS instance.
     * 
     * This method MUST be implemented by all KMS providers to advertise
     * their capabilities. The returned set should be immutable and reflect
     * the actual capabilities of the KMS.
     * 
     * **Example:**
     * ```kotlin
     * override suspend fun getSupportedAlgorithms(): Set<Algorithm> {
     *     return setOf(Algorithm.Ed25519, Algorithm.Secp256k1, Algorithm.P256)
     * }
     * ```
     * 
     * @return Immutable set of supported algorithms
     */
    suspend fun getSupportedAlgorithms(): Set<Algorithm>
    
    /**
     * Checks if a specific algorithm is supported.
     * 
     * @param algorithm The algorithm to check
     * @return true if supported, false otherwise
     */
    suspend fun supportsAlgorithm(algorithm: Algorithm): Boolean {
        return getSupportedAlgorithms().contains(algorithm)
    }
    
    /**
     * Checks if an algorithm by name is supported.
     * 
     * @param algorithmName The algorithm name (case-insensitive)
     * @return true if supported, false otherwise
     */
    suspend fun supportsAlgorithm(algorithmName: String): Boolean {
        val algorithm = Algorithm.parse(algorithmName) ?: return false
        return supportsAlgorithm(algorithm)
    }
    
    /**
     * Generates a new cryptographic key.
     *
     * @param algorithm The algorithm to use
     * @param options Additional options for key generation
     * @return A KeyHandle for the newly generated key
     * @throws UnsupportedAlgorithmException if the algorithm is not supported
     */
    suspend fun generateKey(
        algorithm: Algorithm,
        options: Map<String, Any?> = emptyMap()
    ): KeyHandle
    
    /**
     * Generates a new cryptographic key by algorithm name.
     * 
     * Convenience method that parses the algorithm name.
     * 
     * @param algorithmName The algorithm name (e.g., "Ed25519", "secp256k1")
     * @param options Additional options for key generation
     * @return A KeyHandle for the newly generated key
     * @throws UnsupportedAlgorithmException if the algorithm is not supported or invalid
     */
    suspend fun generateKey(
        algorithmName: String,
        options: Map<String, Any?> = emptyMap()
    ): KeyHandle {
        val algorithm = Algorithm.parse(algorithmName)
            ?: throw UnsupportedAlgorithmException("Unknown algorithm: $algorithmName")
        
        if (!supportsAlgorithm(algorithm)) {
            throw UnsupportedAlgorithmException(
                "Algorithm '$algorithmName' is not supported by this KMS. " +
                "Supported algorithms: ${getSupportedAlgorithms().joinToString(", ") { it.name }}"
            )
        }
        
        return generateKey(algorithm, options)
    }

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
        algorithm: Algorithm? = null
    ): ByteArray
    
    /**
     * Signs data using the specified key by algorithm name.
     * 
     * Convenience method that parses the algorithm name.
     * 
     * @param keyId The identifier of the key to use for signing
     * @param data The data to sign
     * @param algorithmName Optional algorithm name override (if null, uses the key's default algorithm)
     * @return The signature bytes
     * @throws KeyNotFoundException if the key does not exist
     */
    suspend fun sign(
        keyId: String,
        data: ByteArray,
        algorithmName: String?
    ): ByteArray {
        val algorithm = algorithmName?.let { Algorithm.parse(it) }
        return sign(keyId, data, algorithm)
    }

    /**
     * Deletes a key from the key management service.
     *
     * @param keyId The identifier of the key to delete
     * @return true if the key was deleted, false if it did not exist
     */
    suspend fun deleteKey(keyId: String): Boolean
}

/**
 * Exception thrown when an algorithm is not supported by a KMS.
 */
class UnsupportedAlgorithmException(
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)

/**
 * Exception thrown when a requested key is not found.
 */
class KeyNotFoundException(message: String, cause: Throwable? = null) : Exception(message, cause)

