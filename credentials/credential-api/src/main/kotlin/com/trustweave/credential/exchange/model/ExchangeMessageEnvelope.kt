package com.trustweave.credential.exchange.model

import com.trustweave.credential.identifiers.ExchangeProtocolName
import kotlinx.serialization.json.JsonElement

/**
 * Protocol-agnostic exchange message envelope.
 * 
 * Encapsulates protocol-specific message data in an opaque structure.
 * The actual message format (DidCommMessage, OIDC4VCI offer, etc.) 
 * is hidden from the API consumer.
 * 
 * Similar to how VerifiableCredential.proof contains format-specific proof data,
 * this envelope contains opaque protocol-specific message data.
 */
data class ExchangeMessageEnvelope(
    val protocolName: ExchangeProtocolName,
    val messageType: ExchangeMessageType,
    val messageData: JsonElement,  // Opaque protocol-specific data
    val metadata: Map<String, JsonElement> = emptyMap()
)

/**
 * Exchange message type.
 * 
 * Sealed class for type-safe message type identification.
 */
sealed class ExchangeMessageType {
    abstract val identifier: String
    
    object Offer : ExchangeMessageType() {
        override val identifier = "offer"
    }
    
    object Request : ExchangeMessageType() {
        override val identifier = "request"
    }
    
    object Issue : ExchangeMessageType() {
        override val identifier = "issue"
    }
    
    object ProofRequest : ExchangeMessageType() {
        override val identifier = "proof-request"
    }
    
    object ProofPresentation : ExchangeMessageType() {
        override val identifier = "proof-presentation"
    }
    
    object Acknowledgment : ExchangeMessageType() {
        override val identifier = "acknowledgment"
    }
    
    data class Custom(override val identifier: String) : ExchangeMessageType()
}

