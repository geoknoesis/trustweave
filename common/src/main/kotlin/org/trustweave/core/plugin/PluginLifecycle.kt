package org.trustweave.core.plugin

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
