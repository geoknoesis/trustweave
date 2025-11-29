package com.trustweave.anchor.spi

import com.trustweave.anchor.BlockchainAnchorClient
import com.trustweave.anchor.BlockchainAnchorRegistry
import java.util.ServiceLoader

/**
 * Helper utility for blockchain integration classes.
 *
 * Provides common functionality for discovering and registering blockchain adapters via SPI.
 * Reduces duplication across integration classes like `AlgorandIntegration`, `PolygonIntegration`, etc.
 *
 * **Example Usage**:
 * ```
 * // Discover and register all supported chains
 * val chains = BlockchainIntegrationHelper.discoverAndRegister("algorand", options)
 *
 * // Register specific chains
 * val chains = BlockchainIntegrationHelper.setup(
 *     providerName = "polygon",
 *     chainIds = listOf("eip155:137"),
 *     defaultChainIds = listOf("eip155:80001"),
 *     options = options
 * )
 * ```
 *
 * **Thread Safety**: This utility is thread-safe for concurrent access.
 */
object BlockchainIntegrationHelper {

    /**
     * Discovers and registers blockchain anchor clients via SPI.
     * Registers all supported chains for the given provider name.
     *
     * @param providerName The name of the provider (e.g., "algorand", "polygon", "indy")
     * @param options Configuration options
     * @return List of registered chain IDs
     * @throws IllegalStateException if provider is not found
     */
    fun discoverAndRegister(
        providerName: String,
        registry: BlockchainAnchorRegistry,
        options: Map<String, Any?> = emptyMap()
    ): List<String> {
        val providers = ServiceLoader.load(BlockchainAnchorClientProvider::class.java)
        val provider = providers.find { it.name == providerName }
            ?: throw IllegalStateException(
                "$providerName blockchain provider not found. " +
                "Ensure TrustWeave-$providerName is on classpath."
            )

        val registeredChains = mutableListOf<String>()
        for (chainId in provider.supportedChains) {
            val client = provider.create(chainId, options)
            if (client != null) {
                registry.register(chainId, client)
                registeredChains.add(chainId)
            }
        }

        return registeredChains
    }

    /**
     * Manually register blockchain clients for specific chains.
     *
     * @param providerName The name of the provider
     * @param chainIds List of chain IDs to register
     * @param defaultChainIds Default chain IDs if chainIds is empty
     * @param options Configuration options
     * @param allowCustomChains If true, allows chain IDs not in supportedChains (e.g., custom Indy pools)
     * @return List of registered chain IDs
     * @throws IllegalStateException if provider is not found
     */
    fun setup(
        providerName: String,
        registry: BlockchainAnchorRegistry,
        chainIds: List<String>,
        defaultChainIds: List<String>,
        options: Map<String, Any?> = emptyMap(),
        allowCustomChains: Boolean = false
    ): List<String> {
        val providers = ServiceLoader.load(BlockchainAnchorClientProvider::class.java)
        val provider = providers.find { it.name == providerName }
            ?: throw IllegalStateException(
                "$providerName blockchain provider not found. " +
                "Ensure TrustWeave-$providerName is on classpath."
            )

        val chainsToRegister = if (chainIds.isEmpty()) defaultChainIds else chainIds
        val registeredChains = mutableListOf<String>()

        for (chainId in chainsToRegister) {
            val isSupported = chainId in provider.supportedChains
            val shouldRegister = isSupported || (allowCustomChains && chainId.startsWith("$providerName:"))

            if (shouldRegister) {
                val client = provider.create(chainId, options)
                if (client != null) {
                    registry.register(chainId, client)
                    registeredChains.add(chainId)
                }
            }
        }

        return registeredChains
    }
}

