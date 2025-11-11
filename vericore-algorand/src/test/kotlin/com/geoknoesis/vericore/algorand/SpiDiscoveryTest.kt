package com.geoknoesis.vericore.algorand

import com.geoknoesis.vericore.anchor.spi.BlockchainAnchorClientProvider
import org.junit.jupiter.api.Test
import java.util.ServiceLoader
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests for SPI (Service Provider Interface) discovery of Algorand adapter.
 */
class SpiDiscoveryTest {

    @Test
    fun `SPI should discover Algorand provider`() {
        val providers = ServiceLoader.load(BlockchainAnchorClientProvider::class.java)
        val algorandProvider = providers.find { it.name == "algorand" }

        assertNotNull(algorandProvider, "Algorand blockchain provider should be discoverable via SPI")
        assertEquals("algorand", algorandProvider?.name)
        assertNotNull(algorandProvider?.supportedChains)
        assertNotNull(algorandProvider?.create(AlgorandBlockchainAnchorClient.TESTNET))
    }
}

