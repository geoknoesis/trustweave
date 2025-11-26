package com.trustweave.trust.types

import com.trustweave.credential.CredentialVerificationResult
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.did.DidDocument
import java.time.Instant

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
            if (result.valid) {
                return Valid(
                    credential = credential,
                    warnings = result.warnings
                )
            }
            
            // Determine primary failure reason
            val failures = mutableListOf<Invalid>()
            
            if (!result.notExpired) {
                val expiredAt = credential.expirationDate?.let {
                    try {
                        Instant.parse(it)
                    } catch (e: Exception) {
                        null
                    }
                } ?: Instant.now()
                
                failures.add(Invalid.Expired(
                    credential = credential,
                    expiredAt = expiredAt,
                    errors = result.errors.filter { it.contains("expir", ignoreCase = true) }
                ))
            }
            
            if (!result.notRevoked) {
                failures.add(Invalid.Revoked(
                    credential = credential,
                    revokedAt = null, // Could be extracted from credential status if available
                    errors = result.errors.filter { it.contains("revok", ignoreCase = true) }
                ))
            }
            
            if (!result.proofValid) {
                failures.add(Invalid.InvalidProof(
                    credential = credential,
                    reason = result.errors.firstOrNull { it.contains("proof", ignoreCase = true) }
                        ?: "Proof validation failed",
                    errors = result.errors.filter { it.contains("proof", ignoreCase = true) }
                ))
            }
            
            if (!result.issuerValid) {
                val issuerDid = try {
                    Did(credential.issuer)
                } catch (e: Exception) {
                    return Invalid.Other(
                        credential = credential,
                        reason = "Invalid issuer DID format: ${credential.issuer}",
                        errors = result.errors
                    )
                }
                
                failures.add(Invalid.IssuerResolutionFailed(
                    credential = credential,
                    issuer = issuerDid,
                    reason = result.errors.firstOrNull { it.contains("issuer", ignoreCase = true) }
                        ?: "Issuer DID resolution failed",
                    errors = result.errors.filter { it.contains("issuer", ignoreCase = true) }
                ))
            }
            
            if (!result.trustRegistryValid) {
                val issuerDid = try {
                    Did(credential.issuer)
                } catch (e: Exception) {
                    return Invalid.Other(
                        credential = credential,
                        reason = "Invalid issuer DID format: ${credential.issuer}",
                        errors = result.errors
                    )
                }
                
                failures.add(Invalid.UntrustedIssuer(
                    credential = credential,
                    issuer = issuerDid,
                    credentialType = credential.type.firstOrNull(),
                    errors = result.errors.filter { it.contains("trust", ignoreCase = true) }
                ))
            }
            
            if (!result.schemaValid) {
                failures.add(Invalid.SchemaValidationFailed(
                    credential = credential,
                    errors = result.errors.filter { it.contains("schema", ignoreCase = true) }
                ))
            }
            
            // Return most specific failure, or multiple failures if several occurred
            return when {
                failures.size == 1 -> failures.first()
                failures.size > 1 -> Invalid.MultipleFailures(
                    credential = credential,
                    failures = failures,
                    errors = result.errors
                )
                else -> Invalid.Other(
                    credential = credential,
                    reason = result.errors.firstOrNull() ?: "Verification failed",
                    errors = result.errors
                )
            }
        }
    }
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

