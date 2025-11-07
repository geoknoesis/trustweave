package io.geoknoesis.vericore.indy

import io.geoknoesis.vericore.anchor.spi.BlockchainAnchorClientProvider
import org.junit.jupiter.api.Test
import java.util.ServiceLoader
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests for SPI (Service Provider Interface) discovery of Indy adapter.
 */
class SpiDiscoveryTest {

    @Test
    fun `SPI should discover Indy provider`() {
        val providers = ServiceLoader.load(BlockchainAnchorClientProvider::class.java)
        val indyProvider = providers.find { it.name == "indy" }

        assertNotNull(indyProvider, "Indy blockchain provider should be discoverable via SPI")
        assertEquals("indy", indyProvider?.name)
        assertNotNull(indyProvider?.supportedChains)
        assertNotNull(indyProvider?.create(IndyBlockchainAnchorClient.BCOVRIN_TESTNET))
    }
}

