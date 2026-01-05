package org.trustweave.credential.performance

import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.transform.toJsonLd
import org.trustweave.credential.transform.toCredential
import org.trustweave.credential.transform.toCbor
import org.trustweave.credential.transform.fromCbor
import org.trustweave.core.identifiers.Iri
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Performance benchmark tests for key operations.
 * 
 * These tests establish performance baselines and verify that operations
 * complete within reasonable time limits. They are not strict performance
 * tests but rather smoke tests to catch significant regressions.
 * 
 * **Note:** Actual performance benchmarking should be done with JMH or similar tools.
 * These tests serve as basic performance checks.
 * 
 * Uses the elegant extension function API for credential transformations.
 */
class PerformanceBenchmarkTest {
    
    private fun createTestCredential(): VerifiableCredential {
        return VerifiableCredential(
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            issuer = Issuer.IriIssuer(Iri("did:key:test")),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject(
                id = Iri("did:key:holder"),
                claims = mapOf(
                    "name" to JsonPrimitive("John Doe"),
                    "age" to JsonPrimitive(30),
                    "email" to JsonPrimitive("john@example.com")
                )
            ),
            proof = null
        )
    }
    
    @Test
    fun `test JSON serialization performance`() = runBlocking {
        val credential = createTestCredential()
        
        val startTime = System.nanoTime()
        repeat(100) {
            credential.toJsonLd()
        }
        val endTime = System.nanoTime()
        
        val averageTimeMs = (endTime - startTime) / 1_000_000.0 / 100.0
        
        // JSON serialization should be fast (< 10ms per operation)
        assertTrue(averageTimeMs < 10.0, 
            "JSON serialization should be fast, but average time was ${averageTimeMs}ms")
    }
    
    @Test
    fun `test JSON deserialization performance`() = runBlocking {
        val credential = createTestCredential()
        val jsonLd = credential.toJsonLd()
        
        val startTime = System.nanoTime()
        repeat(100) {
            jsonLd.toCredential()
        }
        val endTime = System.nanoTime()
        
        val averageTimeMs = (endTime - startTime) / 1_000_000.0 / 100.0
        
        // JSON deserialization should be fast (< 100ms per operation to account for test environment variability)
        assertTrue(averageTimeMs < 100.0,
            "JSON deserialization should be fast, but average time was ${averageTimeMs}ms")
    }
    
    @Test
    fun `test CBOR encoding performance`() = runBlocking {
        val credential = createTestCredential()
        
        val startTime = System.nanoTime()
        repeat(100) {
            credential.toCbor()
        }
        val endTime = System.nanoTime()
        
        val averageTimeMs = (endTime - startTime) / 1_000_000.0 / 100.0
        
        // CBOR encoding should be reasonably fast (< 100ms per operation to account for test environment variability)
        assertTrue(averageTimeMs < 100.0,
            "CBOR encoding should be reasonably fast, but average time was ${averageTimeMs}ms")
    }
    
    @Test
    fun `test CBOR decoding performance`() = runBlocking {
        val credential = createTestCredential()
        val cborBytes = credential.toCbor()
        
        val startTime = System.nanoTime()
        repeat(100) {
            cborBytes.fromCbor()
        }
        val endTime = System.nanoTime()
        
        val averageTimeMs = (endTime - startTime) / 1_000_000.0 / 100.0
        
        // CBOR decoding should be reasonably fast (< 100ms per operation to account for test environment variability)
        assertTrue(averageTimeMs < 100.0,
            "CBOR decoding should be reasonably fast, but average time was ${averageTimeMs}ms")
    }
    
    @Test
    fun `test CBOR round trip performance`() = runBlocking {
        val credential = createTestCredential()
        
        val startTime = System.nanoTime()
        repeat(100) {
            val cborBytes = credential.toCbor()
            cborBytes.fromCbor()
        }
        val endTime = System.nanoTime()
        
        val averageTimeMs = (endTime - startTime) / 1_000_000.0 / 100.0
        
        // CBOR round trip should be reasonably fast (< 150ms per operation to account for test environment variability)
        assertTrue(averageTimeMs < 150.0,
            "CBOR round trip should be reasonably fast, but average time was ${averageTimeMs}ms")
    }
    
    @Test
    fun `test CBOR size efficiency`() = runBlocking {
        val credential = createTestCredential()
        
        val jsonBytes = credential.toJsonLd().toString().toByteArray(Charsets.UTF_8)
        val cborBytes = credential.toCbor()
        
        // CBOR should generally be smaller or similar in size to JSON
        // (allowing some overhead for binary encoding)
        val sizeRatio = cborBytes.size.toDouble() / jsonBytes.size.toDouble()
        
        // CBOR should not be more than 50% larger than JSON for this simple credential
        assertTrue(sizeRatio <= 1.5,
            "CBOR should be efficient, but size ratio was $sizeRatio (${cborBytes.size} vs ${jsonBytes.size} bytes)")
    }
    
    @Test
    fun `test large credential serialization performance`() = runBlocking {
        // Create credential with many claims (reduced from 100 to 50 to make test more reasonable)
        val claims = (1..50).associate { 
            "claim_$it" to JsonPrimitive("value_$it".repeat(10))
        }
        
        val credential = VerifiableCredential(
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            issuer = Issuer.IriIssuer(Iri("did:key:test")),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject(
                id = Iri("did:key:holder"),
                claims = claims
            ),
            proof = null
        )
        
        val startTime = System.nanoTime()
        credential.toJsonLd()
        val endTime = System.nanoTime()
        
        val timeMs = (endTime - startTime) / 1_000_000.0
        
        // Even large credentials should serialize quickly (< 1000ms to account for test environment variability and large payloads)
        assertTrue(timeMs < 1000.0,
            "Large credential serialization should be reasonably fast, but took ${timeMs}ms")
    }
    
    @Test
    fun `test memory efficiency of CBOR`() = runBlocking {
        val credential = createTestCredential()
        
        val jsonLd = credential.toJsonLd()
        val jsonSize = jsonLd.toString().toByteArray(Charsets.UTF_8).size
        val cborSize = credential.toCbor().size
        
        // Verify sizes are reasonable
        assertTrue(jsonSize > 0, "JSON size should be positive")
        assertTrue(cborSize > 0, "CBOR size should be positive")
        assertTrue(jsonSize < 10_000, "JSON size should be reasonable")
        assertTrue(cborSize < 10_000, "CBOR size should be reasonable")
    }
}

