package com.trustweave.kms

import com.trustweave.core.types.KeyId

/**
 * Represents a key handle with metadata about the key.
 *
 * @param id Type-safe key identifier
 * @param algorithm The cryptographic algorithm (e.g., "Ed25519", "secp256k1")
 * @param publicKeyJwk Public key in JWK format (optional)
 * @param publicKeyMultibase Public key in multibase format (optional)
 */
data class KeyHandle(
    val id: KeyId,
    val algorithm: String,
    val publicKeyJwk: Map<String, Any?>? = null,
    val publicKeyMultibase: String? = null
) {
    /**
     * Backward compatibility: get id as string.
     * @deprecated Use id.value instead
     */
    @Deprecated("Use id.value instead", ReplaceWith("id.value"))
    val idString: String
        get() = id.value
}

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
     * @param keyId Type-safe key identifier
     * @return A KeyHandle containing the public key information
     * @throws KeyNotFoundException if the key does not exist
     */
    suspend fun getPublicKey(keyId: KeyId): KeyHandle

    /**
     * Signs data using the specified key.
     *
     * **Algorithm Validation:**
     * Implementations SHOULD validate that the provided algorithm (if any) is compatible
     * with the key's actual algorithm. Use [validateSigningAlgorithm] to perform this validation.
     *
     * @param keyId Type-safe key identifier
     * @param data The data to sign
     * @param algorithm Optional algorithm override (if null, uses the key's default algorithm).
     *                  If provided, MUST be compatible with the key's algorithm.
     * @return The signature bytes
     * @throws KeyNotFoundException if the key does not exist
     * @throws UnsupportedAlgorithmException if the provided algorithm is incompatible with the key
     */
    suspend fun sign(
        keyId: KeyId,
        data: ByteArray,
        algorithm: Algorithm? = null
    ): ByteArray

    /**
     * Signs data using the specified key by algorithm name.
     *
     * Convenience method that parses the algorithm name.
     *
     * @param keyId Type-safe key identifier
     * @param data The data to sign
     * @param algorithmName Optional algorithm name override (if null, uses the key's default algorithm)
     * @return The signature bytes
     * @throws KeyNotFoundException if the key does not exist
     */
    suspend fun sign(
        keyId: KeyId,
        data: ByteArray,
        algorithmName: String?
    ): ByteArray {
        val algorithm = algorithmName?.let { Algorithm.parse(it) }
        return sign(keyId, data, algorithm)
    }

    /**
     * Deletes a key from the key management service.
     *
     * @param keyId Type-safe key identifier
     * @return true if the key was deleted, false if it did not exist
     */
    suspend fun deleteKey(keyId: KeyId): Boolean

    /**
     * Validates that a requested algorithm is compatible with a key's actual algorithm.
     *
     * This helper method can be used by implementations to validate algorithm compatibility
     * before performing signing operations. It retrieves the key's specification and
     * validates that the requested algorithm (if provided) is compatible.
     *
     * **Example Usage in Implementation:**
     * ```kotlin
     * override suspend fun sign(keyId: KeyId, data: ByteArray, algorithm: Algorithm?): ByteArray {
     *     // Validate algorithm compatibility
     *     val effectiveAlgorithm = algorithm ?: validateSigningAlgorithm(keyId, null)
     *     validateSigningAlgorithm(keyId, effectiveAlgorithm)
     *
     *     // Proceed with signing...
     * }
     * ```
     *
     * @param keyId The key identifier
     * @param requestedAlgorithm The algorithm to validate (null means use key's default)
     * @return The algorithm to use for signing (either requested or key's default)
     * @throws KeyNotFoundException if the key does not exist
     * @throws UnsupportedAlgorithmException if the requested algorithm is incompatible
     */
    suspend fun validateSigningAlgorithm(
        keyId: KeyId,
        requestedAlgorithm: Algorithm?
    ): Algorithm {
        val keyHandle = getPublicKey(keyId)
        val keySpec = KeySpec.fromKeyHandle(keyHandle)

        return if (requestedAlgorithm != null) {
            // Validate that the requested algorithm is compatible
            keySpec.requireSupports(requestedAlgorithm)
            requestedAlgorithm
        } else {
            // Use the key's default algorithm
            keySpec.algorithm
        }
    }
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
 *
 * @deprecated Use KmsException.KeyNotFound instead
 */
@Deprecated("Use KmsException.KeyNotFound instead", ReplaceWith("KmsException.KeyNotFound(keyId)"))
class KeyNotFoundException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    constructor(keyId: String) : this(
        message = "Key not found: $keyId",
        cause = null
    )
}

