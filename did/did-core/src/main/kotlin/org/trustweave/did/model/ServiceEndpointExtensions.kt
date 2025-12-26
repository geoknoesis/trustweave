package org.trustweave.did.model

import kotlinx.serialization.json.*

/**
 * Type-safe extensions for [DidService.serviceEndpoint].
 *
 * The W3C DID Core spec allows serviceEndpoint to be:
 * - A string (URL)
 * - An object (Map<String, Any?>)
 * - An array of strings/objects
 *
 * These extensions provide type-safe access without breaking serialization compatibility.
 */

/**
 * Sealed class representing the possible types of service endpoints.
 * This provides type safety for consumers while maintaining backward compatibility.
 */
sealed class ServiceEndpoint {
    /**
     * Service endpoint as a single URL string.
     */
    data class Url(val url: String) : ServiceEndpoint()

    /**
     * Service endpoint as an object (map of key-value pairs).
     */
    data class ObjectEndpoint(val data: Map<String, Any?>) : ServiceEndpoint()

    /**
     * Service endpoint as an array of endpoints (can be mixed types).
     */
    data class ArrayEndpoint(val endpoints: List<ServiceEndpoint>) : ServiceEndpoint()
}

/**
 * Converts the serviceEndpoint `Any` to a type-safe [ServiceEndpoint].
 *
 * @return ServiceEndpoint with proper type, or null if conversion fails
 */
fun DidService.serviceEndpointTyped(): ServiceEndpoint? {
    return when (val endpoint = serviceEndpoint) {
        is String -> ServiceEndpoint.Url(endpoint)
        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            ServiceEndpoint.ObjectEndpoint(endpoint as Map<String, Any?>)
        }
        is List<*> -> {
            val typedEndpoints = endpoint.mapNotNull { item ->
                when (item) {
                    is String -> ServiceEndpoint.Url(item)
                    is Map<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        ServiceEndpoint.ObjectEndpoint(item as Map<String, Any?>)
                    }
                    else -> null
                }
            }
            if (typedEndpoints.size == endpoint.size) {
                ServiceEndpoint.ArrayEndpoint(typedEndpoints)
            } else {
                null
            }
        }
        else -> null
    }
}

/**
 * Gets the service endpoint as a URL string, if it's a simple URL.
 *
 * @return The URL string, or null if the endpoint is not a simple URL
 */
fun DidService.serviceEndpointAsUrl(): String? {
    return when (val endpoint = serviceEndpoint) {
        is String -> endpoint
        else -> null
    }
}

/**
 * Gets the service endpoint as an object (map), if it's an object.
 *
 * @return The object map, or null if the endpoint is not an object
 */
fun DidService.serviceEndpointAsObject(): Map<String, Any?>? {
    return when (val endpoint = serviceEndpoint) {
        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            endpoint as? Map<String, Any?>
        }
        else -> null
    }
}

/**
 * Gets the service endpoint as an array, if it's an array.
 *
 * @return The array list, or null if the endpoint is not an array
 */
fun DidService.serviceEndpointAsArray(): List<Any>? {
    return when (val endpoint = serviceEndpoint) {
        is List<*> -> {
            @Suppress("UNCHECKED_CAST")
            endpoint as? List<Any>
        }
        else -> null
    }
}

/**
 * Converts a [ServiceEndpoint] back to `Any` for serialization.
 *
 * @return The endpoint as `Any` for use in DidService
 */
fun ServiceEndpoint.toAny(): Any {
    return when (this) {
        is ServiceEndpoint.Url -> url
        is ServiceEndpoint.ObjectEndpoint -> data
        is ServiceEndpoint.ArrayEndpoint -> endpoints.map { it.toAny() }
    }
}

