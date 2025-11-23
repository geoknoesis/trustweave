package com.trustweave.godiddy

import com.trustweave.did.didCreationOptions
import kotlin.test.*

/**
 * Tests for GodiddyConfig data class and companion object methods.
 */
class GodiddyConfigTest {

    @Test
    fun `test GodiddyConfig with default values`() {
        val config = GodiddyConfig()
        
        assertEquals("https://api.godiddy.com", config.baseUrl)
        assertEquals(30000L, config.timeout)
        assertNull(config.apiKey)
    }

    @Test
    fun `test GodiddyConfig with all fields`() {
        val config = GodiddyConfig(
            baseUrl = "https://custom.godiddy.com",
            timeout = 60000L,
            apiKey = "api-key-123"
        )
        
        assertEquals("https://custom.godiddy.com", config.baseUrl)
        assertEquals(60000L, config.timeout)
        assertEquals("api-key-123", config.apiKey)
    }

    @Test
    fun `test GodiddyConfig default companion method`() {
        val config = GodiddyConfig.default()
        
        assertEquals("https://api.godiddy.com", config.baseUrl)
        assertEquals(30000L, config.timeout)
        assertNull(config.apiKey)
    }

    @Test
    fun `test GodiddyConfig fromOptions with all fields`() {
        val options = didCreationOptions {
            property("baseUrl", "https://custom.godiddy.com")
            property("timeout", 60000L)
            property("apiKey", "api-key-123")
        }
        
        val config = GodiddyConfig.fromOptions(options)
        
        assertEquals("https://custom.godiddy.com", config.baseUrl)
        assertEquals(60000L, config.timeout)
        assertEquals("api-key-123", config.apiKey)
    }

    @Test
    fun `test GodiddyConfig fromOptions with partial fields`() {
        val options = didCreationOptions {
            property("baseUrl", "https://custom.godiddy.com")
        }
        
        val config = GodiddyConfig.fromOptions(options)
        
        assertEquals("https://custom.godiddy.com", config.baseUrl)
        assertEquals(30000L, config.timeout) // Default value
        assertNull(config.apiKey)
    }

    @Test
    fun `test GodiddyConfig fromOptions with empty map`() {
        val config = GodiddyConfig.fromOptions(com.trustweave.did.DidCreationOptions())
        
        assertEquals("https://api.godiddy.com", config.baseUrl)
        assertEquals(30000L, config.timeout)
        assertNull(config.apiKey)
    }

    @Test
    fun `test GodiddyConfig fromOptions with null values`() {
        val options = didCreationOptions {
            property("baseUrl", null)
            property("timeout", null)
            property("apiKey", null)
        }
        
        val config = GodiddyConfig.fromOptions(options)
        
        assertEquals("https://api.godiddy.com", config.baseUrl) // Default
        assertEquals(30000L, config.timeout) // Default
        assertNull(config.apiKey)
    }

    @Test
    fun `test GodiddyConfig fromOptions with timeout as Int`() {
        val options = didCreationOptions {
            property("timeout", 45000) // Int instead of Long
        }
        
        val config = GodiddyConfig.fromOptions(options)
        
        assertEquals(45000L, config.timeout)
    }

    @Test
    fun `test GodiddyConfig fromOptions with timeout as Double`() {
        val options = didCreationOptions {
            property("timeout", 45000.0) // Double
        }
        
        val config = GodiddyConfig.fromOptions(options)
        
        assertEquals(45000L, config.timeout)
    }

    @Test
    fun `test GodiddyConfig equality`() {
        val config1 = GodiddyConfig(
            baseUrl = "https://custom.godiddy.com",
            timeout = 60000L,
            apiKey = "api-key-123"
        )
        val config2 = GodiddyConfig(
            baseUrl = "https://custom.godiddy.com",
            timeout = 60000L,
            apiKey = "api-key-123"
        )
        
        assertEquals(config1, config2)
    }

    @Test
    fun `test GodiddyConfig copy`() {
        val original = GodiddyConfig()
        val copied = original.copy(apiKey = "new-key")
        
        assertEquals(original.baseUrl, copied.baseUrl)
        assertEquals(original.timeout, copied.timeout)
        assertEquals("new-key", copied.apiKey)
    }

    @Test
    fun `test GodiddyConfig toString`() {
        val config = GodiddyConfig(apiKey = "test-key")
        
        val str = config.toString()
        assertTrue(str.contains("GodiddyConfig"))
    }
}



