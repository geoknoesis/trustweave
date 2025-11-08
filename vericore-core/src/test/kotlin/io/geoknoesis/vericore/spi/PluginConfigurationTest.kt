package io.geoknoesis.vericore.spi

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*
import java.io.File

/**
 * Comprehensive tests for PluginConfiguration, PluginConfig, PluginType, PluginConfigurationLoader.
 */
class PluginConfigurationTest {

    @Test
    fun `test PluginConfiguration with all fields`() {
        val config = PluginConfiguration(
            plugins = listOf(
                PluginConfig(
                    id = "test-plugin",
                    type = PluginType.CREDENTIAL_SERVICE,
                    provider = "test",
                    enabled = true,
                    config = mapOf("key" to "value"),
                    priority = 1
                )
            ),
            defaultProviders = mapOf("credential-service" to "test"),
            providerChains = mapOf("credential-service" to listOf("test", "fallback"))
        )
        
        assertEquals(1, config.plugins.size)
        assertEquals(1, config.defaultProviders.size)
        assertEquals(1, config.providerChains.size)
    }

    @Test
    fun `test PluginConfiguration with defaults`() {
        val config = PluginConfiguration()
        
        assertTrue(config.plugins.isEmpty())
        assertTrue(config.defaultProviders.isEmpty())
        assertTrue(config.providerChains.isEmpty())
    }

    @Test
    fun `test PluginConfig with all fields`() {
        val pluginConfig = PluginConfig(
            id = "test-plugin",
            type = PluginType.CREDENTIAL_SERVICE,
            provider = "test",
            enabled = true,
            config = mapOf("key1" to "value1", "key2" to "value2"),
            priority = 5
        )
        
        assertEquals("test-plugin", pluginConfig.id)
        assertEquals(PluginType.CREDENTIAL_SERVICE, pluginConfig.type)
        assertEquals("test", pluginConfig.provider)
        assertTrue(pluginConfig.enabled)
        assertEquals(2, pluginConfig.config.size)
        assertEquals(5, pluginConfig.priority)
    }

    @Test
    fun `test PluginConfig with defaults`() {
        val pluginConfig = PluginConfig(
            id = "test-plugin",
            type = PluginType.CREDENTIAL_SERVICE,
            provider = "test"
        )
        
        assertTrue(pluginConfig.enabled)
        assertTrue(pluginConfig.config.isEmpty())
        assertEquals(0, pluginConfig.priority)
    }

    @Test
    fun `test PluginConfig getConfigValue`() {
        val pluginConfig = PluginConfig(
            id = "test-plugin",
            type = PluginType.CREDENTIAL_SERVICE,
            provider = "test",
            config = mapOf("key" to "value")
        )
        
        assertEquals("value", pluginConfig.getConfigValue("key"))
        assertNull(pluginConfig.getConfigValue("nonexistent"))
    }

    @Test
    fun `test PluginConfig getConfigAsJsonObject`() {
        val pluginConfig = PluginConfig(
            id = "test-plugin",
            type = PluginType.CREDENTIAL_SERVICE,
            provider = "test",
            config = mapOf("key" to "value", "number" to "42")
        )
        
        val jsonObject = pluginConfig.getConfigAsJsonObject()
        
        assertEquals("value", jsonObject["key"]?.jsonPrimitive?.content)
        assertEquals("42", jsonObject["number"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test PluginType enum values`() {
        assertEquals(PluginType.BLOCKCHAIN, PluginType.valueOf("BLOCKCHAIN"))
        assertEquals(PluginType.CREDENTIAL_STORE, PluginType.valueOf("CREDENTIAL_STORE"))
        assertEquals(PluginType.CREDENTIAL_SERVICE, PluginType.valueOf("CREDENTIAL_SERVICE"))
        assertEquals(PluginType.TRUST_SERVICE, PluginType.valueOf("TRUST_SERVICE"))
        assertEquals(PluginType.SCHEMA_VALIDATOR, PluginType.valueOf("SCHEMA_VALIDATOR"))
        assertEquals(PluginType.PROOF_GENERATOR, PluginType.valueOf("PROOF_GENERATOR"))
        assertEquals(PluginType.REVOCATION_SERVICE, PluginType.valueOf("REVOCATION_SERVICE"))
        assertEquals(PluginType.DID_METHOD, PluginType.valueOf("DID_METHOD"))
        assertEquals(PluginType.KMS, PluginType.valueOf("KMS"))
        assertEquals(PluginType.PRESENTATION_PROTOCOL, PluginType.valueOf("PRESENTATION_PROTOCOL"))
        assertEquals(PluginType.QUERY_ENGINE, PluginType.valueOf("QUERY_ENGINE"))
        assertEquals(11, PluginType.values().size)
    }

    @Test
    fun `test PluginConfigurationLoader loadFromJson`() {
        val jsonString = """
        {
            "plugins": [
                {
                    "id": "test-plugin",
                    "type": "CREDENTIAL_SERVICE",
                    "provider": "test",
                    "enabled": true,
                    "priority": 1
                }
            ],
            "defaultProviders": {
                "credential-service": "test"
            },
            "providerChains": {
                "credential-service": ["test", "fallback"]
            }
        }
        """
        
        val config = PluginConfigurationLoader.loadFromJson(jsonString)
        
        assertEquals(1, config.plugins.size)
        assertEquals("test-plugin", config.plugins.first().id)
        assertEquals(1, config.defaultProviders.size)
        assertEquals(1, config.providerChains.size)
    }

    @Test
    fun `test PluginConfigurationLoader loadFromJsonObject`() {
        val jsonObject = buildJsonObject {
            put("plugins", buildJsonArray {
                addJsonObject {
                    put("id", "test-plugin")
                    put("type", "CREDENTIAL_SERVICE")
                    put("provider", "test")
                }
            })
        }
        
        val config = PluginConfigurationLoader.loadFromJsonObject(jsonObject)
        
        assertEquals(1, config.plugins.size)
        assertEquals("test-plugin", config.plugins.first().id)
    }
}

