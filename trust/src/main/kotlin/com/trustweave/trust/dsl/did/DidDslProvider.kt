package com.trustweave.trust.dsl.did

import com.trustweave.did.DidCreationOptions
import com.trustweave.did.DidDocument
import com.trustweave.did.DidMethod
import com.trustweave.did.delegation.DelegationChainResult

/**
 * Provider interface for DID DSL operations.
 * 
 * This interface allows DID DSLs to work without directly depending on TrustLayerContext,
 * enabling them to be in the trust module while being used by other modules.
 */
interface DidDslProvider {
    /**
     * Get a DID method by name.
     */
    fun getDidMethod(name: String): DidMethod?
    
    /**
     * Resolve a DID to a DID document.
     */
    suspend fun resolveDid(did: String): DidDocument?
    
    /**
     * Get a DID resolver for delegation operations.
     * This returns the native DID resolver interface used by did:core.
     */
    fun getDidResolver(): com.trustweave.did.resolver.DidResolver
}

