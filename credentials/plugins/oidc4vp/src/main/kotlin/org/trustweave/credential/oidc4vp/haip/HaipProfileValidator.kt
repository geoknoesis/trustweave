package org.trustweave.credential.oidc4vp.haip

import org.trustweave.credential.oidc4vp.models.AuthorizationRequest
import org.trustweave.credential.oidc4vp.models.ClientIdScheme

/**
 * High Assurance Interoperability Profile (HAIP) validator for OID4VP authorization requests.
 *
 * HAIP mandates specific constraints over OID4VP to achieve high-assurance credential exchange:
 * - Credential formats restricted to `vc+sd-jwt` and `mso_mdoc`
 * - Client ID scheme must be `did`, `x509_san_dns`, or `verifier_attestation`
 * - Response mode must be `direct_post` or `direct_post.jwt`
 * - A `nonce` is required for replay-protection
 *
 * Usage:
 * ```kotlin
 * val violations = HaipProfileValidator.validateAuthorizationRequest(authorizationRequest)
 * if (violations.isNotEmpty()) {
 *     throw Oidc4VpException.HaipViolation(violations)
 * }
 * ```
 *
 * @see <a href="https://openid.net/specs/openid4vc-haip.html">OpenID4VC HAIP specification</a>
 */
object HaipProfileValidator {

    /** Credential formats permitted by HAIP. */
    val SUPPORTED_FORMATS: Set<String> = setOf("vc+sd-jwt", "mso_mdoc")

    /** Client ID schemes permitted by HAIP. */
    val SUPPORTED_CLIENT_ID_SCHEMES: Set<ClientIdScheme> = setOf(
        ClientIdScheme.DID,
        ClientIdScheme.X509_SAN_DNS,
        ClientIdScheme.VERIFIER_ATTESTATION,
    )

    /** Response modes permitted by HAIP. */
    val SUPPORTED_RESPONSE_MODES: Set<String> = setOf("direct_post", "direct_post.jwt")

    /**
     * Validate an OID4VP [AuthorizationRequest] for HAIP compliance.
     *
     * @return A (possibly empty) list of [HaipViolation]s. An empty list means the request is
     *   HAIP-compliant.
     */
    fun validateAuthorizationRequest(request: AuthorizationRequest): List<HaipViolation> {
        val violations = mutableListOf<HaipViolation>()

        // client_id_scheme must be did, x509_san_dns, or verifier_attestation
        if (request.clientIdScheme !in SUPPORTED_CLIENT_ID_SCHEMES) {
            violations += HaipViolation(
                field = "client_id_scheme",
                message = "HAIP requires client_id_scheme to be one of " +
                    "${SUPPORTED_CLIENT_ID_SCHEMES.joinToString { it.name.lowercase() }}, " +
                    "got '${request.clientIdScheme.name.lowercase()}'",
            )
        }

        // response_mode must be direct_post or direct_post.jwt
        val responseMode = request.responseMode
        if (responseMode == null || responseMode !in SUPPORTED_RESPONSE_MODES) {
            violations += HaipViolation(
                field = "response_mode",
                message = "HAIP requires response_mode to be one of " +
                    "${SUPPORTED_RESPONSE_MODES.joinToString { "'$it'" }}, " +
                    "got '${responseMode ?: "<absent>"}'",
            )
        }

        // response_uri is required when response_mode is direct_post
        if (responseMode != null && responseMode in SUPPORTED_RESPONSE_MODES && request.responseUri == null) {
            violations += HaipViolation(
                field = "response_uri",
                message = "HAIP requires 'response_uri' when response_mode is '$responseMode'",
            )
        }

        // nonce is mandatory for replay-attack protection
        if (request.nonce.isNullOrBlank()) {
            violations += HaipViolation(
                field = "nonce",
                message = "HAIP requires a non-empty 'nonce' for replay-attack protection",
            )
        }

        // At least one of presentation_definition or dcql_query must be present
        if (request.presentationDefinition == null && request.dcqlQuery == null) {
            violations += HaipViolation(
                field = "presentation_definition",
                message = "HAIP requires either 'presentation_definition' or 'dcql_query'",
            )
        }

        return violations
    }

    /**
     * Validate that a credential format string is HAIP-permitted.
     *
     * @return `true` if the format is allowed by HAIP.
     */
    fun isHaipFormat(format: String): Boolean = format in SUPPORTED_FORMATS

    /**
     * Validate that a credential format string is HAIP-permitted, returning a violation if not.
     *
     * @return A [HaipViolation] if [format] is not HAIP-permitted, `null` otherwise.
     */
    fun validateFormat(format: String): HaipViolation? =
        if (format !in SUPPORTED_FORMATS) {
            HaipViolation(
                field = "format",
                message = "HAIP allows only ${SUPPORTED_FORMATS.joinToString { "'$it'" }} formats, got '$format'",
            )
        } else null
}

/**
 * A single HAIP compliance violation, identifying the offending field and the reason.
 */
data class HaipViolation(
    val field: String,
    val message: String,
)
