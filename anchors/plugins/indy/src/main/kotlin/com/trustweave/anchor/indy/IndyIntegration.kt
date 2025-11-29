package com.trustweave.anchor.indy

import com.trustweave.anchor.BlockchainAnchorClient
import com.trustweave.anchor.BlockchainAnchorRegistry
import com.trustweave.anchor.spi.BlockchainAnchorClientProvider
import com.trustweave.anchor.spi.BlockchainIntegrationHelper

/**
 * SPI provider for Hyperledger Indy blockchain anchor clients.
 * Supports Indy ledger pools (mainnet, testnet, custom pools).
 */
class IndyBlockchainAnchorClientProvider : BlockchainAnchorClientProvider {

    override val name: String = "indy"

    override val supportedChains: List<String> = listOf(
        IndyBlockchainAnchorClient.SOVRIN_MAINNET,
        IndyBlockchainAnchorClient.SOVRIN_STAGING,
        IndyBlockchainAnchorClient.BCOVRIN_TESTNET
    )

    override fun create(chainId: String, options: Map<String, Any?>): BlockchainAnchorClient? {
        if (!chainId.startsWith("indy:")) {
            return null
        }
        return IndyBlockchainAnchorClient(chainId, options)
    }
}

/**
 * Integration helper for Hyperledger Indy blockchain adapters.
 * Supports Indy ledger pools (mainnet, testnet, custom pools).
 */
data class IndyIntegrationResult(
    val registry: BlockchainAnchorRegistry,
    val registeredChains: List<String>
)

object IndyIntegration {

    /**
     * Discovers and registers Indy blockchain anchor clients via SPI.
     * Registers all supported chains.
     *
     * @param options Configuration options (walletName, walletKey, did, poolEndpoint)
     * @return List of registered chain IDs
     */
    fun discoverAndRegister(
        options: Map<String, Any?> = emptyMap(),
        registry: BlockchainAnchorRegistry = BlockchainAnchorRegistry()
    ): IndyIntegrationResult {
        val registeredChains = BlockchainIntegrationHelper.discoverAndRegister(
            providerName = "indy",
            registry = registry,
            options = options
        )
        return IndyIntegrationResult(
            registry = registry,
            registeredChains = registeredChains
        )
    }

    /**
     * Manually register Indy clients for specific chains.
     *
     * @param chainIds List of chain IDs to register (defaults to testnet for testing)
     * @param options Configuration options (walletName, walletKey, did, poolEndpoint)
     * @return List of registered chain IDs
     */
    fun setup(
        chainIds: List<String> = listOf(IndyBlockchainAnchorClient.BCOVRIN_TESTNET),
        options: Map<String, Any?> = emptyMap(),
        registry: BlockchainAnchorRegistry = BlockchainAnchorRegistry()
    ): IndyIntegrationResult {
        val registeredChains = BlockchainIntegrationHelper.setup(
            providerName = "indy",
            registry = registry,
            chainIds = chainIds,
            defaultChainIds = listOf(IndyBlockchainAnchorClient.BCOVRIN_TESTNET),
            options = options,
            allowCustomChains = true // Allow custom Indy pools
        )
        return IndyIntegrationResult(
            registry = registry,
            registeredChains = registeredChains
        )
    }
}

