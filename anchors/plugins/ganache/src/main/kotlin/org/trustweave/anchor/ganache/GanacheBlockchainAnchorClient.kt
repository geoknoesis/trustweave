package org.trustweave.anchor.ganache

import org.trustweave.anchor.evm.AbstractEvmAnchorClient
import org.trustweave.anchor.evm.EvmChainConfig
import org.trustweave.anchor.options.GanacheOptions

/**
 * Ganache (local Ethereum) blockchain anchor client implementation.
 *
 * Supports local Ethereum networks via Ganache.
 * Uses Ethereum transaction data fields to store payload data.
 * All chain mechanics (signing, nonce, gas, confirmation, reads) live in
 * [AbstractEvmAnchorClient]; unlike public networks, a `privateKey` is
 * always required.
 *
 * For testing, use a fixed mnemonic to get deterministic accounts:
 * ganache --port 8545 --mnemonic "test test test test test test test test test test test junk"
 *
 * **Resource Management**: This client implements `Closeable` to properly clean up
 * Web3j resources. Always use `use {}` or call `close()` when done:
 *
 * ```
 * GanacheBlockchainAnchorClient(chainId, options).use { client ->
 *     // Use client
 * }
 * ```
 *
 * **Type-Safe Options**: Use [GanacheOptions] for type-safe configuration:
 * ```
 * val options = GanacheOptions(
 *     rpcUrl = "http://localhost:8545",
 *     privateKey = "0x..."
 * )
 * val client = GanacheBlockchainAnchorClient(chainId, options)
 * ```
 */
class GanacheBlockchainAnchorClient(
    chainId: String,
    options: Map<String, Any?> = emptyMap()
) : AbstractEvmAnchorClient(chainId, options, resolveChain(chainId)) {

    /**
     * Convenience constructor using type-safe [GanacheOptions].
     */
    constructor(chainId: String, options: GanacheOptions) : this(chainId, options.toMap())

    companion object {
        const val LOCAL = "eip155:1337"  // Ganache default chain ID

        // Default Ganache RPC endpoint
        private const val DEFAULT_RPC_URL = "http://localhost:8545"

        private fun resolveChain(chainId: String): EvmChainConfig {
            require(chainId.startsWith("eip155:")) {
                "Invalid chain ID for Ethereum/Ganache: $chainId"
            }
            val chainIdNum = chainId.substringAfter(":").toLongOrNull() ?: 1337L
            return EvmChainConfig(
                numericChainId = chainIdNum,
                defaultRpcUrl = DEFAULT_RPC_URL,
                blockchainName = "Ganache",
                networkName = "ganache-local",
                credentialsRequired = true
            )
        }
    }

    override fun generateTestTxHash(): String {
        return "ganache_test_${uniqueTestHashSuffix()}"
    }
}
