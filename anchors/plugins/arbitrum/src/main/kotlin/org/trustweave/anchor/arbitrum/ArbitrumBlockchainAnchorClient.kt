package org.trustweave.anchor.arbitrum

import org.slf4j.LoggerFactory
import org.trustweave.anchor.evm.AbstractEvmAnchorClient
import org.trustweave.anchor.evm.EvmChainConfig
import org.trustweave.anchor.evm.EvmGas
import java.math.BigInteger

/**
 * Arbitrum blockchain anchor client implementation.
 *
 * Supports Arbitrum One mainnet and Arbitrum Sepolia testnet chains.
 * Uses Ethereum-compatible transaction data fields to store payload data.
 * All chain mechanics (signing, nonce, confirmation, reads) live in
 * [AbstractEvmAnchorClient]; only the gas-limit strategy is Arbitrum-specific.
 *
 * **Example Usage:**
 * ```kotlin
 * val options = mapOf(
 *     "rpcUrl" to "https://arb1.arbitrum.io/rpc",
 *     "privateKey" to "0x..."
 * )
 * val client = ArbitrumBlockchainAnchorClient(ArbitrumBlockchainAnchorClient.MAINNET, options)
 * ```
 */
class ArbitrumBlockchainAnchorClient(
    chainId: String,
    options: Map<String, Any?> = emptyMap()
) : AbstractEvmAnchorClient(chainId, options, resolveChain(chainId)) {

    companion object {
        const val MAINNET = "eip155:42161"  // Arbitrum One mainnet
        const val ARBITRUM_SEPOLIA = "eip155:421614" // Arbitrum Sepolia testnet

        // Network RPC endpoints
        private const val MAINNET_RPC_URL = "https://arb1.arbitrum.io/rpc"
        private const val ARBITRUM_SEPOLIA_RPC_URL = "https://sepolia-rollup.arbitrum.io/rpc"

        // Safety margin applied on top of eth_estimateGas, in percent. Arbitrum's
        // L1 calldata-posting component (gasUsedForL1) moves with L1 gas prices
        // between estimation and execution, so leave generous headroom.
        internal const val ESTIMATE_GAS_MARGIN_PERCENT = 20

        private val logger = LoggerFactory.getLogger(ArbitrumBlockchainAnchorClient::class.java)

        /**
         * Derives the transaction gas limit from an `eth_estimateGas` result:
         * estimate +[ESTIMATE_GAS_MARGIN_PERCENT]% margin, floored at the Ethereum
         * intrinsic gas (the protocol minimum for tx validity).
         *
         * When estimation failed ([estimatedGas] is null), falls back to
         * [EvmGas.txGasLimit] — which is likely too low on Arbitrum Nitro, where
         * the L1 calldata-posting component (`gasUsedForL1`) is charged inside
         * `gasUsed`, so actual usage routinely exceeds intrinsic+10% — and logs
         * a warning.
         */
        internal fun gasLimitFrom(estimatedGas: BigInteger?, data: ByteArray): BigInteger {
            if (estimatedGas == null) {
                val fallback = EvmGas.txGasLimit(data)
                logger.warn(
                    "eth_estimateGas failed; falling back to Ethereum intrinsic gas limit {} — " +
                        "this is likely too low on Arbitrum Nitro (the L1 calldata-posting component " +
                        "is charged inside gasUsed) and the transaction may run out of gas.",
                    fallback
                )
                return fallback
            }
            return maxOf(
                EvmGas.withMargin(estimatedGas, ESTIMATE_GAS_MARGIN_PERCENT),
                EvmGas.intrinsicGas(data)
            )
        }

        private fun resolveChain(chainId: String): EvmChainConfig {
            require(chainId.startsWith("eip155:")) {
                "Invalid chain ID for Arbitrum: $chainId"
            }
            val chainIdNum = chainId.substringAfter(":").toIntOrNull()
            require(chainIdNum == 42161 || chainIdNum == 421614) {
                "Unsupported Arbitrum chain ID: $chainId. Use 'eip155:42161' (mainnet) or 'eip155:421614' (Arbitrum Sepolia testnet)"
            }
            return when (chainId) {
                MAINNET -> EvmChainConfig(
                    numericChainId = 42161L,
                    defaultRpcUrl = MAINNET_RPC_URL,
                    blockchainName = "Arbitrum",
                    networkName = "arbitrum-mainnet"
                )
                else -> EvmChainConfig(
                    numericChainId = 421614L,
                    defaultRpcUrl = ARBITRUM_SEPOLIA_RPC_URL,
                    blockchainName = "Arbitrum",
                    networkName = "arbitrum-sepolia-testnet"
                )
            }
        }
    }

    /**
     * Arbitrum Nitro charges the L1 calldata-posting component (gasUsedForL1)
     * inside gasUsed, so the Ethereum intrinsic calldata gas routinely falls
     * short here. eth_estimateGas is authoritative; add headroom for L2
     * variability and fall back to intrinsic gas only if estimation fails.
     */
    override fun deriveGasLimit(data: ByteArray, from: String): BigInteger =
        gasLimitFrom(tryEstimateGas(data, from), data)
}
