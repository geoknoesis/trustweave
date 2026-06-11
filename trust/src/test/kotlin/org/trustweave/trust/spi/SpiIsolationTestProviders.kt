package org.trustweave.trust.spi

import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.anchor.spi.BlockchainAnchorClientProvider
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.spi.DidMethodProvider
import org.trustweave.kms.KeyManagementService
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import org.trustweave.testkit.did.DidKeyMockMethod
import org.trustweave.testkit.kms.InMemoryKeyManagementService

/**
 * Synthetic SPI providers (registered via `src/test/resources/META-INF/services`) used by
 * [org.trustweave.trust.TrustWeaveFactorySpiIsolationTest] to prove that one broken
 * provider does not abort `TrustWeaveFactory`'s SPI resolution.
 *
 * The broken providers are listed FIRST in the services files so the factory encounters
 * them before the working ones. They only claim the dedicated `spi-isolation-test` method /
 * `spi-isolation:test` chain, so the rest of the test suite is unaffected (the DID
 * auto-register pass records a create-failure for the broken provider and moves on).
 */
internal const val SPI_ISOLATION_DID_METHOD = "spi-isolation-test"
internal const val SPI_ISOLATION_CHAIN_ID = "spi-isolation:test"
internal const val SPI_ISOLATION_ANCHOR_PROVIDER = "spi-isolation"

/** DID method provider whose [create] always throws. Must be skipped, not fatal. */
class BrokenSpiIsolationDidMethodProvider : DidMethodProvider {
    override val name: String = "spi-isolation-broken"
    override val supportedMethods: List<String> = listOf(SPI_ISOLATION_DID_METHOD)

    override fun create(methodName: String, options: DidCreationOptions): DidMethod {
        throw RuntimeException("Synthetic broken DidMethodProvider (SPI isolation test)")
    }
}

/** Working DID method provider listed after the broken one. */
class WorkingSpiIsolationDidMethodProvider : DidMethodProvider {
    override val name: String = "spi-isolation-working"
    override val supportedMethods: List<String> = listOf(SPI_ISOLATION_DID_METHOD)

    override fun create(methodName: String, options: DidCreationOptions): DidMethod? {
        if (methodName != SPI_ISOLATION_DID_METHOD) return null
        val kms = options.additionalProperties["kms"] as? KeyManagementService
            ?: InMemoryKeyManagementService()
        return SpiIsolationDidMethod(kms)
    }
}

/** Minimal working DID method: a key mock that reports the isolation-test method name. */
class SpiIsolationDidMethod(kms: KeyManagementService) : DidMethod by DidKeyMockMethod(kms) {
    override val method: String = SPI_ISOLATION_DID_METHOD
}

/** Anchor client provider whose [create] always throws. Must be skipped, not fatal. */
class BrokenSpiIsolationAnchorClientProvider : BlockchainAnchorClientProvider {
    override val name: String = SPI_ISOLATION_ANCHOR_PROVIDER
    override val supportedChains: List<String> = listOf(SPI_ISOLATION_CHAIN_ID)

    override fun create(chainId: String, options: Map<String, Any?>): BlockchainAnchorClient {
        throw RuntimeException("Synthetic broken BlockchainAnchorClientProvider (SPI isolation test)")
    }
}

/** Working anchor client provider listed after the broken one. */
class WorkingSpiIsolationAnchorClientProvider : BlockchainAnchorClientProvider {
    override val name: String = SPI_ISOLATION_ANCHOR_PROVIDER
    override val supportedChains: List<String> = listOf(SPI_ISOLATION_CHAIN_ID)

    override fun create(chainId: String, options: Map<String, Any?>): BlockchainAnchorClient? {
        if (chainId != SPI_ISOLATION_CHAIN_ID) return null
        return InMemoryBlockchainAnchorClient(chainId)
    }
}
