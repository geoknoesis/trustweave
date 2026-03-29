package org.trustweave.trust.types

import org.trustweave.credential.results.VerificationResult
import org.trustweave.did.model.DidDocument
import org.trustweave.did.identifiers.Did

/**
 * Ergonomic extension properties for [VerificationResult].
 *
 * These extensions add trust-layer-specific helpers (proof validity, issuer validity,
 * trust-registry validity, etc.) on top of the canonical credential-module sealed type.
 */

val VerificationResult.valid: Boolean
    get() = this is VerificationResult.Valid

val VerificationResult.revoked: Boolean
    get() = this is VerificationResult.Invalid.Revoked

val VerificationResult.errors: List<String>
    get() = allErrors

val VerificationResult.warnings: List<String>
    get() = allWarnings

val VerificationResult.proofValid: Boolean
    get() = this !is VerificationResult.Invalid.InvalidProof

val VerificationResult.issuerValid: Boolean
    get() = this !is VerificationResult.Invalid.InvalidIssuer

val VerificationResult.trustRegistryValid: Boolean
    get() = this !is VerificationResult.Invalid.UntrustedIssuer

val VerificationResult.delegationValid: Boolean
    get() = allErrors.none {
        it.contains("delegation", ignoreCase = true) ||
            it.contains("capability", ignoreCase = true)
    }

val VerificationResult.proofPurposeValid: Boolean
    get() = allErrors.none {
        it.contains("proof purpose", ignoreCase = true) ||
            it.contains("proofPurpose", ignoreCase = true)
    }

val VerificationResult.notExpired: Boolean
    get() = this !is VerificationResult.Invalid.Expired

val VerificationResult.notRevoked: Boolean
    get() = this !is VerificationResult.Invalid.Revoked

/**
 * Sealed result type for DID operations.
 */
sealed class DidResult {
    /**
     * DID operation succeeded.
     */
    data class Success(
        val did: Did,
        val document: DidDocument
    ) : DidResult()

    /**
     * DID operation failed.
     */
    sealed class Failure : DidResult() {
        data class ResolutionFailed(
            val did: Did,
            val reason: String
        ) : Failure()

        data class CreationFailed(
            val reason: String,
            val cause: Throwable? = null
        ) : Failure()

        data class UpdateFailed(
            val did: Did,
            val reason: String,
            val cause: Throwable? = null
        ) : Failure()

        data class DeactivationFailed(
            val did: Did,
            val reason: String,
            val cause: Throwable? = null
        ) : Failure()
    }
}
