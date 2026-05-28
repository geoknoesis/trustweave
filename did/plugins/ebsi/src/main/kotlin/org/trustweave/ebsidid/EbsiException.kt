package org.trustweave.ebsidid

import org.trustweave.core.exception.TrustWeaveException

/**
 * Exception type for EBSI DID method errors.
 *
 * @property code        Machine-readable error code (e.g. `"EBSI_NOT_FOUND"`).
 * @property message     Human-readable description.
 * @property httpStatus  HTTP status code when the error originated from an API call, or `null`.
 * @param    cause       Optional underlying exception.
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
                message = "Bearer token is required for $operation on EBSI. " +
                    "Set EbsiDidConfig.bearerToken or use resolve-only mode.",
            )

        /** Shorthand for unexpected HTTP errors. */
        fun httpError(status: Int, body: String): EbsiException =
            EbsiException(
                code = "EBSI_HTTP_ERROR",
                message = "EBSI API returned HTTP $status: $body",
                httpStatus = status,
            )
    }
}
