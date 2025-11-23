package com.trustweave.core

import java.util.concurrent.ConcurrentHashMap

/**
 * Unified plugin registry for all TrustWeave components.
 *
 * Provides capability-based discovery and provider selection.
 * This registry is the central hub for all plugins in TrustWeave,
 * enabling dynamic discovery and selection of providers based on capabilities.
 *
 * **Thread Safety**: This registry is thread-safe for concurrent access.
 *
 * **Example Usage**:
 * ```kotlin
 * // Register a plugin
 * val metadata = PluginMetadata(
 *     id = "waltid-credential",
 *     name = "walt.id Credential Service",
 *     version = "1.0.0",
 *     provider = "waltid",
 *     capabilities = PluginCapabilities(
 *         supportedProofTypes = listOf("Ed25519Signature2020", "JsonWebSignature2020"),
 *         supportedSchemaFormats = listOf(SchemaFormat.JSON_SCHEMA),
 *         features = setOf("credential-storage", "revocation")
 *     )
 * )
 * PluginRegistry.register(metadata, credentialService)
 *
 * // Find plugins by capability
 * val credentialPlugins = PluginRegistry.findByCapability("credential-storage")
 *
 * // Select best provider
 * val provider = PluginRegistry.selectProvider("credential-storage", listOf("waltid", "godiddy"))
 * ```
 */
object PluginRegistry {
    private val plugins = ConcurrentHashMap<String, PluginMetadata>()
    private val instances = ConcurrentHashMap<String, Any>()

    /**
     * Register a plugin with metadata.
     *
     * @param metadata Plugin metadata describing capabilities
     * @param instance Plugin instance
     */
    fun register(metadata: PluginMetadata, instance: Any) {
        plugins[metadata.id] = metadata
        instances[metadata.id] = instance
    }

    /**
     * Unregister a plugin.
     *
     * @param pluginId Plugin ID to unregister
     */
    fun unregister(pluginId: String) {
        plugins.remove(pluginId)
        instances.remove(pluginId)
    }

    /**
     * Get plugin metadata.
     *
     * @param pluginId Plugin ID
     * @return Plugin metadata, or null if not found
     */
    fun getMetadata(pluginId: String): PluginMetadata? {
        return plugins[pluginId]
    }

    /**
     * Get plugin instance.
     *
     * @param pluginId Plugin ID
     * @return Plugin instance, or null if not found
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getInstance(pluginId: String): T? {
        return instances[pluginId] as? T
    }

    /**
     * Find plugins by capability/feature.
     *
     * @param capability Feature name (e.g., "credential-storage", "revocation")
     * @return List of plugins that support this capability
     */
    fun findByCapability(capability: String): List<PluginMetadata> {
        return plugins.values.filter {
            it.capabilities.features.contains(capability)
        }
    }

    /**
     * Find blockchain plugins for a specific chain.
     *
     * @param chainId Chain ID (CAIP-2 format, e.g., "algorand:testnet")
     * @return List of plugins that support this blockchain
     */
    fun findBlockchainPlugins(chainId: String): List<PluginMetadata> {
        return plugins.values.filter {
            it.capabilities.supportedBlockchains.contains(chainId)
        }
    }

    /**
     * Find credential store plugins.
     *
     * @return List of plugins that provide credential storage
     */
    fun findCredentialStorePlugins(): List<PluginMetadata> {
        return plugins.values.filter {
            it.capabilities.features.contains("credential-storage")
        }
    }

    /**
     * Find plugins by provider name.
     *
     * @param provider Provider name (e.g., "waltid", "godiddy")
     * @return List of plugins from this provider
     */
    fun findByProvider(provider: String): List<PluginMetadata> {
        return plugins.values.filter { it.provider == provider }
    }

    /**
     * Find plugins that support a specific proof type.
     *
     * @param proofType Proof type (e.g., "Ed25519Signature2020")
     * @return List of plugins that support this proof type
     */
    fun findByProofType(proofType: String): List<PluginMetadata> {
        return plugins.values.filter {
            it.capabilities.supportedProofTypes.contains(proofType)
        }
    }

    /**
     * Find plugins that support a specific schema format.
     *
     * @param format Schema format (JSON_SCHEMA or SHACL)
     * @return List of plugins that support this schema format
     */
    fun findBySchemaFormat(format: SchemaFormat): List<PluginMetadata> {
        return plugins.values.filter {
            it.capabilities.supportedSchemaFormats.contains(format)
        }
    }

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
        preferences: List<String> = emptyList()
    ): PluginMetadata? {
        val candidates = findByCapability(capability)

        // Try preferences first
        for (pref in preferences) {
            candidates.find { it.provider == pref }?.let { return it }
        }

        // Return first available
        return candidates.firstOrNull()
    }

    /**
     * Get all registered plugins.
     *
     * @return List of all plugin metadata
     */
    fun getAllPlugins(): List<PluginMetadata> {
        return plugins.values.toList()
    }

    /**
     * Clear all registered plugins.
     * Useful for testing.
     */
    fun clear() {
        plugins.clear()
        instances.clear()
    }

    /**
     * Check if a plugin is registered.
     *
     * @param pluginId Plugin ID
     * @return true if plugin is registered
     */
    fun isRegistered(pluginId: String): Boolean {
        return plugins.containsKey(pluginId)
    }
}


