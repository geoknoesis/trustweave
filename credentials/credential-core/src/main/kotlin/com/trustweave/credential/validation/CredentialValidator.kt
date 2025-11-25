package com.trustweave.credential.validation

import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.core.util.ValidationResult  // From :common module
import com.trustweave.did.validation.DidValidator  // From :did:did-core module
import kotlinx.serialization.json.JsonNull

/**
 * Credential-specific validation utilities for TrustWeave.
 * 
 * Common validation (DID, Chain ID) is in the root common module.
 * This module provides credential-specific validation.
 */

/**
 * Credential validation utilities.
 */
object CredentialValidator {
    /**
     * Validates basic credential structure.
     * 
     * @param credential The credential to validate
     * @return ValidationResult indicating if the credential structure is valid
     */
    fun validateStructure(credential: VerifiableCredential): ValidationResult {
        // Check for VerifiableCredential type
        if (!credential.type.contains("VerifiableCredential")) {
            return ValidationResult.Invalid(
                code = "MISSING_VERIFIABLE_CREDENTIAL_TYPE",
                message = "Credential type must include 'VerifiableCredential'",
                field = "type",
                value = credential.type
            )
        }
        
        // Check issuer
        if (credential.issuer.isBlank()) {
            return ValidationResult.Invalid(
                code = "MISSING_ISSUER",
                message = "Credential issuer is required",
                field = "issuer",
                value = credential.issuer
            )
        }
        
        // Validate issuer DID format
        DidValidator.validateFormat(credential.issuer).let {
            if (!it.isValid()) {
                return ValidationResult.Invalid(
                    code = "INVALID_ISSUER_DID",
                    message = "Invalid issuer DID format: ${it.errorMessage()}",
                    field = "issuer",
                    value = credential.issuer
                )
            }
        }
        
        // Check issuance date
        if (credential.issuanceDate.isBlank()) {
            return ValidationResult.Invalid(
                code = "MISSING_ISSUANCE_DATE",
                message = "Credential issuanceDate is required",
                field = "issuanceDate",
                value = credential.issuanceDate
            )
        }
        
        // Check credential subject (JsonElement can be JsonNull, but we require a valid value)
        if (credential.credentialSubject is JsonNull) {
            return ValidationResult.Invalid(
                code = "MISSING_CREDENTIAL_SUBJECT",
                message = "Credential subject is required and cannot be null",
                field = "credentialSubject",
                value = null
            )
        }
        
        return ValidationResult.Valid
    }
    
    /**
     * Validates that the credential has a proof.
     * 
     * @param credential The credential to validate
     * @return ValidationResult indicating if the credential has a proof
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
        
        return ValidationResult.Valid
    }
}


