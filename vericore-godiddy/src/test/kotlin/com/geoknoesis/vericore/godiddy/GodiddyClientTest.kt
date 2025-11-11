package com.geoknoesis.vericore.godiddy

import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Tests for GodiddyClient focusing on constructor and basic functionality.
 */
class GodiddyClientTest {

    @Test
    fun `test GodiddyClient constructor with default config`() {
        val config = GodiddyConfig.default()
        val client = GodiddyClient(config)
        
        assertNotNull(client)
        assertEquals(config, client.config)
        client.close()
    }

    @Test
    fun `test GodiddyClient constructor with custom config`() {
        val config = GodiddyConfig(
            baseUrl = "https://custom.example.com",
            timeout = 30000,
            apiKey = "test-key"
        )
        val client = GodiddyClient(config)
        
        assertNotNull(client)
        assertEquals("https://custom.example.com", client.config.baseUrl)
        assertEquals(30000, client.config.timeout)
        assertEquals("test-key", client.config.apiKey)
        client.close()
    }

    @Test
    fun `test GodiddyClient constructor with null API key`() {
        val config = GodiddyConfig(
            baseUrl = "https://example.com",
            timeout = 10000,
            apiKey = null
        )
        val client = GodiddyClient(config)
        
        assertNotNull(client)
        assertNull(client.config.apiKey)
        client.close()
    }

    @Test
    fun `test GodiddyClient close`() {
        val config = GodiddyConfig.default()
        val client = GodiddyClient(config)
        
        // Should not throw
        client.close()
    }

    @Test
    fun `test GodiddyClient close multiple times`() {
        val config = GodiddyConfig.default()
        val client = GodiddyClient(config)
        
        client.close()
        // Should not throw on second close
        client.close()
    }
}

