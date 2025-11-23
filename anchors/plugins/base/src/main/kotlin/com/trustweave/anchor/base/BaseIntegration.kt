package com.trustweave.anchor.base

import com.trustweave.anchor.BlockchainAnchorClient
import com.trustweave.anchor.BlockchainAnchorRegistry
import com.trustweave.anchor.spi.BlockchainAnchorClientProvider
import com.trustweave.anchor.spi.BlockchainIntegrationHelper

/**
 * SPI provider for Base blockchain anchor clients.
 * Supports Base mainnet and Base Sepolia testnet chains.
 */
class BaseBlockchainAnchorClientProvider : BlockchainAnchorClientProvider {

    override val name: String = "base"
    
    override val supportedChains: List<String> = listOf(
        BaseBlockchainAnchorClient.MAINNET,
        BaseBlockchainAnchorClient.BASE_SEPOLIA
    )

    override fun create(chainId: String, options: Map<String, Any?>): BlockchainAnchorClient? {
        if (!chainId.startsWith("eip155:")) {
            return null
        }
        val chainIdNum = chainId.substringAfter(":").toIntOrNull()
        if (chainIdNum != 8453 && chainIdNum != 84532) {
            return null
        }
        return BaseBlockchainAnchorClient(chainId, options)
    }
}

/**
 * Integration helper for Base blockchain adapters.
 * Supports Base mainnet and Base Sepolia testnet chains.
 */
data class BaseIntegrationResult(
    val registry: BlockchainAnchorRegistry,
    val registeredChains: List<String>
)

object BaseIntegration {

    /**
     * Discovers and registers Base blockchain anchor clients via SPI.
     * Registers all supported chains (mainnet and Base Sepolia testnet).
     *
     * @param options Configuration options
     * @return List of registered chain IDs
     */
    fun discoverAndRegister(
        options: Map<String, Any?> = emptyMap(),
        registry: BlockchainAnchorRegistry = BlockchainAnchorRegistry()
    ): BaseIntegrationResult {
        val registeredChains = BlockchainIntegrationHelper.discoverAndRegister(
            providerName = "base",
            registry = registry,
            options = options
        )
        return BaseIntegrationResult(
            registry = registry,
            registeredChains = registeredChains
        )
    }

    /**
     * Manually register Base clients for specific chains.
     *
     * @param chainIds List of chain IDs to register (defaults to Base Sepolia testnet for testing)
     * @param options Configuration options
     * @return List of registered chain IDs
     */
    fun setup(
        chainIds: List<String> = listOf(BaseBlockchainAnchorClient.BASE_SEPOLIA),
        options: Map<String, Any?> = emptyMap(),
        registry: BlockchainAnchorRegistry = BlockchainAnchorRegistry()
    ): BaseIntegrationResult {
        val registeredChains = BlockchainIntegrationHelper.setup(
            providerName = "base",
            registry = registry,
            chainIds = chainIds,
            defaultChainIds = listOf(BaseBlockchainAnchorClient.BASE_SEPOLIA),
            options = options
        )
        return BaseIntegrationResult(
            registry = registry,
            registeredChains = registeredChains
        )
    }
}

