package com.trustweave.trust.dsl.credential

import com.trustweave.credential.issuer.CredentialIssuer
import com.trustweave.credential.verifier.CredentialVerifier
import com.trustweave.credential.revocation.StatusListManager
import com.trustweave.trust.types.ProofType

/**
 * Credential DSL Provider Interface.
 * 
 * Provides a minimal abstraction for credential DSL operations.
 * This makes credentials:core self-contained and focused only on credential operations.
 * 
 * All types used here are from credentials:core itself - no external dependencies.
 */
interface CredentialDslProvider {
    /**
     * Get the credential issuer.
     */
    fun getIssuer(): CredentialIssuer
    
    /**
     * Get the credential verifier.
     */
    fun getVerifier(): CredentialVerifier
    
    /**
     * Get the status list manager for revocation checking.
     * Returns null if not configured.
     */
    fun getStatusListManager(): StatusListManager?
    
    /**
     * Get the default proof type.
     * 
     * Returns a type-safe ProofType instead of a string to prevent errors
     * and improve API safety.
     */
    fun getDefaultProofType(): ProofType
}


