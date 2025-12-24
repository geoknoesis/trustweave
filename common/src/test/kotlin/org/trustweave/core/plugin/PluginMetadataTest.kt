package org.trustweave.core.plugin

import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for PluginMetadata, PluginCapabilities, PluginDependency.
 */
class PluginMetadataTest {

    @Test
    fun `test PluginMetadata with all fields`() {
        val capabilities = PluginCapabilities(
            features = setOf("credential-storage"),
            extensions = mapOf("customCapability" to "customValue")
        )
        val dependencies = listOf(
            PluginDependency(pluginId = "kms-plugin", versionRange = ">=1.0.0", optional = false)
        )

        val metadata = PluginMetadata(
            id = "test-plugin",
            name = "Test Plugin",
            version = "1.0.0",
            description = "A test plugin",
            provider = "test",
            capabilities = capabilities,
            dependencies = dependencies,
            configuration = mapOf("key" to "value")
        )

        assertEquals("test-plugin", metadata.id)
        assertEquals("Test Plugin", metadata.name)
        assertEquals("1.0.0", metadata.version)
        assertEquals("A test plugin", metadata.description)
        assertEquals("test", metadata.provider)
        assertEquals(capabilities, metadata.capabilities)
        assertEquals(1, metadata.dependencies.size)
        assertEquals(1, metadata.configuration.size)
    }

    @Test
    fun `test PluginMetadata with defaults`() {
        val metadata = PluginMetadata(
            id = "test-plugin",
            name = "Test Plugin",
            version = "1.0.0",
            provider = "test",
            capabilities = PluginCapabilities()
        )

        assertNull(metadata.description)
        assertTrue(metadata.dependencies.isEmpty())
        assertTrue(metadata.configuration.isEmpty())
    }

    @Test
    fun `test PluginCapabilities with features and custom extensions`() {
        val capabilities = PluginCapabilities(
            features = setOf("selective-disclosure", "revocation", "credential-storage"),
            extensions = mapOf(
                // Custom capabilities not handled by domain modules
                "customFeature" to "customValue",
                "customList" to listOf("item1", "item2")
            )
        )

        assertEquals(3, capabilities.features.size)
        assertEquals(2, capabilities.extensions.size)
        assertEquals("customValue", capabilities.extensions["customFeature"])

        // Note: Domain-specific capabilities (blockchains, DID methods, proof types, etc.)
        // should be handled by their respective domain modules, not stored in extensions
    }

    @Test
    fun `test PluginCapabilities with defaults`() {
        val capabilities = PluginCapabilities()

        assertTrue(capabilities.extensions.isEmpty())
        assertTrue(capabilities.features.isEmpty())
    }

    @Test
    fun `test PluginDependency with all fields`() {
        val dependency = PluginDependency(
            pluginId = "kms-plugin",
            versionRange = ">=1.0.0,<2.0.0",
            optional = true
        )

        assertEquals("kms-plugin", dependency.pluginId)
        assertEquals(">=1.0.0,<2.0.0", dependency.versionRange)
        assertTrue(dependency.optional)
    }

    @Test
    fun `test PluginDependency with defaults`() {
        val dependency = PluginDependency(pluginId = "kms-plugin")

        assertEquals("kms-plugin", dependency.pluginId)
        assertNull(dependency.versionRange)
        assertFalse(dependency.optional)
    }
}

