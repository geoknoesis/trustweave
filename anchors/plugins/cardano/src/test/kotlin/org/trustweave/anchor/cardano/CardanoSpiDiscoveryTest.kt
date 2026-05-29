package org.trustweave.anchor.cardano

import org.junit.jupiter.api.Test
import org.trustweave.anchor.spi.BlockchainAnchorClientProvider
import java.util.ServiceLoader
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CardanoSpiDiscoveryTest {

    @Test
    fun `SPI discovers cardano provider`() {
        val providers = ServiceLoader.load(BlockchainAnchorClientProvider::class.java)
        val cardano = providers.find { it.name == "cardano" }
        assertNotNull(cardano, "Cardano provider should be discoverable via SPI")
        assertEquals("cardano", cardano.name)
        assertEquals(3, cardano.supportedChains.size)
    }
}
