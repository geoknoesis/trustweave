package com.geoknoesis.vericore.credential.dsl

import com.geoknoesis.vericore.credential.CredentialVerificationOptions
import com.geoknoesis.vericore.credential.CredentialVerificationResult
import com.geoknoesis.vericore.credential.did.CredentialDidResolver
import com.geoknoesis.vericore.credential.did.asCredentialDidResolution
import com.geoknoesis.vericore.credential.models.VerifiableCredential
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
    private var checkTrustRegistry: Boolean = false
    private var verifyDelegation: Boolean = false
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
     * Verify blockchain anchor.
     */
    fun verifyAnchor(chainId: String? = null) {
        this.verifyBlockchainAnchor = true
        this.chainId = chainId
    }
    
    /**
     * Enable trust registry checking.
     */
    fun checkTrustRegistry() {
        this.checkTrustRegistry = true
    }
    
    /**
     * Enable delegation verification.
     */
    fun verifyDelegation() {
        this.verifyDelegation = true
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
        
        val config = context.getConfig()
        val chainIdToUse = chainId ?: config.credentialConfig.defaultChain
        
        val didResolver = context.getConfig().didResolver ?: CredentialDidResolver { did ->
            runCatching { context.getConfig().registries.didRegistry.resolve(did) }
                .getOrNull()
                ?.asCredentialDidResolution()
        }

        val options = CredentialVerificationOptions(
            checkRevocation = checkRevocation,
            checkExpiration = checkExpiration,
            validateSchema = validateSchema,
            schemaId = schemaId,
            verifyBlockchainAnchor = verifyBlockchainAnchor,
            chainId = chainIdToUse,
            checkTrustRegistry = checkTrustRegistry,
            trustRegistry = context.getTrustRegistry(),
            verifyDelegation = verifyDelegation,
            didResolver = didResolver,
            validateProofPurpose = validateProofPurpose
        )
        
        context.getVerifier().verify(cred, options)
    }
}

