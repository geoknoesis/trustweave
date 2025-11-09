package io.geoknoesis.vericore.credential.dsl

import io.geoknoesis.vericore.did.delegation.DelegationChainResult
import io.geoknoesis.vericore.did.delegation.DelegationService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Delegation DSL.
 * 
 * Provides a fluent API for verifying delegation chains between DIDs.
 * 
 * **Example Usage**:
 * ```kotlin
 * val trustLayer = trustLayer { ... }
 * 
 * val result = trustLayer.delegate {
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
class DelegationBuilder(private val context: TrustLayerContext) {
    private val resolveDidFn: suspend (String) -> Any? = { did ->
        try {
            val didRegistryClass = Class.forName("io.geoknoesis.vericore.did.DidRegistry")
            val resolveMethod = didRegistryClass.getMethod("resolve", String::class.java)
            resolveMethod.invoke(null, did) as? Any
        } catch (e: Exception) {
            null
        }
    }

    private val delegationService = DelegationService(resolveDidFn)
    
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

suspend fun TrustLayerContext.delegate(block: suspend DelegationBuilder.() -> Unit): DelegationChainResult {
    val builder = DelegationBuilder(this)
    builder.block()
    return builder.verify()
}

suspend fun TrustLayerConfig.delegate(block: suspend DelegationBuilder.() -> Unit): DelegationChainResult {
    return dsl().delegate(block)
}

suspend fun TrustLayerContext.delegation(block: DelegationBuilder.() -> Unit): DelegationBuilder {
    val builder = DelegationBuilder(this)
    builder.block()
    return builder
}

suspend fun TrustLayerConfig.delegation(block: DelegationBuilder.() -> Unit): DelegationBuilder {
    return dsl().delegation(block)
}
