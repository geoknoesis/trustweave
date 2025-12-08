package com.trustweave.credential.didcomm.protocol.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * DIDComm goal code for protocol messages.
 * 
 * Represents the purpose or goal of a DIDComm credential exchange operation.
 * Goal codes are used in the `goal_code` field of DIDComm message bodies.
 * 
 * **DIDComm-Specific**: This is specific to DIDComm protocol only.
 * 
 * **Relationship to ExchangeOperation**:
 * - [com.trustweave.credential.exchange.ExchangeOperation] represents what operations
 *   a protocol CAN support (capability, protocol-agnostic)
 * - DidCommGoalCode represents what a specific DIDComm message is trying to accomplish
 *   (message intent, DIDComm-specific)
 * 
 * **Example Usage**:
 * ```kotlin
 * // Convert to string for ExchangeModels (protocol-agnostic API)
 * val goalCodeString: String? = DidCommGoalCode.IssueVc.value
 * 
 * // Use in DIDComm-specific code
 * val goalCode = DidCommGoalCode.IssueVc
 * ```
 */
@Serializable(with = DidCommGoalCodeSerializer::class)
class DidCommGoalCode(val value: String) {
    init {
        require(value.isNotBlank()) { "Goal code cannot be blank" }
        // DIDComm goal codes are typically lowercase with hyphens
    }
    
    override fun toString(): String = value
    override fun equals(other: Any?): Boolean = other is DidCommGoalCode && value == other.value
    override fun hashCode(): Int = value.hashCode()
    
    companion object {
        /** Goal: Issue a verifiable credential */
        val IssueVc = DidCommGoalCode("issue-vc")
        
        /** Goal: Request a credential */
        val RequestCredential = DidCommGoalCode("request-credential")
        
        /** Goal: Request a proof presentation */
        val RequestProof = DidCommGoalCode("request-proof")
        
        /** Goal: Present a proof */
        val PresentProof = DidCommGoalCode("present-proof")
        
        /** Goal: Acknowledge credential receipt */
        val AckCredential = DidCommGoalCode("ack-credential")
        
        /** Goal: Acknowledge proof receipt */
        val AckProof = DidCommGoalCode("ack-proof")
        
        /**
         * Convert from string (e.g., from ExchangeModels).
         */
        fun fromString(value: String?): DidCommGoalCode? {
            return value?.let { DidCommGoalCode(it) }
        }
    }
}

object DidCommGoalCodeSerializer : KSerializer<DidCommGoalCode> {
    override val descriptor: SerialDescriptor = 
        PrimitiveSerialDescriptor("DidCommGoalCode", PrimitiveKind.STRING)
    
    override fun serialize(encoder: Encoder, value: DidCommGoalCode) {
        encoder.encodeString(value.value)
    }
    
    override fun deserialize(decoder: Decoder): DidCommGoalCode {
        val string = decoder.decodeString()
        return try {
            DidCommGoalCode(string)
        } catch (e: IllegalArgumentException) {
            throw kotlinx.serialization.SerializationException(
                "Failed to deserialize DidCommGoalCode: ${e.message}",
                e
            )
        }
    }
}

