package org.trustweave.provider

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.spi.DidMethodProvider
import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProviderDiscoveryTest {
    @Test
    fun `published SPI resolves and never claims an unsupported method`() {
        val providers = ServiceLoader.load(DidMethodProvider::class.java).filter { it.name == "plc" }
        assertEquals(1, providers.size)
        val provider = providers.single()
        assertTrue("plc" in provider.supportedMethods)
        assertNull(provider.create("not-a-supported-method", DidCreationOptions()))
    }
}
