package com.trustweave.credential.exchange.exception

import com.trustweave.core.exception.TrustWeaveException

/**
 * Base exception class for all credential exchange protocol errors.
 *
 * All exchange-related exceptions extend this class, which extends TrustWeaveException.
 * This provides a consistent error handling structure across all exchange protocols.
 */
open class ExchangeException(
    override val code: String,
    override val message: String,
    override val context: Map<String, Any?> = emptyMap(),
    override val cause: Throwable? = null
) : TrustWeaveException(code, message, context, cause) {

    /**
     * Protocol not registered error.
     */
    data class ProtocolNotRegistered(
        val protocolName: String,
        val availableProtocols: List<String> = emptyList()
    ) : ExchangeException(
        code = "PROTOCOL_NOT_REGISTERED",
        message = "Protocol '$protocolName' is not registered. Available protocols: ${availableProtocols.joinToString()}"
    )

    /**
     * Operation not supported error.
     */
    data class OperationNotSupported(
        val protocolName: String,
        val operation: String,
        val supportedOperations: List<String> = emptyList()
    ) : ExchangeException(
        code = "OPERATION_NOT_SUPPORTED",
        message = "Protocol '$protocolName' does not support operation '$operation'. Supported operations: ${supportedOperations.joinToString()}"
    )

    /**
     * Missing required option error.
     */
    data class MissingRequiredOption(
        val optionName: String,
        val protocolName: String? = null
    ) : ExchangeException(
        code = "MISSING_REQUIRED_OPTION",
        message = protocolName?.let { "Missing required option '$optionName' for protocol '$it'" }
            ?: "Missing required option '$optionName'"
    )

    /**
     * Invalid request error.
     */
    data class InvalidRequest(
        val field: String,
        val reason: String,
        val protocolName: String? = null,
        override val cause: Throwable? = null
    ) : ExchangeException(
        code = "INVALID_REQUEST",
        message = protocolName?.let { "Invalid request field '$field' for protocol '$it': $reason" }
            ?: "Invalid request field '$field': $reason",
        cause = cause
    )

    /**
     * Offer not found error.
     */
    data class OfferNotFound(
        val offerId: String,
        val protocolName: String? = null
    ) : ExchangeException(
        code = "OFFER_NOT_FOUND",
        message = protocolName?.let { "Offer '$offerId' not found for protocol '$it'" }
            ?: "Offer '$offerId' not found"
    )

    /**
     * Request not found error.
     */
    data class RequestNotFound(
        val requestId: String,
        val protocolName: String? = null
    ) : ExchangeException(
        code = "REQUEST_NOT_FOUND",
        message = protocolName?.let { "Request '$requestId' not found for protocol '$it'" }
            ?: "Request '$requestId' not found"
    )

    /**
     * Proof request not found error.
     */
    data class ProofRequestNotFound(
        val requestId: String,
        val protocolName: String? = null
    ) : ExchangeException(
        code = "PROOF_REQUEST_NOT_FOUND",
        message = protocolName?.let { "Proof request '$requestId' not found for protocol '$it'" }
            ?: "Proof request '$requestId' not found"
    )

    /**
     * Message not found error.
     */
    data class MessageNotFound(
        val messageId: String,
        val protocolName: String? = null
    ) : ExchangeException(
        code = "MESSAGE_NOT_FOUND",
        message = protocolName?.let { "Message '$messageId' not found for protocol '$it'" }
            ?: "Message '$messageId' not found"
    )

    /**
     * Unknown error.
     */
    data class Unknown(
        val reason: String,
        val errorType: String? = null,
        override val cause: Throwable? = null
    ) : ExchangeException(
        code = "EXCHANGE_UNKNOWN_ERROR",
        message = errorType?.let { "Unknown exchange error ($it): $reason" }
            ?: "Unknown exchange error: $reason",
        cause = cause
    )
}



