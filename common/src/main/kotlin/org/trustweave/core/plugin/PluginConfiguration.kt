package org.trustweave.core.plugin

import org.trustweave.core.exception.TrustWeaveException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
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

/**
 * Plugin type enumeration.
 *
 * Represents the plugin interface categories that TrustWeave framework supports.
 * These are framework-level plugin types, not domain-specific implementations.
 *
 * Each enum value corresponds to a plugin interface in the TrustWeave framework:
 * - `BLOCKCHAIN` → `BlockchainAnchorClient` interface (in `anchors:core` module)
 * - `CREDENTIAL_SERVICE` → `CredentialService` interface (in `credentials:core` module)
 * - `DID_METHOD` → `DidMethod` interface (in `did:core` module)
 * - `KMS` → `KeyManagementService` interface (in `kms:core` module)
 * - etc.
 *
 * **Important Distinction:**
 * - **PluginType enum** = Framework plugin architecture (what plugin interfaces exist)
 * - **Domain-specific capabilities** = Implementation details (which specific blockchains, DID methods, proof types)
 *
 * For example:
 * - `PluginType.CREDENTIAL_SERVICE` means "this plugin implements CredentialService interface"
 * - `supportedProofTypes: ["Ed25519Signature2020"]` is a domain-specific capability handled by the CredentialService interface
 *
 * This enum is part of the framework's plugin architecture definition, not domain-specific business logic.
 */
enum class PluginType {
    /** Blockchain adapter plugin (implements BlockchainAnchorClient) */
    BLOCKCHAIN,

    /** Credential storage/wallet plugin */
    CREDENTIAL_STORE,

    /** Credential service plugin (implements CredentialService) */
    CREDENTIAL_SERVICE,

    /** Trust service plugin */
    TRUST_SERVICE,

    /** Schema validator plugin */
    SCHEMA_VALIDATOR,

    /** Proof generator plugin */
    PROOF_GENERATOR,

    /** Revocation service plugin */
    REVOCATION_SERVICE,

    /** DID method plugin (implements DidMethod) */
    DID_METHOD,

    /** Key management service plugin (implements KeyManagementService) */
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
    
    // Cache serializer to avoid reflection overhead on each call
    private val configSerializer = serializer<PluginConfiguration>()

    /**
     * Load plugin configuration from JSON file.
     *
     * @param path File path
     * @return Plugin configuration
     * @throws TrustWeaveException.InvalidConfigFormat if path is blank
     * @throws TrustWeaveException.ConfigNotFound if file doesn't exist
     * @throws TrustWeaveException.ConfigReadFailed if file cannot be read
     */
    fun loadFromFile(path: String): PluginConfiguration {
        if (path.isBlank()) {
            throw TrustWeaveException.InvalidConfigFormat(
                parseError = "File path cannot be blank"
            )
        }
        val file = File(path)
        if (!file.exists()) {
            throw TrustWeaveException.ConfigNotFound(path = path)
        }
        if (!file.canRead()) {
            throw TrustWeaveException.ConfigReadFailed(
                path = path,
                reason = "File is not readable"
            )
        }

        return try {
            val content = file.readText()
            loadFromJson(content)
        } catch (e: TrustWeaveException) {
            // Re-throw TrustWeaveException as-is (from loadFromJson)
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException.ConfigReadFailed(
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
     * @throws TrustWeaveException.InvalidConfigFormat if resource path is blank
     * @throws TrustWeaveException.ConfigNotFound if resource doesn't exist
     * @throws TrustWeaveException.ConfigReadFailed if resource cannot be read
     */
    fun loadFromResource(resource: String): PluginConfiguration {
        if (resource.isBlank()) {
            throw TrustWeaveException.InvalidConfigFormat(
                parseError = "Resource path cannot be blank"
            )
        }
        val inputStream = PluginConfigurationLoader::class.java.classLoader
            .getResourceAsStream(resource)
            ?: throw TrustWeaveException.ConfigNotFound(path = resource)

        return try {
            val content = inputStream.bufferedReader().use { it.readText() }
            loadFromJson(content)
        } catch (e: TrustWeaveException) {
            // Re-throw TrustWeaveException as-is (from loadFromJson)
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException.ConfigReadFailed(
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
     * @throws TrustWeaveException.InvalidConfigFormat if JSON parsing fails
     */
    fun loadFromJson(jsonString: String): PluginConfiguration {
        return try {
            json.decodeFromString(configSerializer, jsonString)
        } catch (e: SerializationException) {
            throw TrustWeaveException.InvalidConfigFormat(
                jsonString = jsonString,
                parseError = e.message ?: "JSON parsing error",
                field = null
            )
        } catch (e: Exception) {
            throw TrustWeaveException.InvalidConfigFormat(
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
