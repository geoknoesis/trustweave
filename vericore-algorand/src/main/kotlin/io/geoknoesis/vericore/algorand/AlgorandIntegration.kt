package io.geoknoesis.vericore.algorand

import io.geoknoesis.vericore.anchor.BlockchainAnchorClient
import io.geoknoesis.vericore.anchor.spi.BlockchainAnchorClientProvider
import io.geoknoesis.vericore.anchor.spi.BlockchainIntegrationHelper

/**
 * SPI provider for Algorand blockchain anchor clients.
 * Supports mainnet, testnet, and betanet chains.
 */
class AlgorandBlockchainAnchorClientProvider : BlockchainAnchorClientProvider {

    override val name: String = "algorand"
    
    override val supportedChains: List<String> = listOf(
        AlgorandBlockchainAnchorClient.MAINNET,
        AlgorandBlockchainAnchorClient.TESTNET,
        AlgorandBlockchainAnchorClient.BETANET
    )

    override fun create(chainId: String, options: Map<String, Any?>): BlockchainAnchorClient? {
        if (!chainId.startsWith("algorand:")) {
            return null
        }
        return AlgorandBlockchainAnchorClient(chainId, options)
    }
}

/**
 * Integration helper for Algorand blockchain adapters.
 * Supports mainnet, testnet, and betanet chains.
 */
object AlgorandIntegration {

    /**
     * Discovers and registers Algorand blockchain anchor clients via SPI.
     * Registers all supported chains (mainnet, testnet, betanet).
     *
     * @param options Configuration options
     * @return List of registered chain IDs
     */
    fun discoverAndRegister(options: Map<String, Any?> = emptyMap()): List<String> {
        return BlockchainIntegrationHelper.discoverAndRegister("algorand", options)
    }

    /**
     * Manually register Algorand clients for specific chains.
     *
     * @param chainIds List of chain IDs to register (defaults to testnet for testing)
     * @param options Configuration options
     * @return List of registered chain IDs
     */
    fun setup(
        chainIds: List<String> = listOf(AlgorandBlockchainAnchorClient.TESTNET),
        options: Map<String, Any?> = emptyMap()
    ): List<String> {
        return BlockchainIntegrationHelper.setup(
            providerName = "algorand",
            chainIds = chainIds,
            defaultChainIds = listOf(AlgorandBlockchainAnchorClient.TESTNET),
            options = options
        )
    }
}

