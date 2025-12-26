package org.trustweave.did.registration.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Data model for DID Method Registration JSON specification.
 *
 * Based on the DID Registration specification from https://identity.foundation/did-registration/
 *
 * This allows DID methods to be registered via JSON files, making it easy to add
 * new method support without writing code.
 *
 * **Example JSON:**
 * ```json
 * {
 *   "name": "example",
 *   "status": "implemented",
 *   "specification": "https://example.com/did-method-spec",
 *   "contact": {
 *     "name": "Example Team",
 *     "email": "contact@example.com"
 *   },
 *   "driver": {
 *     "type": "universal-resolver",
 *     "baseUrl": "https://dev.uniresolver.io",
 *     "protocolAdapter": "standard"
 *   },
 *   "capabilities": {
 *     "create": false,
 *     "resolve": true,
 *     "update": false,
 *     "deactivate": false
 *   }
 * }
 * ```
 */
@Serializable
data class DidRegistrationSpec(
    /**
     * The DID method name (e.g., "web", "key", "ion").
     */
    val name: String,

    /**
     * Implementation status of the method.
     * Common values: "proposed", "implemented", "deprecated"
     */
    val status: String? = null,

    /**
     * URL to the full DID method specification document.
     */
    val specification: String? = null,

    /**
     * Contact information for the method maintainers.
     */
    val contact: ContactInfo? = null,

    /**
     * Driver configuration for how to interact with this DID method.
     * This determines how resolution, creation, etc. are handled.
     */
    val driver: DriverConfig? = null,

    /**
     * Capabilities supported by this DID method implementation.
     */
    val capabilities: MethodCapabilities? = null,

    /**
     * Additional properties not defined in the spec.
     */
    val additionalProperties: Map<String, JsonObject> = emptyMap()
)

/**
 * Driver configuration for DID method operations.
 *
 * Different driver types support different ways of interacting with DID methods:
 * - universal-resolver: Uses a Universal Resolver instance for resolution
 * - native: Native implementation (requires code)
 * - custom: Custom driver implementation
 */
@Serializable
data class DriverConfig(
    /**
     * Type of driver: "universal-resolver", "native", or "custom"
     */
    val type: String,

    /**
     * Base URL for Universal Resolver (required for universal-resolver type)
     */
    val baseUrl: String? = null,

    /**
     * Base URL for Universal Registrar (optional, for create/update/deactivate operations)
     * 
     * If provided, HttpDidMethod will automatically create a DefaultUniversalRegistrar
     * instance to handle registration operations. This enables full DID Registration
     * specification compliance without requiring explicit registrar configuration.
     */
    val registrarUrl: String? = null,

    /**
     * Protocol adapter name for Universal Resolver (e.g., "standard", "godiddy")
     */
    val protocolAdapter: String? = null,

    /**
     * API key for authentication (if required)
     */
    val apiKey: String? = null,

    /**
     * Timeout in seconds for HTTP requests (default: 30)
     */
    val timeout: Int? = null,

    /**
     * Additional driver-specific configuration
     */
    val config: Map<String, String> = emptyMap()
)

/**
 * Capabilities supported by a DID method implementation.
 */
@Serializable
data class MethodCapabilities(
    /**
     * Whether this implementation supports creating new DIDs
     */
    val create: Boolean = false,

    /**
     * Whether this implementation supports resolving DIDs
     */
    val resolve: Boolean = true,

    /**
     * Whether this implementation supports updating DID documents
     */
    val update: Boolean = false,

    /**
     * Whether this implementation supports deactivating DIDs
     */
    val deactivate: Boolean = false
)

/**
 * Companion object with parsing utilities.
 */
object DidRegistrationSpecParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Parses a DID Registration JSON string into a DidRegistrationSpec.
     */
    fun parse(jsonString: String): DidRegistrationSpec {
        return json.decodeFromString(jsonString)
    }

    /**
     * Parses a DID Registration JSON object into a DidRegistrationSpec.
     */
    fun parse(jsonObject: JsonObject): DidRegistrationSpec {
        return json.decodeFromJsonElement(jsonObject)
    }
}

