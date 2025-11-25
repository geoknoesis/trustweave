package com.trustweave.credential.exchange.exception

import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Error recovery utilities for credential exchange operations.
 * 
 * Provides retry logic, error classification, and recovery strategies
 * for handling exchange-related errors.
 * 
 * **Example Usage:**
 * ```kotlin
 * val result = retryExchangeOperation(maxRetries = 3) {
 *     registry.offerCredential("didcomm", request)
 * }
 * ```
 */
object ExchangeExceptionRecovery {
    
    /**
     * Retries an exchange operation with exponential backoff.
     * 
     * Automatically retries on transient errors (network issues, timeouts)
     * but immediately fails on validation errors or protocol errors.
     * 
     * @param maxRetries Maximum number of retry attempts (default: 3)
     * @param initialDelay Initial delay in milliseconds (default: 1000)
     * @param maxDelay Maximum delay in milliseconds (default: 10000)
     * @param multiplier Backoff multiplier (default: 2.0)
     * @param operation The operation to retry
     * @return The result of the operation
     * @throws ExchangeException if all retries fail or a non-retryable error occurs
     */
    suspend fun <T> retryExchangeOperation(
        maxRetries: Int = 3,
        initialDelay: Long = 1000,
        maxDelay: Long = 10000,
        multiplier: Double = 2.0,
        operation: suspend () -> T
    ): T {
        var delay = initialDelay.toDouble()
        var lastError: ExchangeException? = null
        
        repeat(maxRetries) { attempt ->
            try {
                return operation()
            } catch (e: ExchangeException) {
                lastError = e
                
                // Don't retry on validation or protocol errors
                if (!isRetryable(e)) {
                    throw e
                }
                
                // Retry with exponential backoff
                if (attempt < maxRetries - 1) {
                    val jitter = Random.nextLong(0, (delay * 0.1).toLong())
                    val actualDelay = minOf((delay + jitter).toLong(), maxDelay)
                    delay(actualDelay)
                    delay *= multiplier
                }
            } catch (e: Throwable) {
                // Convert to ExchangeException and retry if retryable
                val exchangeException = e.toExchangeException()
                lastError = exchangeException
                
                if (!isRetryable(exchangeException)) {
                    throw exchangeException
                }
                
                if (attempt < maxRetries - 1) {
                    val jitter = Random.nextLong(0, (delay * 0.1).toLong())
                    val actualDelay = minOf((delay + jitter).toLong(), maxDelay)
                    delay(actualDelay)
                    delay *= multiplier
                }
            }
        }
        
        throw lastError ?: ExchangeException.Unknown(
            reason = "Operation failed after $maxRetries retries",
            errorType = "RETRY_EXHAUSTED"
        )
    }
    
    /**
     * Determines if an exception is retryable.
     * 
     * Transient errors (network issues, timeouts) are retryable,
     * while validation errors and protocol errors are not.
     * 
     * @param exception The exception to check
     * @return true if the exception is retryable, false otherwise
     */
    fun isRetryable(exception: ExchangeException): Boolean {
        val code = exception.code
        
        // Validation errors are not retryable
        return when (code) {
            "MISSING_REQUIRED_OPTION",
            "INVALID_REQUEST",
            "PROTOCOL_NOT_REGISTERED",
            "OPERATION_NOT_SUPPORTED",
            "OFFER_NOT_FOUND",
            "REQUEST_NOT_FOUND",
            "PROOF_REQUEST_NOT_FOUND",
            "MESSAGE_NOT_FOUND",
            "EXCHANGE_UNKNOWN_ERROR" -> false
            
            // DIDComm protocol errors are not retryable
            "DIDCOMM_PROTOCOL_ERROR" -> false
            
            // CHAPI errors are not retryable
            "CHAPI_BROWSER_NOT_AVAILABLE" -> false
            
            // OIDC4VCI HTTP errors - retry on 5xx, not on 4xx
            "OIDC4VCI_HTTP_REQUEST_FAILED" -> {
                val statusCode = exception.context["statusCode"] as? Int
                statusCode == null || statusCode >= 500
            }
            
            // DIDComm errors might be retryable depending on the reason
            "DIDCOMM_ENCRYPTION_FAILED",
            "DIDCOMM_DECRYPTION_FAILED",
            "DIDCOMM_PACKING_FAILED",
            "DIDCOMM_UNPACKING_FAILED" -> {
                val reason = (exception.context["reason"] as? String ?: exception.message).lowercase()
                reason.contains("timeout") || 
                reason.contains("network") || 
                reason.contains("connection") ||
                reason.contains("unavailable")
            }
            
            // OIDC4VCI errors might be retryable
            "OIDC4VCI_TOKEN_EXCHANGE_FAILED",
            "OIDC4VCI_METADATA_FETCH_FAILED",
            "OIDC4VCI_CREDENTIAL_REQUEST_FAILED" -> {
                val reason = (exception.context["reason"] as? String ?: exception.message).lowercase()
                reason.contains("timeout") || 
                reason.contains("network") || 
                reason.contains("connection") ||
                reason.contains("unavailable")
            }
            
            // Unknown errors are not retryable by default
            else -> false
        }
    }
    
    /**
     * Determines if an exception is transient (temporary).
     * 
     * Transient errors are typically network issues or temporary service unavailability
     * that might resolve on retry.
     * 
     * @param exception The exception to check
     * @return true if the exception is transient, false otherwise
     */
    fun isTransient(exception: ExchangeException): Boolean {
        val code = exception.code
        return when {
            // OIDC4VCI HTTP errors
            code == "OIDC4VCI_HTTP_REQUEST_FAILED" -> {
                val statusCode = exception.context["statusCode"] as? Int
                statusCode == null || statusCode >= 500 || statusCode == 429
            }
            // DIDComm errors
            code.startsWith("DIDCOMM_") -> {
                val reason = exception.context["reason"] as? String ?: exception.message
                val reasonLower = reason.lowercase()
                reasonLower.contains("timeout") || 
                reasonLower.contains("network") || 
                reasonLower.contains("connection") ||
                reasonLower.contains("unavailable")
            }
            // OIDC4VCI other errors
            code.startsWith("OIDC4VCI_") -> {
                val reason = exception.context["reason"] as? String ?: exception.message
                val reasonLower = reason.lowercase()
                reasonLower.contains("timeout") || 
                reasonLower.contains("network") || 
                reasonLower.contains("connection") ||
                reasonLower.contains("unavailable")
            }
            else -> false
        }
    }
    
    /**
     * Gets a user-friendly error message for display.
     * 
     * @param exception The exception
     * @return A user-friendly error message
     */
    fun getUserFriendlyMessage(exception: ExchangeException): String = when (exception) {
        is ExchangeException.ProtocolNotRegistered -> {
            if (exception.availableProtocols.isEmpty()) {
                "No credential exchange protocols are available. Please register a protocol first."
            } else {
                "Protocol '${exception.protocolName}' is not available. " +
                "Available protocols: ${exception.availableProtocols.joinToString(", ")}"
            }
        }
        is ExchangeException.OperationNotSupported -> {
            "Operation '${exception.operation}' is not supported by protocol '${exception.protocolName}'. " +
            "Supported operations: ${exception.supportedOperations.joinToString(", ")}"
        }
        is ExchangeException.MissingRequiredOption -> {
            "Missing required option '${exception.optionName}'. " +
            "Please provide this option to complete the operation."
        }
        is ExchangeException.InvalidRequest -> {
            "Invalid request: ${exception.reason}. " +
            "Please check the '${exception.field}' field and try again."
        }
        is ExchangeException.OfferNotFound -> {
            "Credential offer '${exception.offerId}' not found. " +
            "The offer may have expired or been revoked."
        }
        is ExchangeException.RequestNotFound -> {
            "Credential request '${exception.requestId}' not found. " +
            "The request may have expired or been cancelled."
        }
        is ExchangeException.ProofRequestNotFound -> {
            "Proof request '${exception.requestId}' not found. " +
            "The request may have expired or been cancelled."
        }
        else -> {
            // Handle plugin-specific exceptions by code
            val code = exception.code
            when {
                code.startsWith("DIDCOMM_") -> {
                    when (code) {
                        "DIDCOMM_ENCRYPTION_FAILED" -> {
                            "Failed to encrypt DIDComm message. " +
                            "Please check your encryption keys and try again."
                        }
                        "DIDCOMM_DECRYPTION_FAILED" -> {
                            "Failed to decrypt DIDComm message. " +
                            "Please check your decryption keys and try again."
                        }
                        else -> exception.message
                    }
                }
                code.startsWith("OIDC4VCI_") -> {
                    when (code) {
                        "OIDC4VCI_HTTP_REQUEST_FAILED" -> {
                            val statusCode = exception.context["statusCode"] as? Int
                            if (statusCode != null) {
                                "HTTP request failed with status $statusCode. " +
                                "Please check the server status and try again."
                            } else {
                                val reason = exception.context["reason"] as? String ?: exception.message
                                "HTTP request failed: $reason. " +
                                "Please check your network connection and try again."
                            }
                        }
                        else -> exception.message
                    }
                }
                code.startsWith("CHAPI_") -> {
                    when (code) {
                        "CHAPI_BROWSER_NOT_AVAILABLE" -> {
                            "CHAPI requires a browser environment. " +
                            "This operation cannot be performed outside of a browser."
                        }
                        else -> exception.message
                    }
                }
                else -> exception.message
            }
        }
    }
    
    /**
     * Attempts to recover from an error by trying an alternative protocol.
     * 
     * @param exception The exception that occurred
     * @param availableProtocols List of available protocol names
     * @param operation Function to execute with the alternative protocol
     * @return The result of the alternative operation, or null if no alternative is available
     */
    suspend fun <T> tryAlternativeProtocol(
        exception: ExchangeException,
        availableProtocols: List<String>,
        operation: suspend (String) -> T
    ): T? {
        if (exception !is ExchangeException.ProtocolNotRegistered &&
            exception !is ExchangeException.OperationNotSupported) {
            return null
        }
        
        val failedProtocol = when (exception) {
            is ExchangeException.ProtocolNotRegistered -> exception.protocolName
            is ExchangeException.OperationNotSupported -> exception.protocolName
            else -> return null
        }
        
        // Try alternative protocols
        for (protocol in availableProtocols) {
            if (protocol != failedProtocol) {
                try {
                    return operation(protocol)
                } catch (e: ExchangeException) {
                    // Continue to next protocol
                    continue
                }
            }
        }
        
        return null
    }
}

/**
 * Extension function for retrying exchange operations.
 * 
 * **Example:**
 * ```kotlin
 * val offer = retryExchangeOperation {
 *     registry.offerCredential("didcomm", request)
 * }
 * ```
 */
suspend fun <T> retryExchangeOperation(
    maxRetries: Int = 3,
    initialDelay: Long = 1000,
    maxDelay: Long = 10000,
    multiplier: Double = 2.0,
    operation: suspend () -> T
): T = ExchangeExceptionRecovery.retryExchangeOperation(
    maxRetries = maxRetries,
    initialDelay = initialDelay,
    maxDelay = maxDelay,
    multiplier = multiplier,
    operation = operation
)

