package com.trustweave.did.dsl

import com.trustweave.did.delegation.DelegationChainResult
import com.trustweave.did.delegation.DelegationService
import com.trustweave.did.services.DidDocumentAccess
import com.trustweave.did.services.VerificationMethodAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Delegation DSL.
 * 
 * Provides a fluent API for verifying delegation chains between DIDs.
 * 
 * **Example Usage**:
 * ```kotlin
 * val didProvider: DidDslProvider = ...
 * 
 * val result = didProvider.delegate {
 *     from("did:key:delegator")
 *     to("did:key:delegate")
 *     capability("issueCredentials")
 *     verify()
 * }
 * 
 * if (result.valid) {
 *     println("Delegation verified: ${result.path}")
 * }
 * ```
 */
class DelegationBuilder(private val provider: DidDslProvider) {
    private val didResolver = provider.getDidResolver()
    
    private val delegationService: DelegationService = run {
        val docAccess = provider.getDidDocumentAccess() 
            ?: throw IllegalStateException("DidDocumentAccess not configured")
        val vmAccess = provider.getVerificationMethodAccess()
            ?: throw IllegalStateException("VerificationMethodAccess not configured")
        DelegationService(didResolver, docAccess, vmAccess)
    }
    
    private var delegatorDid: String? = null
    private var delegateDid: String? = null
    private var capability: String? = null
    
    fun from(delegatorDid: String) {
        this.delegatorDid = delegatorDid
    }
    
    fun to(delegateDid: String) {
        this.delegateDid = delegateDid
    }
    
    fun capability(capability: String) {
        this.capability = capability
    }
    
    suspend fun verify(): DelegationChainResult = withContext(Dispatchers.IO) {
        val delegator = delegatorDid ?: throw IllegalStateException("Delegator DID is required. Use from(\"did:key:...\")")
        val delegate = delegateDid ?: throw IllegalStateException("Delegate DID is required. Use to(\"did:key:...\")")
        return@withContext delegationService.verifyDelegationChain(delegator, delegate, capability)
    }
    
    suspend fun verifyChain(delegatorDid: String, delegateDid: String, capability: String? = null): DelegationChainResult {
        return delegationService.verifyDelegationChain(delegatorDid, delegateDid, capability)
    }
    
    suspend fun verifyChain(chain: List<String>, capability: String? = null): DelegationChainResult {
        return delegationService.verifyDelegationChainMultiHop(chain, capability)
    }
}

/**
 * Extension function to delegate using a DID DSL provider.
 */
suspend fun DidDslProvider.delegate(block: suspend DelegationBuilder.() -> Unit): DelegationChainResult {
    val builder = DelegationBuilder(this)
    builder.block()
    return builder.verify()
}

/**
 * Extension function to get a delegation builder.
 */
suspend fun DidDslProvider.delegation(block: DelegationBuilder.() -> Unit): DelegationBuilder {
    val builder = DelegationBuilder(this)
    builder.block()
    return builder
}
