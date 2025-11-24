package com.trustweave.core.plugin

/**
 * Helper functions for plugin-related tests.
 */

/**
 * Creates a test PluginMetadata with default values.
 */
fun createTestPluginMetadata(
    id: String = "test-plugin",
    name: String = "Test Plugin",
    version: String = "1.0.0",
    provider: String = "test",
    capabilities: PluginCapabilities = PluginCapabilities()
): PluginMetadata {
    return PluginMetadata(
        id = id,
        name = name,
        version = version,
        provider = provider,
        capabilities = capabilities
    )
}

