package org.trustweave.did.orb

import org.trustweave.core.exception.TrustWeaveException

/**
 * Exception type for did:orb method errors.
 *
 * @property code       Machine-readable error code.
 * @property message    Human-readable description.
 * @property httpStatus HTTP status code when the error originated from an Orb API call, else `null`.
 * @param    cause      Optional underlying exception.
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

        fun httpError(status: Int, body: String): OrbException =
            OrbException(
                code = "ORB_HTTP_ERROR",
                message = "Orb node returned HTTP $status: $body",
                httpStatus = status,
            )

        fun invalidResponse(message: String, cause: Throwable? = null): OrbException =
            OrbException(
                code = "ORB_INVALID_RESPONSE",
                message = message,
                cause = cause,
            )
    }
}
