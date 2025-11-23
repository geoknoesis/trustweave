package com.trustweave.anchor.polygon

import com.trustweave.anchor.spi.BlockchainAnchorClientProvider
import org.junit.jupiter.api.Test
import java.util.ServiceLoader
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests for SPI (Service Provider Interface) discovery of Polygon adapter.
 */
class SpiDiscoveryTest {

    @Test
    fun `SPI should discover Polygon provider`() {
        val providers = ServiceLoader.load(BlockchainAnchorClientProvider::class.java)
        val polygonProvider = providers.find { it.name == "polygon" }

        assertNotNull(polygonProvider, "Polygon blockchain provider should be discoverable via SPI")
        assertEquals("polygon", polygonProvider?.name)
        assertNotNull(polygonProvider?.supportedChains)
        assertNotNull(polygonProvider?.create(PolygonBlockchainAnchorClient.MUMBAI))
    }
}

