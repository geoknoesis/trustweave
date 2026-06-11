package org.trustweave.anchor.optimism

import org.trustweave.anchor.evm.AbstractEvmAnchorClient
import org.trustweave.anchor.evm.EvmChainConfig

/**
 * Optimism blockchain anchor client implementation.
 *
 * Supports Optimism mainnet and Sepolia testnet chains.
 * Uses Ethereum-compatible transaction data fields to store payload data.
 * All chain mechanics (signing, nonce, gas, confirmation, reads) live in
 * [AbstractEvmAnchorClient].
 *
 * Chain ID format: "eip155:<chain-id>"
 * Examples:
 * - "eip155:10" (Optimism mainnet)
 * - "eip155:11155420" (Optimism Sepolia testnet)
 *
 * **Example:**
 * ```kotlin
 * val client = OptimismBlockchainAnchorClient(
 *     chainId = "eip155:10",
 *     options = mapOf(
 *         "rpcUrl" to "https://mainnet.optimism.io",
 *         "privateKey" to "0x..."
 *     )
 * )
 * ```
 */
class OptimismBlockchainAnchorClient(
    chainId: String,
    options: Map<String, Any?> = emptyMap()
) : AbstractEvmAnchorClient(chainId, options, resolveChain(chainId)) {

    /**
     * Resolves the registry contract recorded on anchor refs.
     *
     * **Legacy option key**: historical releases of this plugin configured the
     * contract via `options["contract"]`, not the `contractAddress` key the shared
     * [AbstractEvmAnchorClient] reads. This override keeps existing configurations
     * working: the legacy `contract` key is honored first, falling back to the
     * base behavior (`contractAddress`) when absent.
     */
    override fun getContractAddress(): String? =
        options["contract"] as? String ?: super.getContractAddress()

    companion object {
        const val MAINNET = "eip155:10"  // Optimism mainnet
        const val SEPOLIA = "eip155:11155420" // Optimism Sepolia testnet

        // Network RPC endpoints
        private const val MAINNET_RPC_URL = "https://mainnet.optimism.io"
        private const val SEPOLIA_RPC_URL = "https://sepolia.optimism.io"

        private fun resolveChain(chainId: String): EvmChainConfig {
            require(chainId.startsWith("eip155:")) {
                "Invalid chain ID for Optimism: $chainId"
            }
            val chainIdNum = chainId.substringAfter(":").toIntOrNull()
            require(chainIdNum == 10 || chainIdNum == 11155420) {
                "Unsupported Optimism chain ID: $chainId. Use 'eip155:10' (mainnet) or 'eip155:11155420' (Sepolia testnet)"
            }
            return when (chainId) {
                MAINNET -> EvmChainConfig(
                    numericChainId = 10L,
                    defaultRpcUrl = MAINNET_RPC_URL,
                    blockchainName = "Optimism",
                    networkName = "optimism-mainnet"
                )
                else -> EvmChainConfig(
                    numericChainId = 11155420L,
                    defaultRpcUrl = SEPOLIA_RPC_URL,
                    blockchainName = "Optimism",
                    networkName = "optimism-sepolia"
                )
            }
        }
    }
}
