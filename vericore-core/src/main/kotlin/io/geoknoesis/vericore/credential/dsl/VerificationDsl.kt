package io.geoknoesis.vericore.credential.dsl

import io.geoknoesis.vericore.credential.CredentialVerificationOptions
import io.geoknoesis.vericore.credential.CredentialVerificationResult
import io.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Verification Builder DSL.
 * 
 * Provides a fluent API for verifying credentials using trust layer configuration.
 * 
 * **Example Usage**:
 * ```kotlin
 * val result = trustLayer.verify {
 *     credential(credential)
 *     checkRevocation()
 *     checkExpiration()
 *     validateSchema("https://example.edu/schemas/degree.json")
 *     verifyAnchor() // uses trust layer's anchor configuration
 * }
 * ```
 */
class VerificationBuilder(
    private val context: TrustLayerContext
) {
    private var credential: VerifiableCredential? = null
    private var checkRevocation: Boolean = true
    private var checkExpiration: Boolean = true
    private var validateSchema: Boolean = false
    private var schemaId: String? = null
    private var verifyBlockchainAnchor: Boolean = false
    private var chainId: String? = null
    
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
     * Verify blockchain anchor.
     */
    fun verifyAnchor(chainId: String? = null) {
        this.verifyBlockchainAnchor = true
        this.chainId = chainId
    }
    
    /**
     * Build and perform verification.
     */
    suspend fun build(): CredentialVerificationResult = withContext(Dispatchers.IO) {
        val cred = credential ?: throw IllegalStateException("Credential is required")
        
        val config = context.getConfig()
        val chainIdToUse = chainId ?: config.credentialConfig.defaultChain
        
        val options = CredentialVerificationOptions(
            checkRevocation = checkRevocation,
            checkExpiration = checkExpiration,
            validateSchema = validateSchema,
            schemaId = schemaId,
            verifyBlockchainAnchor = verifyBlockchainAnchor,
            chainId = chainIdToUse
        )
        
        context.getVerifier().verify(cred, options)
    }
}

