package org.trustweave.core.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.datetime.Instant

/**
 * Serializer for kotlinx.datetime.Instant.
 * 
 * Serializes Instant as ISO-8601 string (e.g., "2024-01-01T00:00:00Z").
 * This is the standard format used in JSON-LD and W3C specifications.
 */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())  // ISO 8601 format
    }
    
    override fun deserialize(decoder: Decoder): Instant {
        val string = decoder.decodeString()
        return try {
            Instant.parse(string)
        } catch (e: Exception) {
            throw kotlinx.serialization.SerializationException(
                "Failed to deserialize Instant: '$string'. Expected ISO-8601 format.",
                e
            )
        }
    }
}

/**
 * Serializer for nullable kotlinx.datetime.Instant?.
 * 
 * Handles null values and serializes non-null values as ISO-8601 strings.
 * This is needed because kotlinx.serialization requires explicit nullable serializers
 * for @Contextual annotations with nullable types.
 */
object NullableInstantSerializer : KSerializer<Instant?> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("Instant?", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: Instant?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeString(value.toString())  // ISO 8601 format
        }
    }
    
    override fun deserialize(decoder: Decoder): Instant? {
        // Check if next value is null
        if (!decoder.decodeNotNullMark()) {
            return decoder.decodeNull()
        }
        // Not null, decode as ISO 8601 string
        val string = decoder.decodeString()
        return try {
            Instant.parse(string)
        } catch (e: Exception) {
            throw kotlinx.serialization.SerializationException(
                "Failed to deserialize Instant: '$string'. Expected ISO-8601 format.",
                e
            )
        }
    }
}

