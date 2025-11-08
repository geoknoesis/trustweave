package io.geoknoesis.vericore.did

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

class DidTest {

    @Test
    fun `Did toString should format correctly`() {
        val did = Did(method = "web", id = "example.com")
        assertEquals("did:web:example.com", did.toString())
    }

    @Test
    fun `Did parse should parse valid DID strings`() {
        val did = Did.parse("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
        assertEquals("key", did.method)
        assertEquals("z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK", did.id)
    }

    @Test
    fun `Did parse should throw for invalid format`() {
        assertFailsWith<IllegalArgumentException> {
            Did.parse("not-a-did")
        }
    }

    @Test
    fun `Did parse with complex id`() {
        val did = Did.parse("did:web:example.com:path:to:resource")
        assertEquals("web", did.method)
        assertEquals("example.com:path:to:resource", did.id)
    }
}

class DidRegistryTest {

    @BeforeEach
    fun setup() {
        DidRegistry.clear()
    }

    @Test
    fun `DidRegistry should register and retrieve methods`() {
        val mockMethod = object : DidMethod {
            override val method = "test"
            override suspend fun createDid(options: Map<String, Any?>) = TODO()
            override suspend fun resolveDid(did: String) = TODO()
            override suspend fun updateDid(did: String, updater: (DidDocument) -> DidDocument) = TODO()
            override suspend fun deactivateDid(did: String) = TODO()
        }

        DidRegistry.register(mockMethod)
        assertEquals(mockMethod, DidRegistry.get("test"))
        assertNull(DidRegistry.get("nonexistent"))

        DidRegistry.clear()
    }

    @Test
    fun `test resolve DID`() = runBlocking {
        val method = createMockDidMethod("test")
        DidRegistry.register(method)
        
        val result = DidRegistry.resolve("did:test:123")
        
        assertNotNull(result)
        assertNotNull(result.document)
    }

    @Test
    fun `test resolve fails when method not registered`() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            DidRegistry.resolve("did:nonexistent:123")
        }
    }

    @Test
    fun `test register overwrites existing method`() {
        val method1 = createMockDidMethod("test")
        val method2 = createMockDidMethod("test")
        
        DidRegistry.register(method1)
        DidRegistry.register(method2)
        
        val retrieved = DidRegistry.get("test")
        assertNotNull(retrieved)
        assertEquals("test", retrieved?.method)
    }

    @Test
    fun `test resolve with invalid DID format`() = runBlocking {
        val method = createMockDidMethod("test")
        DidRegistry.register(method)
        
        assertFailsWith<IllegalArgumentException> {
            DidRegistry.resolve("invalid-did")
        }
    }

    @Test
    fun `test resolve with multiple registered methods`() = runBlocking {
        DidRegistry.register(createMockDidMethod("method1"))
        DidRegistry.register(createMockDidMethod("method2"))
        
        val result1 = DidRegistry.resolve("did:method1:123")
        val result2 = DidRegistry.resolve("did:method2:456")
        
        assertNotNull(result1.document)
        assertNotNull(result2.document)
    }

    @Test
    fun `test resolve extracts correct method from DID`() = runBlocking {
        val method = createMockDidMethod("web")
        DidRegistry.register(method)
        
        val result = DidRegistry.resolve("did:web:example.com")
        
        assertNotNull(result.document)
        assertEquals("did:web:example.com", result.document?.id)
    }

    @AfterEach
    fun cleanup() {
        DidRegistry.clear()
    }

    private fun createMockDidMethod(methodName: String): DidMethod {
        return object : DidMethod {
            override val method = methodName
            
            override suspend fun createDid(options: Map<String, Any?>) = DidDocument(
                id = "did:$methodName:123"
            )
            
            override suspend fun resolveDid(did: String) = DidResolutionResult(
                document = DidDocument(id = did)
            )
            
            override suspend fun updateDid(did: String, updater: (DidDocument) -> DidDocument) = DidDocument(id = did)
            
            override suspend fun deactivateDid(did: String) = true
        }
    }
}

