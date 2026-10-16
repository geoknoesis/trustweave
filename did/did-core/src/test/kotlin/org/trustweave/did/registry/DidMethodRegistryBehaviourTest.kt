package org.trustweave.did.registry

import kotlinx.coroutines.test.runTest
import org.trustweave.did.identifiers.Did
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.telemetry.TelemetryDidMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DidMethodRegistryBehaviourTest {
    private fun method(
        name: String,
        tag: String = "t",
    ) = CovFakeMethod(name, tag)

    @Test
    fun `a new registry is empty`() {
        val registry = DidMethodRegistry()
        assertEquals(0, registry.size())
        assertNull(registry["key"])
        assertFalse("key" in registry)
        assertFalse(registry.has("key"))
        assertTrue(registry.getAllMethodNames().isEmpty())
    }

    @Test
    fun `registered methods are instrumented but the original stays reachable`() {
        val original = method("a")
        val registry = DidMethodRegistry()
        registry.register(original)

        val fetched = assertNotNull(registry["a"])
        assertIs<TelemetryDidMethod>(fetched)
        assertSame(original, fetched.delegate)
        assertEquals("a", fetched.method)
        assertTrue("a" in registry)
        assertTrue(registry.has("a"))
        assertEquals(1, registry.size())
    }

    @Test
    fun `registering the same name replaces the earlier method`() =
        runTest {
            val registry = DidMethodRegistry()
            registry.register(method("a", "first"))
            registry.register(method("a", "second"))

            assertEquals(1, registry.size())
            assertEquals("did:a:second", registry["a"]!!.createDid().id.value)
        }

    @Test
    fun `registerAll and of register every method`() {
        val viaAll = DidMethodRegistry().also { it.registerAll(method("a"), method("b"), method("c")) }
        val viaOf = DidMethodRegistry.of(method("a"), method("b"))
        assertEquals(setOf("a", "b", "c"), viaAll.getAllMethodNames().toSet())
        assertEquals(setOf("a", "b"), viaOf.getAllMethodNames().toSet())
    }

    @Test
    fun `index assignment requires the key to match the method name`() {
        val registry = DidMethodRegistry()
        val error = assertFailsWith<IllegalArgumentException> { registry["wrong"] = method("a") }
        assertTrue(error.message!!.contains("wrong") && error.message!!.contains("'a'"), error.message)
        assertEquals(0, registry.size())

        registry["a"] = method("a")
        assertTrue("a" in registry)
    }

    @Test
    fun `unregister reports whether anything was removed`() {
        val registry = DidMethodRegistry.of(method("a"))
        assertTrue(registry.unregister("a"))
        assertFalse(registry.unregister("a"))
        assertEquals(0, registry.size())
    }

    @Test
    fun `getAllMethods is a snapshot not a live view`() {
        val registry = DidMethodRegistry.of(method("a"))
        val snapshot = registry.getAllMethods()
        registry.register(method("b"))
        registry.unregister("a")

        assertEquals(setOf("a"), snapshot.keys)
        assertEquals(setOf("b"), registry.getAllMethods().keys)
    }

    @Test
    fun `clear removes everything`() {
        val registry = DidMethodRegistry.of(method("a"), method("b"))
        registry.clear()
        assertEquals(0, registry.size())
    }

    @Test
    fun `resolve of a malformed string is InvalidFormat carrying the input`() =
        runTest {
            val result = DidMethodRegistry.of(method("a")).resolve("not-a-did")
            val failure = assertIs<DidResolutionResult.Failure.InvalidFormat>(result)
            assertEquals("not-a-did", failure.did)
            assertTrue(failure.reason.isNotBlank())
        }

    @Test
    fun `resolve of an unregistered method lists what is available`() =
        runTest {
            val result = DidMethodRegistry.of(method("a")).resolve("did:zzz:abc")
            val failure = assertIs<DidResolutionResult.Failure.MethodNotRegistered>(result)
            assertEquals("zzz", failure.method)
            assertEquals(listOf("a"), failure.availableMethods)
        }

    @Test
    fun `resolve dispatches to the registered method`() =
        runTest {
            val result = DidMethodRegistry.of(method("a")).resolve("did:a:abc")
            val success = assertIs<DidResolutionResult.Success>(result)
            assertEquals(Did("did:a:abc"), success.document.id)
        }
}
