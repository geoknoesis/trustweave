package org.trustweave.did.registry

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.trustweave.did.DidMethod
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionResult
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for concurrent access to DefaultDidMethodRegistry.
 *
 * Verifies thread safety and correctness under high concurrency.
 */
class DefaultDidMethodRegistryConcurrencyTest {

    @Test
    fun `test concurrent registration`() = runTest {
        val registry = DefaultDidMethodRegistry()
        val methods = (1..100).map { i ->
            object : DidMethod {
                override val method: String = "test$i"
                override suspend fun createDid(options: DidCreationOptions): DidDocument {
                    throw UnsupportedOperationException()
                }
                override suspend fun resolveDid(did: Did): DidResolutionResult {
                    throw UnsupportedOperationException()
                }
                override suspend fun updateDid(did: Did, updater: (DidDocument) -> DidDocument): DidDocument {
                    throw UnsupportedOperationException()
                }
                override suspend fun deactivateDid(did: Did): Boolean {
                    throw UnsupportedOperationException()
                }
            }
        }

        // Register all methods concurrently
        coroutineScope {
            methods.forEach { method ->
                launch {
                    registry.register(method)
                }
            }
        }

        // Verify all methods were registered
        assertEquals(100, registry.size())
        (1..100).forEach { i ->
            assertTrue(registry.has("test$i"))
            assertNotNull(registry.get("test$i"))
        }
    }

    @Test
    fun `test concurrent registration and retrieval`() = runTest {
        val registry = DefaultDidMethodRegistry()
        val methods = (1..50).map { i ->
            object : DidMethod {
                override val method: String = "method$i"
                override suspend fun createDid(options: DidCreationOptions): DidDocument {
                    throw UnsupportedOperationException()
                }
                override suspend fun resolveDid(did: Did): DidResolutionResult {
                    throw UnsupportedOperationException()
                }
                override suspend fun updateDid(did: Did, updater: (DidDocument) -> DidDocument): DidDocument {
                    throw UnsupportedOperationException()
                }
                override suspend fun deactivateDid(did: Did): Boolean {
                    throw UnsupportedOperationException()
                }
            }
        }

        // Concurrent registration and retrieval
        coroutineScope {
            // Register methods
            methods.forEach { method ->
                launch {
                    registry.register(method)
                }
            }

            // Retrieve methods concurrently
            repeat(100) {
                launch {
                    val methodName = "method${(it % 50) + 1}"
                    // May or may not be registered yet, but should not crash
                    registry.get(methodName)
                    registry.has(methodName)
                }
            }
        }

        // Verify final state
        assertEquals(50, registry.size())
    }

    @Test
    fun `test concurrent unregister`() = runTest {
        val registry = DefaultDidMethodRegistry()
        val methods = (1..100).map { i ->
            object : DidMethod {
                override val method: String = "method$i"
                override suspend fun createDid(options: DidCreationOptions): DidDocument {
                    throw UnsupportedOperationException()
                }
                override suspend fun resolveDid(did: Did): DidResolutionResult {
                    throw UnsupportedOperationException()
                }
                override suspend fun updateDid(did: Did, updater: (DidDocument) -> DidDocument): DidDocument {
                    throw UnsupportedOperationException()
                }
                override suspend fun deactivateDid(did: Did): Boolean {
                    throw UnsupportedOperationException()
                }
            }
        }

        // Register all methods
        methods.forEach { registry.register(it) }
        assertEquals(100, registry.size())

        // Unregister concurrently
        coroutineScope {
            (1..100).forEach { i ->
                launch {
                    registry.unregister("method$i")
                }
            }
        }

        // Verify all methods were unregistered
        assertEquals(0, registry.size())
        (1..100).forEach { i ->
            assertFalse(registry.has("method$i"))
        }
    }

    @Test
    fun `test concurrent getAllMethods`() = runTest {
        val registry = DefaultDidMethodRegistry()
        val methods = (1..50).map { i ->
            object : DidMethod {
                override val method: String = "method$i"
                override suspend fun createDid(options: DidCreationOptions): DidDocument {
                    throw UnsupportedOperationException()
                }
                override suspend fun resolveDid(did: Did): DidResolutionResult {
                    throw UnsupportedOperationException()
                }
                override suspend fun updateDid(did: Did, updater: (DidDocument) -> DidDocument): DidDocument {
                    throw UnsupportedOperationException()
                }
                override suspend fun deactivateDid(did: Did): Boolean {
                    throw UnsupportedOperationException()
                }
            }
        }

        // Register methods concurrently
        coroutineScope {
            methods.forEach { method ->
                launch {
                    registry.register(method)
                }
            }
        }

        // Concurrently retrieve all methods
        val results = coroutineScope {
            (1..10).map {
                async {
                    registry.getAllMethods()
                }
            }.awaitAll()
        }

        // All results should be consistent
        results.forEach { result ->
            assertEquals(50, result.size)
            (1..50).forEach { i ->
                assertTrue(result.containsKey("method$i"))
            }
        }
    }

    @Test
    fun `test concurrent snapshot`() = runTest {
        val registry = DefaultDidMethodRegistry()
        val methods = (1..50).map { i ->
            object : DidMethod {
                override val method: String = "method$i"
                override suspend fun createDid(options: DidCreationOptions): DidDocument {
                    throw UnsupportedOperationException()
                }
                override suspend fun resolveDid(did: Did): DidResolutionResult {
                    throw UnsupportedOperationException()
                }
                override suspend fun updateDid(did: Did, updater: (DidDocument) -> DidDocument): DidDocument {
                    throw UnsupportedOperationException()
                }
                override suspend fun deactivateDid(did: Did): Boolean {
                    throw UnsupportedOperationException()
                }
            }
        }

        // Register methods
        methods.forEach { registry.register(it) }

        // Create snapshots concurrently
        val snapshots = coroutineScope {
            (1..10).map {
                async {
                    registry.snapshot()
                }
            }.awaitAll()
        }

        // All snapshots should be consistent
        snapshots.forEach { snapshot ->
            assertEquals(50, snapshot.size())
            (1..50).forEach { i ->
                assertTrue(snapshot.has("method$i"))
            }
        }
    }

    @Test
    fun `test concurrent clear`() = runTest {
        val registry = DefaultDidMethodRegistry()
        val methods = (1..50).map { i ->
            object : DidMethod {
                override val method: String = "method$i"
                override suspend fun createDid(options: DidCreationOptions): DidDocument {
                    throw UnsupportedOperationException()
                }
                override suspend fun resolveDid(did: Did): DidResolutionResult {
                    throw UnsupportedOperationException()
                }
                override suspend fun updateDid(did: Did, updater: (DidDocument) -> DidDocument): DidDocument {
                    throw UnsupportedOperationException()
                }
                override suspend fun deactivateDid(did: Did): Boolean {
                    throw UnsupportedOperationException()
                }
            }
        }

        // Register methods
        methods.forEach { registry.register(it) }

        // Clear concurrently (should be safe)
        coroutineScope {
            repeat(10) {
                launch {
                    registry.clear()
                }
            }
        }

        // Registry should be empty
        assertEquals(0, registry.size())
    }

    @Test
    fun `test concurrent registration and resolution`() = runTest {
        val registry = DefaultDidMethodRegistry()
        val testDid = Did("did:test:123")
        
        val method = object : DidMethod {
            override val method: String = "test"
            override suspend fun createDid(options: DidCreationOptions): DidDocument {
                throw UnsupportedOperationException()
            }
            override suspend fun resolveDid(did: Did): DidResolutionResult {
                return DidResolutionResult.Success(
                    document = DidDocument(id = did)
                )
            }
            override suspend fun updateDid(did: Did, updater: (DidDocument) -> DidDocument): DidDocument {
                throw UnsupportedOperationException()
            }
            override suspend fun deactivateDid(did: Did): Boolean {
                throw UnsupportedOperationException()
            }
        }

        // Register and resolve concurrently
        coroutineScope {
            // Register
            launch {
                registry.register(method)
            }

            // Try to resolve (may fail if not registered yet, but should not crash)
            repeat(100) {
                launch {
                    try {
                        registry.resolve(testDid.value)
                    } catch (e: Exception) {
                        // Expected if method not registered yet
                    }
                }
            }
        }

        // Final resolution should succeed
        val result = registry.resolve(testDid.value)
        assertTrue(result is DidResolutionResult.Success)
    }
}

