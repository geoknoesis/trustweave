package org.trustweave.credential.requests

import org.trustweave.credential.identifiers.SchemaId
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Revocation failure policy.
 *
 * Determines how to handle failures when checking revocation status.
 */
enum class RevocationFailurePolicy {
    /**
     * Fail-open: If revocation check fails, allow verification to proceed.
     * This is more permissive and allows credentials to be verified even if
     * the revocation service is unavailable. Use when availability is more
     * important than strict revocation checking.
     */
    FAIL_OPEN,

    /**
     * Fail-closed: If revocation check fails, reject the credential.
     * This is more secure and ensures that credentials cannot be verified
     * if revocation status cannot be confirmed. Use when security is more
     * important than availability.
     */
    FAIL_CLOSED,

    /**
     * Fail-with-warning: If revocation check fails, allow verification but
     * add a warning to the result. This balances security and availability.
     */
    FAIL_WITH_WARNING
}

/**
 * Verification options.
 */
data class VerificationOptions(
    val checkRevocation: Boolean = true,
    val checkExpiration: Boolean = true,
    val checkNotBefore: Boolean = true,
    val resolveIssuerDid: Boolean = true,
    val validateSchema: Boolean = false,
    val schemaId: SchemaId? = null,
    val clockSkewTolerance: Duration = 5.minutes,
    /**
     * Policy for handling revocation check failures.
     *
     * Default is FAIL_CLOSED for security. Credentials cannot be verified
     * if revocation status cannot be confirmed. Use FAIL_OPEN only if
     * availability is more important than security.
     */
    val revocationFailurePolicy: RevocationFailurePolicy = RevocationFailurePolicy.FAIL_CLOSED,
    // Presentation verification options
    val verifyPresentationProof: Boolean = true,
    val verifyChallenge: Boolean = false,
    val expectedChallenge: String? = null,
    val verifyDomain: Boolean = false,
    val expectedDomain: String? = null,
    /** Format-specific or engine-specific verification parameters (e.g. `sessionTranscript` for mDL). */
    val additionalOptions: Map<String, Any?> = emptyMap(),
    /**
     * Envelope-level holder binding checks (opt-in; defaults to false):
     * - each presented credential's `credentialSubject.id` must match the presentation
     *   holder, and
     * - for LD-proof presentations, the proof's `verificationMethod` must belong to the
     *   holder DID.
     * Requires [verifyPresentationProof] to be true (enforced; the holder field is
     * meaningless without a verified presentation proof).
     *
     * **Note — SD-JWT cryptographic holder binding is NOT gated by this option.**
     * When a presented SD-JWT credential carries an issuer-signed `cnf` claim (emitted
     * automatically at issuance whenever the credential subject id is a DID), the Key
     * Binding JWT is ALWAYS required to be signed by the `cnf`-designated DID's
     * authentication key, and the presentation `holder` must equal that DID — regardless
     * of this option's value. This option's default remains false because flipping it
     * globally would reject legitimate flows with no holder binding (e.g. LD-proof
     * presentations of credentials about third-party subjects, or bearer credentials).
     * Legacy SD-JWT credentials without `cnf` only get the weaker envelope-holder
     * binding; verifiers that require subject-equals-holder semantics for such
     * credentials should opt in here.
     */
    val enforceHolderBinding: Boolean = false,
)
