package com.trustweave.did

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import com.trustweave.did.DidCreationOptions
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.did.registry.DidMethodRegistry
import com.trustweave.did.registry.DefaultDidMethodRegistry
import com.trustweave.did.exception.DidException
import com.trustweave.did.exception.DidException.InvalidDidFormat
import com.trustweave.did.exception.DidException.DidMethodNotRegistered

class DidTest {

    private lateinit var registry: DidMethodRegistry

    @BeforeEach
    fun setup() {
        registry = DefaultDidMethodRegistry()
    }

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
        assertFailsWith<InvalidDidFormat> {
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

    private lateinit var registry: DidMethodRegistry

    @BeforeEach
    fun setup() {
        registry = DefaultDidMethodRegistry()
    }

    @Test
    fun `DidRegistry should register and retrieve methods`() {
        val mockMethod = object : DidMethod {
            override val method = "test"
            override suspend fun createDid(options: DidCreationOptions) = TODO()
            override suspend fun resolveDid(did: String) = TODO()
            override suspend fun updateDid(did: String, updater: (DidDocument) -> DidDocument) = TODO()
            override suspend fun deactivateDid(did: String) = TODO()
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
        
        assertNotNull(result1.document)
        assertNotNull(result2.document)
    }

    @Test
    fun `test resolve extracts correct method from DID`() = runBlocking {
        val method = createMockDidMethod("web")
        registry.register(method)
        
        val result = registry.resolve("did:web:example.com")
        
        assertNotNull(result.document)
        assertEquals("did:web:example.com", result.document?.id)
    }

    private fun createMockDidMethod(methodName: String): DidMethod {
        return object : DidMethod {
            override val method = methodName
            
            override suspend fun createDid(options: DidCreationOptions) = DidDocument(
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

