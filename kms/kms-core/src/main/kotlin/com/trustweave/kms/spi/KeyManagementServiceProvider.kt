package com.trustweave.kms.spi

import com.trustweave.kms.Algorithm
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.KmsCreationOptions

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
     * Returns the list of environment variables required for this provider to function.
     *
     * This method allows tests to automatically skip when required credentials are not available.
     * Each provider implementation should declare what environment variables it needs.
     *
     * **Example:**
     * ```kotlin
     * override val requiredEnvironmentVariables: List<String> = listOf(
     *     "AWS_REGION",
     *     "AWS_ACCESS_KEY_ID",
     *     "AWS_SECRET_ACCESS_KEY"
     * )
     * ```
     *
     * **Note:** Optional env vars should be prefixed with "?" (e.g., "?AWS_SESSION_TOKEN")
     *
     * @return List of required environment variable names (empty by default for providers that don't need credentials)
     */
    val requiredEnvironmentVariables: List<String>
        get() = emptyList()

    /**
     * Checks if all required environment variables are available for this provider.
     *
     * Default implementation checks if all non-optional env vars are set.
     * Providers can override this to implement custom logic (e.g., checking for IAM roles).
     *
     * @return true if all required env vars are set or provider-specific checks pass, false otherwise
     */
    fun hasRequiredEnvironmentVariables(): Boolean {
        return requiredEnvironmentVariables.all { envVar ->
            val isOptional = envVar.startsWith("?")
            val actualVar = if (isOptional) envVar.substring(1) else envVar
            if (isOptional) true else System.getenv(actualVar) != null
        }
    }

    /**
     * Creates a KeyManagementService instance.
     *
     * @param options Configuration options for the service
     * @return A KeyManagementService instance
     * @throws IllegalArgumentException if the provider cannot be created with the given options
     */
    fun create(options: Map<String, Any?> = emptyMap()): KeyManagementService

    /**
     * Creates a KeyManagementService instance using typed configuration.
     *
     * This method provides type-safe configuration. The default implementation
     * converts [KmsCreationOptions] to a Map and calls [create].
     *
     * @param options Typed configuration options for the service
     * @return A KeyManagementService instance
     * @throws IllegalArgumentException if the provider cannot be created with the given options
     */
    fun create(options: KmsCreationOptions): KeyManagementService {
        return create(options.toMap())
    }
}

