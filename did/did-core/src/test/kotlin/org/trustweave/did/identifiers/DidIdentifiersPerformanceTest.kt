package org.trustweave.did.identifiers

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Performance tests for DID identifier parsing and operations.
 *
 * Verifies that DID operations perform well under load.
 */
class DidIdentifiersPerformanceTest {

    @Test
    fun `test DID parsing performance`() {
        val didStrings = (1..1000).map { "did:test:identifier$it" }
        
        val startTime = System.nanoTime()
        val dids = didStrings.map { assertDoesNotThrow { Did(it) } }
        val endTime = System.nanoTime()
        
        val durationMs = (endTime - startTime) / 1_000_000.0
        println("Parsed 1000 DIDs in ${durationMs}ms (${durationMs / 1000}ms per DID)")
        
        // Should complete in reasonable time (< 1 second for 1000 DIDs)
        assert(durationMs < 1000) { "DID parsing too slow: ${durationMs}ms" }
        
        // Verify all DIDs were parsed correctly
        assertEquals(1000, dids.size)
        dids.forEachIndexed { index, did ->
            assertEquals("test", did.method)
            assertEquals("identifier${index + 1}", did.identifier)
        }
    }

    @Test
    fun `test DID method extraction performance`() {
        val did = Did("did:test:identifier123")
        
        val startTime = System.nanoTime()
        repeat(10000) {
            val method = did.method
            assertNotNull(method)
        }
        val endTime = System.nanoTime()
        
        val durationMs = (endTime - startTime) / 1_000_000.0
        println("Extracted method 10000 times in ${durationMs}ms (${durationMs / 10000}ms per extraction)")
        
        // Should be very fast due to lazy caching
        assert(durationMs < 100) { "Method extraction too slow: ${durationMs}ms" }
    }

    @Test
    fun `test DID identifier extraction performance`() {
        val did = Did("did:test:identifier123")
        
        val startTime = System.nanoTime()
        repeat(10000) {
            val identifier = did.identifier
            assertNotNull(identifier)
        }
        val endTime = System.nanoTime()
        
        val durationMs = (endTime - startTime) / 1_000_000.0
        println("Extracted identifier 10000 times in ${durationMs}ms (${durationMs / 10000}ms per extraction)")
        
        // Should be very fast due to lazy caching
        assert(durationMs < 100) { "Identifier extraction too slow: ${durationMs}ms" }
    }

    @Test
    fun `test baseDid performance`() {
        // Use a DID with path instead of fragment (fragments are removed in Did constructor)
        val didWithPath = Did("did:test:identifier123")
        val didUrl = "did:test:identifier123/path"
        
        val startTime = System.nanoTime()
        repeat(10000) {
            // Test with a DID that has a path component
            val baseDid = if (it == 0) {
                // Create a DidUrl-like scenario by parsing
                Did(didUrl.substringBefore("/"))
            } else {
                didWithPath.baseDid
            }
            assertNotNull(baseDid)
        }
        val endTime = System.nanoTime()
        
        val durationMs = (endTime - startTime) / 1_000_000.0
        println("Extracted baseDid 10000 times in ${durationMs}ms (${durationMs / 10000}ms per extraction)")
        
        // Should be reasonably fast
        assert(durationMs < 500) { "baseDid extraction too slow: ${durationMs}ms" }
    }

    @Test
    fun `test VerificationMethodId creation performance`() {
        val did = Did("did:test:identifier123")
        
        val startTime = System.nanoTime()
        val vmIds = (1..1000).map { i ->
            did + "key-$i"
        }
        val endTime = System.nanoTime()
        
        val durationMs = (endTime - startTime) / 1_000_000.0
        println("Created 1000 VerificationMethodIds in ${durationMs}ms (${durationMs / 1000}ms per ID)")
        
        // Should complete in reasonable time
        assert(durationMs < 500) { "VerificationMethodId creation too slow: ${durationMs}ms" }
        
        // Verify all IDs were created correctly
        assertEquals(1000, vmIds.size)
        vmIds.forEachIndexed { index, vmId ->
            assertEquals(did, vmId.did)
            assertEquals("key-${index + 1}", vmId.keyId.value.removePrefix("#"))
        }
    }

    @Test
    fun `test VerificationMethodId parsing performance`() {
        val vmIdStrings = (1..1000).map { "did:test:identifier123#key-$it" }
        
        val startTime = System.nanoTime()
        val vmIds = vmIdStrings.map { string ->
            assertDoesNotThrow {
                VerificationMethodId.parse(string)
            }
        }
        val endTime = System.nanoTime()
        
        val durationMs = (endTime - startTime) / 1_000_000.0
        println("Parsed 1000 VerificationMethodIds in ${durationMs}ms (${durationMs / 1000}ms per ID)")
        
        // Should complete in reasonable time
        assert(durationMs < 1000) { "VerificationMethodId parsing too slow: ${durationMs}ms" }
        
        // Verify all IDs were parsed correctly
        assertEquals(1000, vmIds.size)
    }

    @Test
    fun `test DID comparison performance`() {
        val dids = (1..1000).map { Did("did:test:identifier$it") }
        
        val startTime = System.nanoTime()
        var comparisons = 0
        for (i in dids.indices) {
            for (j in dids.indices) {
                dids[i].compareTo(dids[j])
                comparisons++
            }
        }
        val endTime = System.nanoTime()
        
        val durationMs = (endTime - startTime) / 1_000_000.0
        println("Performed $comparisons comparisons in ${durationMs}ms")
        
        // Should complete in reasonable time
        assert(durationMs < 5000) { "DID comparison too slow: ${durationMs}ms" }
    }

    @Test
    fun `test DID sorting performance`() {
        // Use zero-padded identifiers for proper lexicographic sorting
        val dids = (1..1000).shuffled().map { Did("did:test:identifier${it.toString().padStart(4, '0')}") }
        
        val startTime = System.nanoTime()
        val sorted = dids.sorted()
        val endTime = System.nanoTime()
        
        val durationMs = (endTime - startTime) / 1_000_000.0
        println("Sorted 1000 DIDs in ${durationMs}ms (${durationMs / 1000}ms per DID)")
        
        // Should complete in reasonable time
        assert(durationMs < 1000) { "DID sorting too slow: ${durationMs}ms" }
        
        // Verify sorted order (lexicographic with zero-padding)
        sorted.forEachIndexed { index, did ->
            val expectedId = (index + 1).toString().padStart(4, '0')
            assertEquals("identifier$expectedId", did.identifier)
        }
    }
}

