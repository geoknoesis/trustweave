package com.trustweave.credential.identifiers

import com.trustweave.core.identifiers.Iri
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Protocol identifier types for credential exchange protocols.
 * 
 * These provide type-safe wrappers for protocol names and exchange operation IDs.
 */

/**
 * Exchange protocol name identifier.
 * 
 * Type-safe wrapper for credential exchange protocol identifiers (e.g., "didcomm", "oidc4vci", "chapi").
 * Enforces lowercase alphanumeric with hyphens format.
 */
@Serializable(with = ExchangeProtocolNameSerializer::class)
class ExchangeProtocolName(val value: String) {
    init {
        require(value.isNotBlank()) { "Exchange protocol name cannot be blank" }
        require(value.matches(Regex("[a-z0-9-]+"))) { 
            "Exchange protocol name must be lowercase alphanumeric with hyphens: $value" 
        }
    }
    
    override fun toString(): String = value
    override fun equals(other: Any?): Boolean = other is ExchangeProtocolName && value == other.value
    override fun hashCode(): Int = value.hashCode()
    
    companion object {
        val DidComm = ExchangeProtocolName("didcomm")
        val Oidc4Vci = ExchangeProtocolName("oidc4vci")
        val Oidc4Vp = ExchangeProtocolName("oidc4vp")
        val Chapi = ExchangeProtocolName("chapi")
    }
}

object ExchangeProtocolNameSerializer : KSerializer<ExchangeProtocolName> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("ExchangeProtocolName", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: ExchangeProtocolName) {
        encoder.encodeString(value.value)
    }
    
    override fun deserialize(decoder: Decoder): ExchangeProtocolName {
        val string = decoder.decodeString()
        return try {
            ExchangeProtocolName(string)
        } catch (e: IllegalArgumentException) {
            throw kotlinx.serialization.SerializationException(
                "Failed to deserialize ExchangeProtocolName: ${e.message}",
                e
            )
        }
    }
}

/**
 * Credential offer identifier.
 */
@Serializable(with = OfferIdSerializer::class)
class OfferId(val value: String) {
    init {
        require(value.isNotBlank()) { "Offer ID cannot be blank" }
    }
    
    override fun toString(): String = value
    override fun equals(other: Any?): Boolean = other is OfferId && value == other.value
    override fun hashCode(): Int = value.hashCode()
}

object OfferIdSerializer : KSerializer<OfferId> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("OfferId", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: OfferId) {
        encoder.encodeString(value.value)
    }
    
    override fun deserialize(decoder: Decoder): OfferId {
        val string = decoder.decodeString()
        return try {
            OfferId(string)
        } catch (e: IllegalArgumentException) {
            throw kotlinx.serialization.SerializationException(
                "Failed to deserialize OfferId: ${e.message}",
                e
            )
        }
    }
}

/**
 * Credential request identifier.
 */
@Serializable(with = RequestIdSerializer::class)
class RequestId(val value: String) {
    init {
        require(value.isNotBlank()) { "Request ID cannot be blank" }
    }
    
    override fun toString(): String = value
    override fun equals(other: Any?): Boolean = other is RequestId && value == other.value
    override fun hashCode(): Int = value.hashCode()
}

object RequestIdSerializer : KSerializer<RequestId> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("RequestId", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: RequestId) {
        encoder.encodeString(value.value)
    }
    
    override fun deserialize(decoder: Decoder): RequestId {
        val string = decoder.decodeString()
        return try {
            RequestId(string)
        } catch (e: IllegalArgumentException) {
            throw kotlinx.serialization.SerializationException(
                "Failed to deserialize RequestId: ${e.message}",
                e
            )
        }
    }
}

/**
 * Credential issue identifier.
 */
@Serializable(with = IssueIdSerializer::class)
class IssueId(val value: String) {
    init {
        require(value.isNotBlank()) { "Issue ID cannot be blank" }
    }
    
    override fun toString(): String = value
    override fun equals(other: Any?): Boolean = other is IssueId && value == other.value
    override fun hashCode(): Int = value.hashCode()
}

object IssueIdSerializer : KSerializer<IssueId> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("IssueId", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: IssueId) {
        encoder.encodeString(value.value)
    }
    
    override fun deserialize(decoder: Decoder): IssueId {
        val string = decoder.decodeString()
        return try {
            IssueId(string)
        } catch (e: IllegalArgumentException) {
            throw kotlinx.serialization.SerializationException(
                "Failed to deserialize IssueId: ${e.message}",
                e
            )
        }
    }
}

/**
 * Proof presentation identifier.
 */
@Serializable(with = PresentationIdSerializer::class)
class PresentationId(val value: String) {
    init {
        require(value.isNotBlank()) { "Presentation ID cannot be blank" }
    }
    
    override fun toString(): String = value
    override fun equals(other: Any?): Boolean = other is PresentationId && value == other.value
    override fun hashCode(): Int = value.hashCode()
}

object PresentationIdSerializer : KSerializer<PresentationId> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("PresentationId", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: PresentationId) {
        encoder.encodeString(value.value)
    }
    
    override fun deserialize(decoder: Decoder): PresentationId {
        val string = decoder.decodeString()
        return try {
            PresentationId(string)
        } catch (e: IllegalArgumentException) {
            throw kotlinx.serialization.SerializationException(
                "Failed to deserialize PresentationId: ${e.message}",
                e
            )
        }
    }
}

