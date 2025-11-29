package com.trustweave.anchor.bitcoin

import com.trustweave.anchor.BlockchainAnchorClient
import com.trustweave.anchor.spi.BlockchainAnchorClientProvider

/**
 * SPI provider for Bitcoin blockchain anchor client.
 *
 * Automatically discovered via Java ServiceLoader when the module is on the classpath.
 */
class BitcoinIntegration : BlockchainAnchorClientProvider {
    override val name: String = "bitcoin"

    override val supportedChains: List<String> = listOf(
        BitcoinBlockchainAnchorClient.MAINNET,
        BitcoinBlockchainAnchorClient.TESTNET
    )

    override fun create(chainId: String, options: Map<String, Any?>): BlockchainAnchorClient? {
        return if (supportedChains.contains(chainId) || chainId.startsWith("bip122:")) {
            BitcoinBlockchainAnchorClient(chainId, options)
        } else {
            null
        }
    }
}

