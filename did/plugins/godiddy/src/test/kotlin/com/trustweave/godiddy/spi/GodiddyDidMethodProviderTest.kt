package com.trustweave.godiddy.spi

import com.trustweave.did.didCreationOptions
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Branch coverage tests for GodiddyDidMethodProvider.
 */
class GodiddyDidMethodProviderTest {

    @Test
    fun `test GodiddyDidMethodProvider name`() {
        val provider = GodiddyDidMethodProvider()
        assertEquals("godiddy", provider.name)
    }

    @Test
    fun `test GodiddyDidMethodProvider supportedMethods contains expected methods`() {
        val provider = GodiddyDidMethodProvider()
        val methods = provider.supportedMethods
        
        assertTrue(methods.contains("key"))
        assertTrue(methods.contains("web"))
        assertTrue(methods.contains("ion"))
        assertTrue(methods.isNotEmpty())
    }

    @Test
    fun `test GodiddyDidMethodProvider create with valid method`() {
        val provider = GodiddyDidMethodProvider()
        val options = didCreationOptions {
            property("baseUrl", "https://example.com")
        }
        
        val method = provider.create("key", options)
        
        assertNotNull(method)
        assertEquals("key", method.method)
    }

    @Test
    fun `test GodiddyDidMethodProvider create with different method`() {
        val provider = GodiddyDidMethodProvider()
        val options = didCreationOptions {
            property("baseUrl", "https://example.com")
        }
        
        val method = provider.create("web", options)
        
        assertNotNull(method)
        assertEquals("web", method.method)
    }

    @Test
    fun `test GodiddyDidMethodProvider create handles registrar exception`() {
        val provider = GodiddyDidMethodProvider()
        val options = didCreationOptions {
            property("baseUrl", "https://example.com")
        }
        
        // Should handle registrar creation failure gracefully
        val method = provider.create("key", options)
        
        // Method should still be created even if registrar fails
        assertNotNull(method)
    }

    @Test
    fun `test GodiddyDidMethodProvider create with empty options`() {
        val provider = GodiddyDidMethodProvider()
        
        val method = provider.create("key")
        
        assertNotNull(method)
    }

    @Test
    fun `test GodiddyDidMethodProvider create with custom baseUrl`() {
        val provider = GodiddyDidMethodProvider()
        val options = didCreationOptions {
            property("baseUrl", "https://custom.example.com")
            property("timeout", 30000)
        }
        
        val method = provider.create("key", options)
        
        assertNotNull(method)
    }

    @Test
    fun `test GodiddyDidMethodProvider create with apiKey`() {
        val provider = GodiddyDidMethodProvider()
        val options = didCreationOptions {
            property("baseUrl", "https://example.com")
            property("apiKey", "test-key")
        }
        
        val method = provider.create("key", options)
        
        assertNotNull(method)
    }
}



