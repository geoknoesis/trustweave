package com.trustweave.kms

import com.trustweave.core.types.KeyId

/**
 * Represents key specifications with algorithm validation support.
 *
 * This class provides type-safe validation that a requested algorithm is compatible
 * with a key's actual algorithm. It prevents accidental use of incompatible algorithms
 * during signing operations.
 *
 * **Example Usage:**
 * ```kotlin
 * val keyHandle = kms.getPublicKey(keyId)
 * val keySpec = KeySpec.fromKeyHandle(keyHandle)
 *
 * // Validate algorithm before signing
 * val algorithm = Algorithm.Ed25519
 * require(keySpec.supports(algorithm)) {
 *     "Key ${keySpec.id} does not support algorithm ${algorithm.name}"
 * }
 * ```
 */
data class KeySpec(
    /**
     * The key identifier.
     */
    val id: KeyId,

    /**
     * The cryptographic algorithm this key supports.
     */
    val algorithm: Algorithm
) {
    /**
     * Checks if this key spec supports the given algorithm.
     *
     * For most keys, this means the algorithms must match exactly.
     * Some keys may support multiple algorithms (e.g., RSA keys can support
     * different padding schemes), but this implementation checks for exact matches.
     *
     * @param required The algorithm to check
     * @return true if the key supports the algorithm, false otherwise
     */
    fun supports(required: Algorithm): Boolean {
        return algorithm == required
    }

    /**
     * Checks if this key spec supports the given algorithm by name.
     *
     * @param algorithmName The algorithm name to check
     * @return true if the key supports the algorithm, false otherwise
     */
    fun supports(algorithmName: String): Boolean {
        val alg = Algorithm.parse(algorithmName) ?: return false
        return supports(alg)
    }

    /**
     * Validates that this key spec supports the given algorithm.
     *
     * @param required The algorithm to validate
     * @throws UnsupportedAlgorithmException if the algorithm is not supported
     */
    fun requireSupports(required: Algorithm) {
        if (!supports(required)) {
            throw UnsupportedAlgorithmException(
                "Key ${id.value} (algorithm: ${algorithm.name}) does not support " +
                "requested algorithm: ${required.name}"
            )
        }
    }

    companion object {
        /**
         * Creates a KeySpec from a KeyHandle.
         *
         * @param keyHandle The key handle to convert
         * @return A KeySpec instance
         * @throws IllegalArgumentException if the key handle's algorithm cannot be parsed
         */
        fun fromKeyHandle(keyHandle: KeyHandle): KeySpec {
            val algorithm = Algorithm.parse(keyHandle.algorithm)
                ?: throw IllegalArgumentException(
                    "Cannot parse algorithm '${keyHandle.algorithm}' from key handle for key: ${keyHandle.id.value}"
                )
            return KeySpec(keyHandle.id, algorithm)
        }

        /**
         * Creates a KeySpec from a key ID and algorithm.
         *
         * @param keyId The key identifier
         * @param algorithm The algorithm
         * @return A KeySpec instance
         */
        fun from(keyId: KeyId, algorithm: Algorithm): KeySpec {
            return KeySpec(keyId, algorithm)
        }
    }
}

