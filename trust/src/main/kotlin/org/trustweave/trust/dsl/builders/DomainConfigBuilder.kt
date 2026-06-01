package org.trustweave.trust.dsl.builders

import org.trustweave.trust.domain.DomainId
import org.trustweave.trust.domain.treasury.KmsKeyRef
import org.trustweave.trust.domain.treasury.SpendPolicy
import org.trustweave.trust.dsl.TrustWeaveDsl

/**
 * Per-chain account binding inside a `domain { ... }` block. Captures the
 * on-chain address plus the [KmsKeyRef] used to sign on its behalf.
 */
data class DomainChainAccountConfig(
    val chainId: String,
    val address: String,
    val keyRef: KmsKeyRef,
)

/**
 * Snapshot of the `domain { ... }` builder consumed by [org.trustweave.trust.dsl.TrustWeaveFactory].
 */
data class DomainConfig(
    val domainId: DomainId,
    val payerDid: String,
    val accounts: List<DomainChainAccountConfig>,
    val spendPolicy: SpendPolicy,
)

/**
 * Builder for the single Trusted Domain attached to a TrustWeave instance.
 *
 * Phase 2 scope: one active domain per facade, one [ChainAccount] per chain.
 * Multi-domain deployments wrap multiple facades — or extend this builder once
 * the registry pattern is needed.
 */
@TrustWeaveDsl
class DomainConfigBuilder {
    private var domainId: DomainId? = null
    private var payerDid: String? = null
    private val accounts = mutableListOf<DomainChainAccountConfig>()
    private var spendPolicy: SpendPolicy = SpendPolicy.DEFAULT

    fun domainId(value: String) {
        domainId = DomainId(value)
    }

    fun domainId(value: DomainId) {
        domainId = value
    }

    fun payerDid(did: String) {
        payerDid = did
    }

    fun chainAccount(chainId: String, block: ChainAccountBuilder.() -> Unit) {
        val builder = ChainAccountBuilder(chainId).apply(block)
        accounts += builder.build()
    }

    fun spendPolicy(policy: SpendPolicy) {
        spendPolicy = policy
    }

    internal fun build(): DomainConfig {
        val id = requireNotNull(domainId) { "domain { domainId(...) } is required" }
        val payer = requireNotNull(payerDid) { "domain { payerDid(...) } is required" }
        require(accounts.isNotEmpty()) { "domain { ... } must declare at least one chainAccount" }
        return DomainConfig(
            domainId = id,
            payerDid = payer,
            accounts = accounts.toList(),
            spendPolicy = spendPolicy,
        )
    }
}

/**
 * Builder for a single per-chain account inside a `domain { ... }` block.
 */
@TrustWeaveDsl
class ChainAccountBuilder(private val chainId: String) {
    var keyRef: KmsKeyRef? = null
    var address: String? = null

    internal fun build(): DomainChainAccountConfig {
        val ref = requireNotNull(keyRef) { "chainAccount($chainId) { keyRef = ... } is required" }
        val addr = requireNotNull(address) { "chainAccount($chainId) { address = ... } is required" }
        return DomainChainAccountConfig(chainId = chainId, address = addr, keyRef = ref)
    }
}
