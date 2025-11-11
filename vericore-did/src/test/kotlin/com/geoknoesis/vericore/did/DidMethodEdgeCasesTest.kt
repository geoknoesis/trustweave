package com.geoknoesis.vericore.did

import kotlinx.coroutines.runBlocking
import com.geoknoesis.vericore.did.DidCreationOptions
import com.geoknoesis.vericore.did.didCreationOptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import java.time.Instant

/**
 * Comprehensive edge case tests for DidMethod interface and DidRegistry.
 */
class DidMethodEdgeCasesTest {

    private lateinit var registry: DidMethodRegistry

    @BeforeEach
    fun setup() {
        registry = DefaultDidMethodRegistry()
    }

    @Test
    fun `test DidMethod createDid with empty options`() = runBlocking {
        val method = createMockDidMethod("test")
        
        val doc = method.createDid()
        
        assertNotNull(doc)
        assertTrue(doc.id.startsWith("did:test:"))
    }

    @Test
    fun `test DidMethod createDid with options`() = runBlocking {
        val method = createMockDidMethod("test")
        
        val doc = method.createDid(
            didCreationOptions {
                algorithm = DidCreationOptions.KeyAlgorithm.ED25519
                property("keyId", "key-123")
            }
        )
        
        assertNotNull(doc)
    }

    @Test
    fun `test DidMethod resolveDid with valid DID`() = runBlocking {
        val method = createMockDidMethod("test")
        
        val result = method.resolveDid("did:test:123")
        
        assertNotNull(result)
        assertNotNull(result.document)
        assertEquals("did:test:123", result.document?.id)
    }

    @Test
    fun `test DidMethod resolveDid with invalid DID format`() = runBlocking {
        val method = createMockDidMethod("test")
        
        // Should handle gracefully or throw
        try {
            val result = method.resolveDid("invalid-did")
            assertNotNull(result)
        } catch (_: IllegalArgumentException) {
            // expected path
        }
    }

    @Test
    fun `test DidMethod updateDid`() = runBlocking {
        val method = createMockDidMethod("test")
        val originalDoc = DidDocument(id = "did:test:123")
        
        val updated = method.updateDid("did:test:123") { doc ->
            doc.copy(alsoKnownAs = listOf("did:web:example.com"))
        }
        
        assertNotNull(updated)
        assertEquals(1, updated.alsoKnownAs.size)
    }

    @Test
    fun `test DidMethod updateDid with complex updater`() = runBlocking {
        val method = createMockDidMethod("test")
        val vm = VerificationMethodRef(
            id = "did:test:123#key-1",
            type = "Ed25519VerificationKey2020",
            controller = "did:test:123"
        )
        
        val updated = method.updateDid("did:test:123") { doc ->
            doc.copy(
                verificationMethod = listOf(vm),
                authentication = listOf("did:test:123#key-1")
            )
        }
        
        assertEquals(1, updated.verificationMethod.size)
        assertEquals(1, updated.authentication.size)
    }

    @Test
    fun `test DidMethod deactivateDid`() = runBlocking {
        val method = createMockDidMethod("test")
        
        val result = method.deactivateDid("did:test:123")
        
        assertTrue(result)
    }

    @Test
    fun `test DidMethod deactivateDid with nonexistent DID`() = runBlocking {
        val method = createMockDidMethod("test")
        
        // May return false or throw
        try {
            val result = method.deactivateDid("did:test:nonexistent")
            assertNotNull(result) // Boolean value
        } catch (e: Exception) {
            assertTrue(true) // Exception is acceptable
        }
    }

    @Test
    fun `test DidRegistry register multiple methods`() {
        val method1 = createMockDidMethod("method1")
        val method2 = createMockDidMethod("method2")
        
        registry.register(method1)
        registry.register(method2)
        
        assertEquals(method1, registry.get("method1"))
        assertEquals(method2, registry.get("method2"))
    }

    @Test
    fun `test DidRegistry register overwrites existing method`() {
        val method1 = createMockDidMethod("test")
        val method2 = createMockDidMethod("test")
        
        registry.register(method1)
        registry.register(method2)
        
        assertEquals(method2, registry.get("test"))
    }

    @Test
    fun `test DidRegistry resolve with metadata`() = runBlocking {
        val method = object : DidMethod {
            override val method = "test"
            
            override suspend fun createDid(options: DidCreationOptions) = DidDocument(id = "did:test:123")
            
            override suspend fun resolveDid(did: String) = DidResolutionResult(
                document = DidDocument(id = did),
                documentMetadata = DidDocumentMetadata(
                    created = Instant.parse("2024-01-01T00:00:00Z"),
                    updated = Instant.parse("2024-01-02T00:00:00Z")
                ),
                resolutionMetadata = mapOf("duration" to 100L, "cached" to false)
            )
            
            override suspend fun updateDid(did: String, updater: (DidDocument) -> DidDocument) = DidDocument(id = did)
            override suspend fun deactivateDid(did: String) = true
        }
        
        registry.register(method)
        
        val result = registry.resolve("did:test:123")
        
        assertNotNull(result.document)
        assertNotNull(result.documentMetadata.created)
        assertNotNull(result.documentMetadata.updated)
        assertEquals(2, result.resolutionMetadata.size)
    }

    @Test
    fun `test DidRegistry resolve with null document`() = runBlocking {
        val method = object : DidMethod {
            override val method = "test"
            
            override suspend fun createDid(options: DidCreationOptions) = DidDocument(id = "did:test:123")
            
            override suspend fun resolveDid(did: String) = DidResolutionResult(
                document = null,
                resolutionMetadata = mapOf("error" to "notFound")
            )
            
            override suspend fun updateDid(did: String, updater: (DidDocument) -> DidDocument) = DidDocument(id = did)
            override suspend fun deactivateDid(did: String) = true
        }
        
        registry.register(method)
        
        val result = registry.resolve("did:test:nonexistent")
        
        assertNull(result.document)
        assertTrue(result.resolutionMetadata.containsKey("error"))
    }

    @Test
    fun `test DidRegistry clear removes all methods`() {
        val method1 = createMockDidMethod("method1")
        val method2 = createMockDidMethod("method2")
        
        registry.register(method1)
        registry.register(method2)
        
        registry.clear()
        
        assertNull(registry.get("method1"))
        assertNull(registry.get("method2"))
    }

    @Test
    fun `test DidRegistry resolve with DID containing special characters`() = runBlocking {
        val method = createMockDidMethod("test")
        registry.register(method)
        
        val result = registry.resolve("did:test:abc-123_xyz")
        
        assertNotNull(result)
        assertNotNull(result.document)
    }

    @Test
    fun `test DidRegistry resolve with very long DID`() = runBlocking {
        val method = createMockDidMethod("test")
        registry.register(method)
        
        val longId = "a".repeat(1000)
        val result = registry.resolve("did:test:$longId")
        
        assertNotNull(result)
    }

    private fun createMockDidMethod(methodName: String): DidMethod {
        return object : DidMethod {
            override val method = methodName
            
            override suspend fun createDid(options: DidCreationOptions): DidDocument {
                val keyId = options.additionalProperties["keyId"] as? String ?: "123"
                return DidDocument(id = "did:$methodName:$keyId")
            }
            
            override suspend fun resolveDid(did: String) = DidResolutionResult(
                document = DidDocument(id = did)
            )
            
            override suspend fun updateDid(did: String, updater: (DidDocument) -> DidDocument): DidDocument {
                val current = DidDocument(id = did)
                return updater(current)
            }
            
            override suspend fun deactivateDid(did: String) = did.startsWith("did:$methodName:")
        }
    }
}


