package org.trustweave.trust.domain.treasury

import org.trustweave.anchor.payment.OperationDescriptor
import org.trustweave.anchor.payment.TokenAmount

/**
 * On-chain account owned by a Trusted Domain on one specific chain.
 *
 * Holds the key reference (not the key material), the on-chain address, and
 * the operations needed by the treasury to query balance, estimate fees, and
 * sign unsigned transactions handed up from a plugin.
 *
 * Implementations are chain-aware (delegating to the corresponding
 * [org.trustweave.anchor.BlockchainAnchorClient] for estimation and to a
 * [org.trustweave.kms.KeyManagementService] for signing) and **must not**
 * cache key material outside of the configured KMS.
 */
interface ChainAccount {
    /** CAIP-2 chain ID — e.g. `eip155:137`, `algorand:mainnet`. */
    val chainId: String

    /** Native account identifier on [chainId]. */
    val address: String

    /** KMS reference used to sign on behalf of this account. */
    val keyRef: KmsKeyRef

    /** Current native-token balance, queried live from the chain. */
    suspend fun balance(): TokenAmount

    /** Estimate the fee an [op] would incur if submitted now. */
    suspend fun estimateFee(op: OperationDescriptor): TokenAmount

    /**
     * Sign an unsigned, plugin-built transaction. The transaction is opaque
     * to the treasury — only the plugin understands its structure (Cardano
     * UTXO, ETH RLP, Algorand MsgPack, …). The treasury merely brokers the
     * KMS call.
     */
    suspend fun sign(tx: UnsignedTx): SignedTx
}

/**
 * Opaque unsigned transaction handed from a plugin to a [ChainAccount] for
 * signing. The plugin populates [bytes] with chain-canonical encoded form
 * (RLP, MsgPack, CBOR, PSBT — chain's choice).
 *
 * [domainSeparation] disambiguates signature contexts that share an account
 * (e.g. EIP-712 vs raw RLP, mDoc COSE vs raw bytes).
 */
data class UnsignedTx(
    val chainId: String,
    val bytes: ByteArray,
    val domainSeparation: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UnsignedTx) return false
        return chainId == other.chainId &&
            bytes.contentEquals(other.bytes) &&
            domainSeparation == other.domainSeparation
    }

    override fun hashCode(): Int {
        var r = chainId.hashCode()
        r = 31 * r + bytes.contentHashCode()
        r = 31 * r + (domainSeparation?.hashCode() ?: 0)
        return r
    }
}

data class SignedTx(
    val chainId: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SignedTx) return false
        return chainId == other.chainId && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * chainId.hashCode() + bytes.contentHashCode()
}
