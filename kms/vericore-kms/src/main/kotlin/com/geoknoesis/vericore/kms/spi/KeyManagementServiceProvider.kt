package com.geoknoesis.vericore.kms.spi

import com.geoknoesis.vericore.kms.Algorithm
import com.geoknoesis.vericore.kms.KeyManagementService

/**
 * Service Provider Interface for KeyManagementService implementations.
 * Implementations of this interface will be discovered via Java ServiceLoader.
 * 
 * **All providers MUST advertise their supported algorithms.**
 */
interface KeyManagementServiceProvider {
    /**
     * The name/identifier of this provider (e.g., "waltid", "inmemory").
     */
    val name: String
    
    /**
     * Returns the set of algorithms supported by this provider.
     * 
     * This property MUST be implemented by all providers to advertise
     * their capabilities before creating a KMS instance. This allows
     * discovery and selection of appropriate providers.
     * 
     * **Example:**
     * ```kotlin
     * override val supportedAlgorithms: Set<Algorithm> = setOf(
     *     Algorithm.Ed25519,
     *     Algorithm.Secp256k1,
     *     Algorithm.P256,
     *     Algorithm.P384,
     *     Algorithm.P521
     * )
     * ```
     * 
     * @return Immutable set of supported algorithms
     */
    val supportedAlgorithms: Set<Algorithm>
    
    /**
     * Checks if a specific algorithm is supported by this provider.
     * 
     * @param algorithm The algorithm to check
     * @return true if supported, false otherwise
     */
    fun supportsAlgorithm(algorithm: Algorithm): Boolean {
        return supportedAlgorithms.contains(algorithm)
    }
    
    /**
     * Checks if an algorithm by name is supported.
     * 
     * @param algorithmName The algorithm name (case-insensitive)
     * @return true if supported, false otherwise
     */
    fun supportsAlgorithm(algorithmName: String): Boolean {
        val algorithm = Algorithm.parse(algorithmName) ?: return false
        return supportsAlgorithm(algorithm)
    }
    
    /**
     * Creates a KeyManagementService instance.
     *
     * @param options Configuration options for the service
     * @return A KeyManagementService instance
     * @throws IllegalArgumentException if the provider cannot be created with the given options
     */
    fun create(options: Map<String, Any?> = emptyMap()): KeyManagementService
}

