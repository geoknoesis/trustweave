package com.trustweave.credential.dsl

import com.trustweave.credential.CredentialIssuanceOptions
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.models.CredentialStatus
import com.trustweave.credential.revocation.StatusPurpose
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Issuance Builder DSL.
 * 
 * Provides a fluent API for issuing credentials.
 * Focused only on credential-specific operations.
 * 
 * **Example Usage**:
 * ```kotlin
 * val issuedCredential = issuer.issue {
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
 * }
 * ```
 */
class IssuanceBuilder(
    private val issuer: com.trustweave.credential.issuer.CredentialIssuer,
    private val statusListManager: com.trustweave.credential.revocation.StatusListManager? = null,
    private val defaultProofType: String = "Ed25519Signature2020"
) {
    private var credential: VerifiableCredential? = null
    private var issuerDid: String? = null
    private var keyId: String? = null
    private var proofType: String? = null
    private var challenge: String? = null
    private var domain: String? = null
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
            if (statusListManager != null) {
                try {
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
                } catch (e: Exception) {
                    // Status list creation failed - continue without revocation
                    // In production, you might want to log this or throw
                }
            }
        }
        
        val proofTypeToUse = proofType ?: defaultProofType
        
        // Construct verification method ID that matches the DID document format
        // This ensures the proof verification can find the verification method
        val verificationMethodId = "$issuer#$key"
        
        val options = CredentialIssuanceOptions(
            proofType = proofTypeToUse,
            keyId = key,
            issuerDid = issuer,
            challenge = challenge,
            domain = domain,
            anchorToBlockchain = false, // Anchoring should be handled by orchestration layer
            chainId = null,
            additionalOptions = mapOf("verificationMethod" to verificationMethodId)
        )
        
        // Issue credential - use fully qualified method call to avoid ambiguity with extension function
        val issuedCredential = (issuer as com.trustweave.credential.issuer.CredentialIssuer).issue(
            credential = credentialToIssue,
            issuerDid = issuer,
            keyId = key,
            options = options
        )
        issuedCredential
}

/**
 * Extension function to issue credentials using CredentialDslProvider.
 */
suspend fun CredentialDslProvider.issue(block: IssuanceBuilder.() -> Unit): VerifiableCredential {
    val builder = IssuanceBuilder(
        issuer = getIssuer(),
        statusListManager = getStatusListManager(),
        defaultProofType = getDefaultProofType()
    )
    builder.block()
    return builder.build()
}
}

