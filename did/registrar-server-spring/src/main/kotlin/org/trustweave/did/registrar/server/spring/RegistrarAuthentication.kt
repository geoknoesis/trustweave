package org.trustweave.did.registrar.server.spring

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.trustweave.did.registrar.server.spring.dto.ErrorResponse
import java.security.MessageDigest

/**
 * Authentication for the registrar's mutating endpoints (create, update, deactivate), mirroring
 * the Ktor server's `HostAuthentication`.
 *
 * These endpoints perform key custody operations, so they are refused until the host states what
 * protects them: a bearer token ([bearerToken]), or an explicit declaration that something in
 * front of this server already authenticates callers ([frontedByProxy]). With neither
 * ([unconfigured], the default) every mutation is refused with 503 and an actionable message;
 * reads (job status) keep working.
 *
 * This is authentication, not encryption: serve it over TLS, or a bearer token is readable by
 * anything on the path.
 */
class RegistrarAuthentication private constructor(
    private val expected: ByteArray?,
    /** What authenticates callers in front of this server, for [frontedByProxy]. */
    val delegatedTo: String?,
) {
    companion object {
        private const val MAX_HEADER_LENGTH = 263

        /**
         * Requires `Authorization: Bearer <token>`, compared in constant time.
         *
         * @param token 32-256 printable non-space ASCII characters; shorter tokens are refused.
         */
        @JvmStatic
        fun bearerToken(token: String): RegistrarAuthentication {
            require(token.length in 32..256 && token.all { it.code in 33..126 }) {
                "Bearer token must contain 32-256 printable non-space ASCII characters"
            }
            return RegistrarAuthentication("Bearer $token".toByteArray(Charsets.UTF_8), null)
        }

        /**
         * Records that a proxy or gateway in front of this server authenticates callers. Admits
         * every request; it exists so that decision is explicit in the host's code.
         */
        @JvmStatic
        fun frontedByProxy(reason: String): RegistrarAuthentication {
            require(reason.isNotBlank()) { "State what authenticates callers in front of this server" }
            return RegistrarAuthentication(null, reason.trim())
        }

        /** The fail-closed default: refuse every mutation. */
        @JvmStatic
        fun unconfigured(): RegistrarAuthentication = RegistrarAuthentication(null, null)

        /**
         * Builds the configuration from property values (blank means "not set"). A bearer token
         * wins over a proxy declaration; neither gives [unconfigured].
         */
        @JvmStatic
        fun fromProperties(
            bearerToken: String?,
            frontedByProxy: String?,
        ): RegistrarAuthentication =
            when {
                !bearerToken.isNullOrBlank() -> bearerToken(bearerToken.trim())
                !frontedByProxy.isNullOrBlank() -> frontedByProxy(frontedByProxy)
                else -> unconfigured()
            }
    }

    /**
     * Returns `null` when a mutation carrying [authorizationHeader] may proceed, otherwise the
     * refusal to send back.
     */
    fun refusal(authorizationHeader: String?): ResponseEntity<Any>? {
        if (delegatedTo != null) return null
        val token =
            expected ?: return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(
                    ErrorResponse.fromMessage(
                        "The DID registrar refuses mutating requests until authentication is configured: set " +
                            "trustweave.registrar.auth.bearer-token, or trustweave.registrar.auth.fronted-by-proxy " +
                            "to record what already authenticates callers",
                        "AUTHENTICATION_NOT_CONFIGURED",
                    ),
                )
        val actual =
            authorizationHeader
                ?.takeIf { it.length <= MAX_HEADER_LENGTH }
                ?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        if (MessageDigest.isEqual(token, actual)) return null
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(ErrorResponse.fromMessage("Caller is not authorized for this operation", "UNAUTHORIZED"))
    }

    override fun toString(): String =
        when {
            expected != null -> "RegistrarAuthentication(bearerToken=<redacted>)"
            delegatedTo != null -> "RegistrarAuthentication(frontedByProxy=$delegatedTo)"
            else -> "RegistrarAuthentication(unconfigured)"
        }
}
