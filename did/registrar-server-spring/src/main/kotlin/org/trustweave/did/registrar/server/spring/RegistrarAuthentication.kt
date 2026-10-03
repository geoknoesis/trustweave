package org.trustweave.did.registrar.server.spring

import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.trustweave.did.registrar.server.spring.dto.ErrorResponse
import java.security.MessageDigest

/**
 * Authentication for the registrar's endpoints: the mutating ones (create, update, deactivate)
 * and, unless [publicJobStatus] is set, the job-status read, mirroring the Ktor server's
 * `HostAuthentication`. Job records can carry secrets and DID state, so they are not public by
 * default.
 *
 * These endpoints perform key custody operations, so they are refused until the host states what
 * protects them: a bearer token ([bearerToken]), or an explicit declaration that something in
 * front of this server already authenticates callers ([frontedByProxy]). With neither
 * ([unconfigured], the default) every mutation and every non-public read is refused with 503 and
 * an actionable message.
 *
 * This is authentication, not encryption: serve it over TLS, or a bearer token is readable by
 * anything on the path.
 */
class RegistrarAuthentication private constructor(
    /** SHA-256 of the expected token: comparing digests leaks neither the token nor its length. */
    private val expectedDigest: ByteArray?,
    /** What authenticates callers in front of this server, for [frontedByProxy]. */
    val delegatedTo: String?,
    /**
     * When true, `GET /1.0/jobs/{id}` needs no credentials. Off by default; set it
     * (`trustweave.registrar.auth.public-job-status=true`) only when job ids are unguessable and
     * job results hold nothing sensitive.
     */
    val publicJobStatus: Boolean = false,
) {
    companion object {
        private const val MAX_HEADER_LENGTH = 263
        private const val BEARER_SCHEME = "bearer"
        private val log = LoggerFactory.getLogger(RegistrarAuthentication::class.java)

        /** Values that are a switch, not a statement of what authenticates callers. */
        private val NON_REASONS = setOf("true", "false", "yes", "no", "on", "off", "1", "0", "y", "n")

        private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

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
            return RegistrarAuthentication(sha256(token.toByteArray(Charsets.UTF_8)), null)
        }

        /**
         * Records that a proxy or gateway in front of this server authenticates callers. Admits
         * every request; it exists so that decision is explicit in the host's code.
         *
         * @param reason a non-blank statement of what authenticates callers (for example "mTLS at
         *   the ingress gateway"); a bare switch such as `true` is not a reason and is refused.
         *   A warning is logged because every request is admitted from here on.
         */
        @JvmStatic
        fun frontedByProxy(reason: String): RegistrarAuthentication {
            require(reason.isNotBlank()) { "State what authenticates callers in front of this server" }
            require(reason.trim().lowercase() !in NON_REASONS) {
                "fronted-by-proxy must state what authenticates callers (e.g. 'mTLS at the ingress gateway'), not '$reason'"
            }
            log.warn(
                "DID registrar authentication is delegated to an upstream proxy ({}): this server admits EVERY " +
                    "request, so it must never be reachable except through that proxy.",
                reason.trim(),
            )
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
            publicJobStatus: Boolean = false,
        ): RegistrarAuthentication {
            val base =
                when {
                    !bearerToken.isNullOrBlank() -> bearerToken(bearerToken.trim())
                    !frontedByProxy.isNullOrBlank() -> frontedByProxy(frontedByProxy)
                    else -> unconfigured()
                }
            return if (publicJobStatus) base.withPublicJobStatus() else base
        }
    }

    /** Copy of this configuration that lets job status reads through without credentials. */
    fun withPublicJobStatus(): RegistrarAuthentication {
        log.warn("DID registrar job status is PUBLIC (trustweave.registrar.auth.public-job-status=true).")
        return RegistrarAuthentication(expectedDigest, delegatedTo, publicJobStatus = true)
    }

    /**
     * Like [refusal], for the job-status read: admitted without credentials only when
     * [publicJobStatus] is set.
     */
    fun jobStatusRefusal(authorizationHeader: String?): ResponseEntity<Any>? = if (publicJobStatus) null else refusal(authorizationHeader)

    /**
     * Returns `null` when a mutation carrying [authorizationHeader] may proceed, otherwise the
     * refusal to send back.
     */
    fun refusal(authorizationHeader: String?): ResponseEntity<Any>? {
        if (delegatedTo != null) return null
        val expected =
            expectedDigest ?: return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(
                    ErrorResponse.fromMessage(
                        "The DID registrar refuses requests until authentication is configured: set " +
                            "trustweave.registrar.auth.bearer-token, or trustweave.registrar.auth.fronted-by-proxy " +
                            "to record what already authenticates callers",
                        "AUTHENTICATION_NOT_CONFIGURED",
                    ),
                )
        // RFC 7235 section 2.1: the scheme is case-insensitive. Compare digests of the credentials
        // so the comparison time depends neither on the token's content nor on its length.
        val presented = bearerCredentials(authorizationHeader) ?: ""
        if (MessageDigest.isEqual(expected, sha256(presented.toByteArray(Charsets.UTF_8)))) return null
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(ErrorResponse.fromMessage("Caller is not authorized for this operation", "UNAUTHORIZED"))
    }

    /** Extracts the token of an `Authorization: Bearer <token>` header, or null if it is not one. */
    private fun bearerCredentials(header: String?): String? {
        if (header == null || header.length > MAX_HEADER_LENGTH) return null
        val space = header.indexOf(' ')
        if (space <= 0) return null
        if (!header.substring(0, space).equals(BEARER_SCHEME, ignoreCase = true)) return null
        return header.substring(space).trimStart(' ').takeIf { it.isNotEmpty() }
    }

    override fun toString(): String =
        when {
            expectedDigest != null -> "RegistrarAuthentication(bearerToken=<redacted>)"
            delegatedTo != null -> "RegistrarAuthentication(frontedByProxy=$delegatedTo)"
            else -> "RegistrarAuthentication(unconfigured)"
        }
}
