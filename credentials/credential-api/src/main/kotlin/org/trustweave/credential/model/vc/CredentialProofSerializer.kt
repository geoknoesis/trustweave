package org.trustweave.credential.model.vc

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.SerializersModule
import org.trustweave.core.serialization.SerializationModule

/**
 * Custom serializer for CredentialProof that uses "@type" as discriminator
 * to avoid conflict with LinkedDataProof.type property.
 * 
 * This serializer manually handles serialization of each subclass to avoid
 * the discriminator conflict.
 */
object CredentialProofSerializer : KSerializer<CredentialProof> {
    private val json = Json {
        prettyPrint = false
        encodeDefaults = false
        ignoreUnknownKeys = true
        classDiscriminator = "@type"
        useArrayPolymorphism = false
        serializersModule = SerializationModule.default
    }

    override val descriptor: SerialDescriptor = 
        kotlinx.serialization.descriptors.buildClassSerialDescriptor("CredentialProof")

    override fun serialize(encoder: Encoder, value: CredentialProof) {
        when (encoder) {
            is JsonEncoder -> {
                val jsonElement = when (value) {
                    is CredentialProof.LinkedDataProof -> json.encodeToJsonElement(value)
                    is CredentialProof.JwtProof -> json.encodeToJsonElement(value)
                    is CredentialProof.SdJwtVcProof -> json.encodeToJsonElement(value)
                }
                // Add discriminator
                val jsonObject = jsonElement.jsonObject.toMutableMap()
                jsonObject["@type"] = JsonPrimitive(when (value) {
                    is CredentialProof.LinkedDataProof -> "LinkedDataProof"
                    is CredentialProof.JwtProof -> "JwtProof"
                    is CredentialProof.SdJwtVcProof -> "SdJwtVcProof"
                })
                encoder.encodeJsonElement(JsonObject(jsonObject))
            }
            else -> {
                // For non-JSON encoders, delegate to the appropriate serializer
                when (value) {
                    is CredentialProof.LinkedDataProof -> CredentialProof.LinkedDataProof.serializer().serialize(encoder, value)
                    is CredentialProof.JwtProof -> CredentialProof.JwtProof.serializer().serialize(encoder, value)
                    is CredentialProof.SdJwtVcProof -> CredentialProof.SdJwtVcProof.serializer().serialize(encoder, value)
                }
            }
        }
    }

    override fun deserialize(decoder: Decoder): CredentialProof {
        when (decoder) {
            is JsonDecoder -> {
                val jsonElement = decoder.decodeJsonElement()
                val jsonObject = jsonElement.jsonObject
                val type = jsonObject["@type"]?.jsonPrimitive?.content
                    ?: throw kotlinx.serialization.SerializationException("Missing @type discriminator")
                return when (type) {
                    "LinkedDataProof" -> json.decodeFromJsonElement<CredentialProof.LinkedDataProof>(jsonElement)
                    "JwtProof" -> json.decodeFromJsonElement<CredentialProof.JwtProof>(jsonElement)
                    "SdJwtVcProof" -> json.decodeFromJsonElement<CredentialProof.SdJwtVcProof>(jsonElement)
                    else -> throw kotlinx.serialization.SerializationException("Unknown CredentialProof type: $type")
                }
            }
            else -> {
                throw kotlinx.serialization.SerializationException("CredentialProofSerializer requires JsonDecoder")
            }
        }
    }
}

