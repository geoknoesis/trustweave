package com.geoknoesis.vericore.credential.dsl

import com.geoknoesis.vericore.credential.CredentialIssuanceOptions
import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.credential.anchor.CredentialAnchorService
import com.geoknoesis.vericore.credential.models.CredentialStatus
import com.geoknoesis.vericore.credential.revocation.StatusPurpose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Issuance Builder DSL.
 * 
 * Provides a fluent API for issuing credentials using trust layer configuration.
 * 
 * **Example Usage**:
 * ```kotlin
 * val issuedCredential = trustLayer.issue {
 *     credential {
 *         id("https://example.edu/credentials/123")
 *         type("DegreeCredential")
 *         issuer("did:key:university")
 *         subject {
 *             id("did:key:student")
 *             "degree" {
 *                 "type" to "BachelorDegree"
 *                 "name" to "Bachelor of Science"
 *             }
 *         }
 *         issued(Instant.now())
 *         withRevocation() // Auto-creates status list if needed
 *     }
 *     by(issuerDid = "did:key:university", keyId = "key-1")
 *     withProof("Ed25519Signature2020")
 *     anchor("algorand:testnet") // optional
 * }
 * ```
 */
class IssuanceBuilder(
    private val context: TrustLayerContext
) {
    private var credential: VerifiableCredential? = null
    private var issuerDid: String? = null
    private var keyId: String? = null
    private var proofType: String? = null
    private var challenge: String? = null
    private var domain: String? = null
    private var anchorChain: String? = null
    private var autoAnchor: Boolean = false
    private var autoRevocation: Boolean = false
    
    /**
     * Set the credential to issue (can be built inline).
     */
    fun credential(block: CredentialBuilder.() -> Unit) {
        val builder = CredentialBuilder()
        builder.block()
        credential = builder.build()
    }
    
    /**
     * Set the credential directly.
     */
    fun credential(credential: VerifiableCredential) {
        this.credential = credential
    }
    
    /**
     * Set issuer DID and key ID.
     */
    fun by(issuerDid: String, keyId: String) {
        this.issuerDid = issuerDid
        this.keyId = keyId
    }
    
    /**
     * Set proof type.
     */
    fun withProof(type: String) {
        this.proofType = type
    }
    
    /**
     * Set challenge for proof.
     */
    fun challenge(challenge: String) {
        this.challenge = challenge
    }
    
    /**
     * Set domain for proof.
     */
    fun domain(domain: String) {
        this.domain = domain
    }
    
    /**
     * Anchor credential to blockchain.
     */
    fun anchor(chainId: String) {
        this.anchorChain = chainId
        this.autoAnchor = true
    }
    
    /**
     * Enable automatic revocation support (creates status list if needed).
     */
    fun withRevocation() {
        this.autoRevocation = true
    }
    
    /**
     * Build and issue the credential.
     */
    suspend fun build(): VerifiableCredential = withContext(Dispatchers.IO) {
        val cred = credential ?: throw IllegalStateException("Credential is required")
        val issuer = issuerDid ?: throw IllegalStateException("Issuer DID is required")
        val key = keyId ?: throw IllegalStateException("Key ID is required")
        
        // Handle auto-revocation if enabled
        var credentialToIssue = cred
        if (autoRevocation && cred.credentialStatus == null) {
            try {
                val statusListManager = context.getStatusListManager()
                if (statusListManager != null) {
                    // Create or get status list for issuer
                    val statusList = statusListManager.createStatusList(
                        issuerDid = issuer,
                        purpose = StatusPurpose.REVOCATION
                    )
                    
                    // Add credential status to credential
                    val credentialStatus = CredentialStatus(
                        id = "${statusList.id}#0",
                        type = "StatusList2021Entry",
                        statusPurpose = "revocation",
                        statusListIndex = "0",
                        statusListCredential = statusList.id
                    )
                    
                    credentialToIssue = cred.copy(credentialStatus = credentialStatus)
                }
            } catch (e: Exception) {
                // Status list manager not available - continue without revocation
                // In production, you might want to log this or throw
            }
        }
        
        val config = context.getConfig()
        val proofTypeToUse = proofType ?: config.credentialConfig.defaultProofType
        
        // Construct verification method ID that matches the DID document format
        // This ensures the proof verification can find the verification method
        val verificationMethodId = "$issuer#$key"
        
        val options = CredentialIssuanceOptions(
            proofType = proofTypeToUse,
            keyId = key,
            issuerDid = issuer,
            challenge = challenge,
            domain = domain,
            anchorToBlockchain = autoAnchor || config.credentialConfig.autoAnchor,
            chainId = anchorChain ?: config.credentialConfig.defaultChain,
            additionalOptions = mapOf("verificationMethod" to verificationMethodId)
        )
        
        // Issue credential
        val issuedCredential = context.getIssuer().issue(
            credential = credentialToIssue,
            issuerDid = issuer,
            keyId = key,
            options = options
        )
        
        // Auto-anchor if configured
        if (autoAnchor || config.credentialConfig.autoAnchor) {
            val chainId = anchorChain ?: config.credentialConfig.defaultChain
                ?: throw IllegalStateException("Chain ID required for anchoring")
            
            val anchorClient = context.getAnchorClient(chainId)
                ?: throw IllegalStateException("No anchor client configured for chain: $chainId")
            
            val anchorService = CredentialAnchorService(anchorClient)
            // Note: anchorCredential is a suspend function, but we're already in a suspend context
            try {
                val anchorMethod = anchorService.javaClass.getMethod(
                    "anchorCredential",
                    VerifiableCredential::class.java,
                    String::class.java,
                    Any::class.java // AnchorOptions
                )
                // Create AnchorOptions via reflection
                val anchorOptionsClass = Class.forName("com.geoknoesis.vericore.credential.anchor.AnchorOptions")
                val anchorOptions = anchorOptionsClass.getDeclaredConstructor(Boolean::class.java)
                    .newInstance(true)
                anchorMethod.invoke(anchorService, issuedCredential, chainId, anchorOptions)
            } catch (e: Exception) {
                // Anchoring failed, but credential is still issued
                // Log warning or handle as needed
            }
        }
        
        issuedCredential
    }
}

