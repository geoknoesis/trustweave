package org.trustweave.credential.internal

import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.schema.SchemaRegistry
import org.trustweave.core.identifiers.Iri
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Credential validation utilities.
 * 
 * Extracted validation logic from DefaultCredentialService to improve maintainability
 * and testability.
 */
internal object CredentialValidation {
    /**
     * Validate VC context.
     * 
     * Checks that the credential has a valid W3C VC context (VC 1.1 or VC 2.0).
     * 
     * @param credential The credential to validate
     * @return VerificationResult.Invalid if context is invalid, null if valid
     */
    fun validateContext(credential: VerifiableCredential): VerificationResult.Invalid.InvalidProof? {
        if (!credential.hasValidContext()) {
            return VerificationResult.Invalid.InvalidProof(
                credential = credential,
                reason = "Invalid or missing W3C VC context",
                errors = listOf(
                    "Credential context must include at least one of: " +
                    "'${CredentialConstants.VcContexts.VC_1_1}' (VC 1.1) or " +
                    "'${CredentialConstants.VcContexts.VC_2_0}' (VC 2.0). " +
                    "Found: ${credential.context}"
                )
            )
        }
        return null
    }
    
    /**
     * Validate credential has a proof.
     * 
     * Per W3C Verifiable Credentials Data Model, a verifiable credential must
     * have a proof to be considered verifiable. This validation ensures the proof
     * exists before attempting verification.
     * 
     * @param credential The credential to validate
     * @return VerificationResult.Invalid if proof is missing, null if present
     */
    fun validateProofExists(credential: VerifiableCredential): VerificationResult.Invalid.InvalidProof? {
        if (credential.proof == null) {
            return VerificationResult.Invalid.InvalidProof(
                credential = credential,
                reason = "Credential has no proof",
                errors = listOf(
                    "VerifiableCredential must have a proof property to be verifiable. " +
                    "The proof contains the cryptographic signature or other evidence " +
                    "that the credential was issued by the stated issuer."
                )
            )
        }
        return null
    }
    
    /**
     * Validate temporal validity (notBefore/validFrom check).
     * 
     * Validates that the credential's `validFrom` date (VC 2.0) has passed.
     * Accounts for clock skew tolerance to handle minor time differences between systems.
     * 
     * **Note:** VC 1.1 uses `issuanceDate` as the earliest valid time, while VC 2.0
     * introduces `validFrom` for more precise control. This function checks `validFrom`
     * when present, which is the VC 2.0 approach.
     * 
     * @param credential The credential to validate
     * @param options Verification options (includes clock skew tolerance)
     * @param now Current timestamp (defaults to system clock)
     * @return VerificationResult.Invalid if not yet valid, null if valid or not checked
     */
    fun validateNotBefore(
        credential: VerifiableCredential,
        options: VerificationOptions,
        now: Instant = Clock.System.now()
    ): VerificationResult.Invalid.NotYetValid? {
        if (!options.checkNotBefore || credential.validFrom == null) {
            return null
        }
        
        val clockSkewKt = kotlin.time.Duration.parse(options.clockSkewTolerance.toString())
        val earliestValidTime = credential.validFrom.minus(clockSkewKt)
        
        if (now < earliestValidTime) {
            val timeUntilValid = earliestValidTime - now
            return VerificationResult.Invalid.NotYetValid(
                credential = credential,
                validFrom = credential.validFrom,
                errors = listOf(
                    "Credential is not yet valid. Valid from: ${credential.validFrom}, " +
                    "Current time: $now (accounting for ${options.clockSkewTolerance} clock skew tolerance). " +
                    "Credential will be valid in approximately ${timeUntilValid.inWholeSeconds} seconds."
                )
            )
        }
        return null
    }
    
    /**
     * Validate expiration date.
     * 
     * Validates that the credential has not expired. Accounts for clock skew tolerance
     * to handle minor time differences between systems.
     * 
     * **Security Note:** Expired credentials should generally be rejected, but in some
     * use cases (e.g., audit trails, historical verification), expired credentials may
     * be accepted. Use `options.checkExpiration` to control this behavior.
     * 
     * @param credential The credential to validate
     * @param options Verification options (includes clock skew tolerance and expiration check flag)
     * @param now Current timestamp (defaults to system clock)
     * @return VerificationResult.Invalid if expired, null if valid or not checked
     */
    fun validateExpiration(
        credential: VerifiableCredential,
        options: VerificationOptions,
        now: Instant = Clock.System.now()
    ): VerificationResult.Invalid.Expired? {
        if (!options.checkExpiration || credential.expirationDate == null) {
            return null
        }
        
        val clockSkewKt = kotlin.time.Duration.parse(options.clockSkewTolerance.toString())
        val latestValidTime = credential.expirationDate.plus(clockSkewKt)
        
        if (now > latestValidTime) {
            val timeSinceExpiration = now - latestValidTime
            return VerificationResult.Invalid.Expired(
                credential = credential,
                expiredAt = credential.expirationDate,
                errors = listOf(
                    "Credential has expired. Expiration date: ${credential.expirationDate}, " +
                    "Current time: $now (accounting for ${options.clockSkewTolerance} clock skew tolerance). " +
                    "Credential expired approximately ${timeSinceExpiration.inWholeSeconds} seconds ago."
                )
            )
        }
        return null
    }
    
    /**
     * Validate credential against schema.
     * 
     * @param credential The credential to validate
     * @param options Verification options
     * @param schemaRegistry Schema registry (if available)
     * @return VerificationResult.Invalid if schema validation fails, null if valid or not checked
     */
    suspend fun validateSchema(
        credential: VerifiableCredential,
        options: VerificationOptions,
        schemaRegistry: SchemaRegistry?
    ): VerificationResult.Invalid.SchemaValidationFailed? {
        if (options.validateSchema && schemaRegistry != null && credential.credentialSchema != null) {
            val schemaId = options.schemaId ?: credential.credentialSchema.id
            
            // Validate schema ID length
            try {
                InputValidation.validateSchemaId(schemaId.value)
            } catch (e: IllegalArgumentException) {
                return VerificationResult.Invalid.SchemaValidationFailed(
                    credential = credential,
                    schemaId = schemaId.value,
                    validationErrors = listOf("Invalid schema ID: ${e.message}"),
                    errors = listOf("Schema ID validation failed: ${e.message}")
                )
            }
            
            try {
                val schemaResult = schemaRegistry.validate(credential, schemaId)
                if (!schemaResult.valid) {
                    val validationErrors = schemaResult.errors.map { "${it.path}: ${it.message}" }
                    return VerificationResult.Invalid.SchemaValidationFailed(
                        credential = credential,
                        schemaId = schemaId.value,
                        validationErrors = validationErrors,
                        errors = validationErrors
                    )
                }
            } catch (e: IllegalArgumentException) {
                // Re-throw input validation errors as-is
                return VerificationResult.Invalid.SchemaValidationFailed(
                    credential = credential,
                    schemaId = schemaId.value,
                    validationErrors = listOf("Schema validation input error: ${e.message}"),
                    errors = listOf("Schema validation failed: ${e.message}")
                )
            } catch (e: IllegalStateException) {
                return VerificationResult.Invalid.SchemaValidationFailed(
                    credential = credential,
                    schemaId = schemaId.value,
                    validationErrors = listOf("Schema registry error: ${e.message}"),
                    errors = listOf("Schema validation failed: ${e.message}")
                )
            } catch (e: Exception) {
                return VerificationResult.Invalid.SchemaValidationFailed(
                    credential = credential,
                    schemaId = schemaId.value,
                    validationErrors = listOf("Schema validation error: ${e.message ?: e.javaClass.simpleName}"),
                    errors = listOf("Schema validation failed: ${e.message ?: e.javaClass.simpleName}")
                )
            }
        }
        return null
    }
    
    /**
     * Validate issuer trust.
     * 
     * Validates that the credential's issuer is trusted according to the provided trust policy.
     * If no trust policy is provided, this validation is skipped (fail-open).
     * 
     * **Security Note:** This function only validates DIDs. If the issuer is a URI/URN instead of
     * a DID, trust validation is skipped and will be handled by other validation layers if needed.
     * 
     * @param credential The credential to validate
     * @param trustPolicy Trust policy (if configured)
     * @return VerificationResult.Invalid if issuer is untrusted, null if trusted or no policy
     */
    suspend fun validateTrust(
        credential: VerifiableCredential,
        trustPolicy: org.trustweave.credential.trust.TrustPolicy?
    ): VerificationResult.Invalid.UntrustedIssuer? {
        if (trustPolicy == null) {
            // No trust policy configured - skip trust validation (fail-open)
            return null
        }
        
        val issuerIri = credential.issuer.id
        
        // Only validate DIDs - URIs/URNs are handled differently
        if (!issuerIri.isDid) {
            return null
        }
        
        try {
            // Validate IRI length before parsing
            InputValidation.validateIri(issuerIri)
            
            val issuerDid = org.trustweave.did.identifiers.Did(issuerIri.value)
            val isTrusted = trustPolicy.isTrusted(issuerDid)
            
            if (!isTrusted) {
                return VerificationResult.Invalid.UntrustedIssuer(
                    credential = credential,
                    issuerDid = issuerDid
                )
            }
        } catch (e: IllegalArgumentException) {
            // Invalid DID format or length - will be caught by proof engine verification
            // Don't fail trust validation here, let the proof verification catch format errors
            return null
        }
        
        return null
    }
}

