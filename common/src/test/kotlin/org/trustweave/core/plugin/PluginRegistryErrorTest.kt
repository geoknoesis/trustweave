package org.trustweave.core.plugin

import org.trustweave.core.exception.TrustWeaveException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Error handling tests for PluginRegistry.
 *
 * **Test Isolation**: Each test uses its own isolated PluginRegistry instance.
 */
class PluginRegistryErrorTest {

    private lateinit var registry: PluginRegistry

    @BeforeEach
    fun setup() {
        registry = DefaultPluginRegistry()
    }

    @Test
    fun `test register throws BlankPluginId when ID is blank`() {
        val metadata = PluginMetadata(
            id = "",
            name = "Test Plugin",
            version = "1.0.0",
            provider = "test",
            capabilities = PluginCapabilities()
        )

        val exception = assertFailsWith<TrustWeaveException.BlankPluginId> {
            registry.register(metadata, Any())
        }

        assertEquals("BLANK_PLUGIN_ID", exception.code)
    }

    @Test
    fun `test register throws BlankPluginId when ID is whitespace`() {
        val metadata = PluginMetadata(
            id = "   ",
            name = "Test Plugin",
            version = "1.0.0",
            provider = "test",
            capabilities = PluginCapabilities()
        )

        val exception = assertFailsWith<TrustWeaveException.BlankPluginId> {
            registry.register(metadata, Any())
        }

        assertEquals("BLANK_PLUGIN_ID", exception.code)
    }

    @Test
    fun `test register throws PluginAlreadyRegistered when plugin already exists`() {
        val metadata = PluginMetadata(
            id = "test-plugin",
            name = "Test Plugin",
            version = "1.0.0",
            provider = "test",
            capabilities = PluginCapabilities()
        )

        registry.register(metadata, Any())

        val exception = assertFailsWith<TrustWeaveException.PluginAlreadyRegistered> {
            registry.register(metadata, Any())
        }

        assertEquals("PLUGIN_ALREADY_REGISTERED", exception.code)
        assertEquals("test-plugin", exception.pluginId)
        assertEquals("Test Plugin", exception.existingPlugin)
    }

    @Test
    fun `test getInstance returns null for wrong type`() {
        val metadata = PluginMetadata(
            id = "test-plugin",
            name = "Test Plugin",
            version = "1.0.0",
            provider = "test",
            capabilities = PluginCapabilities()
        )

        registry.register(metadata, "string-instance")

        val result = registry.getInstance<Int>("test-plugin")

        assertNull(result)
    }

    @Test
    fun `test getInstance returns instance for correct type`() {
        val metadata = PluginMetadata(
            id = "test-plugin",
            name = "Test Plugin",
            version = "1.0.0",
            provider = "test",
            capabilities = PluginCapabilities()
        )

        val instance = listOf(1, 2, 3)
        registry.register(metadata, instance)

        val result = registry.getInstance<List<Int>>("test-plugin")

        assertNotNull(result)
        assertEquals(instance, result)
    }

    @Test
    fun `test getInstance returns null for non-existent plugin`() {
        val result = registry.getInstance<Any>("nonexistent")

        assertNull(result)
    }

    @Test
    fun `test findByCapability throws exception for blank capability`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            registry.findByCapability("")
        }

        assertTrue(exception.message?.contains("blank") == true)
    }

    @Test
    fun `test findByProvider throws exception for blank provider`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            registry.findByProvider("")
        }

        assertTrue(exception.message?.contains("blank") == true)
    }

    @Test
    fun `test selectProvider throws exception for blank capability`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            registry.selectProvider("")
        }

        assertTrue(exception.message?.contains("blank") == true)
    }

    @Test
    fun `test selectProvider handles blank preferences gracefully`() {
        val metadata = PluginMetadata(
            id = "test-plugin",
            name = "Test Plugin",
            version = "1.0.0",
            provider = "test",
            capabilities = PluginCapabilities(features = setOf("test-capability"))
        )

        registry.register(metadata, Any())

        val selected = registry.selectProvider("test-capability", listOf("", "  ", "test"))

        assertNotNull(selected)
        assertEquals("test-plugin", selected.id)
    }
}

