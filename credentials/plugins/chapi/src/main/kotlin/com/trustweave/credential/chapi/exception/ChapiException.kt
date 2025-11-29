package com.trustweave.credential.chapi.exception

import com.trustweave.credential.exchange.exception.ExchangeException

/**
 * CHAPI-specific exception types.
 *
 * These exceptions extend ExchangeException and provide CHAPI-specific error information.
 * All CHAPI exceptions are part of the ExchangeException hierarchy for consistent handling.
 */
sealed class ChapiException(
    override val code: String,
    override val message: String,
    override val context: Map<String, Any?> = emptyMap(),
    override val cause: Throwable? = null
) : ExchangeException(code, message, context, cause) {

    /**
     * Exception thrown when CHAPI browser environment is not available.
     *
     * @param reason The reason browser is not available
     */
    data class BrowserNotAvailable(
        val reason: String = "CHAPI requires browser environment"
    ) : ChapiException(
        code = "CHAPI_BROWSER_NOT_AVAILABLE",
        message = reason,
        context = mapOf("reason" to reason)
    )
}

