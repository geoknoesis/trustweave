package org.trustweave.credential.model.vc

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.trustweave.core.identifiers.Iri

/**
 * Reads and writes `issuer` the way the W3C VC data model defines it: either an IRI string, or an
 * object carrying an `id`.
 *
 * Without this, the sealed class fell back to kotlinx's polymorphic encoding and produced
 * `{"type":"org.trustweave.credential.model.vc.Issuer.IriIssuer","id":"did:key:z…"}` — a Kotlin
 * class name inside a credential document. No other implementation could read that, and this
 * library could not read the ordinary string form that every other implementation emits.
 *
 * The JSON-LD path has always written `credential.issuer.id.value` directly rather than going
 * through this serializer, so signatures were computed over the correct shape and are unaffected.
 */
object IssuerSerializer : KSerializer<Issuer> {
    private const val ID_KEY = "id"
    private const val NAME_KEY = "name"

    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(
        encoder: Encoder,
        value: Issuer,
    ) {
        val jsonEncoder =
            encoder as? JsonEncoder
                ?: throw IllegalStateException("Issuer can only be encoded to JSON")

        val element: JsonElement =
            when (value) {
                is Issuer.IriIssuer -> JsonPrimitive(value.id.value)
                is Issuer.ObjectIssuer ->
                    buildJsonObject {
                        put(ID_KEY, value.id.value)
                        value.name?.let { put(NAME_KEY, it) }
                        // Members this library does not model are carried through untouched rather
                        // than dropped; a credential's issuer object may legitimately hold more.
                        value.additionalProperties
                            .filterKeys { it != ID_KEY && it != NAME_KEY }
                            .forEach { (key, member) -> put(key, member) }
                    }
            }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): Issuer {
        val jsonDecoder =
            decoder as? JsonDecoder
                ?: throw IllegalStateException("Issuer can only be decoded from JSON")

        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonPrimitive -> {
                val iri =
                    element.contentOrNull
                        ?: throw IllegalArgumentException("issuer string is null")
                Issuer.IriIssuer(Iri(iri))
            }

            is JsonObject -> fromObject(element)

            else -> throw IllegalArgumentException(
                "issuer must be an IRI string or an object with an id, got: $element",
            )
        }
    }

    private fun fromObject(obj: JsonObject): Issuer {
        val id =
            obj[ID_KEY]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalArgumentException("issuer object is missing 'id'")

        return Issuer.ObjectIssuer(
            id = Iri(id),
            name = obj[NAME_KEY]?.jsonPrimitive?.contentOrNull,
            additionalProperties = obj.filterKeys { it != ID_KEY && it != NAME_KEY },
        )
    }
}
