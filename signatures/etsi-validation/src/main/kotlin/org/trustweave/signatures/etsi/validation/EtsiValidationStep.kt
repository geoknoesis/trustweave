package org.trustweave.signatures.etsi.validation

/**
 * Discrete steps of the ETSI EN 319 102-1 signature validation procedure.
 *
 * Each step in the standard maps to one entry below. The pipeline records an [StepOutcome]
 * per step so callers can inspect exactly which check passed, failed, or was skipped.
 *
 * The MVP implementation does not yet wire [REVOCATION] (§5.4.4) and [LONG_TERM_VALIDATION]
 * (§5.6) — those steps return [StepOutcome.NotApplicable] as placeholders for B-LT/B-LTA
 * support landing later.
 */
enum class EtsiValidationStep {
    /** §5.1: signature is well-formed (parseable JAdES). */
    FORMAT_CHECK,

    /** §5.2: signer identifier (cert chain) resolves. */
    IDENTIFIER_CHECK,

    /** §5.3: cryptographic signature value verifies against the signer's public key. */
    SIGNATURE_ACCEPTANCE,

    /** §5.4: signer cert chains to a trust anchor. */
    X509_CERT_PATH,

    /** §5.4.4: signer cert is not revoked. Placeholder in MVP. */
    REVOCATION,

    /** §5.5.1: algorithms (signature, digest) meet policy. */
    CRYPTO_CONSTRAINTS,

    /** §5.5.3: signature policy match (trust-status URI accepted). */
    SIGNATURE_POLICY,

    /** Signer cert is valid at the asserted signing time. */
    SIGNING_TIME_VALIDITY,

    /** §5.5.5: sigTst (signature time-stamp) is valid (B-T or higher). */
    TIME_STAMP_TOKEN,

    /** §5.6: B-LT/B-LTA-specific long-term validation. Placeholder in MVP. */
    LONG_TERM_VALIDATION,

    /** Aggregate verdict produced after all preceding steps are run. */
    FINAL_VERDICT,
}
