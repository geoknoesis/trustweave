package org.trustweave.did.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/**
 * Serialize service [type] (DID 1.1: string or set of strings) to JSON.
 * Single type → string; multiple → array.
 */
fun List<String>.toServiceTypeJsonElement(): JsonElement =
    if (size == 1) JsonPrimitive(this[0]) else JsonArray(map { JsonPrimitive(it) })

/**
 * Parse service type from JSON (string or array of strings) per DID 1.1 §5.4.
 */
fun parseServiceTypesFromJson(el: JsonElement?): List<String>? = when (el) {
    null -> null
    is JsonPrimitive -> el.content?.let { listOf(it) }
    is JsonArray -> el.mapNotNull { (it as? JsonPrimitive)?.content }.takeIf { it.isNotEmpty() }
    else -> null
}

/**
 * Type-safe model for [DidService.serviceEndpoint].
 *
 * The W3C DID Core spec allows serviceEndpoint to be:
 * - A string (URL)
 * - An object (Map<String, Any?>)
 * - An array of strings/objects
 *
 * This sealed type captures those variants while [ServiceEndpointSerializer] guarantees the
 * on-the-wire JSON stays byte-compatible (a bare string stays a string, an object stays an object,
 * and an array stays an array).
 */
@kotlinx.serialization.Serializable(with = ServiceEndpointSerializer::class)
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

    companion object {
        /**
         * Builds a [ServiceEndpoint] from a raw value (`String`, `Map`, or `List`).
         *
         * This is the factory construction sites should use when bridging from untyped
         * input (e.g. JSON conversion results, DSL builders, or interop payloads).
         *
         * @throws IllegalArgumentException if [value] (or any array item) is not a String, Map, or List
         */
        fun of(value: Any): ServiceEndpoint = when (value) {
            is ServiceEndpoint -> value
            is String -> Url(value)
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                ObjectEndpoint(value as Map<String, Any?>)
            }
            is List<*> -> ArrayEndpoint(
                value.map { item ->
                    requireNotNull(item) { "Service endpoint array items must not be null" }
                    of(item)
                }
            )
            else -> throw IllegalArgumentException(
                "Unsupported service endpoint type: ${value::class.simpleName}"
            )
        }

        /**
         * Like [of] but returns `null` instead of throwing when [value] (or any array item)
         * is not a String, Map, or List.
         */
        fun ofOrNull(value: Any?): ServiceEndpoint? = when (value) {
            null -> null
            is ServiceEndpoint -> value
            is String -> Url(value)
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                ObjectEndpoint(value as Map<String, Any?>)
            }
            is List<*> -> {
                val typed = value.map { item -> ofOrNull(item) }
                if (typed.size == value.size && typed.all { it != null }) {
                    @Suppress("UNCHECKED_CAST")
                    ArrayEndpoint(typed as List<ServiceEndpoint>)
                } else {
                    null
                }
            }
            else -> null
        }
    }
}

/**
 * Returns the typed [ServiceEndpoint] for this service.
 *
 * Retained for source compatibility: now that [DidService.serviceEndpoint] is already a
 * [ServiceEndpoint], this simply returns it.
 */
fun DidService.serviceEndpointTyped(): ServiceEndpoint = serviceEndpoint

/**
 * Gets the service endpoint as a URL string, if it's a simple URL.
 *
 * @return The URL string, or null if the endpoint is not a simple URL
 */
fun DidService.serviceEndpointAsUrl(): String? = when (val endpoint = serviceEndpoint) {
    is ServiceEndpoint.Url -> endpoint.url
    else -> null
}

/**
 * Gets the service endpoint as an object (map), if it's an object.
 *
 * @return The object map, or null if the endpoint is not an object
 */
fun DidService.serviceEndpointAsObject(): Map<String, Any?>? = when (val endpoint = serviceEndpoint) {
    is ServiceEndpoint.ObjectEndpoint -> endpoint.data
    else -> null
}

/**
 * Gets the service endpoint as an array, if it's an array.
 *
 * @return The array list of raw values, or null if the endpoint is not an array
 */
fun DidService.serviceEndpointAsArray(): List<Any>? = when (val endpoint = serviceEndpoint) {
    is ServiceEndpoint.ArrayEndpoint -> endpoint.endpoints.map { it.toAny() }
    else -> null
}

/**
 * Converts a [ServiceEndpoint] back to a raw value (`String`, `Map`, or `List`).
 *
 * @return The endpoint as `Any` for interop with untyped consumers
 */
fun ServiceEndpoint.toAny(): Any = when (this) {
    is ServiceEndpoint.Url -> url
    is ServiceEndpoint.ObjectEndpoint -> data
    is ServiceEndpoint.ArrayEndpoint -> endpoints.map { it.toAny() }
}

/**
 * KSerializer for [ServiceEndpoint] that preserves the exact W3C DID Core wire format:
 * - [ServiceEndpoint.Url] ⇄ JSON string
 * - [ServiceEndpoint.ObjectEndpoint] ⇄ JSON object
 * - [ServiceEndpoint.ArrayEndpoint] ⇄ JSON array
 *
 * Scalar/object conversions intentionally mirror [serviceEndpointFromJsonElement] /
 * [serviceEndpointToJsonElement] so round-trips through [DidService] are byte-compatible
 * with the legacy `Any`-typed representation.
 */
object ServiceEndpointSerializer : KSerializer<ServiceEndpoint> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: ServiceEndpoint) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw IllegalStateException("ServiceEndpoint can only be serialized to JSON")
        jsonEncoder.encodeJsonElement(serviceEndpointToJsonElement(value))
    }

    override fun deserialize(decoder: Decoder): ServiceEndpoint {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw IllegalStateException("ServiceEndpoint can only be deserialized from JSON")
        return serviceEndpointFromJsonElement(jsonDecoder.decodeJsonElement())
            ?: throw IllegalArgumentException("Unsupported serviceEndpoint JSON shape")
    }
}

/**
 * Converts a [ServiceEndpoint] to a [JsonElement] using the canonical DID document wire rules.
 * Numbers are emitted as their original JSON token where possible; primitives recovered from
 * [ServiceEndpoint.ObjectEndpoint]/[ServiceEndpoint.ArrayEndpoint] follow the same rules as the
 * shared DID document producer.
 */
fun serviceEndpointToJsonElement(endpoint: ServiceEndpoint): JsonElement = when (endpoint) {
    is ServiceEndpoint.Url -> JsonPrimitive(endpoint.url)
    is ServiceEndpoint.ObjectEndpoint -> anyToServiceEndpointJsonElement(endpoint.data)
    is ServiceEndpoint.ArrayEndpoint -> JsonArray(endpoint.endpoints.map { serviceEndpointToJsonElement(it) })
}

/**
 * Parses a [JsonElement] into a [ServiceEndpoint], or returns `null` for null/unsupported shapes.
 * Object values are decoded into native Kotlin values (String/Long/Double/Boolean/Map/List) so the
 * representation matches what the previous untyped parser produced.
 */
fun serviceEndpointFromJsonElement(element: JsonElement?): ServiceEndpoint? = when (element) {
    null, is JsonNull -> null
    is JsonPrimitive -> if (element.isString) {
        ServiceEndpoint.Url(element.content)
    } else {
        null
    }
    is JsonObject -> ServiceEndpoint.ObjectEndpoint(
        element.entries.associate { it.key to jsonElementToAny(it.value) }
    )
    is JsonArray -> {
        val typed = element.mapNotNull { serviceEndpointFromJsonElement(it) }
        if (typed.size == element.size) ServiceEndpoint.ArrayEndpoint(typed) else null
    }
}

private fun jsonElementToAny(element: JsonElement): Any? = when (element) {
    is JsonPrimitive -> when {
        element.isString -> element.content
        element.booleanOrNull != null -> element.boolean
        element.longOrNull != null -> element.long
        element.doubleOrNull != null -> element.double
        else -> element.content
    }
    is JsonArray -> element.map { jsonElementToAny(it) }
    is JsonObject -> element.entries.associate { it.key to jsonElementToAny(it.value) }
    is JsonNull -> null
}

private fun anyToServiceEndpointJsonElement(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is String -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value.toDouble())
    is Boolean -> JsonPrimitive(value)
    is Map<*, *> -> buildJsonObject {
        value.forEach { (key, v) -> put(key.toString(), anyToServiceEndpointJsonElement(v)) }
    }
    is List<*> -> JsonArray(value.map { anyToServiceEndpointJsonElement(it) })
    is ServiceEndpoint -> serviceEndpointToJsonElement(value)
    else -> JsonPrimitive(value.toString())
}
