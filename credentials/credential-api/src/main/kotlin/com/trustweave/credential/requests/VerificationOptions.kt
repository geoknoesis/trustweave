package com.trustweave.credential.requests

import com.trustweave.credential.identifiers.SchemaId
import java.time.Duration

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
    val clockSkewTolerance: Duration = Duration.ofMinutes(5),
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
    val expectedDomain: String? = null
)

