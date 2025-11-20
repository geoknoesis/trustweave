package com.geoknoesis.vericore.anchor.cardano

import com.geoknoesis.vericore.anchor.BlockchainAnchorClient
import com.geoknoesis.vericore.anchor.spi.BlockchainAnchorClientProvider

/**
 * SPI provider for Cardano blockchain anchor client.
 * 
 * Automatically discovered via Java ServiceLoader when the module is on the classpath.
 */
class CardanoIntegration : BlockchainAnchorClientProvider {
    override val name: String = "cardano"
    
    override val supportedChains: List<String> = listOf(
        CardanoBlockchainAnchorClient.MAINNET,
        CardanoBlockchainAnchorClient.TESTNET
    )

    override fun create(chainId: String, options: Map<String, Any?>): BlockchainAnchorClient? {
        return if (supportedChains.contains(chainId) || chainId.startsWith("cardano:")) {
            CardanoBlockchainAnchorClient(chainId, options)
        } else {
            null
        }
    }
}

