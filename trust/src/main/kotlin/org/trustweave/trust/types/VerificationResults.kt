package org.trustweave.trust.types

import org.trustweave.credential.results.VerificationResult as CredentialVerificationResult
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.did.model.DidDocument
import org.trustweave.did.identifiers.Did
import kotlinx.datetime.Instant

/**
 * Sealed result types for credential verification.
 *
 * Provides exhaustive error handling with clear, type-safe error cases.
 * Wraps the underlying CredentialVerificationResult for better API ergonomics.
 *
 * **Example Usage:**
 * ```kotlin
 * val result: VerificationResult = trustWeave.verify {
 *     credential(credential)
 * }
 *
 * when (result) {
 *     is VerificationResult.Valid -> {
 *         println("Credential is valid: ${result.credential.id}")
 *     }
 *     is VerificationResult.Invalid.Expired -> {
 *         println("Credential expired at ${result.expiredAt}")
 *     }
 *     is VerificationResult.Invalid.Revoked -> {
 *         println("Credential revoked at ${result.revokedAt}")
 *     }
 *     is VerificationResult.Invalid.UntrustedIssuer -> {
 *         println("Issuer ${result.issuer} is not trusted")
 *     }
 *     // Compiler ensures all cases handled
 * }
 * ```
 */
sealed class VerificationResult {
    /**
     * Credential verification succeeded.
     */
    data class Valid(
        val credential: VerifiableCredential,
        val warnings: List<String> = emptyList()
    ) : VerificationResult()

    /**
     * Credential verification failed.
     */
    sealed class Invalid : VerificationResult() {
        /**
         * Credential has expired.
         */
        data class Expired(
            val credential: VerifiableCredential,
            val expiredAt: Instant,
            val errors: List<String> = emptyList()
        ) : Invalid()

        /**
         * Credential has been revoked.
         */
        data class Revoked(
            val credential: VerifiableCredential,
            val revokedAt: Instant? = null,
            val errors: List<String> = emptyList()
        ) : Invalid()

        /**
         * Proof signature is invalid.
         */
        data class InvalidProof(
            val credential: VerifiableCredential,
            val reason: String,
            val errors: List<String> = emptyList()
        ) : Invalid()

        /**
         * Issuer DID could not be resolved or is invalid.
         */
        data class IssuerResolutionFailed(
            val credential: VerifiableCredential,
            val issuer: Did,
            val reason: String,
            val errors: List<String> = emptyList()
        ) : Invalid()

        /**
         * Issuer is not trusted for this credential type.
         */
        data class UntrustedIssuer(
            val credential: VerifiableCredential,
            val issuer: Did,
            val credentialType: String? = null,
            val errors: List<String> = emptyList()
        ) : Invalid()

        /**
         * Schema validation failed.
         */
        data class SchemaValidationFailed(
            val credential: VerifiableCredential,
            val errors: List<String>
        ) : Invalid()

        /**
         * Multiple validation failures.
         */
        data class MultipleFailures(
            val credential: VerifiableCredential,
            val failures: List<Invalid>,
            val errors: List<String>
        ) : Invalid()

        /**
         * Other validation failure.
         */
        data class Other(
            val credential: VerifiableCredential,
            val reason: String,
            val errors: List<String> = emptyList()
        ) : Invalid()
    }

    companion object {
        /**
         * Convert a CredentialVerificationResult to a sealed VerificationResult.
         *
         * This provides a bridge from the existing data class API to the new
         * sealed class API for better error handling.
         */
        fun from(
            credential: VerifiableCredential,
            result: CredentialVerificationResult
        ): VerificationResult {
            return when (result) {
                is CredentialVerificationResult.Valid -> {
                    Valid(
                        credential = credential,
                        warnings = result.warnings
                    )
                }
                is CredentialVerificationResult.Invalid.Expired -> {
                    Invalid.Expired(
                        credential = credential,
                        expiredAt = result.expiredAt,
                        errors = result.errors
                    )
                }
                is CredentialVerificationResult.Invalid.Revoked -> {
                    Invalid.Revoked(
                        credential = credential,
                        revokedAt = result.revokedAt,
                        errors = result.errors
                    )
                }
                is CredentialVerificationResult.Invalid.InvalidProof -> {
                    Invalid.InvalidProof(
                        credential = credential,
                        reason = result.reason,
                        errors = result.errors
                    )
                }
                is CredentialVerificationResult.Invalid.InvalidIssuer -> {
                    val issuerDid = try {
                        Did(result.issuerIri.value)
                    } catch (e: Exception) {
                        return Invalid.Other(
                            credential = credential,
                            reason = "Invalid issuer IRI format: ${result.issuerIri.value}",
                            errors = result.errors
                        )
                    }
                    Invalid.IssuerResolutionFailed(
                        credential = credential,
                        issuer = issuerDid,
                        reason = result.reason,
                        errors = result.errors
                    )
                }
                is CredentialVerificationResult.Invalid.UntrustedIssuer -> {
                    val issuerDid = result.issuerDid
                    Invalid.UntrustedIssuer(
                        credential = credential,
                        issuer = issuerDid,
                        credentialType = credential.type.firstOrNull()?.value,
                        errors = result.errors
                    )
                }
                is CredentialVerificationResult.Invalid.SchemaValidationFailed -> {
                    Invalid.SchemaValidationFailed(
                        credential = credential,
                        errors = result.errors
                    )
                }
                // Note: InvalidBlockchainAnchor, InvalidProofPurpose, InvalidDelegation 
                // are not in credential-api, so we map them to Other
                // These may have been removed or consolidated into other error types
                is CredentialVerificationResult.Invalid.MultipleFailures -> {
                    // Convert the errors to a list of Invalid types if needed
                    // For now, just create an Other with all errors
                    Invalid.Other(
                        credential = credential,
                        reason = result.errors.firstOrNull() ?: "Multiple verification failures",
                        errors = result.errors
                    )
                }
                is CredentialVerificationResult.Invalid.NotYetValid -> {
                    Invalid.Other(
                        credential = credential,
                        reason = "Credential not yet valid. Valid from: ${result.validFrom}",
                        errors = result.errors
                    )
                }
                is CredentialVerificationResult.Invalid.UnsupportedFormat -> {
                    Invalid.Other(
                        credential = credential,
                        reason = "Unsupported format: ${result.format.value}",
                        errors = result.errors
                    )
                }
            }
        }
    }
}

/**
 * Extension property to check if verification result is valid.
 */
val VerificationResult.valid: Boolean
    get() = this is VerificationResult.Valid

val VerificationResult.revoked: Boolean
    get() = when (this) {
        is VerificationResult.Valid -> false
        is VerificationResult.Invalid.Revoked -> true
        else -> false
    }

val VerificationResult.errors: List<String>
    get() = when (this) {
        is VerificationResult.Valid -> emptyList()
        is VerificationResult.Invalid.Expired -> this.errors
        is VerificationResult.Invalid.Revoked -> this.errors
        is VerificationResult.Invalid.InvalidProof -> this.errors
        is VerificationResult.Invalid.IssuerResolutionFailed -> this.errors
        is VerificationResult.Invalid.UntrustedIssuer -> this.errors
        is VerificationResult.Invalid.SchemaValidationFailed -> this.errors
        is VerificationResult.Invalid.MultipleFailures -> this.errors
        is VerificationResult.Invalid.Other -> this.errors
    }

val VerificationResult.warnings: List<String>
    get() = when (this) {
        is VerificationResult.Valid -> this.warnings
        else -> emptyList()
    }

val VerificationResult.proofValid: Boolean
    get() = when (this) {
        is VerificationResult.Valid -> true
        is VerificationResult.Invalid.InvalidProof -> false
        else -> true // Other failures don't necessarily mean proof is invalid
    }

val VerificationResult.issuerValid: Boolean
    get() = when (this) {
        is VerificationResult.Valid -> true
        is VerificationResult.Invalid.IssuerResolutionFailed -> false
        else -> true // Other failures don't necessarily mean issuer is invalid
    }

val VerificationResult.trustRegistryValid: Boolean
    get() = when (this) {
        is VerificationResult.Valid -> true
        is VerificationResult.Invalid.UntrustedIssuer -> false
        else -> true // Other failures don't necessarily mean trust registry check failed
    }

val VerificationResult.delegationValid: Boolean
    get() = when (this) {
        is VerificationResult.Valid -> true
        else -> {
            // Check if there are delegation-related errors
            val hasDelegationErrors = errors.any {
                it.contains("delegation", ignoreCase = true) ||
                it.contains("capability", ignoreCase = true)
            }
            !hasDelegationErrors
        }
    }

val VerificationResult.proofPurposeValid: Boolean
    get() = when (this) {
        is VerificationResult.Valid -> true
        else -> {
            // Check if there are proof purpose-related errors
            val hasProofPurposeErrors = errors.any {
                it.contains("proof purpose", ignoreCase = true) ||
                it.contains("proofPurpose", ignoreCase = true)
            }
            !hasProofPurposeErrors
        }
    }

val VerificationResult.notExpired: Boolean
    get() = this !is VerificationResult.Invalid.Expired

val VerificationResult.notRevoked: Boolean
    get() = this !is VerificationResult.Invalid.Revoked

val VerificationResult.allWarnings: List<String>
    get() = warnings

val VerificationResult.allErrors: List<String>
    get() = errors

/**
 * Get the credential from any VerificationResult variant.
 */
val VerificationResult.credential: VerifiableCredential
    get() = when (this) {
        is VerificationResult.Valid -> this.credential
        is VerificationResult.Invalid.Expired -> this.credential
        is VerificationResult.Invalid.Revoked -> this.credential
        is VerificationResult.Invalid.InvalidProof -> this.credential
        is VerificationResult.Invalid.IssuerResolutionFailed -> this.credential
        is VerificationResult.Invalid.UntrustedIssuer -> this.credential
        is VerificationResult.Invalid.SchemaValidationFailed -> this.credential
        is VerificationResult.Invalid.MultipleFailures -> this.credential
        is VerificationResult.Invalid.Other -> this.credential
    }

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
        /**
         * DID resolution failed.
         */
        data class ResolutionFailed(
            val did: Did,
            val reason: String
        ) : Failure()

        /**
         * DID creation failed.
         */
        data class CreationFailed(
            val reason: String,
            val cause: Throwable? = null
        ) : Failure()

        /**
         * DID update failed.
         */
        data class UpdateFailed(
            val did: Did,
            val reason: String,
            val cause: Throwable? = null
        ) : Failure()

        /**
         * DID deactivation failed.
         */
        data class DeactivationFailed(
            val did: Did,
            val reason: String,
            val cause: Throwable? = null
        ) : Failure()
    }
}

