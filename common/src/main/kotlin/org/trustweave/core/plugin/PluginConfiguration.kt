package org.trustweave.core.plugin

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
     * Get configuration value as string.
     *
     * @param key Configuration key
     * @return Configuration value as string, or null if not found
     */
    fun getConfigValue(key: String): String? {
        return config[key]
    }

    /**
     * Get configuration value as integer.
     *
     * @param key Configuration key
     * @return Configuration value as integer, or null if not found or invalid
     */
    fun getConfigInt(key: String): Int? {
        return config[key]?.toIntOrNull()
    }

    /**
     * Get configuration value as boolean.
     *
     * @param key Configuration key
     * @return Configuration value as boolean, or null if not found or invalid
     */
    fun getConfigBoolean(key: String): Boolean? {
        return config[key]?.toBooleanStrictOrNull()
    }

    /**
     * Get configuration value as long.
     *
     * @param key Configuration key
     * @return Configuration value as long, or null if not found or invalid
     */
    fun getConfigLong(key: String): Long? {
        return config[key]?.toLongOrNull()
    }

    /**
     * Get configuration value as double.
     *
     * @param key Configuration key
     * @return Configuration value as double, or null if not found or invalid
     */
    fun getConfigDouble(key: String): Double? {
        return config[key]?.toDoubleOrNull()
    }

    /**
     * Get configuration as JsonObject for complex configurations.
     *
     * Attempts to parse each configuration value as JSON. If parsing fails,
     * falls back to storing the value as a string.
     * 
     * Performance: Result is cached after first call to avoid re-parsing.
     */
    private val jsonObjectCache: JsonObject by lazy {
        buildJsonObject {
            config.forEach { (key, value) ->
                // Try to parse as JSON, fallback to string
                try {
                    val jsonElement = Json.parseToJsonElement(value)
                    put(key, jsonElement)
                } catch (e: SerializationException) {
                    // Fallback to string if JSON parsing fails
                    put(key, value)
                }
            }
        }
    }

    fun getConfigAsJsonObject(): JsonObject = jsonObjectCache
}
