package io.geoknoesis.vericore.godiddy

import io.geoknoesis.vericore.did.DidRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test for godiddy integration helper.
 */
class GodiddyIntegrationTest {

    @BeforeEach
    fun setUp() {
        // Clear registry before each test
        DidRegistry.clear()
    }

    @Test
    fun `test discoverAndRegister with default configuration`() {
        val result = GodiddyIntegration.discoverAndRegister()
        
        assertNotNull(result, "Integration result should not be null")
        assertTrue(result.registeredDidMethods.isNotEmpty(), "Should register at least one DID method")
        assertNotNull(result.resolver, "Resolver should be available")
    }

    @Test
    fun `test setup with custom base URL`() {
        val customBaseUrl = "https://custom.godiddy.com"
        val result = GodiddyIntegration.setup(baseUrl = customBaseUrl)
        
        assertNotNull(result, "Integration result should not be null")
        assertTrue(result.registeredDidMethods.isNotEmpty(), "Should register at least one DID method")
        assertNotNull(result.resolver, "Resolver should be available")
    }

    @Test
    fun `test setup with specific DID methods`() {
        val result = GodiddyIntegration.setup(didMethods = listOf("key", "web"))
        
        assertNotNull(result, "Integration result should not be null")
        assertTrue(result.registeredDidMethods.contains("key"), "Should register 'key' method")
        assertTrue(result.registeredDidMethods.contains("web"), "Should register 'web' method")
    }
}

