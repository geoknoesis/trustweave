package org.trustweave.anchor.optimism

import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.anchor.payment.PaymentDeprecation
import org.trustweave.anchor.spi.BlockchainAnchorClientProvider

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
            PaymentDeprecation.warnIfRawCreds(chainId, options, this)
            OptimismBlockchainAnchorClient(chainId, options)
        } else {
            null
        }
    }
}

