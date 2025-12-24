package org.trustweave.godiddy

import org.trustweave.did.registry.DidMethodRegistry
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test for godiddy integration helper.
 */
class GodiddyIntegrationTest {

    @Test
    fun `test discoverAndRegister with default configuration`() {
        val registry = DidMethodRegistry()
        val result = GodiddyIntegration.discoverAndRegister(registry)

        assertNotNull(result, "Integration result should not be null")
        assertTrue(result.registeredDidMethods.isNotEmpty(), "Should register at least one DID method")
        assertNotNull(result.resolver, "Resolver should be available")
    }

    @Test
    fun `test setup with custom base URL`() {
        val customBaseUrl = "https://custom.godiddy.com"
        val registry = DidMethodRegistry()
        val result = GodiddyIntegration.setup(baseUrl = customBaseUrl, registry = registry)

        assertNotNull(result, "Integration result should not be null")
        assertTrue(result.registeredDidMethods.isNotEmpty(), "Should register at least one DID method")
        assertNotNull(result.resolver, "Resolver should be available")
    }

    @Test
    fun `test setup with specific DID methods`() {
        val registry = DidMethodRegistry()
        val result = GodiddyIntegration.setup(registry = registry, didMethods = listOf("key", "web"))

        assertNotNull(result, "Integration result should not be null")
        assertTrue(result.registeredDidMethods.contains("key"), "Should register 'key' method")
        assertTrue(result.registeredDidMethods.contains("web"), "Should register 'web' method")
    }
}

