package org.trustweave.credential.eudiw

/**
 * EUDIW High Assurance Interoperability Profile constraints for OID4VP flows.
 *
 * Defines the mandatory response modes, `client_id_scheme` values, and validation
 * rules that Verifiers and Wallets MUST conform to when presenting credentials
 * within the EUDIW ecosystem.
 *
 * References:
 * - EUDIW ARF §6.4 — Credential presentation (OID4VP profile)
 * - OpenID for Verifiable Presentations (OID4VP) spec
 */
object EudiwOid4VpProfile {

    /**
     * Response modes required by EUDIW.
     *
     * EUDIW mandates that the authorization response is sent via a POST request
     * to the Verifier's `response_uri`, rather than via a redirect. The `.jwt`
     * variant adds JOSE protection for the response.
     */
    val REQUIRED_RESPONSE_MODES: Set<String> = setOf("direct_post", "direct_post.jwt")

    /**
     * `client_id_scheme` values recognized by EUDIW-conformant wallets.
     *
     * - `did`: Verifier is identified by a DID; request object must be signed by
     *   a key in the DID Document.
     * - `x509_san_dns` / `x509_san_uri`: Verifier is identified by an X.509
     *   certificate's Subject Alternative Name.
     * - `verifier_attestation`: Verifier presents a wallet-verifier-attestation JWT
     *   issued by a trust framework body.
     */
    val SUPPORTED_CLIENT_ID_SCHEMES: Set<String> = setOf(
        "did",
        "x509_san_dns",
        "x509_san_uri",
        "verifier_attestation",
    )

    /**
     * Maximum age of a presentation request nonce in seconds.
     *
     * Wallets MUST reject presentation requests whose nonce was issued more than
     * [MAX_NONCE_AGE_SECONDS] seconds before the current time, to prevent replay attacks.
     */
    const val MAX_NONCE_AGE_SECONDS: Long = 300L  // 5 minutes

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    /** Outcome of a profile validation check. */
    data class ValidationResult(val valid: Boolean, val violations: List<String>)

    /**
     * Validates that the `response_mode` in an Authorization Request conforms to
     * EUDIW requirements.
     *
     * @param responseMode The `response_mode` value from the Authorization Request, or
     *   `null` if the parameter was absent.
     */
    fun validateResponseMode(responseMode: String?): ValidationResult {
        val violations = if (responseMode == null || responseMode !in REQUIRED_RESPONSE_MODES) {
            listOf(
                "EUDIW requires response_mode to be one of $REQUIRED_RESPONSE_MODES, " +
                    "but received: $responseMode",
            )
        } else {
            emptyList()
        }
        return ValidationResult(violations.isEmpty(), violations)
    }

    /**
     * Validates that the `client_id_scheme` in an Authorization Request is recognized
     * by the EUDIW profile.
     *
     * @param scheme The `client_id_scheme` value from the Authorization Request, or
     *   `null` if the parameter was absent.
     */
    fun validateClientIdScheme(scheme: String?): ValidationResult {
        val violations = if (scheme == null || scheme !in SUPPORTED_CLIENT_ID_SCHEMES) {
            listOf(
                "EUDIW requires client_id_scheme to be one of $SUPPORTED_CLIENT_ID_SCHEMES, " +
                    "but received: $scheme",
            )
        } else {
            emptyList()
        }
        return ValidationResult(violations.isEmpty(), violations)
    }
}
