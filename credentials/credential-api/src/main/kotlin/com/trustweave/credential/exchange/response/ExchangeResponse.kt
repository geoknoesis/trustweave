package com.trustweave.credential.exchange.response

import com.trustweave.credential.exchange.model.ExchangeMessageEnvelope
import com.trustweave.credential.exchange.model.ExchangeMessageType
import com.trustweave.credential.identifiers.*
import com.trustweave.credential.identifiers.ExchangeProtocolName
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.VerifiablePresentation
import com.trustweave.did.identifiers.Did

/**
 * Protocol-agnostic exchange response.
 * 
 * Sealed class hierarchy for type-safe exchange operation responses.
 * All protocol-specific data is encapsulated in ExchangeMessageEnvelope.
 */
sealed class ExchangeResponse {
    abstract val protocolName: ExchangeProtocolName
    abstract val messageEnvelope: ExchangeMessageEnvelope
    
    /**
     * Response from credential offer operation.
     */
    data class Offer(
        override val protocolName: ExchangeProtocolName,
        override val messageEnvelope: ExchangeMessageEnvelope,
        val offerId: OfferId
    ) : ExchangeResponse() {
        init {
            require(messageEnvelope.messageType == ExchangeMessageType.Offer) {
                "Offer response must have Offer message type"
            }
        }
    }
    
    /**
     * Response from credential request operation.
     */
    data class Request(
        override val protocolName: ExchangeProtocolName,
        override val messageEnvelope: ExchangeMessageEnvelope,
        val requestId: RequestId
    ) : ExchangeResponse() {
        init {
            require(messageEnvelope.messageType == ExchangeMessageType.Request) {
                "Request response must have Request message type"
            }
        }
    }
    
    /**
     * Response from credential issue operation.
     */
    data class Issue(
        override val protocolName: ExchangeProtocolName,
        override val messageEnvelope: ExchangeMessageEnvelope,
        val issueId: IssueId,
        val credential: VerifiableCredential
    ) : ExchangeResponse() {
        init {
            require(messageEnvelope.messageType == ExchangeMessageType.Issue) {
                "Issue response must have Issue message type"
            }
        }
    }
}

/**
 * Protocol-agnostic proof exchange response.
 */
sealed class ProofExchangeResponse {
    abstract val protocolName: ExchangeProtocolName
    abstract val messageEnvelope: ExchangeMessageEnvelope
    
    /**
     * Response from proof request operation.
     */
    data class Request(
        override val protocolName: ExchangeProtocolName,
        override val messageEnvelope: ExchangeMessageEnvelope,
        val requestId: RequestId
    ) : ProofExchangeResponse() {
        init {
            require(messageEnvelope.messageType == ExchangeMessageType.ProofRequest) {
                "Proof request response must have ProofRequest message type"
            }
        }
    }
    
    /**
     * Response from proof presentation operation.
     */
    data class Presentation(
        override val protocolName: ExchangeProtocolName,
        override val messageEnvelope: ExchangeMessageEnvelope,
        val presentationId: PresentationId,
        val presentation: VerifiablePresentation
    ) : ProofExchangeResponse() {
        init {
            require(messageEnvelope.messageType == ExchangeMessageType.ProofPresentation) {
                "Proof presentation response must have ProofPresentation message type"
            }
        }
    }
}

