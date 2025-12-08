package com.trustweave.credential.validation

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.getFormatId
import com.trustweave.credential.model.CredentialType
import com.trustweave.core.identifiers.Iri
import com.trustweave.did.identifiers.Did
import kotlinx.serialization.json.JsonNull

/**
 * Verifiable Credential validation utilities.
 *
 * Provides structure validation helpers for Verifiable Credentials.
 * Note: This validates structure only. For full verification including
 * cryptographic proof validation, use [CredentialService.verify].
 */
object CredentialValidator {
    /**
     * Validates basic Verifiable Credential structure.
     *
     * @param credential The VerifiableCredential to validate
     * @return Validation result with errors if any
     */
    fun validateStructure(credential: VerifiableCredential): ValidationResult {
        // Check for VerifiableCredential type
        if (!credential.type.any { it.value == "VerifiableCredential" }) {
            return ValidationResult.Invalid(
                code = "MISSING_VERIFIABLE_CREDENTIAL_TYPE",
                message = "Credential type must include 'VerifiableCredential'",
                field = "type",
                value = credential.type.map { it.value }
            )
        }

        // Check issuer
        val issuerIri = credential.issuer.id
        if (issuerIri.value.isBlank()) {
            return ValidationResult.Invalid(
                code = "MISSING_ISSUER",
                message = "Credential issuer is required",
                field = "issuer",
                value = issuerIri.value
            )
        }

        // Validate issuer IRI format (if it's a DID)
        if (issuerIri.isDid) {
            try {
                Did(issuerIri.value)
            } catch (e: IllegalArgumentException) {
                return ValidationResult.Invalid(
                    code = "INVALID_ISSUER_DID",
                    message = "Invalid issuer DID format: ${e.message}",
                    field = "issuer",
                    value = issuerIri.value
                )
            }
        }

        // Check credential subject
        val subjectId = credential.credentialSubject.id
        if (subjectId.value.isBlank()) {
            return ValidationResult.Invalid(
                code = "MISSING_SUBJECT",
                message = "Credential subject ID is required",
                field = "credentialSubject.id",
                value = null
            )
        }

        // Validate subject IRI format (if it's a DID)
        if (subjectId.isDid) {
            try {
                Did(subjectId.value)
            } catch (e: IllegalArgumentException) {
                return ValidationResult.Invalid(
                    code = "INVALID_SUBJECT_DID",
                    message = "Invalid subject DID format: ${e.message}",
                    field = "credentialSubject.id",
                    value = subjectId.value
                )
            }
        }

        // Check that claims don't contain JsonNull values
        for ((key, value) in credential.credentialSubject.claims) {
            if (value is JsonNull) {
                return ValidationResult.Invalid(
                    code = "NULL_CLAIM_VALUE",
                    message = "Claim '$key' cannot be null",
                    field = "credentialSubject.claims.$key",
                    value = null
                )
            }
        }

        // Check proof
        if (credential.proof == null) {
            return ValidationResult.Invalid(
                code = "MISSING_PROOF",
                message = "Credential proof is required",
                field = "proof",
                value = null
            )
        }

        // Check proof format (extract from proof type)
        val proofSuiteId = credential.proof?.getFormatId()
        if (proofSuiteId == null || proofSuiteId.value.isBlank()) {
            return ValidationResult.Invalid(
                code = "MISSING_PROOF_FORMAT",
                message = "Credential proof format could not be determined",
                field = "proof",
                value = null
            )
        }

        return ValidationResult.Valid
    }

    /**
     * Validates that the Verifiable Credential has a proof.
     *
     * @param credential The VerifiableCredential to validate
     * @return Validation result
     */
    fun validateProof(credential: VerifiableCredential): ValidationResult {
        if (credential.proof == null) {
            return ValidationResult.Invalid(
                code = "MISSING_PROOF",
                message = "Credential must have a proof",
                field = "proof",
                value = null
            )
        }

        val proofSuiteId = credential.proof?.getFormatId()
        if (proofSuiteId == null || proofSuiteId.value.isBlank()) {
            return ValidationResult.Invalid(
                code = "MISSING_PROOF_FORMAT",
                message = "Credential must have a proof with valid format",
                field = "proof",
                value = null
            )
        }

        return ValidationResult.Valid
    }
}

/**
 * Validation result.
 */
sealed class ValidationResult {
    /**
     * Validation passed.
     */
    object Valid : ValidationResult()
    
    /**
     * Validation failed.
     */
    data class Invalid(
        val code: String,
        val message: String,
        val field: String? = null,
        val value: Any? = null
    ) : ValidationResult()
    
    /**
     * Check if validation passed.
     */
    fun isValid(): Boolean = this is Valid
    
    /**
     * Get error message if validation failed.
     */
    fun errorMessage(): String? = (this as? Invalid)?.message
}
