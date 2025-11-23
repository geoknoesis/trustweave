package com.trustweave.anchor.ethereum

import com.trustweave.anchor.BlockchainAnchorClient
import com.trustweave.anchor.BlockchainAnchorRegistry
import com.trustweave.anchor.spi.BlockchainAnchorClientProvider
import com.trustweave.anchor.spi.BlockchainIntegrationHelper

/**
 * SPI provider for Ethereum blockchain anchor clients.
 * Supports Ethereum mainnet and Sepolia testnet chains.
 */
class EthereumBlockchainAnchorClientProvider : BlockchainAnchorClientProvider {

    override val name: String = "ethereum"
    
    override val supportedChains: List<String> = listOf(
        EthereumBlockchainAnchorClient.MAINNET,
        EthereumBlockchainAnchorClient.SEPOLIA
    )

    override fun create(chainId: String, options: Map<String, Any?>): BlockchainAnchorClient? {
        if (!chainId.startsWith("eip155:")) {
            return null
        }
        val chainIdNum = chainId.substringAfter(":").toIntOrNull()
        if (chainIdNum != 1 && chainIdNum != 11155111) {
            return null
        }
        return EthereumBlockchainAnchorClient(chainId, options)
    }
}

/**
 * Integration helper for Ethereum blockchain adapters.
 * Supports Ethereum mainnet and Sepolia testnet chains.
 */
data class EthereumIntegrationResult(
    val registry: BlockchainAnchorRegistry,
    val registeredChains: List<String>
)

object EthereumIntegration {

    /**
     * Discovers and registers Ethereum blockchain anchor clients via SPI.
     * Registers all supported chains (mainnet and Sepolia testnet).
     *
     * @param options Configuration options
     * @return List of registered chain IDs
     */
    fun discoverAndRegister(
        options: Map<String, Any?> = emptyMap(),
        registry: BlockchainAnchorRegistry = BlockchainAnchorRegistry()
    ): EthereumIntegrationResult {
        val registeredChains = BlockchainIntegrationHelper.discoverAndRegister(
            providerName = "ethereum",
            registry = registry,
            options = options
        )
        return EthereumIntegrationResult(
            registry = registry,
            registeredChains = registeredChains
        )
    }

    /**
     * Manually register Ethereum clients for specific chains.
     *
     * @param chainIds List of chain IDs to register (defaults to Sepolia testnet for testing)
     * @param options Configuration options
     * @return List of registered chain IDs
     */
    fun setup(
        chainIds: List<String> = listOf(EthereumBlockchainAnchorClient.SEPOLIA),
        options: Map<String, Any?> = emptyMap(),
        registry: BlockchainAnchorRegistry = BlockchainAnchorRegistry()
    ): EthereumIntegrationResult {
        val registeredChains = BlockchainIntegrationHelper.setup(
            providerName = "ethereum",
            registry = registry,
            chainIds = chainIds,
            defaultChainIds = listOf(EthereumBlockchainAnchorClient.SEPOLIA),
            options = options
        )
        return EthereumIntegrationResult(
            registry = registry,
            registeredChains = registeredChains
        )
    }
}

