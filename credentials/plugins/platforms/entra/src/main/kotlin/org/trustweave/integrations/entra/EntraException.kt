package org.trustweave.integrations.entra

import org.trustweave.core.exception.TrustWeaveException

/**
 * Errors raised by the Microsoft Entra Verified ID plugin.
 *
 * Sealed subclass of [TrustWeaveException] so callers can pattern-match across
 * authentication, HTTP, and protocol-level failures.
 */
sealed class EntraException(
    code: String,
    message: String,
    context: Map<String, Any?> = emptyMap(),
    cause: Throwable? = null,
) : TrustWeaveException(code, message, context, cause) {
    /** OAuth2 client_credentials flow failed. */
    class AuthenticationFailed(
        message: String,
        context: Map<String, Any?> = emptyMap(),
        cause: Throwable? = null,
    ) : EntraException("ENTRA_AUTH_FAILED", message, context, cause)

    /** Verified ID Request Service returned a non-2xx status. */
    class RequestServiceError private constructor(
        val httpStatus: Int,
        /** A short, control-character-free excerpt of the service response; never the full body. */
        val responseBody: String,
        message: String,
        context: Map<String, Any?>,
        cause: Throwable?,
        @Suppress("UNUSED_PARAMETER") marker: Unit,
    ) : EntraException(
            "ENTRA_REQUEST_SERVICE_ERROR",
            message,
            // The excerpt is deliberately NOT placed in the context map: contexts are logged and
            // serialized, and a remote body can echo secrets or attacker-controlled text.
            context + mapOf("httpStatus" to httpStatus),
            cause,
        ) {
        constructor(
            httpStatus: Int,
            responseBody: String,
            message: String,
            context: Map<String, Any?> = emptyMap(),
            cause: Throwable? = null,
        ) : this(httpStatus, excerpt(responseBody), message, context, cause, Unit)
    }

    /** A wire payload was malformed and could not be parsed. */
    class MalformedResponse(
        message: String,
        context: Map<String, Any?> = emptyMap(),
        cause: Throwable? = null,
    ) : EntraException("ENTRA_MALFORMED_RESPONSE", message, context, cause)

    /** Callback payload carried an `error` block (issuance_error / presentation_error). */
    class CallbackError(
        val errorCode: String,
        message: String,
        context: Map<String, Any?> = emptyMap(),
    ) : EntraException(
            "ENTRA_CALLBACK_ERROR",
            message,
            context + mapOf("errorCode" to errorCode),
        )

    /** Required options/metadata missing from an exchange request. */
    class MissingOption(
        val option: String,
        message: String = "Missing required Entra option: $option",
    ) : EntraException("ENTRA_MISSING_OPTION", message, mapOf("option" to option))

    internal companion object {
        private const val MAX_EXCERPT_CHARS = 256

        fun excerpt(raw: String): String = raw.take(MAX_EXCERPT_CHARS).filter { !it.isISOControl() || it == ' ' }
    }
}
