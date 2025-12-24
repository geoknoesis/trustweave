package org.trustweave.core.plugin

import org.trustweave.core.exception.TrustWeaveException

/**
 * Internal provider chain with fallback support.
 *
 * This is an internal infrastructure component. Use domain-specific chain classes instead:
 * - `CredentialIssuerChain` for credential issuer chains
 * - `DidResolverChain` for DID resolver chains
 * - `KeyManagementChain` for key management service chains
 *
 * @param providers List of providers to try in order
 * @param selector Optional function to filter providers (default: all providers)
 * @suppress This is an internal API
 */
internal class ProviderChain<T>(
    private val providers: List<T>,
    private val selector: (T) -> Boolean = { true }
) {
    init {
        require(providers.isNotEmpty()) { "Provider chain must contain at least one provider" }
        // Validate that selector doesn't filter out all providers
        val selectedCount = providers.count(selector)
        require(selectedCount > 0) {
            "Selector function filters out all providers. At least one provider must be selected."
        }
    }

    /**
     * Execute an operation across the provider chain.
     *
     * Tries each provider in order until one succeeds.
     * If all providers fail, throws a TrustWeaveException.AllProvidersFailed
     * with details about all attempted providers and their errors.
     *
     * @param operation Operation to execute on each provider
     * @return Result from the first successful provider
     * @throws TrustWeaveException.AllProvidersFailed if all providers fail
     * @throws TrustWeaveException.InvalidState if no providers are selected
     */
    suspend fun <R> execute(operation: suspend (T) -> R): R {
        var lastException: Throwable? = null
        val attemptedProviders = mutableListOf<String>()
        val providerErrors = mutableMapOf<String, String>()
        var attemptCount = 0

        for ((index, provider) in providers.withIndex()) {
            if (!selector(provider)) continue
            attemptCount++

            try {
                return operation(provider)
            } catch (e: Throwable) {
                // Critical: Don't catch Error types (OutOfMemoryError, StackOverflowError, etc.).
                // These represent serious JVM-level problems that should propagate immediately
                // rather than being treated as recoverable provider failures. Attempting to
                // continue with other providers in such cases could mask critical system issues.
                if (e is Error) {
                    throw e
                }
                lastException = e

                // Generate a unique identifier for this provider instance.
                // We combine the class name with the index because:
                // 1. Multiple instances of the same class may exist in the chain
                // 2. The index ensures uniqueness even for identical class types
                // 3. This helps with debugging by showing which specific instance failed
                // Note: provider is non-null here (we're in the catch block after a non-null check)
                val providerInstance = provider!!
                val className = providerInstance.javaClass.name
                    .substringAfterLast('.')
                val providerId = "$className[$index]"
                attemptedProviders.add(providerId)

                // Store error message for detailed failure reporting.
                // Fallback chain: exception message -> class name -> "Unknown error"
                providerErrors[providerId] = e.message ?: e::class.simpleName ?: "Unknown error"

                // Continue to next provider in the chain (failover behavior)
                continue
            }
        }

        if (attemptCount == 0) {
            throw TrustWeaveException.InvalidState(
                message = "No providers were selected by the selector function. " +
                    "At least one provider must be available for execution."
            )
        }

        throw TrustWeaveException.AllProvidersFailed(
            attemptedProviders = attemptedProviders,
            providerErrors = providerErrors,
            lastException = lastException
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
     * Get the number of selected providers (after applying selector).
     */
    fun selectedSize(): Int = providers.count(selector)

    /**
     * Check if chain is empty.
     *
     * **Note:** Due to the constructor requirement that at least one provider must be present,
     * this will always return `false` after a `ProviderChain` is successfully constructed.
     * This method exists for API consistency and will only return `true` if the internal
     * state is somehow invalid (which should not occur in normal usage).
     */
    fun isEmpty(): Boolean = providers.isEmpty()
}

/**
 * Internal function to create provider chain from plugin IDs.
 *
 * @suppress This is an internal API
 */
internal inline fun <reified T> createProviderChain(
    pluginIds: List<String>,
    registry: PluginRegistry
): ProviderChain<T> {
    // Map each plugin ID to its instance, preserving the original index.
    // mapIndexedNotNull filters out null results (when plugin not found) while
    // keeping the index for potential ordering/priority information.
    val found = pluginIds.mapIndexedNotNull { _, pluginId ->
        registry.getInstance(pluginId, T::class.java)?.let { pluginId to it }
    }

    // Extract found IDs and compute missing IDs by set difference.
    // This allows us to provide detailed error information about which
    // specific plugins were not found.
    val foundIds = found.map { it.first }
    val missingIds = pluginIds.filter { id -> foundIds.none { it == id } }

    // Error handling: Fail fast if no providers found at all.
    // This prevents creating an invalid chain that would fail on first use.
    if (found.isEmpty()) {
        throw TrustWeaveException.NoProvidersFound(
            pluginIds = pluginIds,
            availablePlugins = registry.getAllPlugins().map { it.id }
        )
    }

    // Error handling: Fail if some (but not all) providers are missing.
    // This ensures that the chain contains exactly the requested providers,
    // maintaining predictable behavior and preventing partial configurations.
    if (missingIds.isNotEmpty()) {
        throw TrustWeaveException.PartialProvidersFound(
            requestedIds = pluginIds,
            foundIds = foundIds,
            missingIds = missingIds
        )
    }

    // Extract just the instances (discarding IDs) for the ProviderChain constructor.
    // The order is preserved from the original pluginIds list, maintaining
    // the intended priority/fallback order.
    return ProviderChain(found.map { it.second })
}

/**
 * Internal function to create provider chain from configuration.
 *
 * @suppress This is an internal API
 */
internal inline fun <reified T> createProviderChainFromConfig(
    chainName: String,
    config: PluginConfiguration,
    registry: PluginRegistry
): ProviderChain<T>? {
    val pluginIds = config.providerChains[chainName] ?: return null
    // Call createProviderChain with the same reified type parameter.
    // The reified type T propagates automatically to the inline function call.
    return createProviderChain<T>(pluginIds, registry)
}
