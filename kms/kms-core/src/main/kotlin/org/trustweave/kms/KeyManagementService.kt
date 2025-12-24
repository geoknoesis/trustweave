package org.trustweave.kms

import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.results.DeleteKeyResult
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.GetPublicKeyResult
import org.trustweave.kms.results.SignResult

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
     * Returns a [GenerateKeyResult] for type-safe error handling.
     *
     * **Example Usage:**
     * ```kotlin
     * when (val result = kms.generateKey(Algorithm.Ed25519)) {
     *     is GenerateKeyResult.Success -> {
     *         val handle = result.keyHandle
     *         // Use key handle
     *     }
     *     is GenerateKeyResult.Failure.UnsupportedAlgorithm -> {
     *         // Algorithm not supported
     *         println("Algorithm not supported. Supported: ${result.supportedAlgorithms}")
     *     }
     *     is GenerateKeyResult.Failure.InvalidOptions -> {
     *         // Invalid options provided
     *         println("Invalid options: ${result.reason}")
     *     }
     *     is GenerateKeyResult.Failure.Error -> {
     *         // Unexpected error
     *         println("Error: ${result.reason}")
     *     }
     * }
     * ```
     *
     * @param algorithm The algorithm to use
     * @param options Additional options for key generation
     * @return Result containing the key handle or failure information
     */
    suspend fun generateKey(
        algorithm: Algorithm,
        options: Map<String, Any?> = emptyMap()
    ): GenerateKeyResult

    /**
     * Generates a new cryptographic key by algorithm name.
     *
     * Convenience method that parses the algorithm name and returns a Result.
     *
     * @param algorithmName The algorithm name (e.g., "Ed25519", "secp256k1")
     * @param options Additional options for key generation
     * @return Result containing the key handle or failure information
     */
    suspend fun generateKey(
        algorithmName: String,
        options: Map<String, Any?> = emptyMap()
    ): GenerateKeyResult {
        val algorithm = Algorithm.parse(algorithmName)
            ?: return GenerateKeyResult.Failure.UnsupportedAlgorithm(
                algorithm = Algorithm.Custom(algorithmName), // Will be rejected by validation
                supportedAlgorithms = getSupportedAlgorithms(),
                reason = "Unknown or invalid algorithm: $algorithmName. " +
                    "Algorithm name must be a recognized standard algorithm or a valid custom algorithm name."
            )

        if (!supportsAlgorithm(algorithm)) {
            return GenerateKeyResult.Failure.UnsupportedAlgorithm(
                algorithm = algorithm,
                supportedAlgorithms = getSupportedAlgorithms()
            )
        }

        return generateKey(algorithm, options)
    }

    /**
     * Retrieves the public key information for a given key ID.
     *
     * Returns a [GetPublicKeyResult] for type-safe error handling.
     * Key not found is an expected failure and is represented as a result.
     *
     * **Example Usage:**
     * ```kotlin
     * when (val result = kms.getPublicKey(keyId)) {
     *     is GetPublicKeyResult.Success -> {
     *         val handle = result.keyHandle
     *         // Use key handle
     *     }
     *     is GetPublicKeyResult.Failure.KeyNotFound -> {
     *         // Expected: Key doesn't exist
     *         println("Key not found: ${result.keyId}")
     *     }
     *     is GetPublicKeyResult.Failure.Error -> {
     *         // Unexpected error
     *         println("Error: ${result.reason}")
     *     }
     * }
     * ```
     *
     * @param keyId Type-safe key identifier
     * @return Result containing the key handle or failure information
     */
    suspend fun getPublicKey(keyId: KeyId): GetPublicKeyResult

    /**
     * Signs data using the specified key.
     *
     * Returns a [SignResult] for type-safe error handling.
     * Key not found and unsupported algorithm are expected failures.
     *
     * **Example Usage:**
     * ```kotlin
     * when (val result = kms.sign(keyId, data, algorithm)) {
     *     is SignResult.Success -> {
     *         val signature = result.signature
     *         // Use signature
     *     }
     *     is SignResult.Failure.KeyNotFound -> {
     *         // Expected: Key doesn't exist
     *         println("Key not found: ${result.keyId}")
     *     }
     *     is SignResult.Failure.UnsupportedAlgorithm -> {
     *         // Algorithm incompatible with key
     *         println("Algorithm ${result.requestedAlgorithm} not compatible with key algorithm ${result.keyAlgorithm}")
     *     }
     *     is SignResult.Failure.Error -> {
     *         // Unexpected error
     *         println("Error: ${result.reason}")
     *     }
     * }
     * ```
     *
     * @param keyId Type-safe key identifier
     * @param data The data to sign
     * @param algorithm Optional algorithm override (if null, uses the key's default algorithm).
     *                  If provided, MUST be compatible with the key's algorithm.
     * @return Result containing the signature or failure information
     */
    suspend fun sign(
        keyId: KeyId,
        data: ByteArray,
        algorithm: Algorithm? = null
    ): SignResult

    /**
     * Signs data using the specified key by algorithm name.
     *
     * Convenience method that parses the algorithm name and returns a Result.
     *
     * @param keyId Type-safe key identifier
     * @param data The data to sign
     * @param algorithmName Optional algorithm name override (if null, uses the key's default algorithm)
     * @return Result containing the signature or failure information
     */
    suspend fun sign(
        keyId: KeyId,
        data: ByteArray,
        algorithmName: String?
    ): SignResult {
        val algorithm = algorithmName?.let { Algorithm.parse(it) }
        return sign(keyId, data, algorithm)
    }

    /**
     * Deletes a key from the key management service.
     *
     * Returns a [DeleteKeyResult] for type-safe error handling.
     * The operation is idempotent - deleting a non-existent key is considered success.
     *
     * **Example Usage:**
     * ```kotlin
     * when (val result = kms.deleteKey(keyId)) {
     *     is DeleteKeyResult.Deleted -> {
     *         // Key was deleted
     *         println("Key deleted")
     *     }
     *     is DeleteKeyResult.NotFound -> {
     *         // Key didn't exist (idempotent success)
     *         println("Key not found (already deleted)")
     *     }
     *     is DeleteKeyResult.Failure.Error -> {
     *         // Unexpected error
     *         println("Error: ${result.reason}")
     *     }
     * }
     * ```
     *
     * @param keyId Type-safe key identifier
     * @return Result indicating deletion status
     */
    suspend fun deleteKey(keyId: KeyId): DeleteKeyResult
}

/**
 * Exception thrown when an algorithm is not supported by a KMS.
 */
class UnsupportedAlgorithmException(
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)

