package org.trustweave.did.orb

import org.slf4j.LoggerFactory
import org.trustweave.core.exception.TrustWeaveException

/**
 * Exception type for did:orb method errors.
 *
 * @property code       Machine-readable error code.
 * @property message    Human-readable description.
 * @property httpStatus HTTP status code when the error originated from an Orb API call, else `null`.
 * @param cause      Optional underlying exception.
 */
class OrbException(
    override val code: String,
    override val message: String,
    val httpStatus: Int? = null,
    cause: Throwable? = null,
) : TrustWeaveException(code, message, buildContext(httpStatus), cause) {
    companion object {
        private fun buildContext(httpStatus: Int?): Map<String, Any?> =
            if (httpStatus != null) mapOf("httpStatus" to httpStatus) else emptyMap()

        fun notFound(did: String): OrbException =
            OrbException(
                code = "ORB_NOT_FOUND",
                message = "Orb DID document not found: $did",
                httpStatus = 404,
            )

        private val log = LoggerFactory.getLogger(OrbException::class.java)

        /**
         * The message carries only the status (or "request failed" when there was no HTTP response);
         * the upstream [body] is untrusted and may be large or sensitive, so it is written
         * (sanitised, size-capped) to the debug log only.
         */
        fun httpError(
            status: Int,
            body: String,
        ): OrbException {
            if (log.isDebugEnabled) log.debug("Orb node returned HTTP {}; detail: {}", status, sanitizeUpstreamBody(body))
            return OrbException(
                code = "ORB_HTTP_ERROR",
                message = if (status > 0) "Orb node returned HTTP $status" else "Orb node request failed (no HTTP response)",
                httpStatus = status,
            )
        }

        fun invalidResponse(
            message: String,
            cause: Throwable? = null,
        ): OrbException =
            OrbException(
                code = "ORB_INVALID_RESPONSE",
                message = message,
                cause = cause,
            )
    }
}

private const val MAX_LOGGED_BODY = 512

/** Strips control characters and caps [body] so an upstream response cannot forge or flood log lines. */
internal fun sanitizeUpstreamBody(body: String): String {
    val cleaned = body.take(MAX_LOGGED_BODY).map { if (it.isISOControl()) ' ' else it }.joinToString("")
    return if (body.length > MAX_LOGGED_BODY) "$cleaned...[truncated ${body.length - MAX_LOGGED_BODY} chars]" else cleaned
}
