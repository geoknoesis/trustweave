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
 *
 * **When to Implement:**
 * - Database-backed plugins (need connection initialization)
 * - Remote service plugins (need connection establishment)
 * - File-based storage (need directory creation)
 * - Blockchain clients (need network connection setup)
 * - Any plugin requiring external resources
 * - Production deployments with persistent storage
 *
 * **When NOT to Implement:**
 * - In-memory implementations (e.g., `InMemoryKeyManagementService`)
 * - Simple test scenarios
 * - Stateless plugins
 * - Quick start examples
 *
 * **Example:**
 * ```kotlin
 * class DatabasePlugin : CredentialService, PluginLifecycle {
 *     private var connection: Connection? = null
 *
 *     override suspend fun initialize(config: Map<String, Any?>): Boolean {
 *         val url = config["databaseUrl"] as? String ?: return false
 *         connection = DriverManager.getConnection(url)
 *         return true
 *     }
 *
 *     override suspend fun start(): Boolean {
 *         // Start connection pool, background threads, etc.
 *         return connection != null
 *     }
 *
 *     override suspend fun stop(): Boolean {
 *         // Stop background processes
 *         return true
 *     }
 *
 *     override suspend fun cleanup() {
 *         connection?.close()
 *         connection = null
 *     }
 * }
 * ```
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

