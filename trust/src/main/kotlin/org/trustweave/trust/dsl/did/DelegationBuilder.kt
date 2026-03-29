package org.trustweave.trust.dsl.did

import org.trustweave.trust.context.DidDslContext
import org.trustweave.did.verifier.DidDocumentDelegationVerifier
import org.trustweave.did.verifier.DelegationChainResult
import org.trustweave.did.identifiers.Did
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Delegation DSL.
 *
 * Provides a fluent API for verifying capability delegation chains between DIDs.
 *
 * **Example Usage**:
 * ```kotlin
 * val didContext: DidDslContext = ... // e.g. [org.trustweave.trust.TrustWeave]
 *
 * val result = didProvider.delegate {
 *     from("did:key:delegator")
 *     to("did:key:delegate")
 *     verify()
 * }
 *
 * if (result.valid) {
 *     println("Delegation verified: ${result.path}")
 * }
 * ```
 */
class DelegationBuilder(private val didContext: DidDslContext) {
    private val didResolver = didContext.getDidResolver()

    private val verifier: DidDocumentDelegationVerifier = DidDocumentDelegationVerifier(didResolver)

    private var delegatorDid: String? = null
    private var delegateDid: String? = null

    fun from(delegatorDid: String) {
        this.delegatorDid = delegatorDid
    }

    fun to(delegateDid: String) {
        this.delegateDid = delegateDid
    }

    suspend fun verify(): DelegationChainResult = withContext(Dispatchers.IO) {
        val delegatorStr = delegatorDid ?: throw IllegalStateException("Delegator DID is required. Use from(\"did:key:...\")")
        val delegateStr = delegateDid ?: throw IllegalStateException("Delegate DID is required. Use to(\"did:key:...\")")
        val delegator = Did(delegatorStr)
        val delegate = Did(delegateStr)
        return@withContext verifier.verify(delegator, delegate)
    }

    suspend fun verifyChain(delegatorDid: String, delegateDid: String): DelegationChainResult {
        return verifier.verify(Did(delegatorDid), Did(delegateDid))
    }

    suspend fun verifyChain(chain: List<String>): DelegationChainResult {
        return verifier.verifyChain(chain.map { Did(it) })
    }
}

/**
 * Extension function to get a delegation builder (any [DidDslContext], including [org.trustweave.trust.TrustWeave]).
 */
suspend fun DidDslContext.delegation(block: DelegationBuilder.() -> Unit): DelegationBuilder {
    val builder = DelegationBuilder(this)
    builder.block()
    return builder
}

