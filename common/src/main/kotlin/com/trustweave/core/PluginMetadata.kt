package com.trustweave.core

/**
 * Metadata about a plugin/provider.
 *
 * Plugins are the fundamental unit of extensibility in trustweave.
 * Each plugin provides metadata describing its capabilities, dependencies, and configuration.
 *
 * @param id Unique identifier for the plugin (e.g., "waltid-credential-service")
 * @param name Human-readable name (e.g., "walt.id Credential Service")
 * @param version Plugin version (e.g., "1.0.0")
 * @param description Optional description of the plugin
 * @param provider Provider name (e.g., "waltid", "godiddy", "native", "custom")
 * @param capabilities Capabilities provided by this plugin
 * @param dependencies List of plugin dependencies
 * @param configuration Plugin-specific configuration
 */
data class PluginMetadata(
    val id: String,
    val name: String,
    val version: String,
    val description: String? = null,
    val provider: String, // "waltid", "godiddy", "native", "custom"
    val capabilities: PluginCapabilities,
    val dependencies: List<PluginDependency> = emptyList(),
    val configuration: Map<String, Any?> = emptyMap()
)

/**
 * Capabilities of a plugin.
 *
 * Describes what the plugin can do - which blockchains it supports,
 * which DID methods, proof types, schema formats, etc.
 *
 * @param supportedBlockchains List of supported blockchain chain IDs (CAIP-2 format)
 * @param supportedDidMethods List of supported DID method names (e.g., "key", "web", "ion")
 * @param supportedProofTypes List of supported proof types (e.g., "Ed25519Signature2020", "JsonWebSignature2020")
 * @param supportedSchemaFormats List of supported schema validation formats
 * @param supportedCredentialFormats List of supported credential formats (e.g., "w3c-vc", "sd-jwt-vc")
 * @param features Set of feature flags (e.g., "selective-disclosure", "revocation", "credential-storage")
 */
data class PluginCapabilities(
    val supportedBlockchains: List<String> = emptyList(),
    val supportedDidMethods: List<String> = emptyList(),
    val supportedProofTypes: List<String> = emptyList(),
    val supportedSchemaFormats: List<SchemaFormat> = emptyList(),
    val supportedCredentialFormats: List<String> = emptyList(),
    val features: Set<String> = emptySet() // "selective-disclosure", "revocation", "credential-storage", etc.
)

/**
 * Plugin dependency information.
 *
 * @param pluginId ID of the required plugin
 * @param versionRange Optional version range requirement (e.g., ">=1.0.0,<2.0.0")
 * @param optional Whether the dependency is optional
 */
data class PluginDependency(
    val pluginId: String,
    val versionRange: String? = null,
    val optional: Boolean = false
)

/**
 * Plugin lifecycle events.
 *
 * Plugins can implement this interface to receive lifecycle callbacks
 * for initialization, startup, shutdown, and cleanup.
 */
interface PluginLifecycle {
    /**
     * Initialize the plugin with configuration.
     * Called before the plugin is used.
     *
     * @param config Plugin configuration
     * @return true if initialization succeeded, false otherwise
     */
    suspend fun initialize(config: Map<String, Any?>): Boolean

    /**
     * Start the plugin.
     * Called after initialization, before the plugin is actively used.
     *
     * @return true if startup succeeded, false otherwise
     */
    suspend fun start(): Boolean

    /**
     * Stop the plugin.
     * Called when the plugin should stop accepting new operations.
     *
     * @return true if stop succeeded, false otherwise
     */
    suspend fun stop(): Boolean

    /**
     * Cleanup plugin resources.
     * Called after stop, for final cleanup.
     */
    suspend fun cleanup()
}

/**
 * Schema format enumeration.
 * Used to specify which schema validation format a plugin supports.
 */
enum class SchemaFormat {
    /** JSON Schema Draft 7 or Draft 2020-12 */
    JSON_SCHEMA,

    /** SHACL (Shapes Constraint Language) for RDF validation */
    SHACL
}


