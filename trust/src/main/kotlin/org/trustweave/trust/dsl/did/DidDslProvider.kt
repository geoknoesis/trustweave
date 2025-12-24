package org.trustweave.trust.dsl.did

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.model.DidDocument
import org.trustweave.did.DidMethod
import org.trustweave.did.resolver.DidResolver
import org.trustweave.did.verifier.DelegationChainResult
import org.trustweave.did.identifiers.Did

/**
 * Provider interface for DID DSL operations.
 *
 * This interface allows DID DSLs to work without directly depending on TrustWeaveContext,
 * enabling them to be in the trust module while being used by other modules.
 */
interface DidDslProvider {
    /**
     * Get a DID method by name.
     */
    fun getDidMethod(name: String): DidMethod?

    /**
     * Resolve a DID to a DID document.
     *
     * @param did Type-safe Did identifier
     * @return Resolved DID document, or null if resolution failed
     */
    suspend fun resolveDid(did: Did): DidDocument?

    /**
     * Get a DID resolver for delegation operations.
     * This returns the native DID resolver interface used by did:core.
     */
    fun getDidResolver(): DidResolver
}

