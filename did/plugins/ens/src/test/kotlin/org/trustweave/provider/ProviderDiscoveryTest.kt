package org.trustweave.provider

import org.trustweave.did.DidCreationOptions
import org.trustweave.did.spi.DidMethodProvider
import org.trustweave.ensdid.spi.EnsDidMethodProvider
import java.util.ServiceLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProviderDiscoveryTest {
    @Test
    fun `stub provider is not registered for ServiceLoader discovery and never claims an unsupported method`() {
        val discovered = ServiceLoader.load(DidMethodProvider::class.java).filter { it.name == "ens" }
        assertEquals(0, discovered.size)
        val provider = EnsDidMethodProvider()
        assertTrue("ens" in provider.supportedMethods)
        assertNull(provider.create("not-a-supported-method", DidCreationOptions()))
    }
}
