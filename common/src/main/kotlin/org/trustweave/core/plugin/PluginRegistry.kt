package org.trustweave.core.plugin

import org.trustweave.core.exception.TrustWeaveException
import java.util.concurrent.ConcurrentHashMap

/**
 * Internal interface for plugin registry operations.
 *
 * This is an internal infrastructure component. Use domain-specific registries instead:
 * - `DidMethodRegistry` for DID method registration
 * - `BlockchainAnchorRegistry` for blockchain anchor client registration
 * - `TrustRegistry` for trust anchor management
 * - `CredentialServiceRegistry` for credential service registration
 *
 * @suppress This is an internal API
 */
internal interface PluginRegistry {
    /**
     * Register a plugin with metadata.
     *
     * @param metadata Plugin metadata describing capabilities
     * @param instance Plugin instance
     * @throws org.trustweave.core.exception.TrustWeaveException.BlankPluginId if plugin ID is blank
     * @throws org.trustweave.core.exception.TrustWeaveException.PluginAlreadyRegistered if plugin is already registered
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
 * Internal default implementation of [PluginRegistry] interface.
 *
 * @suppress This is an internal API
 */
internal class DefaultPluginRegistry : PluginRegistry {

    private val plugins = ConcurrentHashMap<String, PluginMetadata>()
    private val instances = ConcurrentHashMap<String, Any>()
    // Store runtime class information for each instance to enable type-safe retrieval
    private val instanceTypes = ConcurrentHashMap<String, Class<*>>()
    
    // Index capabilities for O(1) lookup instead of O(n) filtering
    // Maps capability name -> set of plugin IDs that support it
    private val capabilityIndex = ConcurrentHashMap<String, MutableSet<String>>()
    
    // Index providers for O(1) lookup instead of O(n) filtering
    // Maps provider name -> set of plugin IDs from that provider
    private val providerIndex = ConcurrentHashMap<String, MutableSet<String>>()

    override fun register(metadata: PluginMetadata, instance: Any) {
        if (metadata.id.isBlank()) {
            throw TrustWeaveException.BlankPluginId
        }

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
        instances[metadata.id] = instance
        // Store the runtime class for type-safe retrieval
        instanceTypes[metadata.id] = instance.javaClass
        
        // Update capability index for O(1) lookups
        metadata.capabilities.features.forEach { capability ->
            capabilityIndex.computeIfAbsent(capability) { 
                ConcurrentHashMap.newKeySet() 
            }.add(metadata.id)
        }
        
        // Update provider index for O(1) lookups
        providerIndex.computeIfAbsent(metadata.provider) { 
            ConcurrentHashMap.newKeySet() 
        }.add(metadata.id)
    }

    override fun unregister(pluginId: String) {
        require(pluginId.isNotBlank()) { "Plugin ID cannot be blank" }
        // Idempotent operation: silently return if plugin not found
        val metadata = plugins[pluginId] ?: return
        
        plugins.remove(pluginId)
        instances.remove(pluginId)
        instanceTypes.remove(pluginId)
        
        // Remove from capability index
        metadata.capabilities.features.forEach { capability ->
            capabilityIndex[capability]?.remove(pluginId)
        }
        
        // Remove from provider index
        providerIndex[metadata.provider]?.remove(pluginId)
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
        // O(1) lookup using capability index instead of O(n) filtering
        val pluginIds = capabilityIndex[capability] ?: return emptyList()
        return pluginIds.mapNotNull { plugins[it] }
    }

    override fun findByProvider(provider: String): List<PluginMetadata> {
        require(provider.isNotBlank()) { "Provider name cannot be blank" }
        // O(1) lookup using provider index instead of O(n) filtering
        val pluginIds = providerIndex[provider] ?: return emptyList()
        return pluginIds.mapNotNull { plugins[it] }
    }


    override fun selectProvider(
        capability: String,
        preferences: List<String>
    ): PluginMetadata? {
        require(capability.isNotBlank()) { "Capability cannot be blank" }
        val candidates = findByCapability(capability)
        if (candidates.isEmpty()) return null

        // Build provider map for O(1) lookup instead of O(n) find() calls
        val providerMap = candidates.associateBy { it.provider }

        // Selection algorithm: Try each preference in order until a match is found.
        // This implements a priority-based selection where the first matching
        // provider in the preferences list is chosen, regardless of other candidates.
        for (pref in preferences) {
            if (pref.isNotBlank()) {
                providerMap[pref]?.let { return it }
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
        plugins.clear()
        instances.clear()
        instanceTypes.clear()
        capabilityIndex.clear()
        providerIndex.clear()
    }

    override fun isRegistered(pluginId: String): Boolean {
        return plugins.containsKey(pluginId)
    }
}

/**
 * Internal extension function for plugin instance retrieval with type safety.
 *
 * @suppress This is an internal API
 */
internal inline fun <reified T> PluginRegistry.getInstance(pluginId: String): T? {
    // Access the reified type parameter's class at runtime and call the interface method
    return getInstance(pluginId, T::class.java)
}
