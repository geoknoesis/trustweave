package com.trustweave.core.plugin

import com.trustweave.core.exception.TrustWeaveException
import java.util.concurrent.ConcurrentHashMap

/**
 * Interface for plugin registry operations.
 *
 * This interface allows for dependency injection and test isolation
 * by enabling the use of test-specific registry instances.
 */
interface PluginRegistry {
    /**
     * Register a plugin with metadata.
     *
     * @param metadata Plugin metadata describing capabilities
     * @param instance Plugin instance
     * @throws com.trustweave.core.exception.TrustWeaveException.BlankPluginId if plugin ID is blank
     * @throws com.trustweave.core.exception.TrustWeaveException.PluginAlreadyRegistered if plugin is already registered
     */
    fun register(metadata: PluginMetadata, instance: Any)

    /**
     * Unregister a plugin.
     *
     * @param pluginId Plugin ID to unregister
     */
    fun unregister(pluginId: String)

    /**
     * Get plugin metadata.
     *
     * @param pluginId Plugin ID
     * @return Plugin metadata, or null if not found
     */
    fun getMetadata(pluginId: String): PluginMetadata?

    /**
     * Get plugin instance with type safety check.
     *
     * This method performs runtime type checking using the provided class.
     * For a more convenient API with reified generics, use the extension function
     * [getInstance] which calls this method internally.
     *
     * @param pluginId Plugin ID
     * @param clazz Expected class type
     * @return Plugin instance, or null if not found or type mismatch
     */
    fun <T> getInstance(pluginId: String, clazz: Class<T>): T?

    /**
     * Find plugins by capability/feature.
     *
     * @param capability Feature name (e.g., "credential-storage", "revocation")
     * @return List of plugins that support this capability
     */
    fun findByCapability(capability: String): List<PluginMetadata>

    /**
     * Find plugins by provider name.
     *
     * @param provider Provider name (e.g., "waltid", "godiddy")
     * @return List of plugins from this provider
     */
    fun findByProvider(provider: String): List<PluginMetadata>

    /**
     * Select best provider for a capability.
     *
     * Tries providers in preference order, falling back to first available.
     *
     * @param capability Feature name
     * @param preferences Ordered list of preferred provider names
     * @return Best matching plugin metadata, or null if none found
     */
    fun selectProvider(
        capability: String,
        preferences: List<String>
    ): PluginMetadata?

    /**
     * Select best provider for a capability.
     *
     * Returns the first available provider for the capability.
     *
     * @param capability Feature name
     * @return Best matching plugin metadata, or null if none found
     */
    fun selectProvider(capability: String): PluginMetadata?

    /**
     * Get all registered plugins.
     *
     * @return List of all plugin metadata
     */
    fun getAllPlugins(): List<PluginMetadata>

    /**
     * Check if a plugin is registered.
     *
     * @param pluginId Plugin ID
     * @return true if plugin is registered
     */
    fun isRegistered(pluginId: String): Boolean

    /**
     * Clear all registered plugins.
     * Useful for testing.
     */
    fun clear()
}

/**
 * Default implementation of [PluginRegistry] interface.
 *
 * Provides capability-based discovery and provider selection.
 * This registry is the central hub for all plugins in TrustWeave,
 * enabling dynamic discovery and selection of providers based on capabilities.
 *
 * **Thread Safety**: This registry is thread-safe for concurrent access.
 *
 * **Dependency Injection**: This class implements [PluginRegistry] and should be
 * instantiated for dependency injection and test isolation.
 *
 * **Example Usage**:
 * ```kotlin
 * // Using dependency injection
 * class MyService(private val registry: PluginRegistry) {
 *     fun findPlugins() = registry.findByCapability("credential-storage")
 * }
 *
 * // Create registry instance
 * val registry = DefaultPluginRegistry()
 * val metadata = PluginMetadata(...)
 * registry.register(metadata, credentialService)
 *
 * // In tests, use isolated instances
 * val testRegistry = DefaultPluginRegistry()
 * val service = MyService(testRegistry)
 * ```
 */
class DefaultPluginRegistry : PluginRegistry {

    private val plugins = ConcurrentHashMap<String, PluginMetadata>()
    private val instances = ConcurrentHashMap<String, Any>()
    // Store runtime class information for each instance to enable type-safe retrieval
    private val instanceTypes = ConcurrentHashMap<String, Class<*>>()

    // Synchronization lock for operations that need atomicity across multiple maps
    private val lock = Any()

    override fun register(metadata: PluginMetadata, instance: Any) {
        if (metadata.id.isBlank()) {
            throw TrustWeaveException.BlankPluginId()
        }

        // Synchronization is required to ensure atomicity across both maps.
        // While ConcurrentHashMap provides thread-safe individual operations,
        // we need to ensure that both plugins and instances maps are updated
        // atomically to prevent inconsistent state (e.g., metadata registered
        // but instance missing, or vice versa).
        synchronized(lock) {
            // putIfAbsent returns the existing value if key exists, null otherwise.
            // This provides atomic check-and-set semantics.
            val existing = plugins.putIfAbsent(metadata.id, metadata)
            if (existing != null) {
                throw TrustWeaveException.PluginAlreadyRegistered(
                    pluginId = metadata.id,
                    existingPlugin = existing.name
                )
            }
            // Only add to instances map if plugin registration succeeded.
            // Since we're synchronized and instances is also a ConcurrentHashMap,
            // this operation is safe and maintains consistency between both maps.
            instances[metadata.id] = instance
            // Store the runtime class for type-safe retrieval
            instanceTypes[metadata.id] = instance.javaClass
        }
    }

    override fun unregister(pluginId: String) {
        synchronized(lock) {
            plugins.remove(pluginId)
            instances.remove(pluginId)
            instanceTypes.remove(pluginId)
        }
    }

    override fun getMetadata(pluginId: String): PluginMetadata? {
        return plugins[pluginId]
    }

    override fun <T> getInstance(pluginId: String, clazz: Class<T>): T? {
        val instance = instances[pluginId] ?: return null
        val storedClass = instanceTypes[pluginId] ?: return null

        // Use Class.isAssignableFrom() to check if stored instance is compatible
        // with the requested type. This handles inheritance correctly.
        if (!clazz.isAssignableFrom(storedClass)) {
            return null
        }

        // Safe cast: we've verified type compatibility above
        @Suppress("UNCHECKED_CAST")
        return instance as T
    }

    override fun findByCapability(capability: String): List<PluginMetadata> {
        require(capability.isNotBlank()) { "Capability cannot be blank" }
        return plugins.values.filter {
            it.capabilities.features.contains(capability)
        }
    }


    override fun findByProvider(provider: String): List<PluginMetadata> {
        require(provider.isNotBlank()) { "Provider name cannot be blank" }
        return plugins.values.filter { it.provider == provider }
    }


    override fun selectProvider(
        capability: String,
        preferences: List<String>
    ): PluginMetadata? {
        require(capability.isNotBlank()) { "Capability cannot be blank" }
        val candidates = findByCapability(capability)

        // Selection algorithm: Try each preference in order until a match is found.
        // This implements a priority-based selection where the first matching
        // provider in the preferences list is chosen, regardless of other candidates.
        for (pref in preferences) {
            if (pref.isNotBlank()) {
                // find() returns the first matching element, or null if none found.
                // The let block returns early if a match is found, implementing
                // the "first match wins" strategy.
                candidates.find { it.provider == pref }?.let { return it }
            }
        }

        // Fallback: If no preference matches, return the first available candidate.
        // This ensures that even without preferences, a provider is selected if available.
        return candidates.firstOrNull()
    }

    override fun selectProvider(capability: String): PluginMetadata? {
        return selectProvider(capability, emptyList())
    }

    override fun getAllPlugins(): List<PluginMetadata> {
        return plugins.values.toList()
    }

    override fun clear() {
        synchronized(lock) {
            plugins.clear()
            instances.clear()
            instanceTypes.clear()
        }
    }

    override fun isRegistered(pluginId: String): Boolean {
        return plugins.containsKey(pluginId)
    }
}

/**
 * Get plugin instance with type safety check using reified type parameter.
 *
 * This is a convenience extension function that provides type-safe retrieval using
 * reified generics. It calls the interface method [PluginRegistry.getInstance] with
 * the reified class, eliminating the need to manually pass Class objects.
 *
 * **Type Safety**: Uses stored class information to verify type compatibility at runtime,
 * eliminating unsafe unchecked casts. Handles inheritance correctly (e.g., ArrayList
 * is compatible with List).
 *
 * **Example Usage**:
 * ```kotlin
 * val credentialService = registry.getInstance<CredentialService>("plugin-id")
 * // credentialService is of type CredentialService? with full type safety
 * ```
 *
 * @param pluginId Plugin ID to retrieve
 * @return Plugin instance of type T, or null if not found or type mismatch
 */
inline fun <reified T> PluginRegistry.getInstance(pluginId: String): T? {
    // Access the reified type parameter's class at runtime and call the interface method
    return getInstance(pluginId, T::class.java)
}
