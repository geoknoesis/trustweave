package io.geoknoesis.vericore.polygon

import io.geoknoesis.vericore.anchor.BlockchainAnchorClient
import io.geoknoesis.vericore.anchor.spi.BlockchainAnchorClientProvider
import io.geoknoesis.vericore.anchor.spi.BlockchainIntegrationHelper

/**
 * SPI provider for Polygon blockchain anchor clients.
 * Supports Polygon mainnet and Mumbai testnet chains.
 */
class PolygonBlockchainAnchorClientProvider : BlockchainAnchorClientProvider {

    override val name: String = "polygon"
    
    override val supportedChains: List<String> = listOf(
        PolygonBlockchainAnchorClient.MAINNET,
        PolygonBlockchainAnchorClient.MUMBAI
    )

    override fun create(chainId: String, options: Map<String, Any?>): BlockchainAnchorClient? {
        if (!chainId.startsWith("eip155:")) {
            return null
        }
        val chainIdNum = chainId.substringAfter(":").toIntOrNull()
        if (chainIdNum != 137 && chainIdNum != 80001) {
            return null
        }
        return PolygonBlockchainAnchorClient(chainId, options)
    }
}

/**
 * Integration helper for Polygon blockchain adapters.
 * Supports Polygon mainnet and Mumbai testnet chains.
 */
object PolygonIntegration {

    /**
     * Discovers and registers Polygon blockchain anchor clients via SPI.
     * Registers all supported chains (mainnet and Mumbai testnet).
     *
     * @param options Configuration options
     * @return List of registered chain IDs
     */
    fun discoverAndRegister(options: Map<String, Any?> = emptyMap()): List<String> {
        return BlockchainIntegrationHelper.discoverAndRegister("polygon", options)
    }

    /**
     * Manually register Polygon clients for specific chains.
     *
     * @param chainIds List of chain IDs to register (defaults to Mumbai testnet for testing)
     * @param options Configuration options
     * @return List of registered chain IDs
     */
    fun setup(
        chainIds: List<String> = listOf(PolygonBlockchainAnchorClient.MUMBAI),
        options: Map<String, Any?> = emptyMap()
    ): List<String> {
        return BlockchainIntegrationHelper.setup(
            providerName = "polygon",
            chainIds = chainIds,
            defaultChainIds = listOf(PolygonBlockchainAnchorClient.MUMBAI),
            options = options
        )
    }
}

