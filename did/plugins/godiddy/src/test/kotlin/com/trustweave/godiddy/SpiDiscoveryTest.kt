package com.trustweave.godiddy

import com.trustweave.did.spi.DidMethodProvider
import org.junit.jupiter.api.Test
import java.util.ServiceLoader
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test for SPI discovery of godiddy DID method provider.
 */
class SpiDiscoveryTest {

    @Test
    fun `test godiddy DID method provider discovery`() {
        val providers = ServiceLoader.load(DidMethodProvider::class.java)
        val godiddyProvider = providers.find { it.name == "godiddy" }
        
        assertNotNull(godiddyProvider, "godiddy DID method provider should be discoverable via SPI")
        assertTrue(godiddyProvider!!.supportedMethods.isNotEmpty(), "godiddy provider should support at least one DID method")
    }
    
    @Test
    fun `test godiddy supports did algo method`() {
        val providers = ServiceLoader.load(DidMethodProvider::class.java)
        val godiddyProvider = providers.find { it.name == "godiddy" }
        
        assertNotNull(godiddyProvider, "godiddy DID method provider should be discoverable")
        assertTrue(
            godiddyProvider!!.supportedMethods.contains("algo"),
            "godiddy provider should support did:algo method"
        )
    }
    
    @Test
    fun `test godiddy supports common DID methods`() {
        val providers = ServiceLoader.load(DidMethodProvider::class.java)
        val godiddyProvider = providers.find { it.name == "godiddy" }
        
        assertNotNull(godiddyProvider)
        val supportedMethods = godiddyProvider!!.supportedMethods
        
        // Verify common methods are supported
        assertTrue(supportedMethods.contains("key"), "Should support did:key")
        assertTrue(supportedMethods.contains("web"), "Should support did:web")
        assertTrue(supportedMethods.contains("ion"), "Should support did:ion")
        assertTrue(supportedMethods.contains("algo"), "Should support did:algo")
        
        println("Supported DID methods: ${supportedMethods.joinToString(", ")}")
    }
}

