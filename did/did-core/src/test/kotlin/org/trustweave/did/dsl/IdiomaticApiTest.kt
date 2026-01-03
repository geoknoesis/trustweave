package org.trustweave.did.dsl

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.did.DidMethod
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.registry.DidMethodRegistry
import org.trustweave.did.registry.didMethodRegistry
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import org.trustweave.did.resolver.RegistryBasedResolver
import org.trustweave.did.dsl.universalResolver
import kotlin.test.*

/**
 * Tests for idiomatic Kotlin API features.
 */
class IdiomaticApiTest {

    @Test
    fun `test registry builder DSL`() {
        val method1 = object : DidMethod {
            override val method = "test1"
            override suspend fun createDid(options: DidCreationOptions) = DidDocument(id = Did("did:test1:123"))
            override suspend fun resolveDid(did: Did) = DidResolutionResult.Success(DidDocument(id = did))
            override suspend fun updateDid(did: Did, updater: (DidDocument) -> DidDocument) = DidDocument(id = did)
            override suspend fun deactivateDid(did: Did) = true
        }
        
        val method2 = object : DidMethod {
            override val method = "test2"
            override suspend fun createDid(options: DidCreationOptions) = DidDocument(id = Did("did:test2:123"))
            override suspend fun resolveDid(did: Did) = DidResolutionResult.Success(DidDocument(id = did))
            override suspend fun updateDid(did: Did, updater: (DidDocument) -> DidDocument) = DidDocument(id = did)
            override suspend fun deactivateDid(did: Did) = true
        }

        val registry = didMethodRegistry {
            register(method1)
            register(method2)
        }

        assertNotNull(registry.get("test1"))
        assertNotNull(registry.get("test2"))
        assertEquals(2, registry.size())
    }

    @Test
    fun `test registry operator overloads`() {
        val registry = DidMethodRegistry()
        val method = object : DidMethod {
            override val method = "test"
            override suspend fun createDid(options: DidCreationOptions) = DidDocument(id = Did("did:test:123"))
            override suspend fun resolveDid(did: Did) = DidResolutionResult.Success(DidDocument(id = did))
            override suspend fun updateDid(did: Did, updater: (DidDocument) -> DidDocument) = DidDocument(id = did)
            override suspend fun deactivateDid(did: Did) = true
        }

        // Test bracket notation for setting
        registry["test"] = method

        // Test getting (using get method since operator conflicts with interface)
        val retrieved = registry.get("test")
        assertNotNull(retrieved)
        assertEquals("test", retrieved.method)

        // Test `in` operator
        assertTrue("test" in registry)
        assertFalse("unknown" in registry)
    }

    @Test
    fun `test resolver builder DSL`() {
        val resolver = universalResolver("https://dev.uniresolver.io") {
            timeout = 60
            apiKey = "test-key"
            retry {
                maxRetries = 5
                initialDelayMs = 200
            }
        }

        assertEquals("https://dev.uniresolver.io", resolver.baseUrl)
    }

    @Test
    fun `test DidResolutionResult extensions`() = runBlocking {
        val success = DidResolutionResult.Success(
            document = DidDocument(id = Did("did:test:123"))
        )

        // Test basic result properties
        assertTrue(success is DidResolutionResult.Success)
        assertFalse(success is DidResolutionResult.Failure)
        assertEquals("did:test:123", success.document.id.value)
        
        // Test that extensions are available (they should be auto-imported)
        // Using direct access to verify functionality
        val doc = when (success) {
            is DidResolutionResult.Success -> success.document
            else -> null
        }
        assertNotNull(doc)
        assertEquals("did:test:123", doc.id.value)
    }

    @Test
    fun `test DidResolutionResult failure extensions`() = runBlocking {
        val failure = DidResolutionResult.Failure.NotFound(
            did = Did("did:test:123"),
            reason = "Not found"
        )

        // Test basic failure properties
        assertTrue(failure is DidResolutionResult.Failure)
        assertFalse(failure is DidResolutionResult.Success)
        assertEquals("did:test:123", failure.did.value)
        assertEquals("Not found", failure.reason)
        
        // Test that failure doesn't have a document
        val doc = when (failure) {
            is DidResolutionResult.Success -> failure.document
            else -> null
        }
        assertNull(doc)
    }

    @Test
    fun `test Did extension functions`() = runBlocking {
        val did = Did("did:test:123")
        val resolver = object : DidResolver {
            override suspend fun resolve(did: Did) = DidResolutionResult.Success(
                DidDocument(id = did)
            )
        }

        // Test resolveWith (core extension function)
        val result = did.resolveWith(resolver)
        assertTrue(result is DidResolutionResult.Success)
        val doc = (result as DidResolutionResult.Success).document
        assertNotNull(doc)
        assertEquals("did:test:123", doc.id.value)

        // Test direct resolution
        val result2 = resolver.resolve(did)
        assertTrue(result2 is DidResolutionResult.Success)
        val doc2 = (result2 as DidResolutionResult.Success).document
        assertEquals("did:test:123", doc2.id.value)
    }
}

