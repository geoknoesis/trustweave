package org.trustweave.credential.internal

import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.schema.SchemaRegistry
import org.trustweave.core.identifiers.Iri
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.toKotlinDuration

/**
 * Credential validation utilities.
 * 
 * This utility object provides validation functions for Verifiable Credentials,
 * ensuring they comply with W3C VC Data Model specifications. It validates
 * credential structure, context, and required fields.
 * 
 * **Validation Checks:**
 * - VC context validation (VC 1.1 or VC 2.0)
 * - Proof existence validation
 * - Required field presence
 * - Type validation
 * 
 * **Usage:**
 * ```kotlin
 * // Validate context
 * CredentialValidation.validateContext(credential)?.let { return it }
 * 
 * // Validate proof exists
 * CredentialValidation.validateProofExists(credential)?.let { return it }
 * ```
 * 
 * **Note:** This is an internal utility used by DefaultCredentialService during
 * credential verification. It returns VerificationResult.Invalid if validation fails,
 * or null if validation passes.
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
        // For a pure VC 2.0 credential (no VC 1.1 context), use only validFrom.
        // For dual-context credentials (both VC 1.1 and VC 2.0 context strings present), fall
        // back to issuanceDate when validFrom is absent — otherwise a credential that only sets
        // issuanceDate would have its not-before check silently skipped.
        // For pure VC 1.1, fall back to issuanceDate as usual.
        val effectiveNotBefore = if (credential.isVc2 && !credential.isVc1) {
            credential.validFrom
        } else {
            credential.validFrom ?: credential.issuanceDate
        }
        if (!options.checkNotBefore || effectiveNotBefore == null) {
            return null
        }

        val clockSkewKt = options.clockSkewTolerance
        val earliestValidTime = effectiveNotBefore.minus(clockSkewKt)

        if (now < earliestValidTime) {
            val timeUntilValid = earliestValidTime - now
            return VerificationResult.Invalid.NotYetValid(
                credential = credential,
                validFrom = effectiveNotBefore,
                errors = listOf(
                    "Credential is not yet valid. Valid from: $effectiveNotBefore, " +
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
        // For a pure VC 2.0 credential (no VC 1.1 context), use only validUntil.
        // For dual-context credentials (both VC 1.1 and VC 2.0 context strings present), fall
        // back to expirationDate when validUntil is absent — otherwise a credential that only
        // sets expirationDate would have its expiration check silently skipped.
        // For pure VC 1.1, fall back to expirationDate as usual.
        val effectiveExpiry = if (credential.isVc2 && !credential.isVc1) {
            credential.validUntil
        } else {
            credential.validUntil ?: credential.expirationDate
        }
        if (!options.checkExpiration || effectiveExpiry == null) {
            return null
        }

        val clockSkewKt = options.clockSkewTolerance
        val latestValidTime = effectiveExpiry.plus(clockSkewKt)

        if (now > latestValidTime) {
            val timeSinceExpiration = now - latestValidTime
            return VerificationResult.Invalid.Expired(
                credential = credential,
                expiredAt = effectiveExpiry,
                errors = listOf(
                    "Credential has expired. Expiration date: $effectiveExpiry, " +
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
        // Copy nullable property to a local — cross-module smart cast on a public
        // property declared in :credentials:credential-models-mp is not possible.
        val credentialSchema = credential.credentialSchema
        if (options.validateSchema && schemaRegistry != null && credentialSchema != null) {
            val schemaId = options.schemaId ?: credentialSchema.id
            
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
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
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
        trustPolicy: org.trustweave.credential.trust.TrustEvaluator?
    ): VerificationResult.Invalid? {
        if (trustPolicy == null) {
            // No trust policy configured - skip trust validation (fail-open)
            return null
        }
        
        val issuerIri = credential.issuer.id
        
        if (!issuerIri.isDid) {
            // Non-DID issuers cannot be trust-checked via DID resolution.
            // When a trust policy is active, reject non-DID issuers by default.
            return VerificationResult.Invalid.InvalidIssuer(
                credential = credential,
                issuerIri = issuerIri,
                reason = "Non-DID issuer '${issuerIri.value}' cannot be evaluated by the configured trust policy",
                errors = listOf("Non-DID issuers are not supported by the trust evaluator")
            )
        }
        
        // Validate IRI length before parsing
        try {
            InputValidation.validateIri(issuerIri)
        } catch (e: IllegalArgumentException) {
            return VerificationResult.Invalid.InvalidIssuer(
                credential = credential,
                issuerIri = issuerIri,
                reason = "Malformed issuer IRI: ${e.message}",
                errors = listOf("Malformed issuer IRI: ${e.message}")
            )
        }

        val issuerDid = try {
            org.trustweave.did.identifiers.Did(issuerIri.value)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return VerificationResult.Invalid.InvalidIssuer(
                credential = credential,
                issuerIri = issuerIri,
                reason = "Malformed issuer DID: ${e.message}",
                errors = listOf("Malformed issuer DID: ${e.message}")
            )
        }

        val isTrusted = try {
            trustPolicy.isTrusted(issuerDid)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return VerificationResult.Invalid.InvalidIssuer(
                credential = credential,
                issuerIri = issuerIri,
                reason = "Trust evaluation failed: ${e.message}",
                errors = listOf("Trust evaluation error: ${e.message}")
            )
        }
        if (!isTrusted) {
            return VerificationResult.Invalid.UntrustedIssuer(
                credential = credential,
                issuerDid = issuerDid
            )
        }

        return null
    }
}

