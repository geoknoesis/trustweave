package org.trustweave.anchor.polygon

import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.anchor.BlockchainAnchorRegistry
import org.trustweave.anchor.payment.PaymentDeprecation
import org.trustweave.anchor.spi.BlockchainAnchorClientProvider
import org.trustweave.anchor.spi.BlockchainIntegrationHelper

/**
 * SPI provider for Polygon blockchain anchor clients.
 * Supports Polygon mainnet and Amoy testnet chains.
 */
class PolygonBlockchainAnchorClientProvider : BlockchainAnchorClientProvider {

    override val name: String = "polygon"

    override val supportedChains: List<String> = listOf(
        PolygonBlockchainAnchorClient.MAINNET,
        PolygonBlockchainAnchorClient.AMOY
    )

    override fun create(chainId: String, options: Map<String, Any?>): BlockchainAnchorClient? {
        if (!chainId.startsWith("eip155:")) {
            return null
        }
        val chainIdNum = chainId.substringAfter(":").toIntOrNull()
        if (chainIdNum != 137 && chainIdNum != 80002) {
            return null
        }
        PaymentDeprecation.warnIfRawCreds(chainId, options, this)
        return PolygonBlockchainAnchorClient(chainId, options)
    }
}

/**
 * Integration helper for Polygon blockchain adapters.
 * Supports Polygon mainnet and Amoy testnet chains.
 */
data class PolygonIntegrationResult(
    val registry: BlockchainAnchorRegistry,
    val registeredChains: List<String>
)

object PolygonIntegration {

    /**
     * Discovers and registers Polygon blockchain anchor clients via SPI.
     * Registers all supported chains (mainnet and Amoy testnet).
     *
     * @param options Configuration options
     * @return List of registered chain IDs
     */
    fun discoverAndRegister(
        options: Map<String, Any?> = emptyMap(),
        registry: BlockchainAnchorRegistry = BlockchainAnchorRegistry()
    ): PolygonIntegrationResult {
        val registeredChains = BlockchainIntegrationHelper.discoverAndRegister(
            providerName = "polygon",
            registry = registry,
            options = options
        )
        return PolygonIntegrationResult(
            registry = registry,
            registeredChains = registeredChains
        )
    }

    /**
     * Manually register Polygon clients for specific chains.
     *
     * @param chainIds List of chain IDs to register (defaults to Amoy testnet for testing)
     * @param options Configuration options
     * @return List of registered chain IDs
     */
    fun setup(
        chainIds: List<String> = listOf(PolygonBlockchainAnchorClient.AMOY),
        options: Map<String, Any?> = emptyMap(),
        registry: BlockchainAnchorRegistry = BlockchainAnchorRegistry()
    ): PolygonIntegrationResult {
        val registeredChains = BlockchainIntegrationHelper.setup(
            providerName = "polygon",
            registry = registry,
            chainIds = chainIds,
            defaultChainIds = listOf(PolygonBlockchainAnchorClient.AMOY),
            options = options
        )
        return PolygonIntegrationResult(
            registry = registry,
            registeredChains = registeredChains
        )
    }
}

