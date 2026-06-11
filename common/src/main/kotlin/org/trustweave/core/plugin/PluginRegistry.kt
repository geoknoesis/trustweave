package org.trustweave.core.plugin

import org.trustweave.core.exception.PluginException
import org.trustweave.core.exception.TrustWeaveException
import java.util.concurrent.ConcurrentHashMap

/**
 * Internal interface for plugin registry operations.
 *
 * This is an internal infrastructure component. Use domain-specific registries instead:
 * - `DidMethodRegistry` for DID method registration
 * - `BlockchainAnchorRegistry` for blockchain anchor client registration
 * - `TrustRegistry` for trust anchor management
 * - `CredentialService` wiring (via `TrustWeave` / `CredentialServices`) for credential operations
 *
 * @suppress This is an internal API
 */
internal interface PluginRegistry {
    /**
     * Register a plugin with metadata.
     *
     * @param metadata Plugin metadata describing capabilities
     * @param instance Plugin instance
     * @throws org.trustweave.core.exception.PluginException.BlankId if plugin ID is blank
     * @throws org.trustweave.core.exception.PluginException.AlreadyRegistered if plugin is already registered
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

    /**
     * Guards all multi-map mutations (register/unregister/clear) so they are atomic
     * with respect to readers. Reads stay lock-free on the ConcurrentHashMaps; the
     * mutation order below (populate instances first, publish metadata last on register;
     * retract metadata first, remove instances last on unregister) guarantees the
     * invariant "metadata visible ⇒ instance visible". Registration is not a hot path,
     * so a single lock is fine.
     */
    private val mutationLock = Any()

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
            throw PluginException.BlankId
        }

        synchronized(mutationLock) {
            val existing = plugins[metadata.id]
            if (existing != null) {
                throw PluginException.AlreadyRegistered(
                    pluginId = metadata.id,
                    existingPlugin = existing.name
                )
            }

            // Populate instance state and indexes BEFORE publishing metadata, so a
            // concurrent reader that observes getMetadata(id) != null is guaranteed
            // to also observe the instance via getInstance(id, ...).
            instances[metadata.id] = instance
            instanceTypes[metadata.id] = instance.javaClass

            metadata.capabilities.features.forEach { capability ->
                capabilityIndex.computeIfAbsent(capability) {
                    ConcurrentHashMap.newKeySet()
                }.add(metadata.id)
            }
            providerIndex.computeIfAbsent(metadata.provider) {
                ConcurrentHashMap.newKeySet()
            }.add(metadata.id)

            // Publish last: metadata visibility is the signal that the plugin is registered.
            plugins[metadata.id] = metadata
        }
    }

    override fun unregister(pluginId: String) {
        require(pluginId.isNotBlank()) { "Plugin ID cannot be blank" }
        synchronized(mutationLock) {
            // Idempotent operation: silently return if plugin not found
            val metadata = plugins[pluginId] ?: return

            // Mirror of register: retract metadata FIRST so readers never observe
            // metadata for a plugin whose instance has already been removed.
            plugins.remove(pluginId)

            // Remove from capability index, pruning empty sets to avoid unbounded growth.
            metadata.capabilities.features.forEach { capability ->
                capabilityIndex.computeIfPresent(capability) { _, ids ->
                    ids.remove(pluginId)
                    if (ids.isEmpty()) null else ids
                }
            }

            // Remove from provider index, pruning empty sets.
            providerIndex.computeIfPresent(metadata.provider) { _, ids ->
                ids.remove(pluginId)
                if (ids.isEmpty()) null else ids
            }

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
        synchronized(mutationLock) {
            // Retract metadata first (mirrors unregister) so readers never observe
            // metadata without a corresponding instance.
            plugins.clear()
            capabilityIndex.clear()
            providerIndex.clear()
            instances.clear()
            instanceTypes.clear()
        }
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
