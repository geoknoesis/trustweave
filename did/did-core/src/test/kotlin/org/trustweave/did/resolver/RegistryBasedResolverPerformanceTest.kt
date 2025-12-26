package org.trustweave.did.resolver

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.DidMethod
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.registry.DefaultDidMethodRegistry
import org.trustweave.did.resolver.RegistryBasedResolver
import org.trustweave.did.resolver.DidResolutionResult
import kotlin.test.assertTrue

/**
 * Performance tests for RegistryBasedResolver.
 *
 * Verifies that resolution operations perform well under load.
 */
class RegistryBasedResolverPerformanceTest {

    @Test
    fun `test resolution performance`() = runBlocking {
        val registry = DefaultDidMethodRegistry()
        val resolver = RegistryBasedResolver(registry)
        
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
        
        registry.register(method)
        
        val dids = (1..1000).map { Did("did:test:identifier$it") }
        
        val startTime = System.nanoTime()
        val results = dids.map { did ->
            resolver.resolve(did)
        }
        val endTime = System.nanoTime()
        
        val durationMs = (endTime - startTime) / 1_000_000.0
        println("Resolved 1000 DIDs in ${durationMs}ms (${durationMs / 1000}ms per resolution)")
        
        // Should complete in reasonable time
        assert(durationMs < 2000) { "Resolution too slow: ${durationMs}ms" }
        
        // Verify all resolutions succeeded
        results.forEach { result ->
            assertTrue(result is DidResolutionResult.Success)
        }
    }

    @Test
    fun `test resolution with invalid format performance`() = runBlocking {
        val registry = DefaultDidMethodRegistry()
        val resolver = RegistryBasedResolver(registry)
        
        val invalidDids = (1..1000).map { "invalid-did-$it" }
        
        val startTime = System.nanoTime()
        val results = invalidDids.map { didString ->
            try {
                resolver.resolve(Did(didString))
            } catch (e: Exception) {
                // Expected - invalid DID format
                null
            }
        }
        val endTime = System.nanoTime()
        
        val durationMs = (endTime - startTime) / 1_000_000.0
        println("Handled 1000 invalid DIDs in ${durationMs}ms (${durationMs / 1000}ms per DID)")
        
        // Should complete in reasonable time
        assert(durationMs < 2000) { "Invalid DID handling too slow: ${durationMs}ms" }
    }

    @Test
    fun `test resolution with method not registered performance`() = runBlocking {
        val registry = DefaultDidMethodRegistry()
        val resolver = RegistryBasedResolver(registry)
        
        val dids = (1..1000).map { Did("did:unknown:identifier$it") }
        
        val startTime = System.nanoTime()
        val results = dids.map { did ->
            resolver.resolve(did)
        }
        val endTime = System.nanoTime()
        
        val durationMs = (endTime - startTime) / 1_000_000.0
        println("Handled 1000 unregistered method DIDs in ${durationMs}ms (${durationMs / 1000}ms per DID)")
        
        // Should complete in reasonable time
        assert(durationMs < 2000) { "Unregistered method handling too slow: ${durationMs}ms" }
        
        // Verify all results are MethodNotRegistered
        results.forEach { result ->
            assertTrue(result is DidResolutionResult.Failure.MethodNotRegistered)
        }
    }
}

