package com.trustweave.credential.dsl

import com.trustweave.credential.CredentialVerificationOptions
import com.trustweave.credential.CredentialVerificationResult
import com.trustweave.credential.models.VerifiableCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Verification Builder DSL.
 * 
 * Provides a fluent API for verifying credentials.
 * Focused only on credential-specific verification operations.
 * 
 * **Example Usage**:
 * ```kotlin
 * val result = verifier.verify {
 *     credential(credential)
 *     checkRevocation()
 *     checkExpiration()
 *     validateSchema("https://example.edu/schemas/degree.json")
 * }
 * ```
 */
class VerificationBuilder(
    private val verifier: com.trustweave.credential.verifier.CredentialVerifier,
    private val statusListManager: com.trustweave.credential.revocation.StatusListManager? = null
) {
    private var credential: VerifiableCredential? = null
    private var checkRevocation: Boolean = true
    private var checkExpiration: Boolean = true
    private var validateSchema: Boolean = false
    private var schemaId: String? = null
    private var validateProofPurpose: Boolean = false
    
    /**
     * Set the credential to verify.
     */
    fun credential(credential: VerifiableCredential) {
        this.credential = credential
    }
    
    /**
     * Enable revocation checking.
     */
    fun checkRevocation() {
        this.checkRevocation = true
    }
    
    /**
     * Disable revocation checking.
     */
    fun skipRevocationCheck() {
        this.checkRevocation = false
    }
    
    /**
     * Enable expiration checking.
     */
    fun checkExpiration() {
        this.checkExpiration = true
    }
    
    /**
     * Disable expiration checking.
     */
    fun skipExpirationCheck() {
        this.checkExpiration = false
    }
    
    /**
     * Enable schema validation.
     */
    fun validateSchema(schemaId: String) {
        this.validateSchema = true
        this.schemaId = schemaId
    }
    
    /**
     * Disable schema validation.
     */
    fun skipSchemaValidation() {
        this.validateSchema = false
        this.schemaId = null
    }
    
    /**
     * Enable proof purpose validation.
     */
    fun validateProofPurpose() {
        this.validateProofPurpose = true
    }
    
    /**
     * Build and perform verification.
     */
    suspend fun build(): CredentialVerificationResult = withContext(Dispatchers.IO) {
        val cred = credential ?: throw IllegalStateException("Credential is required")

        val options = CredentialVerificationOptions(
            checkRevocation = checkRevocation,
            checkExpiration = checkExpiration,
            validateSchema = validateSchema,
            schemaId = schemaId,
            verifyBlockchainAnchor = false, // Anchoring should be handled by orchestration layer
            chainId = null,
            checkTrustRegistry = false, // Trust checking should be handled by orchestration layer
            trustRegistry = null,
            statusListManager = statusListManager,
            verifyDelegation = false, // Delegation should be handled by orchestration layer
            validateProofPurpose = validateProofPurpose
        )
        
        verifier.verify(cred, options)
    }
}

/**
 * Extension function to verify credentials using CredentialDslProvider.
 */
suspend fun CredentialDslProvider.verify(block: VerificationBuilder.() -> Unit): CredentialVerificationResult {
    val builder = VerificationBuilder(
        verifier = getVerifier(),
        statusListManager = getStatusListManager()
    )
    builder.block()
    return builder.build()
}

