package org.trustweave.credential.eudiw

/**
 * EUDIW High Assurance Interoperability Profile constraints for OID4VCI flows.
 *
 * Defines the mandatory formats, proof types, signing algorithms, and validation
 * rules that Credential Issuers and Wallets MUST conform to when operating in
 * the EUDIW ecosystem.
 *
 * References:
 * - EUDIW ARF §6.3 — Credential issuance (OID4VCI profile)
 * - OpenID for Verifiable Credential Issuance (OID4VCI) spec
 */
object EudiwOid4VciProfile {

    /**
     * Credential formats mandated by EUDIW (ARF §6.3).
     * Issuers must support at least one; wallets must support both.
     */
    val SUPPORTED_FORMATS: Set<String> = setOf(
        EudiwConstants.CREDENTIAL_FORMAT_SD_JWT_VC,
        EudiwConstants.CREDENTIAL_FORMAT_MSO_MDOC,
    )

    /**
     * Proof types that wallets may use to prove possession of a key
     * during credential issuance.
     */
    val SUPPORTED_PROOF_TYPES: Set<String> = setOf("jwt", "cwt")

    /**
     * JWA signing algorithms accepted by EUDIW-conformant issuers and wallets.
     * RS256/RS384/RS512 are intentionally excluded (EC and EdDSA only).
     */
    val SUPPORTED_ALGORITHMS: Set<String> = setOf("ES256", "ES384", "ES512", "EdDSA")

    /**
     * Maximum validity period for a Credential Offer (EUDIW ARF §6.3.2).
     * Offers older than this MUST be rejected by wallets.
     */
    const val MAX_OFFER_VALIDITY_SECONDS: Long = 86400L  // 24 hours

    /**
     * Client authentication methods required when a wallet presents itself as a
     * client to a EUDIW-conformant Credential Issuer.
     */
    val REQUIRED_CLIENT_AUTH_METHODS: Set<String> = setOf(
        "attest_jwt_client_auth",
        "private_key_jwt",
    )

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    /** Outcome of a profile validation check. */
    data class ValidationResult(val valid: Boolean, val violations: List<String>)

    /**
     * Validates that a Credential Offer URI conforms to EUDIW constraints.
     *
     * EUDIW requires the `openid-credential-offer://` custom URI scheme so that
     * the operating system can route the offer to an installed wallet application.
     *
     * @param offerUri The Credential Offer URI to validate.
     */
    fun validateCredentialOffer(offerUri: String): ValidationResult {
        val violations = mutableListOf<String>()
        if (!offerUri.startsWith("openid-credential-offer://")) {
            violations.add(
                "EUDIW credential offers must use the openid-credential-offer:// scheme " +
                    "(received: ${offerUri.substringBefore("://") + "://..."})",
            )
        }
        return ValidationResult(violations.isEmpty(), violations)
    }

    /**
     * Validates that a credential format string is supported by the EUDIW profile.
     *
     * @param format Credential format identifier, e.g. `"vc+sd-jwt"`.
     */
    fun validateFormat(format: String): ValidationResult {
        val violations = if (format !in SUPPORTED_FORMATS) {
            listOf(
                "Format '$format' is not supported by the EUDIW profile. " +
                    "Allowed formats: $SUPPORTED_FORMATS",
            )
        } else {
            emptyList()
        }
        return ValidationResult(violations.isEmpty(), violations)
    }

    /**
     * Validates that a JWA signing algorithm is accepted by the EUDIW profile.
     *
     * @param algorithm JWA algorithm identifier, e.g. `"ES256"`.
     */
    fun validateAlgorithm(algorithm: String): ValidationResult {
        val violations = if (algorithm !in SUPPORTED_ALGORITHMS) {
            listOf(
                "Algorithm '$algorithm' is not in the EUDIW supported algorithm set. " +
                    "Allowed algorithms: $SUPPORTED_ALGORITHMS",
            )
        } else {
            emptyList()
        }
        return ValidationResult(violations.isEmpty(), violations)
    }
}
