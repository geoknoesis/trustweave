package org.trustweave.credential.eudiw

/**
 * EU PID credential issuance profile aligned with eIDAS 2.0 Annex V and the
 * EUDIW ARF §6.3.
 *
 * Defines:
 * - The set of mandatory claims that every EU PID MUST contain.
 * - The canonical VC `type` and `@context` arrays for SD-JWT VC serialization.
 * - Claim validation logic to be applied before issuance.
 *
 * References:
 * - eIDAS 2.0 Regulation, Annex V — Minimum data set for the European Digital Identity
 * - EUDIW ARF: https://github.com/eu-digital-identity-wallet/eudi-doc-architecture-and-reference-framework
 */
object EuPidIssuanceProfile {

    /**
     * Mandatory top-level claims for an EU PID credential as specified in
     * eIDAS 2.0 Annex V. All of these MUST be present and non-null in the
     * credential claims map before issuance.
     */
    val REQUIRED_CLAIMS: Set<String> = setOf(
        EudiwConstants.CLAIM_FAMILY_NAME,
        EudiwConstants.CLAIM_GIVEN_NAME,
        EudiwConstants.CLAIM_BIRTH_DATE,
        EudiwConstants.CLAIM_ISSUING_AUTHORITY,
        EudiwConstants.CLAIM_ISSUING_COUNTRY,
        EudiwConstants.CLAIM_ISSUANCE_DATE,
        EudiwConstants.CLAIM_EXPIRY_DATE,
    )

    /**
     * Canonical VC `type` array for an EU PID issued as an SD-JWT VC.
     * The first element is always `"VerifiableCredential"` (W3C base type).
     */
    val PID_VC_TYPES: List<String> = listOf(
        "VerifiableCredential",
        EudiwConstants.PID_VC_TYPE,
    )

    /**
     * Canonical `@context` array for an EU PID issued as an SD-JWT VC.
     * The W3C VC 2.0 context is required; the status list context enables
     * RevocationList2020 / StatusList2021 entries.
     */
    val PID_VC_CONTEXTS: List<String> = listOf(
        EudiwConstants.EUDIW_CONTEXT,
        "https://www.w3.org/ns/credentials/status/v1",
    )

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    /** Outcome of an issuance profile validation check. */
    data class ValidationResult(
        val valid: Boolean,
        val missingClaims: List<String>,
        val violations: List<String>,
    )

    /**
     * Validates that [claims] contains all mandatory EU PID claims.
     *
     * @param claims The flat claims map keyed by claim name (as returned by
     *   [EuPidCredential.toClaims] or assembled by an issuer).
     * @return A [ValidationResult] describing any missing required claims.
     */
    fun validateClaims(claims: Map<String, Any>): ValidationResult {
        val missing = REQUIRED_CLAIMS.filter { it !in claims }
        val violations = missing.map { "Required EU PID claim is missing: '$it'" }
        return ValidationResult(
            valid = missing.isEmpty(),
            missingClaims = missing,
            violations = violations,
        )
    }
}
