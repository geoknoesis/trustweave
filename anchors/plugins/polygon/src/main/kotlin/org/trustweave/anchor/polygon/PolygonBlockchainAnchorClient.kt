package org.trustweave.anchor.polygon

import org.trustweave.anchor.evm.AbstractEvmAnchorClient
import org.trustweave.anchor.evm.EvmChainConfig
import org.trustweave.anchor.options.PolygonOptions

/**
 * Polygon blockchain anchor client implementation.
 *
 * Supports Polygon mainnet and Amoy testnet chains.
 * Uses Ethereum-compatible transaction data fields to store payload data.
 * All chain mechanics (signing, nonce, gas, confirmation, reads) live in
 * [AbstractEvmAnchorClient].
 *
 * **Type-Safe Options**: Use [PolygonOptions] for type-safe configuration:
 * ```
 * val options = PolygonOptions(
 *     rpcUrl = "https://rpc-amoy.polygon.technology",
 *     privateKey = "0x..."
 * )
 * val client = PolygonBlockchainAnchorClient(chainId, options)
 * ```
 */
class PolygonBlockchainAnchorClient(
    chainId: String,
    options: Map<String, Any?> = emptyMap()
) : AbstractEvmAnchorClient(chainId, options, resolveChain(chainId)) {

    /**
     * Convenience constructor using type-safe [PolygonOptions].
     */
    constructor(chainId: String, options: PolygonOptions) : this(chainId, options.toMap())

    companion object {
        const val MAINNET = "eip155:137"  // Polygon mainnet
        const val AMOY = "eip155:80002"   // Amoy testnet (Mumbai's 80001 was decommissioned in 2024)

        // Network RPC endpoints
        private const val MAINNET_RPC_URL = "https://polygon-rpc.com"
        private const val AMOY_RPC_URL = "https://rpc-amoy.polygon.technology"

        private fun resolveChain(chainId: String): EvmChainConfig {
            require(chainId.startsWith("eip155:")) {
                "Invalid chain ID for Polygon/Ethereum: $chainId"
            }
            val chainIdNum = chainId.substringAfter(":").toIntOrNull()
            require(chainIdNum == 137 || chainIdNum == 80002) {
                "Unsupported Polygon chain ID: $chainId. Use 'eip155:137' (mainnet) or 'eip155:80002' (Amoy testnet)"
            }
            return when (chainId) {
                MAINNET -> EvmChainConfig(
                    numericChainId = 137L,
                    defaultRpcUrl = MAINNET_RPC_URL,
                    blockchainName = "Polygon",
                    networkName = "polygon-mainnet"
                )
                else -> EvmChainConfig(
                    numericChainId = 80002L,
                    defaultRpcUrl = AMOY_RPC_URL,
                    blockchainName = "Polygon",
                    networkName = "amoy-testnet"
                )
            }
        }
    }
}
