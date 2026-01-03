package org.trustweave.credential.internal

import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.identifiers.CredentialId
import org.trustweave.core.identifiers.Iri
import org.trustweave.did.identifiers.Did

/**
 * Input validation utilities for security and stability.
 * 
 * Provides validation functions to prevent denial-of-service attacks and ensure
 * input data is within reasonable bounds before processing.
 */
internal object InputValidation {
    /**
     * Validate credential ID length.
     * 
     * @param credentialId The credential ID to validate
     * @throws IllegalArgumentException if credential ID exceeds maximum length
     */
    fun validateCredentialId(credentialId: CredentialId) {
        if (credentialId.value.length > SecurityConstants.MAX_CREDENTIAL_ID_LENGTH) {
            throw IllegalArgumentException(
                "Credential ID exceeds maximum length of ${SecurityConstants.MAX_CREDENTIAL_ID_LENGTH} characters: " +
                "${credentialId.value.length} characters"
            )
        }
    }
    
    /**
     * Validate DID length.
     * 
     * @param did The DID to validate
     * @throws IllegalArgumentException if DID exceeds maximum length
     */
    fun validateDid(did: Did) {
        if (did.value.length > SecurityConstants.MAX_DID_LENGTH) {
            throw IllegalArgumentException(
                "DID exceeds maximum length of ${SecurityConstants.MAX_DID_LENGTH} characters: " +
                "${did.value.length} characters"
            )
        }
    }
    
    /**
     * Validate IRI length.
     * 
     * @param iri The IRI to validate
     * @throws IllegalArgumentException if IRI exceeds maximum length
     */
    fun validateIri(iri: Iri) {
        if (iri.value.length > SecurityConstants.MAX_DID_LENGTH) {
            throw IllegalArgumentException(
                "IRI exceeds maximum length of ${SecurityConstants.MAX_DID_LENGTH} characters: " +
                "${iri.value.length} characters"
            )
        }
    }
    
    /**
     * Validate schema ID length.
     * 
     * @param schemaId The schema ID to validate
     * @throws IllegalArgumentException if schema ID exceeds maximum length
     */
    fun validateSchemaId(schemaId: String) {
        if (schemaId.length > SecurityConstants.MAX_SCHEMA_ID_LENGTH) {
            throw IllegalArgumentException(
                "Schema ID exceeds maximum length of ${SecurityConstants.MAX_SCHEMA_ID_LENGTH} characters: " +
                "${schemaId.length} characters"
            )
        }
    }
    
    /**
     * Validate verification method ID length.
     * 
     * @param verificationMethodId The verification method ID to validate
     * @throws IllegalArgumentException if verification method ID exceeds maximum length
     */
    fun validateVerificationMethodId(verificationMethodId: String) {
        if (verificationMethodId.length > SecurityConstants.MAX_VERIFICATION_METHOD_ID_LENGTH) {
            throw IllegalArgumentException(
                "Verification method ID exceeds maximum length of " +
                "${SecurityConstants.MAX_VERIFICATION_METHOD_ID_LENGTH} characters: " +
                "${verificationMethodId.length} characters"
            )
        }
    }
    
    /**
     * Validate credential claims count.
     * 
     * @param credential The credential to validate
     * @throws IllegalArgumentException if credential has too many claims
     */
    fun validateCredentialClaimsCount(credential: VerifiableCredential) {
        val claimsCount = credential.credentialSubject.claims.size
        if (claimsCount > SecurityConstants.MAX_CLAIMS_PER_CREDENTIAL) {
            throw IllegalArgumentException(
                "Credential exceeds maximum claims count of ${SecurityConstants.MAX_CLAIMS_PER_CREDENTIAL}: " +
                "$claimsCount claims"
            )
        }
    }
    
    /**
     * Validate presentation credentials count.
     * 
     * @param presentation The presentation to validate
     * @throws IllegalArgumentException if presentation contains too many credentials
     */
    fun validatePresentationCredentialsCount(presentation: VerifiablePresentation) {
        val credentialsCount = presentation.verifiableCredential.size
        if (credentialsCount > SecurityConstants.MAX_CREDENTIALS_PER_PRESENTATION) {
            throw IllegalArgumentException(
                "Presentation exceeds maximum credentials count of " +
                "${SecurityConstants.MAX_CREDENTIALS_PER_PRESENTATION}: $credentialsCount credentials"
            )
        }
    }
    
    /**
     * Validate credential structure for basic sanity checks.
     * 
     * @param credential The credential to validate
     * @throws IllegalArgumentException if credential fails validation
     */
    fun validateCredentialStructure(credential: VerifiableCredential) {
        // Validate credential ID if present
        credential.id?.let { validateCredentialId(it) }
        
        // Validate issuer IRI
        validateIri(credential.issuer.id)
        
        // Validate subject IRI
        validateIri(credential.credentialSubject.id)
        
        // Validate claims count
        validateCredentialClaimsCount(credential)
        
        // Validate schema ID if present
        credential.credentialSchema?.id?.value?.let { validateSchemaId(it) }
        
        // Validate status ID if present
        credential.credentialStatus?.id?.value?.let { validateSchemaId(it) }
    }
    
    /**
     * Validate presentation structure for basic sanity checks.
     * 
     * @param presentation The presentation to validate
     * @throws IllegalArgumentException if presentation fails validation
     */
    fun validatePresentationStructure(presentation: VerifiablePresentation) {
        // Validate credentials count
        validatePresentationCredentialsCount(presentation)
        
        // Validate holder IRI
        validateIri(presentation.holder)
        
        // Validate each credential in the presentation
        presentation.verifiableCredential.forEach { credential ->
            validateCredentialStructure(credential)
        }
    }
}






