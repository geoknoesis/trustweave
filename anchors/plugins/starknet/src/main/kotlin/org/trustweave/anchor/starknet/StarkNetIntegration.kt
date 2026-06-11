package org.trustweave.anchor.starknet

import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.anchor.payment.PaymentDeprecation
import org.trustweave.anchor.spi.BlockchainAnchorClientProvider

/**
 * SPI provider for the StarkNet blockchain anchor client.
 *
 * **STUB — NOT IMPLEMENTED.** [StarkNetBlockchainAnchorClient] is a non-functional
 * skeleton (it never submits or reads chain transactions), so this provider is
 * intentionally NOT registered in `META-INF/services` and is never picked up by
 * ServiceLoader discovery. It exists only for explicit, opt-in construction.
 */
class StarkNetIntegration : BlockchainAnchorClientProvider {
    override val name: String = "starknet"

    override val supportedChains: List<String> = listOf(
        StarkNetBlockchainAnchorClient.MAINNET,
        StarkNetBlockchainAnchorClient.TESTNET
    )

    override fun create(chainId: String, options: Map<String, Any?>): BlockchainAnchorClient? {
        return if (supportedChains.contains(chainId) || chainId.startsWith("starknet:")) {
            PaymentDeprecation.warnIfRawCreds(chainId, options, this)
            StarkNetBlockchainAnchorClient(chainId, options)
        } else {
            null
        }
    }
}

