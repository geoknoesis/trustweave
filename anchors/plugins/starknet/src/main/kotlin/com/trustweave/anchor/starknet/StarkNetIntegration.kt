package com.trustweave.anchor.starknet

import com.trustweave.anchor.BlockchainAnchorClient
import com.trustweave.anchor.spi.BlockchainAnchorClientProvider

/**
 * SPI provider for StarkNet blockchain anchor client.
 *
 * Automatically discovered via Java ServiceLoader when the module is on the classpath.
 */
class StarkNetIntegration : BlockchainAnchorClientProvider {
    override val name: String = "starknet"

    override val supportedChains: List<String> = listOf(
        StarkNetBlockchainAnchorClient.MAINNET,
        StarkNetBlockchainAnchorClient.TESTNET
    )

    override fun create(chainId: String, options: Map<String, Any?>): BlockchainAnchorClient? {
        return if (supportedChains.contains(chainId) || chainId.startsWith("starknet:")) {
            StarkNetBlockchainAnchorClient(chainId, options)
        } else {
            null
        }
    }
}

