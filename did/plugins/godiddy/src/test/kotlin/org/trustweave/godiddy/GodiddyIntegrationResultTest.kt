package org.trustweave.godiddy

import org.trustweave.did.registry.DidMethodRegistry
import kotlin.test.*

/**
 * Tests for GodiddyIntegrationResult data class.
 */
class GodiddyIntegrationResultTest {

    @Test
    fun `test GodiddyIntegrationResult with all fields`() {
        val result = GodiddyIntegrationResult(
            registry = DidMethodRegistry(),
            registeredDidMethods = listOf("key", "web"),
            resolver = null, // Mock would be needed
            registrar = null,
            issuer = null,
            verifier = null
        )

        assertEquals(2, result.registeredDidMethods.size)
        assertTrue(result.registeredDidMethods.contains("key"))
        assertTrue(result.registeredDidMethods.contains("web"))
    }

    @Test
    fun `test GodiddyIntegrationResult with empty methods list`() {
        val result = GodiddyIntegrationResult(
            registry = DidMethodRegistry(),
            registeredDidMethods = emptyList()
        )

        assertTrue(result.registeredDidMethods.isEmpty())
    }

    @Test
    fun `test GodiddyIntegrationResult with single method`() {
        val result = GodiddyIntegrationResult(
            registry = DidMethodRegistry(),
            registeredDidMethods = listOf("key")
        )

        assertEquals(1, result.registeredDidMethods.size)
        assertEquals("key", result.registeredDidMethods.first())
    }

    @Test
    fun `test GodiddyIntegrationResult with multiple methods`() {
        val result = GodiddyIntegrationResult(
            registry = DidMethodRegistry(),
            registeredDidMethods = listOf("key", "web", "ion", "polygonid")
        )

        assertEquals(4, result.registeredDidMethods.size)
    }

    @Test
    fun `test GodiddyIntegrationResult equality`() {
        val registry = DidMethodRegistry()
        val result1 = GodiddyIntegrationResult(
            registry = registry,
            registeredDidMethods = listOf("key", "web")
        )
        val result2 = GodiddyIntegrationResult(
            registry = registry,
            registeredDidMethods = listOf("key", "web")
        )

        assertEquals(result1, result2)
    }

    @Test
    fun `test GodiddyIntegrationResult copy`() {
        val original = GodiddyIntegrationResult(
            registry = DidMethodRegistry(),
            registeredDidMethods = listOf("key")
        )
        val copied = original.copy(
            registeredDidMethods = listOf("key", "web")
        )

        assertEquals(1, original.registeredDidMethods.size)
        assertEquals(2, copied.registeredDidMethods.size)
    }

    @Test
    fun `test GodiddyIntegrationResult toString`() {
        val result = GodiddyIntegrationResult(
            registry = DidMethodRegistry(),
            registeredDidMethods = listOf("key")
        )

        val str = result.toString()
        assertTrue(str.contains("GodiddyIntegrationResult"))
    }
}



