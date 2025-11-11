package com.geoknoesis.vericore.spi

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/**
 * Plugin configuration loaded from YAML/JSON.
 *
 * Allows declarative configuration of plugins via configuration files.
 * Supports both YAML and JSON formats.
 *
 * **Example Configuration**:
 * ```yaml
 * plugins:
 *   - id: waltid-credential
 *     type: CREDENTIAL_SERVICE
 *     provider: waltid
 *     enabled: true
 *     priority: 1
 *     config:
 *       baseUrl: "https://issuer.walt.id"
 *
 * defaultProviders:
 *   credential-service: waltid
 *   blockchain: algorand
 *
 * providerChains:
 *   credential-service:
 *     - waltid
 *     - godiddy
 *     - native
 * ```
 */
@Serializable
data class PluginConfiguration(
    val plugins: List<PluginConfig> = emptyList(),
    val defaultProviders: Map<String, String> = emptyMap(),
    val providerChains: Map<String, List<String>> = emptyMap()
)

@Serializable
data class PluginConfig(
    val id: String,
    val type: PluginType,
    val provider: String,
    val enabled: Boolean = true,
    val config: Map<String, String> = emptyMap(),
    val priority: Int = 0
) {
    /**
     * Get configuration value as typed object.
     * Converts string values to appropriate types.
     */
    fun getConfigValue(key: String): Any? {
        return config[key]
    }

    /**
     * Get configuration as JsonObject for complex configurations.
     */
    fun getConfigAsJsonObject(): JsonObject {
        return buildJsonObject {
            config.forEach { (key, value) ->
                // Try to parse as JSON, fallback to string
                try {
                    val jsonElement = Json.parseToJsonElement(value)
                    put(key, jsonElement)
                } catch (e: Exception) {
                    put(key, value)
                }
            }
        }
    }
}

/**
 * Plugin type enumeration.
 * Defines the category of plugin.
 */
enum class PluginType {
    /** Blockchain adapter plugin */
    BLOCKCHAIN,

    /** Credential storage/wallet plugin */
    CREDENTIAL_STORE,

    /** Credential service (issuance/verification) plugin */
    CREDENTIAL_SERVICE,

    /** Trust service plugin */
    TRUST_SERVICE,

    /** Schema validator plugin */
    SCHEMA_VALIDATOR,

    /** Proof generator plugin */
    PROOF_GENERATOR,

    /** Revocation service plugin */
    REVOCATION_SERVICE,

    /** DID method plugin */
    DID_METHOD,

    /** Key management service plugin */
    KMS,

    /** Presentation protocol plugin */
    PRESENTATION_PROTOCOL,

    /** Query engine plugin */
    QUERY_ENGINE
}

/**
 * Load plugin configuration from file.
 *
 * Supports both JSON and YAML formats (YAML requires additional dependency).
 */
object PluginConfigurationLoader {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Load plugin configuration from JSON file.
     *
     * @param path File path
     * @return Plugin configuration
     */
    suspend fun loadFromFile(path: String): PluginConfiguration {
        val file = File(path)
        if (!file.exists()) {
            throw IllegalArgumentException("Configuration file not found: $path")
        }

        val content = file.readText()
        return loadFromJson(content)
    }

    /**
     * Load plugin configuration from classpath resource.
     *
     * @param resource Resource path (e.g., "vericore-plugins.json")
     * @return Plugin configuration
     */
    suspend fun loadFromResource(resource: String): PluginConfiguration {
        val inputStream = PluginConfigurationLoader::class.java.classLoader
            .getResourceAsStream(resource)
            ?: throw IllegalArgumentException("Resource not found: $resource")

        val content = inputStream.bufferedReader().use { it.readText() }
        return loadFromJson(content)
    }

    /**
     * Load plugin configuration from JSON string.
     *
     * @param jsonString JSON configuration string
     * @return Plugin configuration
     */
    fun loadFromJson(jsonString: String): PluginConfiguration {
        return json.decodeFromString(PluginConfiguration.serializer(), jsonString)
    }

    /**
     * Load plugin configuration from JSON object.
     *
     * @param jsonObject JSON object
     * @return Plugin configuration
     */
    fun loadFromJsonObject(jsonObject: JsonObject): PluginConfiguration {
        return json.decodeFromJsonElement(PluginConfiguration.serializer(), jsonObject)
    }
}


