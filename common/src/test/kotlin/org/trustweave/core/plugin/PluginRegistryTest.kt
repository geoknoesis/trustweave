package org.trustweave.core.plugin

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for PluginRegistry.
 *
 * **Test Isolation**: Each test uses its own isolated PluginRegistry instance.
 */
class PluginRegistryTest {

    private lateinit var registry: PluginRegistry

    @BeforeEach
    fun setup() {
        registry = DefaultPluginRegistry()
    }

    @Test
    fun `test register plugin`() {
        val metadata = PluginMetadata(
            id = "test-plugin",
            name = "Test Plugin",
            version = "1.0.0",
            provider = "test",
            capabilities = PluginCapabilities()
        )
        val instance = Any()

        registry.register(metadata, instance)

        assertEquals(metadata, registry.getMetadata("test-plugin"))
        assertEquals(instance, registry.getInstance<Any>("test-plugin"))
    }

    @Test
    fun `test unregister plugin`() {
        val metadata = PluginMetadata(
            id = "test-plugin",
            name = "Test Plugin",
            version = "1.0.0",
            provider = "test",
            capabilities = PluginCapabilities()
        )
        registry.register(metadata, Any())

        registry.unregister("test-plugin")

        assertNull(registry.getMetadata("test-plugin"))
        assertNull(registry.getInstance<Any>("test-plugin"))
    }

    @Test
    fun `test findByCapability`() {
        val metadata1 = PluginMetadata(
            id = "plugin-1",
            name = "Plugin 1",
            version = "1.0.0",
            provider = "test",
            capabilities = PluginCapabilities(features = setOf("credential-storage"))
        )
        val metadata2 = PluginMetadata(
            id = "plugin-2",
            name = "Plugin 2",
            version = "1.0.0",
            provider = "test",
            capabilities = PluginCapabilities(features = setOf("revocation"))
        )

        registry.register(metadata1, Any())
        registry.register(metadata2, Any())

        val storagePlugins = registry.findByCapability("credential-storage")
        assertEquals(1, storagePlugins.size)
        assertEquals("plugin-1", storagePlugins.first().id)
    }

    @Test
    fun `test findByProvider`() {
        val metadata1 = PluginMetadata(
            id = "waltid-1",
            name = "WaltID Plugin 1",
            version = "1.0.0",
            provider = "waltid",
            capabilities = PluginCapabilities()
        )
        val metadata2 = PluginMetadata(
            id = "waltid-2",
            name = "WaltID Plugin 2",
            version = "1.0.0",
            provider = "waltid",
            capabilities = PluginCapabilities()
        )
        val metadata3 = PluginMetadata(
            id = "godiddy-1",
            name = "GoDiddy Plugin",
            version = "1.0.0",
            provider = "godiddy",
            capabilities = PluginCapabilities()
        )

        registry.register(metadata1, Any())
        registry.register(metadata2, Any())
        registry.register(metadata3, Any())

        val waltidPlugins = registry.findByProvider("waltid")
        assertEquals(2, waltidPlugins.size)
    }

    @Test
    fun `test selectProvider with preferences`() {
        val metadata1 = PluginMetadata(
            id = "waltid-plugin",
            name = "WaltID",
            version = "1.0.0",
            provider = "waltid",
            capabilities = PluginCapabilities(features = setOf("credential-storage"))
        )
        val metadata2 = PluginMetadata(
            id = "godiddy-plugin",
            name = "GoDiddy",
            version = "1.0.0",
            provider = "godiddy",
            capabilities = PluginCapabilities(features = setOf("credential-storage"))
        )

        registry.register(metadata1, Any())
        registry.register(metadata2, Any())

        val selected = registry.selectProvider("credential-storage", listOf("waltid", "godiddy"))
        assertEquals("waltid-plugin", selected?.id)
    }

    @Test
    fun `test selectProvider without preferences`() {
        val metadata = PluginMetadata(
            id = "plugin-1",
            name = "Plugin 1",
            version = "1.0.0",
            provider = "test",
            capabilities = PluginCapabilities(features = setOf("credential-storage"))
        )

        registry.register(metadata, Any())

        val selected = registry.selectProvider("credential-storage")
        assertEquals("plugin-1", selected?.id)
    }

    @Test
    fun `test getAllPlugins`() {
        val metadata1 = PluginMetadata(
            id = "plugin-1",
            name = "Plugin 1",
            version = "1.0.0",
            provider = "test",
            capabilities = PluginCapabilities()
        )
        val metadata2 = PluginMetadata(
            id = "plugin-2",
            name = "Plugin 2",
            version = "1.0.0",
            provider = "test",
            capabilities = PluginCapabilities()
        )

        registry.register(metadata1, Any())
        registry.register(metadata2, Any())

        val allPlugins = registry.getAllPlugins()
        assertEquals(2, allPlugins.size)
    }

    @Test
    fun `test isRegistered`() {
        val metadata = PluginMetadata(
            id = "test-plugin",
            name = "Test Plugin",
            version = "1.0.0",
            provider = "test",
            capabilities = PluginCapabilities()
        )

        assertFalse(registry.isRegistered("test-plugin"))

        registry.register(metadata, Any())

        assertTrue(registry.isRegistered("test-plugin"))
    }

    @Test
    fun `test clear`() {
        val metadata = PluginMetadata(
            id = "test-plugin",
            name = "Test Plugin",
            version = "1.0.0",
            provider = "test",
            capabilities = PluginCapabilities()
        )

        registry.register(metadata, Any())
        registry.clear()

        assertTrue(registry.getAllPlugins().isEmpty())
    }

    @Test
    fun `test getInstance returns correct type`() {
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
    fun `test findBlockchainPlugins using features`() {
        val metadata = PluginMetadata(
            id = "algorand-plugin",
            name = "Algorand Plugin",
            version = "1.0.0",
            provider = "algorand",
            capabilities = PluginCapabilities(
                features = setOf("blockchain-anchoring")
            )
        )

        registry.register(metadata, Any())

        // Find by generic capability
        val blockchainPlugins = registry.findByCapability("blockchain-anchoring")
        assertEquals(1, blockchainPlugins.size)
        assertEquals("algorand-plugin", blockchainPlugins.first().id)

        // Note: Specific blockchain chain IDs are handled by the BlockchainAnchorClient
        // interface in the anchors:core module, not stored in PluginCapabilities
    }

    @Test
    fun `test findCredentialStorePlugins using findByCapability`() {
        val metadata = PluginMetadata(
            id = "store-plugin",
            name = "Store Plugin",
            version = "1.0.0",
            provider = "test",
            capabilities = PluginCapabilities(features = setOf("credential-storage"))
        )

        registry.register(metadata, Any())

        // Use generic findByCapability instead of domain-specific method
        val plugins = registry.findByCapability("credential-storage")
        assertEquals(1, plugins.size)
    }

    @Test
    fun `test credential plugin capabilities`() {
        val metadata = PluginMetadata(
            id = "ed25519-plugin",
            name = "Ed25519 Plugin",
            version = "1.0.0",
            provider = "test",
            capabilities = PluginCapabilities(
                features = setOf("credential-issuance", "credential-verification")
            )
        )

        registry.register(metadata, Any())

        // Find by generic capability
        val plugins = registry.findByCapability("credential-issuance")
        assertEquals(1, plugins.size)

        // Note: Specific proof types and schema formats are handled by the
        // CredentialService interface in credentials:core module (via supportedProofTypes
        // and supportedSchemaFormats properties), not stored in PluginCapabilities
    }
}

