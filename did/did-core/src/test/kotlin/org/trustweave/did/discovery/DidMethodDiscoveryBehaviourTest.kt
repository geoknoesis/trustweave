package org.trustweave.did.discovery

import kotlinx.coroutines.test.runTest
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.MethodCapabilities
import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.did.resolver.DidResolutionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DidMethodDiscoveryBehaviourTest {
    private class StubMethod(
        override val method: String,
        override val capabilities: MethodCapabilities? = null,
    ) : DidMethod {
        override suspend fun createDid(options: DidCreationOptions): DidDocument = DidDocument(Did("did:$method:x"))

        override suspend fun resolveDid(did: Did): DidResolutionResult = DidResolutionResult.Failure.NotFound(did)

        override suspend fun updateDid(
            did: Did,
            updater: (DidDocument) -> DidDocument,
        ): DidDocument = updater(DidDocument(did))

        override suspend fun deactivateDid(did: Did): Boolean = false
    }

    @Test
    fun `discovery reflects registry contents as they change`() =
        runTest {
            val registry = DidMethodRegistry()
            val service = DefaultDidMethodDiscoveryService(registry)
            assertTrue(service.discoverAvailableMethods().isEmpty())
            assertFalse(service.isMethodSupported("a"))

            registry.registerAll(StubMethod("a"), StubMethod("b"))
            assertEquals(setOf("a", "b"), service.discoverAvailableMethods().toSet())
            assertTrue(service.isMethodSupported("a"))

            registry.unregister("a")
            assertFalse(service.isMethodSupported("a"))
        }

    @Test
    fun `declared capabilities are returned unchanged`() =
        runTest {
            val declared = MethodCapabilities(method = "a", create = true, update = true, keyTypes = listOf("Ed25519"))
            val service = DefaultDidMethodDiscoveryService(DidMethodRegistry.of(StubMethod("a", declared)))
            assertEquals(declared, service.getMethodCapabilities("a"))
        }

    @Test
    fun `a method without declared capabilities is assumed resolve-only`() =
        runTest {
            val service = DefaultDidMethodDiscoveryService(DidMethodRegistry.of(StubMethod("a")))
            val caps = service.getMethodCapabilities("a")!!
            assertEquals("a", caps.method)
            assertTrue(caps.resolve)
            assertFalse(caps.create)
            assertFalse(caps.update)
            assertFalse(caps.deactivate)
        }

    @Test
    fun `unknown method has no capabilities`() =
        runTest {
            assertNull(DefaultDidMethodDiscoveryService(DidMethodRegistry()).getMethodCapabilities("nope"))
        }

    @Test
    fun `universal resolver discovery is not implemented and yields nothing`() =
        runTest {
            val service = DefaultDidMethodDiscoveryService(DidMethodRegistry.of(StubMethod("a")))
            assertTrue(service.discoverFromUniversalResolver("https://resolver.example").isEmpty())
        }
}
