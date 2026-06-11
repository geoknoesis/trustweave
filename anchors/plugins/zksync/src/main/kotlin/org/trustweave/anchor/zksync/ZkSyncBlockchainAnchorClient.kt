package org.trustweave.anchor.zksync

import org.slf4j.LoggerFactory
import org.trustweave.anchor.evm.AbstractEvmAnchorClient
import org.trustweave.anchor.evm.EvmChainConfig
import org.trustweave.anchor.evm.EvmGas
import java.math.BigInteger

/**
 * zkSync Era blockchain anchor client implementation.
 *
 * Supports zkSync Era mainnet and Sepolia testnet chains.
 * Uses Ethereum-compatible transaction data fields to store payload data.
 * All chain mechanics (signing, nonce, confirmation, reads) live in
 * [AbstractEvmAnchorClient]; only the gas-limit strategy is zkSync-specific.
 *
 * Chain ID format: "eip155:<chain-id>"
 * Examples:
 * - "eip155:324" (zkSync Era mainnet)
 * - "eip155:300" (zkSync Era Sepolia testnet)
 *
 * **Example:**
 * ```kotlin
 * val client = ZkSyncBlockchainAnchorClient(
 *     chainId = "eip155:324",
 *     options = mapOf(
 *         "rpcUrl" to "https://mainnet.era.zksync.io",
 *         "privateKey" to "0x..."
 *     )
 * )
 * ```
 */
class ZkSyncBlockchainAnchorClient(
    chainId: String,
    options: Map<String, Any?> = emptyMap()
) : AbstractEvmAnchorClient(chainId, options, resolveChain(chainId)) {

    companion object {
        const val MAINNET = "eip155:324"  // zkSync Era mainnet
        const val SEPOLIA = "eip155:300" // zkSync Era Sepolia testnet

        // Network RPC endpoints
        private const val MAINNET_RPC_URL = "https://mainnet.era.zksync.io"
        private const val SEPOLIA_RPC_URL = "https://sepolia.era.zksync.io"

        // Safety margin applied on top of eth_estimateGas, in percent. zkSync Era's
        // actual gas usage moves with L1 gas prices and pubdata costs between
        // estimation and execution, so leave generous headroom.
        internal const val ESTIMATE_GAS_MARGIN_PERCENT = 20

        private val logger = LoggerFactory.getLogger(ZkSyncBlockchainAnchorClient::class.java)

        /**
         * Derives the transaction gas limit from an `eth_estimateGas` result:
         * estimate +[ESTIMATE_GAS_MARGIN_PERCENT]% margin, floored at the Ethereum
         * intrinsic gas (the protocol minimum for tx validity).
         *
         * When estimation failed ([estimatedGas] is null), falls back to
         * [EvmGas.txGasLimit] — which is very likely too low on zkSync Era, whose
         * gas model charges bootloader/validation overhead and pubdata costs inside
         * the gas limit (a simple transfer needs ~10^5+ gas) — and logs a warning.
         */
        internal fun gasLimitFrom(estimatedGas: BigInteger?, data: ByteArray): BigInteger {
            if (estimatedGas == null) {
                val fallback = EvmGas.txGasLimit(data)
                logger.warn(
                    "eth_estimateGas failed; falling back to Ethereum intrinsic gas limit {} — " +
                        "this is likely too low on zkSync Era (bootloader/validation overhead and " +
                        "pubdata costs are charged inside the gas limit) and the transaction may be rejected.",
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
                "Invalid chain ID for zkSync: $chainId"
            }
            val chainIdNum = chainId.substringAfter(":").toIntOrNull()
            require(chainIdNum == 324 || chainIdNum == 300) {
                "Unsupported zkSync chain ID: $chainId. Use 'eip155:324' (mainnet) or 'eip155:300' (Sepolia testnet)"
            }
            return when (chainId) {
                MAINNET -> EvmChainConfig(
                    numericChainId = 324L,
                    defaultRpcUrl = MAINNET_RPC_URL,
                    blockchainName = "zkSync Era",
                    networkName = "zksync-era-mainnet"
                )
                else -> EvmChainConfig(
                    numericChainId = 300L,
                    defaultRpcUrl = SEPOLIA_RPC_URL,
                    blockchainName = "zkSync Era",
                    networkName = "zksync-era-sepolia"
                )
            }
        }
    }

    /**
     * zkSync Era's gas model charges bootloader/validation overhead and pubdata
     * costs inside the gas limit, so the Ethereum intrinsic calldata gas is far
     * too low here. eth_estimateGas is authoritative; add headroom for L2
     * variability and fall back to intrinsic gas only if estimation fails.
     */
    override fun deriveGasLimit(data: ByteArray, from: String): BigInteger =
        gasLimitFrom(tryEstimateGas(data, from), data)
}
