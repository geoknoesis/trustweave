package org.trustweave.core.plugin

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
 * Describes what the plugin can do. This class is domain-agnostic:
 * - Use `features` for generic feature flags (e.g., "selective-disclosure", "revocation")
 * - Use `extensions` only for truly custom/unknown capabilities not handled by domain modules
 *
 * **Important:** Domain-specific capabilities (blockchains, DID methods, proof types, etc.)
 * should be handled by their respective domain modules, not stored here. For example:
 * - Blockchain capabilities → `anchors:core` module (via `BlockchainAnchorClient` interface)
 * - DID method capabilities → `did:core` module (via `DidMethod` interface)
 * - Credential proof types → `credentials:core` module (via `CredentialService` interface)
 *
 * **Example:**
 * ```kotlin
 * val capabilities = PluginCapabilities(
 *     features = setOf("selective-disclosure", "revocation")
 *     // Domain-specific capabilities are handled by domain modules, not stored here
 * )
 * ```
 *
 * @param features Set of feature flags (e.g., "selective-disclosure", "revocation", "credential-storage")
 * @param extensions Extensible map for truly custom capabilities not handled by domain modules
 */
data class PluginCapabilities(
    val features: Set<String> = emptySet(),
    val extensions: Map<String, Any> = emptyMap()
)

/**
 * Plugin dependency information.
 *
 * @param pluginId ID of the required plugin
 * @param versionRange Optional version range requirement (e.g., ">=1.0.0,<2.0.0")
 * @param isOptional Whether the dependency is optional
 */
data class PluginDependency(
    val pluginId: String,
    val versionRange: String? = null,
    val isOptional: Boolean = false
)


