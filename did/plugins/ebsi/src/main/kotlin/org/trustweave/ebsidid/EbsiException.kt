package org.trustweave.ebsidid

import org.slf4j.LoggerFactory
import org.trustweave.core.exception.TrustWeaveException

/**
 * Exception type for EBSI DID method errors.
 *
 * @property code        Machine-readable error code (e.g. `"EBSI_NOT_FOUND"`).
 * @property message     Human-readable description.
 * @property httpStatus  HTTP status code when the error originated from an API call, or `null`.
 * @param cause       Optional underlying exception.
 */
class EbsiException(
    override val code: String,
    override val message: String,
    val httpStatus: Int? = null,
    cause: Throwable? = null,
) : TrustWeaveException(code, message, buildContext(httpStatus), cause) {
    companion object {
        private fun buildContext(httpStatus: Int?): Map<String, Any?> =
            if (httpStatus != null) mapOf("httpStatus" to httpStatus) else emptyMap()

        /** Shorthand for a 404 response from the EBSI registry. */
        fun notFound(did: String): EbsiException =
            EbsiException(
                code = "EBSI_NOT_FOUND",
                message = "EBSI DID document not found: $did",
                httpStatus = 404,
            )

        /** Shorthand for a missing bearer token when write operations are attempted. */
        fun authRequired(operation: String): EbsiException =
            EbsiException(
                code = "EBSI_AUTH_REQUIRED",
                message =
                    "Bearer token is required for $operation on EBSI. " +
                        "Set EbsiDidConfig.bearerToken or use resolve-only mode.",
            )

        private val log = LoggerFactory.getLogger(EbsiException::class.java)

        /**
         * Shorthand for unexpected HTTP errors.
         *
         * The message carries only the status; the upstream [body] is untrusted and may be large or
         * sensitive, so it is written (sanitised, size-capped) to the debug log only.
         */
        fun httpError(
            status: Int,
            body: String,
        ): EbsiException {
            if (log.isDebugEnabled) log.debug("EBSI API returned HTTP {}; body: {}", status, sanitizeUpstreamBody(body))
            return EbsiException(
                code = "EBSI_HTTP_ERROR",
                message = "EBSI API returned HTTP $status",
                httpStatus = status,
            )
        }
    }
}

private const val MAX_LOGGED_BODY = 512

/** Strips control characters and caps [body] so an upstream response cannot forge or flood log lines. */
internal fun sanitizeUpstreamBody(body: String): String {
    val cleaned = body.take(MAX_LOGGED_BODY).map { if (it.isISOControl()) ' ' else it }.joinToString("")
    return if (body.length > MAX_LOGGED_BODY) "$cleaned...[truncated ${body.length - MAX_LOGGED_BODY} chars]" else cleaned
}
