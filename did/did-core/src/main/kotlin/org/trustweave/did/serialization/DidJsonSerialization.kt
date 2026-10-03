package org.trustweave.did.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual

/**
 * kotlinx.serialization support for DID models that carry `@Contextual Any?` values, such as
 * `VerificationMethod.publicKeyJwk`.
 *
 * Without a serializer for `Any`, encoding any [org.trustweave.did.model.DidDocument] that has a
 * `publicKeyJwk` fails with "Serializer for class 'Any' is not found". Use [module] in the
 * `Json` that encodes or decodes registrar requests and responses:
 * ```kotlin
 * Json { serializersModule = DidJsonSerialization.module }
 * ```
 */
public object DidJsonSerialization {
    /** Contextual serializer for `Any` values that are JSON-representable. */
    public val module: SerializersModule =
        SerializersModule {
            contextual(Any::class, JsonAnySerializer)
        }

    /** A [Json] configured with [module]. */
    public fun json(builder: kotlinx.serialization.json.JsonBuilder.() -> Unit = {}): Json =
        Json {
            serializersModule = module
            builder()
        }
}

/**
 * Serializes `String`, `Boolean`, numbers, `null`, `Map<String, *>`, `Iterable`/`Array` and
 * [JsonElement] values as the corresponding JSON; anything else fails loudly rather than being
 * written as an opaque `toString()`.
 */
internal object JsonAnySerializer : KSerializer<Any> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: Any,
    ) {
        val jsonEncoder = encoder as? JsonEncoder ?: throw SerializationException("Any is only serializable to JSON")
        jsonEncoder.encodeJsonElement(toJson(value))
    }

    override fun deserialize(decoder: Decoder): Any {
        val jsonDecoder = decoder as? JsonDecoder ?: throw SerializationException("Any is only deserializable from JSON")
        return fromJson(jsonDecoder.decodeJsonElement()) ?: throw SerializationException("Unexpected JSON null for a non-null value")
    }

    fun toJson(value: Any?): JsonElement =
        when (value) {
            null -> JsonNull
            is JsonElement -> value
            is String -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Map<*, *> ->
                JsonObject(
                    value.entries.associate { (k, v) ->
                        (k as? String ?: throw SerializationException("JSON object keys must be strings, got $k")) to toJson(v)
                    },
                )
            is Iterable<*> -> JsonArray(value.map { toJson(it) })
            is Array<*> -> JsonArray(value.map { toJson(it) })
            else -> throw SerializationException("Cannot serialize ${value::class.qualifiedName} as JSON")
        }

    fun fromJson(element: JsonElement): Any? =
        when (element) {
            is JsonNull -> null
            is JsonObject -> element.mapValues { fromJson(it.value) }
            is JsonArray -> element.map { fromJson(it) }
            is JsonPrimitive ->
                when {
                    element.isString -> element.content
                    element.booleanOrNull != null -> element.booleanOrNull
                    element.longOrNull != null -> element.longOrNull
                    element.doubleOrNull != null -> element.doubleOrNull
                    else -> element.content
                }
        }
}
