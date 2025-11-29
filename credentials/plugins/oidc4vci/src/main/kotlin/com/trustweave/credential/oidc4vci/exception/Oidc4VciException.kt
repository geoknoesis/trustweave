package com.trustweave.credential.oidc4vci.exception

import com.trustweave.credential.exchange.exception.ExchangeException

/**
 * OIDC4VCI-specific exception types.
 *
 * These exceptions extend ExchangeException and provide OIDC4VCI-specific error information.
 * All OIDC4VCI exceptions are part of the ExchangeException hierarchy for consistent handling.
 */
sealed class Oidc4VciException(
    override val code: String,
    override val message: String,
    override val context: Map<String, Any?> = emptyMap(),
    override val cause: Throwable? = null
) : ExchangeException(code, message, context, cause) {

    /**
     * Exception thrown when an OIDC4VCI HTTP request fails.
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
    ) : Oidc4VciException(
        code = "OIDC4VCI_HTTP_REQUEST_FAILED",
        message = "OIDC4VCI HTTP request failed: $reason${statusCode?.let { " (HTTP $it)" } ?: ""}",
        context = mapOf(
            "url" to url,
            "statusCode" to statusCode,
            "reason" to reason
        ).filterValues { it != null },
        cause = cause
    )

    /**
     * Exception thrown when OIDC4VCI token exchange fails.
     *
     * @param reason The reason token exchange failed
     * @param credentialIssuer The credential issuer URL (if available)
     * @param cause The underlying exception
     */
    data class TokenExchangeFailed(
        val reason: String,
        val credentialIssuer: String? = null,
        override val cause: Throwable? = null
    ) : Oidc4VciException(
        code = "OIDC4VCI_TOKEN_EXCHANGE_FAILED",
        message = "OIDC4VCI token exchange failed: $reason",
        context = mapOf(
            "reason" to reason,
            "credentialIssuer" to credentialIssuer
        ).filterValues { it != null },
        cause = cause
    )

    /**
     * Exception thrown when OIDC4VCI metadata fetch fails.
     *
     * @param credentialIssuer The credential issuer URL
     * @param reason The reason metadata fetch failed
     * @param cause The underlying exception
     */
    data class MetadataFetchFailed(
        val credentialIssuer: String,
        val reason: String,
        override val cause: Throwable? = null
    ) : Oidc4VciException(
        code = "OIDC4VCI_METADATA_FETCH_FAILED",
        message = "Failed to fetch OIDC4VCI metadata from '$credentialIssuer': $reason",
        context = mapOf(
            "credentialIssuer" to credentialIssuer,
            "reason" to reason
        ),
        cause = cause
    )

    /**
     * Exception thrown when OIDC4VCI credential request fails.
     *
     * @param reason The reason credential request failed
     * @param credentialIssuer The credential issuer URL (if available)
     * @param cause The underlying exception
     */
    data class CredentialRequestFailed(
        val reason: String,
        val credentialIssuer: String? = null,
        override val cause: Throwable? = null
    ) : Oidc4VciException(
        code = "OIDC4VCI_CREDENTIAL_REQUEST_FAILED",
        message = "OIDC4VCI credential request failed: $reason",
        context = mapOf(
            "reason" to reason,
            "credentialIssuer" to credentialIssuer
        ).filterValues { it != null },
        cause = cause
    )
}

