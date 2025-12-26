package org.trustweave.core.plugin

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

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
    fun `test PluginConfig getConfigInt`() {
        val pluginConfig = PluginConfig(
            id = "test-plugin",
            type = PluginType.CREDENTIAL_SERVICE,
            provider = "test",
            config = mapOf("port" to "8080", "invalid" to "not-a-number")
        )

        assertEquals(8080, pluginConfig.getConfigInt("port"))
        assertNull(pluginConfig.getConfigInt("invalid"))
        assertNull(pluginConfig.getConfigInt("nonexistent"))
    }

    @Test
    fun `test PluginConfig getConfigBoolean`() {
        val pluginConfig = PluginConfig(
            id = "test-plugin",
            type = PluginType.CREDENTIAL_SERVICE,
            provider = "test",
            config = mapOf("enabled" to "true", "disabled" to "false", "invalid" to "maybe")
        )

        assertEquals(true, pluginConfig.getConfigBoolean("enabled"))
        assertEquals(false, pluginConfig.getConfigBoolean("disabled"))
        assertNull(pluginConfig.getConfigBoolean("invalid"))
        assertNull(pluginConfig.getConfigBoolean("nonexistent"))
    }

    @Test
    fun `test PluginConfig getConfigLong`() {
        val pluginConfig = PluginConfig(
            id = "test-plugin",
            type = PluginType.CREDENTIAL_SERVICE,
            provider = "test",
            config = mapOf("timestamp" to "1234567890", "invalid" to "not-a-long")
        )

        assertEquals(1234567890L, pluginConfig.getConfigLong("timestamp"))
        assertNull(pluginConfig.getConfigLong("invalid"))
        assertNull(pluginConfig.getConfigLong("nonexistent"))
    }

    @Test
    fun `test PluginConfig getConfigDouble`() {
        val pluginConfig = PluginConfig(
            id = "test-plugin",
            type = PluginType.CREDENTIAL_SERVICE,
            provider = "test",
            config = mapOf("ratio" to "3.14", "invalid" to "not-a-double")
        )

        assertEquals(3.14, pluginConfig.getConfigDouble("ratio"))
        assertNull(pluginConfig.getConfigDouble("invalid"))
        assertNull(pluginConfig.getConfigDouble("nonexistent"))
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
    fun `test PluginConfig getConfigAsJsonObject with JSON values`() {
        val pluginConfig = PluginConfig(
            id = "test-plugin",
            type = PluginType.CREDENTIAL_SERVICE,
            provider = "test",
            config = mapOf(
                "simple" to "value",
                "json" to """{"nested": "object"}"""
            )
        )

        val jsonObject = pluginConfig.getConfigAsJsonObject()

        assertEquals("value", jsonObject["simple"]?.jsonPrimitive?.content)
        // JSON value should be parsed as JsonObject
        assertTrue(jsonObject["json"] is JsonObject)
    }

    @Test
    fun `test PluginConfig getConfigAsJsonObject with invalid JSON falls back to string`() {
        val pluginConfig = PluginConfig(
            id = "test-plugin",
            type = PluginType.CREDENTIAL_SERVICE,
            provider = "test",
            config = mapOf("invalid" to "{ invalid json }")
        )

        val jsonObject = pluginConfig.getConfigAsJsonObject()

        // Should fall back to string when JSON parsing fails
        assertEquals("{ invalid json }", jsonObject["invalid"]?.jsonPrimitive?.content)
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

