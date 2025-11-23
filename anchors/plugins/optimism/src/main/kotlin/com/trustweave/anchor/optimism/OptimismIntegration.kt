package com.trustweave.anchor.optimism

import com.trustweave.anchor.BlockchainAnchorClient
import com.trustweave.anchor.spi.BlockchainAnchorClientProvider

/**
 * SPI provider for Optimism blockchain anchor client.
 * 
 * Automatically discovered via Java ServiceLoader when the module is on the classpath.
 */
class OptimismIntegration : BlockchainAnchorClientProvider {
    override val name: String = "optimism"
    
    override val supportedChains: List<String> = listOf(
        OptimismBlockchainAnchorClient.MAINNET,
        OptimismBlockchainAnchorClient.SEPOLIA
    )

    override fun create(chainId: String, options: Map<String, Any?>): BlockchainAnchorClient? {
        return if (supportedChains.contains(chainId)) {
            OptimismBlockchainAnchorClient(chainId, options)
        } else {
            null
        }
    }
}

