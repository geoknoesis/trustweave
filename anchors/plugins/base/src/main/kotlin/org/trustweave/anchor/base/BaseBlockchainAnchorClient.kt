package org.trustweave.anchor.base

import org.trustweave.anchor.evm.AbstractEvmAnchorClient
import org.trustweave.anchor.evm.EvmChainConfig

/**
 * Base (Coinbase L2) blockchain anchor client implementation.
 *
 * Supports Base mainnet and Base Sepolia testnet chains.
 * Uses Ethereum-compatible transaction data fields to store payload data.
 * All chain mechanics (signing, nonce, gas, confirmation, reads) live in
 * [AbstractEvmAnchorClient].
 *
 * **Example Usage:**
 * ```kotlin
 * val options = mapOf(
 *     "rpcUrl" to "https://mainnet.base.org",
 *     "privateKey" to "0x..."
 * )
 * val client = BaseBlockchainAnchorClient(BaseBlockchainAnchorClient.MAINNET, options)
 * ```
 */
class BaseBlockchainAnchorClient(
    chainId: String,
    options: Map<String, Any?> = emptyMap()
) : AbstractEvmAnchorClient(chainId, options, resolveChain(chainId)) {

    companion object {
        const val MAINNET = "eip155:8453"  // Base mainnet
        const val BASE_SEPOLIA = "eip155:84532" // Base Sepolia testnet

        // Network RPC endpoints
        private const val MAINNET_RPC_URL = "https://mainnet.base.org"
        private const val BASE_SEPOLIA_RPC_URL = "https://sepolia.base.org"

        private fun resolveChain(chainId: String): EvmChainConfig {
            require(chainId.startsWith("eip155:")) {
                "Invalid chain ID for Base: $chainId"
            }
            val chainIdNum = chainId.substringAfter(":").toIntOrNull()
            require(chainIdNum == 8453 || chainIdNum == 84532) {
                "Unsupported Base chain ID: $chainId. Use 'eip155:8453' (mainnet) or 'eip155:84532' (Base Sepolia testnet)"
            }
            return when (chainId) {
                MAINNET -> EvmChainConfig(
                    numericChainId = 8453L,
                    defaultRpcUrl = MAINNET_RPC_URL,
                    blockchainName = "Base",
                    networkName = "base-mainnet"
                )
                else -> EvmChainConfig(
                    numericChainId = 84532L,
                    defaultRpcUrl = BASE_SEPOLIA_RPC_URL,
                    blockchainName = "Base",
                    networkName = "base-sepolia-testnet"
                )
            }
        }
    }
}
