package com.trustweave.did.dsl

import com.trustweave.did.DidCreationOptions
import com.trustweave.did.DidDocument
import com.trustweave.did.DidMethod
import com.trustweave.did.delegation.DelegationChainResult

/**
 * Provider interface for DID DSL operations.
 * 
 * This interface allows DID DSLs to work without directly depending on TrustLayerContext,
 * enabling them to be in the did:core module while being used by the trust module.
 */
interface DidDslProvider {
    /**
     * Get a DID method by name.
     */
    fun getDidMethod(name: String): DidMethod?
    
    /**
     * Get DID document access service.
     */
    fun getDidDocumentAccess(): com.trustweave.did.services.DidDocumentAccess?
    
    /**
     * Get verification method access service.
     */
    fun getVerificationMethodAccess(): com.trustweave.did.services.VerificationMethodAccess?
    
    /**
     * Get DID method service.
     */
    fun getDidMethodService(): com.trustweave.did.services.DidMethodService?
    
    /**
     * Resolve a DID to a DID document.
     */
    suspend fun resolveDid(did: String): DidDocument?
    
    /**
     * Get a DID resolver for delegation operations.
     * This returns the native DID resolver interface used by did:core.
     */
    fun getDidResolver(): com.trustweave.did.resolution.DidResolver
}

