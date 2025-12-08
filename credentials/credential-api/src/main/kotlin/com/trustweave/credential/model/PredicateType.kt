package com.trustweave.credential.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Predicate type for proof requests.
 * 
 * Represents comparison operators used in attribute predicates
 * (e.g., age >= 18, salary <= 100000).
 */
@Serializable(with = PredicateTypeSerializer::class)
enum class PredicateType(val symbol: String) {
    GreaterThanOrEqual(">="),
    LessThanOrEqual("<="),
    Equal("=="),
    GreaterThan(">"),
    LessThan("<"),
    NotEqual("!=");
    
    override fun toString(): String = symbol
}

object PredicateTypeSerializer : KSerializer<PredicateType> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("PredicateType", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: PredicateType) {
        encoder.encodeString(value.symbol)
    }
    
    override fun deserialize(decoder: Decoder): PredicateType {
        val string = decoder.decodeString()
        return PredicateType.entries.find { it.symbol == string }
            ?: throw kotlinx.serialization.SerializationException(
                "Invalid PredicateType: '$string'. Valid values: ${PredicateType.entries.joinToString { it.symbol }}"
            )
    }
}

/**
 * Extension function to convert string to PredicateType.
 */
fun String.toPredicateType(): PredicateType? = 
    PredicateType.entries.find { it.symbol == this }

