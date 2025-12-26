package org.trustweave.core.serialization

import kotlinx.serialization.ExperimentalSerializationApi
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
 * 
 * **Performance:** Uses optimized kotlinx.datetime.Instant parsing and formatting.
 * 
 * **Error Handling:** Provides detailed error messages for debugging.
 */
object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: Instant) {
        // Instant.toString() already produces ISO-8601 format
        encoder.encodeString(value.toString())
    }
    
    override fun deserialize(decoder: Decoder): Instant {
        val string = decoder.decodeString()
        return try {
            Instant.parse(string)
        } catch (e: Exception) {
            // Instant.parse throws DateTimeFormatException (internal) or IllegalArgumentException
            // for invalid format. Catch all exceptions and wrap in SerializationException.
            throw kotlinx.serialization.SerializationException(
                "Failed to deserialize Instant: '$string'. " +
                "Expected ISO-8601 format (e.g., '2024-01-01T00:00:00Z'). " +
                "Error: ${e.message}",
                e
            )
        }
    }
}

/**
 * Serializer for nullable kotlinx.datetime.Instant?.
 * 
 * Handles null values and serializes non-null values as ISO-8601 strings.
 * 
 * **Note:** This serializer may not be necessary if kotlinx.serialization
 * automatically handles nullable types when the non-nullable serializer is registered.
 * However, it's kept for explicit control and to avoid potential edge cases.
 * 
 * **Performance:** Minimal overhead - only checks null before delegating to InstantSerializer.
 */
@OptIn(ExperimentalSerializationApi::class)
object NullableInstantSerializer : KSerializer<Instant?> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("Instant?", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: Instant?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            // Delegate to non-nullable serializer for consistency
            InstantSerializer.serialize(encoder, value)
        }
    }
    
    override fun deserialize(decoder: Decoder): Instant? {
        // Use decodeNotNullMark for efficient null checking
        if (!decoder.decodeNotNullMark()) {
            return decoder.decodeNull()
        }
        // Delegate to non-nullable serializer
        return InstantSerializer.deserialize(decoder)
    }
}

