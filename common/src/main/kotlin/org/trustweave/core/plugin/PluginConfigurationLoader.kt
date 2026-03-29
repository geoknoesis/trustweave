package org.trustweave.core.plugin

import org.trustweave.core.exception.ConfigException
import org.trustweave.core.exception.TrustWeaveException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import java.io.File

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

    // Cache serializer to avoid reflection overhead on each call
    private val configSerializer = serializer<PluginConfiguration>()

    /**
     * Load plugin configuration from JSON file.
     *
     * @param path File path
     * @return Plugin configuration
     * @throws ConfigException.InvalidFormat if path is blank
     * @throws ConfigException.NotFound if file doesn't exist
     * @throws ConfigException.ReadFailed if file cannot be read
     */
    fun loadFromFile(path: String): PluginConfiguration {
        if (path.isBlank()) {
            throw ConfigException.InvalidFormat(
                parseError = "File path cannot be blank"
            )
        }
        val file = File(path)
        if (!file.exists()) {
            throw ConfigException.NotFound(path = path)
        }
        if (!file.canRead()) {
            throw ConfigException.ReadFailed(
                path = path,
                reason = "File is not readable"
            )
        }

        return try {
            val content = file.readText()
            loadFromJson(content)
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: Exception) {
            throw ConfigException.ReadFailed(
                path = path,
                reason = e.message ?: "Failed to read file"
            )
        }
    }

    /**
     * Load plugin configuration from classpath resource.
     *
     * @param resource Resource path (e.g., "TrustWeave-plugins.json")
     * @return Plugin configuration
     * @throws ConfigException.InvalidFormat if resource path is blank
     * @throws ConfigException.NotFound if resource doesn't exist
     * @throws ConfigException.ReadFailed if resource cannot be read
     */
    fun loadFromResource(resource: String): PluginConfiguration {
        if (resource.isBlank()) {
            throw ConfigException.InvalidFormat(
                parseError = "Resource path cannot be blank"
            )
        }
        val inputStream = PluginConfigurationLoader::class.java.classLoader
            .getResourceAsStream(resource)
            ?: throw ConfigException.NotFound(path = resource)

        return try {
            val content = inputStream.bufferedReader().use { it.readText() }
            loadFromJson(content)
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: Exception) {
            throw ConfigException.ReadFailed(
                path = resource,
                reason = e.message ?: "Failed to read resource"
            )
        }
    }

    /**
     * Load plugin configuration from JSON string.
     *
     * @param jsonString JSON configuration string
     * @return Plugin configuration
     * @throws ConfigException.InvalidFormat if JSON parsing fails
     */
    fun loadFromJson(jsonString: String): PluginConfiguration {
        return try {
            json.decodeFromString(configSerializer, jsonString)
        } catch (e: SerializationException) {
            throw ConfigException.InvalidFormat(
                jsonString = jsonString,
                parseError = e.message ?: "JSON parsing error",
                field = null
            )
        } catch (e: Exception) {
            throw ConfigException.InvalidFormat(
                jsonString = jsonString,
                parseError = e.message ?: "Unknown parsing error",
                field = null
            )
        }
    }

    /**
     * Load plugin configuration from JSON object.
     *
     * @param jsonObject JSON object
     * @return Plugin configuration
     */
    fun loadFromJsonObject(jsonObject: JsonObject): PluginConfiguration {
        return json.decodeFromJsonElement(configSerializer, jsonObject)
    }
}
