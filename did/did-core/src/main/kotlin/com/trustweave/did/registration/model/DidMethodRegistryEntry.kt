package com.trustweave.did.registration.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Data model for DID Method Registry Entry following the official DID Registration specification.
 * 
 * This matches the format used in the DID Method Registry at https://identity.foundation/did-registration/
 * 
 * **Official Registry Format:**
 * ```json
 * {
 *   "name": "web",
 *   "status": "implemented",
 *   "specification": "https://w3c-ccg.github.io/did-method-web/",
 *   "contact": {
 *     "name": "W3C CCG",
 *     "email": "contact@example.com",
 *     "url": "https://www.w3.org/community/credentials/"
 *   },
 *   "implementations": [
 *     {
 *       "name": "Universal Resolver",
 *       "driverUrl": "https://dev.uniresolver.io",
 *       "testNet": false
 *     }
 *   ]
 * }
 * ```
 * 
 * @see https://identity.foundation/did-registration/
 */
@Serializable
data class DidMethodRegistryEntry(
    /**
     * The DID method name (e.g., "web", "key", "ion").
     * This is the method identifier used in DID strings: `did:method:id`
     */
    val name: String,
    
    /**
     * Implementation status of the method.
     * Common values: "proposed", "implemented", "deprecated", "withdrawn"
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
     * List of implementations available for this DID method.
     * Each implementation typically includes a driverUrl pointing to a resolver service.
     */
    val implementations: List<MethodImplementation> = emptyList(),
    
    /**
     * Additional properties from the registry entry that aren't explicitly defined.
     * This allows the model to accept future registry fields without breaking.
     */
    val additionalProperties: Map<String, JsonObject> = emptyMap()
)

/**
 * Contact information for DID method maintainers.
 */
@Serializable
data class ContactInfo(
    val name: String? = null,
    val email: String? = null,
    val url: String? = null
)

/**
 * Represents an implementation of a DID method.
 * 
 * In the registry, implementations typically reference resolver services
 * via driverUrl (Universal Resolver instances).
 */
@Serializable
data class MethodImplementation(
    /**
     * Name of the implementation (e.g., "Universal Resolver", "GoDiddy")
     */
    val name: String? = null,
    
    /**
     * URL to the driver/resolver service for this implementation.
     * This is typically a Universal Resolver endpoint.
     */
    val driverUrl: String? = null,
    
    /**
     * URL to the registrar service for this implementation.
     * This is typically a Universal Registrar endpoint.
     * 
     * According to the DID Registration specification, implementations may provide
     * separate endpoints for resolution (driverUrl) and registration (registrarUrl).
     */
    val registrarUrl: String? = null,
    
    /**
     * Whether this implementation is for a test network.
     */
    val testNet: Boolean? = null,
    
    /**
     * Additional implementation-specific properties.
     */
    val additionalProperties: Map<String, String> = emptyMap()
)

/**
 * Parser for DID Method Registry Entry JSON format.
 */
object DidMethodRegistryEntryParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Parses a DID Method Registry Entry JSON string.
     */
    fun parse(jsonString: String): DidMethodRegistryEntry {
        return json.decodeFromString(jsonString)
    }
    
    /**
     * Parses a DID Method Registry Entry from a JsonObject.
     */
    fun parse(jsonObject: JsonObject): DidMethodRegistryEntry {
        return json.decodeFromJsonElement(jsonObject)
    }
}

