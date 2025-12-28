package org.trustweave.credential.oidc4vp.exception

import org.trustweave.core.exception.TrustWeaveException

/**
 * OIDC4VP-specific exception types.
 *
 * These exceptions extend TrustWeaveException and provide OIDC4VP-specific error information.
 */
sealed class Oidc4VpException(
    override val code: String,
    override val message: String,
    override val context: Map<String, Any?> = emptyMap(),
    override val cause: Throwable? = null
) : TrustWeaveException(code, message, context, cause) {

    /**
     * Exception thrown when an OIDC4VP HTTP request fails.
     *
     * @param url The URL that was requested
     * @param statusCode The HTTP status code (if available)
     * @param reason The reason the request failed
     * @param cause The underlying exception
     */
    data class HttpRequestFailed(
        val url: String,
        val statusCode: Int? = null,
        val reason: String,
        override val cause: Throwable? = null
    ) : Oidc4VpException(
        code = "OIDC4VP_HTTP_REQUEST_FAILED",
        message = "OIDC4VP HTTP request failed: $reason${statusCode?.let { " (HTTP $it)" } ?: ""}",
        context = mapOf(
            "url" to url,
            "statusCode" to statusCode,
            "reason" to reason
        ).filterValues { it != null },
        cause = cause
    )

    /**
     * Exception thrown when OIDC4VP authorization request fetch fails.
     *
     * @param requestUri The request URI that was requested
     * @param reason The reason the fetch failed
     * @param cause The underlying exception
     */
    data class AuthorizationRequestFetchFailed(
        val requestUri: String,
        val reason: String,
        override val cause: Throwable? = null
    ) : Oidc4VpException(
        code = "OIDC4VP_AUTHORIZATION_REQUEST_FETCH_FAILED",
        message = "Failed to fetch OIDC4VP authorization request from '$requestUri': $reason",
        context = mapOf(
            "requestUri" to requestUri,
            "reason" to reason
        ),
        cause = cause
    )

    /**
     * Exception thrown when OIDC4VP metadata fetch fails.
     *
     * @param verifierUrl The verifier URL
     * @param reason The reason metadata fetch failed
     * @param cause The underlying exception
     */
    data class MetadataFetchFailed(
        val verifierUrl: String,
        val reason: String,
        override val cause: Throwable? = null
    ) : Oidc4VpException(
        code = "OIDC4VP_METADATA_FETCH_FAILED",
        message = "Failed to fetch OIDC4VP metadata from '$verifierUrl': $reason",
        context = mapOf(
            "verifierUrl" to verifierUrl,
            "reason" to reason
        ),
        cause = cause
    )

    /**
     * Exception thrown when OIDC4VP presentation submission fails.
     *
     * @param reason The reason presentation submission failed
     * @param verifierUrl The verifier URL (if available)
     * @param cause The underlying exception
     */
    data class PresentationSubmissionFailed(
        val reason: String,
        val verifierUrl: String? = null,
        override val cause: Throwable? = null
    ) : Oidc4VpException(
        code = "OIDC4VP_PRESENTATION_SUBMISSION_FAILED",
        message = "OIDC4VP presentation submission failed: $reason",
        context = mapOf(
            "reason" to reason,
            "verifierUrl" to verifierUrl
        ).filterValues { it != null },
        cause = cause
    )

    /**
     * Exception thrown when URL parsing fails.
     *
     * @param url The URL that failed to parse
     * @param reason The reason parsing failed
     */
    data class UrlParseFailed(
        val url: String,
        val reason: String
    ) : Oidc4VpException(
        code = "OIDC4VP_URL_PARSE_FAILED",
        message = "Failed to parse OIDC4VP URL '$url': $reason",
        context = mapOf(
            "url" to url,
            "reason" to reason
        )
    )
}

