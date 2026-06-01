package org.trustweave.anchor.payment

import java.math.BigInteger

/**
 * Reference to an on-chain asset.
 *
 * `Native` is the chain's native fee token (ETH, ALGO, ADA, BTC, MATIC, …).
 * `Token` is a fungible token on that chain (ERC-20 contract on EVM, ASA on
 * Algorand, …). `OperatorCredit` is a synthetic asset that represents off-chain
 * credit with a third-party operator (e.g. an Orb node operator that pays the
 * actual on-chain fee on the domain's behalf and bills off-chain).
 */
sealed class AssetRef {
    object Native : AssetRef() {
        override fun toString(): String = "native"
    }

    data class Token(val symbol: String, val contract: String) : AssetRef()

    data class OperatorCredit(val operatorId: String) : AssetRef()
}

/**
 * Amount denominated in the base units of an on-chain asset.
 *
 * Base units only — never floating point. wei for EVM, microALGO for Algorand,
 * lovelace for Cardano, satoshi for Bitcoin. Display-unit conversion is a
 * presentation concern handled at the UI/DSL layer.
 */
data class TokenAmount(
    val chainId: String,
    val asset: AssetRef,
    val amount: BigInteger,
) {
    init {
        require(chainId.isNotBlank()) { "chainId must not be blank" }
        require(amount.signum() >= 0) { "amount must be non-negative: $amount" }
    }

    operator fun plus(other: TokenAmount): TokenAmount {
        requireSameAsset(other)
        return copy(amount = amount + other.amount)
    }

    operator fun minus(other: TokenAmount): TokenAmount {
        requireSameAsset(other)
        return copy(amount = amount - other.amount)
    }

    operator fun compareTo(other: TokenAmount): Int {
        requireSameAsset(other)
        return amount.compareTo(other.amount)
    }

    fun times(scalar: Int): TokenAmount = copy(amount = amount * BigInteger.valueOf(scalar.toLong()))

    private fun requireSameAsset(other: TokenAmount) {
        require(chainId == other.chainId && asset == other.asset) {
            "TokenAmount arithmetic requires same chain and asset " +
                "(got $chainId/$asset vs ${other.chainId}/${other.asset})"
        }
    }

    companion object {
        fun zero(chainId: String, asset: AssetRef = AssetRef.Native): TokenAmount =
            TokenAmount(chainId, asset, BigInteger.ZERO)

        /**
         * Placeholder amount used when an unmanaged client cannot derive the
         * fee actually paid (e.g. legacy plugins that don't return gas-used).
         * Treated as informational, never debited.
         */
        fun unknown(chainId: String, asset: AssetRef = AssetRef.Native): TokenAmount =
            TokenAmount(chainId, asset, BigInteger.ZERO)
    }
}
