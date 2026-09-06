package org.trustweave.anchor.zksync

import org.junit.jupiter.api.Test
import org.trustweave.anchor.spi.BlockchainAnchorClientProvider
import java.util.ServiceLoader
import kotlin.test.assertTrue

class ProviderDiscoveryTest {
    @Test
    fun `packaged provider is discoverable`() {
        val providers = ServiceLoader.load(BlockchainAnchorClientProvider::class.java).toList()
        assertTrue(providers.any { it is ZkSyncIntegration })
    }
}
