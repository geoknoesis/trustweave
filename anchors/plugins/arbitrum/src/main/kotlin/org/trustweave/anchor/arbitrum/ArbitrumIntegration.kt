package org.trustweave.anchor.arbitrum

import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.anchor.BlockchainAnchorRegistry
import org.trustweave.anchor.spi.BlockchainAnchorClientProvider
import org.trustweave.anchor.spi.BlockchainIntegrationHelper

/**
 * SPI provider for Arbitrum blockchain anchor clients.
 * Supports Arbitrum One mainnet and Arbitrum Sepolia testnet chains.
 */
class ArbitrumBlockchainAnchorClientProvider : BlockchainAnchorClientProvider {

    override val name: String = "arbitrum"

    override val supportedChains: List<String> = listOf(
        ArbitrumBlockchainAnchorClient.MAINNET,
        ArbitrumBlockchainAnchorClient.ARBITRUM_SEPOLIA
    )

    override fun create(chainId: String, options: Map<String, Any?>): BlockchainAnchorClient? {
        if (!chainId.startsWith("eip155:")) {
            return null
        }
        val chainIdNum = chainId.substringAfter(":").toIntOrNull()
        if (chainIdNum != 42161 && chainIdNum != 421614) {
            return null
        }
        return ArbitrumBlockchainAnchorClient(chainId, options)
    }
}

/**
 * Integration helper for Arbitrum blockchain adapters.
 * Supports Arbitrum One mainnet and Arbitrum Sepolia testnet chains.
 */
data class ArbitrumIntegrationResult(
    val registry: BlockchainAnchorRegistry,
    val registeredChains: List<String>
)

object ArbitrumIntegration {

    /**
     * Discovers and registers Arbitrum blockchain anchor clients via SPI.
     * Registers all supported chains (mainnet and Arbitrum Sepolia testnet).
     *
     * @param options Configuration options
     * @return List of registered chain IDs
     */
    fun discoverAndRegister(
        options: Map<String, Any?> = emptyMap(),
        registry: BlockchainAnchorRegistry = BlockchainAnchorRegistry()
    ): ArbitrumIntegrationResult {
        val registeredChains = BlockchainIntegrationHelper.discoverAndRegister(
            providerName = "arbitrum",
            registry = registry,
            options = options
        )
        return ArbitrumIntegrationResult(
            registry = registry,
            registeredChains = registeredChains
        )
    }

    /**
     * Manually register Arbitrum clients for specific chains.
     *
     * @param chainIds List of chain IDs to register (defaults to Arbitrum Sepolia testnet for testing)
     * @param options Configuration options
     * @return List of registered chain IDs
     */
    fun setup(
        chainIds: List<String> = listOf(ArbitrumBlockchainAnchorClient.ARBITRUM_SEPOLIA),
        options: Map<String, Any?> = emptyMap(),
        registry: BlockchainAnchorRegistry = BlockchainAnchorRegistry()
    ): ArbitrumIntegrationResult {
        val registeredChains = BlockchainIntegrationHelper.setup(
            providerName = "arbitrum",
            registry = registry,
            chainIds = chainIds,
            defaultChainIds = listOf(ArbitrumBlockchainAnchorClient.ARBITRUM_SEPOLIA),
            options = options
        )
        return ArbitrumIntegrationResult(
            registry = registry,
            registeredChains = registeredChains
        )
    }
}

