package org.trustweave.did

import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.did.exception.DidException
import org.trustweave.did.exception.DidException.InvalidDidFormat
import org.trustweave.did.exception.DidException.DidMethodNotRegistered

class DidTest {

    private lateinit var registry: DidMethodRegistry

    @BeforeEach
    fun setup() {
        registry = DidMethodRegistry()
    }

    // Basic Did constructor tests - comprehensive coverage in DidParseBranchCoverageTest
    @Test
    fun `Did constructor should parse valid DID strings`() {
        val did = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
        assertEquals("key", did.method)
        assertEquals("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK", did.value)
    }
}

class DidRegistryTest {

    private lateinit var registry: DidMethodRegistry

    @BeforeEach
    fun setup() {
        registry = DidMethodRegistry()
    }

    @Test
    fun `DidRegistry should register and retrieve methods`() {
        val mockMethod = object : DidMethod {
            override val method = "test"
            override suspend fun createDid(options: DidCreationOptions) = DidDocument(id = Did("did:test:123"))
            override suspend fun resolveDid(did: Did) = DidResolutionResult.Success(
                document = DidDocument(id = did)
            )
            override suspend fun updateDid(did: Did, updater: (org.trustweave.did.model.DidDocument) -> org.trustweave.did.model.DidDocument) = DidDocument(id = did)
            override suspend fun deactivateDid(did: Did) = true
        }

        registry.register(mockMethod)
        assertEquals(mockMethod, registry.get("test"))
        assertNull(registry.get("nonexistent"))

        registry.clear()
    }

    @Test
    fun `test resolve DID`() = runBlocking {
        val method = createMockDidMethod("test")
        registry.register(method)

        val result = registry.resolve("did:test:123")

        assertNotNull(result)
        assertTrue(result is DidResolutionResult.Success)
        assertNotNull(result.document)
    }

    @Test
    fun `test resolve fails when method not registered`() = runBlocking {
        assertFailsWith<DidMethodNotRegistered> {
            registry.resolve("did:nonexistent:123")
        }
    }

    @Test
    fun `test register overwrites existing method`() {
        val method1 = createMockDidMethod("test")
        val method2 = createMockDidMethod("test")

        registry.register(method1)
        registry.register(method2)

        val retrieved = registry.get("test")
        assertNotNull(retrieved)
        assertEquals("test", retrieved?.method)
    }

    @Test
    fun `test resolve with invalid DID format`() = runBlocking {
        val method = createMockDidMethod("test")
        registry.register(method)

        assertFailsWith<InvalidDidFormat> {
            registry.resolve("invalid-did")
        }
    }

    @Test
    fun `test resolve with multiple registered methods`() = runBlocking {
        registry.register(createMockDidMethod("method1"))
        registry.register(createMockDidMethod("method2"))

        val result1 = registry.resolve("did:method1:123")
        val result2 = registry.resolve("did:method2:456")

        assertTrue(result1 is DidResolutionResult.Success)
        assertTrue(result2 is DidResolutionResult.Success)
        assertNotNull(result1.document)
        assertNotNull(result2.document)
    }

    @Test
    fun `test resolve extracts correct method from DID`() = runBlocking {
        val method = createMockDidMethod("web")
        registry.register(method)

        val result = registry.resolve("did:web:example.com")

        assertTrue(result is DidResolutionResult.Success)
        assertNotNull(result.document)
        assertEquals("did:web:example.com", result.document.id.value)
    }

    private fun createMockDidMethod(methodName: String): DidMethod {
        return object : DidMethod {
            override val method = methodName

            override suspend fun createDid(options: DidCreationOptions) = DidDocument(
                id = Did("did:$methodName:123")
            )

            override suspend fun resolveDid(did: Did) = DidResolutionResult.Success(
                document = DidDocument(id = did)
            )

            override suspend fun updateDid(did: Did, updater: (DidDocument) -> DidDocument) = DidDocument(id = did)

            override suspend fun deactivateDid(did: Did) = true
        }
    }
}

