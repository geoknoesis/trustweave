package com.geoknoesis.vericore.anchor.zksync

import com.geoknoesis.vericore.anchor.BlockchainAnchorClient
import com.geoknoesis.vericore.anchor.spi.BlockchainAnchorClientProvider

/**
 * SPI provider for zkSync Era blockchain anchor client.
 * 
 * Automatically discovered via Java ServiceLoader when the module is on the classpath.
 */
class ZkSyncIntegration : BlockchainAnchorClientProvider {
    override val name: String = "zksync"
    
    override val supportedChains: List<String> = listOf(
        ZkSyncBlockchainAnchorClient.MAINNET,
        ZkSyncBlockchainAnchorClient.SEPOLIA
    )

    override fun create(chainId: String, options: Map<String, Any?>): BlockchainAnchorClient? {
        return if (supportedChains.contains(chainId)) {
            ZkSyncBlockchainAnchorClient(chainId, options)
        } else {
            null
        }
    }
}

