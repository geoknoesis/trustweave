package com.trustweave.credential.exchange.exception

import com.trustweave.core.exception.TrustWeaveException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

/**
 * Credential exchange protocol-related exception types.
 *
 * These exceptions provide structured error codes and context for exchange operations.
 * All exceptions extend TrustWeaveException for consistent error handling across TrustWeave.
 *
 * **Example Usage:**
 * ```kotlin
 * try {
 *     val offer = registry.offerCredential("didcomm", request)
 * } catch (e: ExchangeException) {
 *     when (e) {
 *         is ExchangeException.ProtocolNotRegistered -> {
 *             println("Available protocols: ${e.availableProtocols}")
 *         }
 *         is ExchangeException.OperationNotSupported -> {
 *             println("Supported operations: ${e.supportedOperations}")
 *         }
 *         // ... handle other exceptions
 *     }
 * }
 * ```
 */
open class ExchangeException(
    override val code: String,
    override val message: String,
    override val context: Map<String, Any?> = emptyMap(),
    override val cause: Throwable? = null
) : TrustWeaveException(code, message, context, cause) {

    // ============================================================================
    // Registry-level exceptions
    // ============================================================================

    /**
     * Exception thrown when a protocol is not registered in the registry.
     *
     * @param protocolName The name of the protocol that was requested
     * @param availableProtocols List of available protocol names
     */
    data class ProtocolNotRegistered(
        val protocolName: String,
        val availableProtocols: List<String> = emptyList()
    ) : ExchangeException(
        code = "PROTOCOL_NOT_REGISTERED",
        message = if (availableProtocols.isEmpty()) {
            "Protocol '$protocolName' not registered. No protocols available."
        } else {
            "Protocol '$protocolName' not registered. Available: ${availableProtocols.joinToString(", ")}"
        },
        context = mapOf(
            "protocolName" to protocolName,
            "availableProtocols" to availableProtocols
        )
    )

    /**
     * Exception thrown when a protocol does not support the requested operation.
     *
     * @param protocolName The name of the protocol
     * @param operation The operation that was requested
     * @param supportedOperations List of operations supported by the protocol
     */
    data class OperationNotSupported(
        val protocolName: String,
        val operation: String,
        val supportedOperations: List<String> = emptyList()
    ) : ExchangeException(
        code = "OPERATION_NOT_SUPPORTED",
        message = if (supportedOperations.isEmpty()) {
            "Protocol '$protocolName' does not support operation '$operation'"
        } else {
            "Protocol '$protocolName' does not support operation '$operation'. Supported: ${supportedOperations.joinToString(", ")}"
        },
        context = mapOf(
            "protocolName" to protocolName,
            "operation" to operation,
            "supportedOperations" to supportedOperations
        )
    )

    // ============================================================================
    // Request validation exceptions
    // ============================================================================

    /**
     * Exception thrown when a required option is missing from the request.
     *
     * @param optionName The name of the missing option
     * @param protocolName The protocol that requires this option
     */
    data class MissingRequiredOption(
        val optionName: String,
        val protocolName: String? = null
    ) : ExchangeException(
        code = "MISSING_REQUIRED_OPTION",
        message = protocolName?.let {
            "Missing required option '$optionName' for protocol '$it'"
        } ?: "Missing required option: $optionName",
        context = mapOf(
            "optionName" to optionName,
            "protocolName" to protocolName
        ).filterValues { it != null }
    )

    /**
     * Exception thrown when a request field is invalid.
     *
     * @param field The name of the invalid field
     * @param reason The reason the field is invalid
     * @param protocolName The protocol name (if applicable)
     */
    data class InvalidRequest(
        val field: String,
        val reason: String,
        val protocolName: String? = null,
        override val cause: Throwable? = null
    ) : ExchangeException(
        code = "INVALID_REQUEST",
        message = "Invalid request field '$field': $reason",
        context = mapOf(
            "field" to field,
            "reason" to reason,
            "protocolName" to protocolName
        ).filterValues { it != null },
        cause = cause
    )

    // ============================================================================
    // Message/Resource not found exceptions
    // ============================================================================

    /**
     * Exception thrown when a message is not found.
     *
     * @param messageId The ID of the message that was not found
     * @param messageType The type of message (e.g., "offer", "request")
     */
    data class MessageNotFound(
        val messageId: String,
        val messageType: String? = null
    ) : ExchangeException(
        code = "MESSAGE_NOT_FOUND",
        message = messageType?.let {
            "$it message not found: $messageId"
        } ?: "Message not found: $messageId",
        context = mapOf(
            "messageId" to messageId,
            "messageType" to messageType
        ).filterValues { it != null }
    )

    /**
     * Exception thrown when a credential offer is not found.
     *
     * @param offerId The ID of the offer that was not found
     */
    data class OfferNotFound(
        val offerId: String
    ) : ExchangeException(
        code = "OFFER_NOT_FOUND",
        message = "Credential offer not found: $offerId",
        context = mapOf("offerId" to offerId)
    )

    /**
     * Exception thrown when a credential request is not found.
     *
     * @param requestId The ID of the request that was not found
     */
    data class RequestNotFound(
        val requestId: String
    ) : ExchangeException(
        code = "REQUEST_NOT_FOUND",
        message = "Credential request not found: $requestId",
        context = mapOf("requestId" to requestId)
    )

    /**
     * Exception thrown when a proof request is not found.
     *
     * @param requestId The ID of the proof request that was not found
     */
    data class ProofRequestNotFound(
        val requestId: String
    ) : ExchangeException(
        code = "PROOF_REQUEST_NOT_FOUND",
        message = "Proof request not found: $requestId",
        context = mapOf("requestId" to requestId)
    )

    // ============================================================================
    // Generic/Unknown exceptions
    // ============================================================================

    companion object {
        /**
         * Checks if an exception is retryable.
         *
         * @see ExchangeExceptionRecovery.isRetryable
         */
        fun isRetryable(exception: ExchangeException): Boolean {
            return ExchangeExceptionRecovery.isRetryable(exception)
        }

        /**
         * Checks if an exception is transient.
         *
         * @see ExchangeExceptionRecovery.isTransient
         */
        fun isTransient(exception: ExchangeException): Boolean {
            return ExchangeExceptionRecovery.isTransient(exception)
        }

        /**
         * Gets a user-friendly error message.
         *
         * @see ExchangeExceptionRecovery.getUserFriendlyMessage
         */
        fun getUserFriendlyMessage(exception: ExchangeException): String {
            return ExchangeExceptionRecovery.getUserFriendlyMessage(exception)
        }
    }

    /**
     * Exception thrown when an unknown or unexpected error occurs.
     *
     * This exception should be used sparingly and only when the error cannot
     * be categorized into a more specific exception type.
     *
     * @param reason The reason for the error
     * @param errorType The type of the original error (if available)
     * @param cause The underlying exception
     */
    data class Unknown(
        val reason: String,
        val errorType: String? = null,
        override val cause: Throwable? = null
    ) : ExchangeException(
        code = "EXCHANGE_UNKNOWN_ERROR",
        message = errorType?.let {
            "Unknown exchange error ($it): $reason"
        } ?: "Unknown exchange error: $reason",
        context = mapOf(
            "reason" to reason,
            "errorType" to errorType
        ).filterValues { it != null },
        cause = cause
    )
}

/**
 * Extension function to convert any Throwable to an ExchangeException.
 *
 * This function provides automatic conversion of standard exceptions
 * to structured ExchangeException types for consistent error handling.
 *
 * **Usage:**
 * ```kotlin
 * try {
 *     // Some operation
 * } catch (e: Throwable) {
 *     throw e.toExchangeException()
 * }
 * ```
 */
fun Throwable.toExchangeException(): ExchangeException = when (this) {
    is ExchangeException -> this
    is IllegalArgumentException -> ExchangeException.InvalidRequest(
        field = "argument",
        reason = message ?: "Invalid argument",
        cause = this
    )
    is IllegalStateException -> ExchangeException.InvalidRequest(
        field = "state",
        reason = message ?: "Invalid state",
        cause = this
    )
    // Network errors are converted to generic HTTP request failed
    // Plugin-specific exceptions should be thrown by the plugins themselves
    is TimeoutException,
    is UnknownHostException,
    is ConnectException,
    is SocketTimeoutException,
    is IOException -> ExchangeException.Unknown(
        reason = when (this) {
            is TimeoutException -> "Request timeout: ${message ?: "Unknown timeout"}"
            is UnknownHostException -> "Unknown host: ${message ?: "Host not found"}"
            is ConnectException -> "Connection failed: ${message ?: "Unable to connect"}"
            is SocketTimeoutException -> "Socket timeout: ${message ?: "Connection timed out"}"
            is IOException -> "I/O error: ${message ?: "Input/output operation failed"}"
            else -> message ?: "Network error"
        },
        errorType = this::class.simpleName,
        cause = this
    )
    is TrustWeaveException -> {
        // Convert TrustWeaveException to ExchangeException.Unknown
        // This preserves the original error information
        ExchangeException.Unknown(
            reason = message ?: "TrustWeave error: ${code}",
            errorType = code,
            cause = this
        )
    }
    else -> ExchangeException.Unknown(
        reason = message ?: "Unknown error: ${this::class.simpleName}",
        errorType = this::class.simpleName,
        cause = this
    )
}

