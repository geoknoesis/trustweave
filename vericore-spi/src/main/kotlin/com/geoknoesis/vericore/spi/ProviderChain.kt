package com.geoknoesis.vericore.spi

/**
 * Provider chain with fallback support.
 *
 * Executes operations across multiple providers in order,
 * trying each provider until one succeeds. This enables
 * automatic failover and provider redundancy.
 *
 * **Example Usage**:
 * ```kotlin
 * val chain = ProviderChain(listOf(provider1, provider2, provider3))
 *
 * val result = chain.execute { provider ->
 *     provider.issueCredential(credential)
 * }
 * ```
 *
 * @param providers List of providers to try in order
 * @param selector Optional function to filter providers (default: all providers)
 */
class ProviderChain<T>(
    private val providers: List<T>,
    private val selector: (T) -> Boolean = { true }
) {
    init {
        require(providers.isNotEmpty()) { "Provider chain must contain at least one provider" }
    }

    /**
     * Execute an operation across the provider chain.
     *
     * Tries each provider in order until one succeeds.
     * If all providers fail, throws a ProviderChainException
     * with the last exception encountered.
     *
     * @param operation Operation to execute on each provider
     * @return Result from the first successful provider
     * @throws ProviderChainException if all providers fail
     */
    suspend fun <R> execute(operation: suspend (T) -> R): R {
        var lastException: Throwable? = null
        val attemptedProviders = mutableListOf<String>()

        for (provider in providers) {
            if (!selector(provider)) continue

            try {
                return operation(provider)
            } catch (e: Exception) {
                lastException = e
                attemptedProviders.add(provider.toString())
                // Try next provider
                continue
            }
        }

        throw ProviderChainException(
            "All providers in chain failed. Attempted: ${attemptedProviders.joinToString(", ")}",
            lastException
        )
    }

    /**
     * Execute an operation with result transformation.
     *
     * Similar to execute, but allows transforming the result
     * before returning it.
     *
     * @param operation Operation to execute
     * @param transform Transform function for the result
     * @return Transformed result from first successful provider
     */
    suspend fun <R, T2> executeAndTransform(
        operation: suspend (T) -> R,
        transform: (R) -> T2
    ): T2 {
        val result = execute(operation)
        return transform(result)
    }

    /**
     * Get the number of providers in this chain.
     */
    fun size(): Int = providers.size

    /**
     * Check if chain is empty.
     */
    fun isEmpty(): Boolean = providers.isEmpty()
}

/**
 * Exception thrown when all providers in a chain fail.
 *
 * @param message Error message
 * @param lastException The last exception encountered before all providers failed
 */
class ProviderChainException(
    message: String,
    val lastException: Throwable? = null
) : RuntimeException(message, lastException)

/**
 * Create provider chain from plugin IDs.
 *
 * Looks up plugins from the registry and creates a chain.
 *
 * @param pluginIds List of plugin IDs in order of preference
 * @param registry Plugin registry to lookup plugins
 * @return Provider chain
 */
fun <T> createProviderChain(
    pluginIds: List<String>,
    registry: PluginRegistry = PluginRegistry
): ProviderChain<T> {
    val providers = pluginIds.mapNotNull { pluginId ->
        registry.getInstance<T>(pluginId)
    }

    if (providers.isEmpty()) {
        throw IllegalArgumentException("No providers found for plugin IDs: ${pluginIds.joinToString(", ")}")
    }

    return ProviderChain(providers)
}

/**
 * Create provider chain from configuration.
 *
 * Uses provider chains defined in PluginConfiguration.
 *
 * @param chainName Name of the provider chain (e.g., "credential-service")
 * @param config Plugin configuration
 * @param registry Plugin registry
 * @return Provider chain, or null if chain not found
 */
fun <T> createProviderChainFromConfig(
    chainName: String,
    config: PluginConfiguration,
    registry: PluginRegistry = PluginRegistry
): ProviderChain<T>? {
    val pluginIds = config.providerChains[chainName] ?: return null
    return createProviderChain(pluginIds, registry)
}


