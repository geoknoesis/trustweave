package io.geoknoesis.vericore.spi

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for PluginMetadata, PluginCapabilities, PluginDependency, SchemaFormat.
 */
class PluginMetadataTest {

    @Test
    fun `test PluginMetadata with all fields`() {
        val capabilities = PluginCapabilities(
            supportedBlockchains = listOf("algorand:testnet"),
            supportedProofTypes = listOf("Ed25519Signature2020"),
            features = setOf("credential-storage")
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
    fun `test PluginCapabilities with all fields`() {
        val capabilities = PluginCapabilities(
            supportedBlockchains = listOf("algorand:testnet", "eip155:137"),
            supportedDidMethods = listOf("key", "web"),
            supportedProofTypes = listOf("Ed25519Signature2020", "JsonWebSignature2020"),
            supportedSchemaFormats = listOf(SchemaFormat.JSON_SCHEMA, SchemaFormat.SHACL),
            supportedCredentialFormats = listOf("w3c-vc", "sd-jwt-vc"),
            features = setOf("selective-disclosure", "revocation", "credential-storage")
        )
        
        assertEquals(2, capabilities.supportedBlockchains.size)
        assertEquals(2, capabilities.supportedDidMethods.size)
        assertEquals(2, capabilities.supportedProofTypes.size)
        assertEquals(2, capabilities.supportedSchemaFormats.size)
        assertEquals(2, capabilities.supportedCredentialFormats.size)
        assertEquals(3, capabilities.features.size)
    }

    @Test
    fun `test PluginCapabilities with defaults`() {
        val capabilities = PluginCapabilities()
        
        assertTrue(capabilities.supportedBlockchains.isEmpty())
        assertTrue(capabilities.supportedDidMethods.isEmpty())
        assertTrue(capabilities.supportedProofTypes.isEmpty())
        assertTrue(capabilities.supportedSchemaFormats.isEmpty())
        assertTrue(capabilities.supportedCredentialFormats.isEmpty())
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

    @Test
    fun `test SchemaFormat enum values`() {
        assertEquals(SchemaFormat.JSON_SCHEMA, SchemaFormat.valueOf("JSON_SCHEMA"))
        assertEquals(SchemaFormat.SHACL, SchemaFormat.valueOf("SHACL"))
        assertEquals(2, SchemaFormat.values().size)
    }
}


