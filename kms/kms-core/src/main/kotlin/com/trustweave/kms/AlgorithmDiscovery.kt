package com.trustweave.kms

import com.trustweave.kms.spi.KeyManagementServiceProvider
import java.util.ServiceLoader

/**
 * Utility for discovering algorithm support across KMS providers.
 *
 * **Example Usage:**
 * ```kotlin
 * // Discover all providers and their algorithms
 * val providers = AlgorithmDiscovery.discoverProviders()
 *
 * // Find providers that support Ed25519
 * val ed25519Providers = AlgorithmDiscovery.findProvidersFor(Algorithm.Ed25519)
 *
 * // Find and create best provider
 * val kms = AlgorithmDiscovery.createProviderFor(Algorithm.Ed25519, "waltid")
 * ```
 */
object AlgorithmDiscovery {
    /**
     * Cached list of providers loaded via ServiceLoader.
     * This prevents repeated ServiceLoader calls and ensures consistency.
     */
    private val cachedProviders: List<KeyManagementServiceProvider> by lazy {
        ServiceLoader.load(KeyManagementServiceProvider::class.java).toList()
    }

    /**
     * Discovers all available KMS providers and their supported algorithms.
     *
     * @return Map of provider name to set of supported algorithms
     */
    fun discoverProviders(): Map<String, Set<Algorithm>> {
        return cachedProviders.associate { it.name to it.supportedAlgorithms }
    }

    /**
     * Finds all providers that support a specific algorithm.
     *
     * @param algorithm The algorithm to search for
     * @return List of provider names that support the algorithm
     */
    fun findProvidersFor(algorithm: Algorithm): List<String> {
        return cachedProviders
            .filter { it.supportsAlgorithm(algorithm) }
            .map { it.name }
    }

    /**
     * Finds all providers that support an algorithm by name.
     *
     * @param algorithmName The algorithm name (case-insensitive)
     * @return List of provider names that support the algorithm, or empty if algorithm is invalid
     */
    fun findProvidersFor(algorithmName: String): List<String> {
        val algorithm = Algorithm.parse(algorithmName) ?: return emptyList()
        return findProvidersFor(algorithm)
    }

    /**
     * Gets the best provider for a given algorithm.
     *
     * @param algorithm The algorithm to find a provider for
     * @param preferredProvider Optional preferred provider name (checked first)
     * @return Provider name if found, null otherwise
     */
    fun findBestProviderFor(
        algorithm: Algorithm,
        preferredProvider: String? = null
    ): String? {
        // Check preferred provider first
        preferredProvider?.let { providerName ->
            cachedProviders.find { it.name == providerName }?.let { provider ->
                if (provider.supportsAlgorithm(algorithm)) {
                    return providerName
                }
            }
        }

        // Find any provider that supports it
        val supportingProviders = findProvidersFor(algorithm)
        return supportingProviders.firstOrNull()
    }

    /**
     * Gets a provider instance that supports a specific algorithm.
     *
     * @param algorithm The algorithm to find a provider for
     * @param preferredProvider Optional preferred provider name
     * @param options Options to pass to provider.create()
     * @return Provider instance if found, null otherwise
     */
    fun createProviderFor(
        algorithm: Algorithm,
        preferredProvider: String? = null,
        options: Map<String, Any?> = emptyMap()
    ): KeyManagementService? {
        val providerName = findBestProviderFor(algorithm, preferredProvider) ?: return null
        return cachedProviders.find { it.name == providerName }?.create(options)
    }
}

