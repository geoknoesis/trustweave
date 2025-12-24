package org.trustweave.kms

import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.results.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlin.test.*
import kotlin.system.measureTimeMillis

/**
 * Performance tests for KeyManagementService implementations.
 * 
 * These tests measure operation performance and can be used to:
 * - Identify performance regressions
 * - Benchmark different implementations
 * - Validate caching effectiveness
 * 
 * **Note**: These tests use the InMemory implementation as a baseline.
 * For cloud KMS plugins, use integration tests with actual services.
 */
abstract class KeyManagementServicePerformanceTest {
    
    abstract fun createKms(): KeyManagementService
    
    private lateinit var kms: KeyManagementService
    
    @BeforeEach
    fun setUp() {
        kms = createKms()
    }
    
    @Test
    fun `test key generation performance`() = runBlocking {
        val iterations = 10
        val times = mutableListOf<Long>()
        
        repeat(iterations) {
            val time = kotlin.system.measureTimeMillis {
                val result = kms.generateKey(Algorithm.Ed25519)
                assertTrue(result is GenerateKeyResult.Success)
            }
            times.add(time)
        }
        
        val avgTime = times.average()
        val maxTime = times.maxOrNull() ?: 0L
        val minTime = times.minOrNull() ?: 0L
        
        println("Key Generation Performance:")
        println("  Average: ${avgTime}ms")
        println("  Min: ${minTime}ms")
        println("  Max: ${maxTime}ms")
        
        // Assert reasonable performance (adjust threshold based on implementation)
        assertTrue(avgTime < 1000, "Average key generation should be under 1 second")
    }
    
    @Test
    fun `test signing performance`() = runBlocking {
        // Generate a key first
        val generateResult = kms.generateKey(Algorithm.Ed25519)
        assertTrue(generateResult is GenerateKeyResult.Success)
        val keyId = generateResult.keyHandle.id
        
        val data = "test data".toByteArray()
        val iterations = 100
        val times = mutableListOf<Long>()
        
        repeat(iterations) {
            val time = kotlin.system.measureTimeMillis {
                val result = kms.sign(keyId, data)
                assertTrue(result is SignResult.Success)
            }
            times.add(time)
        }
        
        val avgTime = times.average()
        val maxTime = times.maxOrNull() ?: 0L
        val minTime = times.minOrNull() ?: 0L
        
        println("Signing Performance:")
        println("  Average: ${avgTime}ms")
        println("  Min: ${minTime}ms")
        println("  Max: ${maxTime}ms")
        
        // Assert reasonable performance
        assertTrue(avgTime < 500, "Average signing should be under 500ms")
    }
    
    @Test
    fun `test concurrent key generation`() = runBlocking {
        val concurrentOperations = 20
        val time = kotlin.system.measureTimeMillis {
            val results = (1..concurrentOperations).map { i ->
                async {
                    kms.generateKey(Algorithm.Ed25519)
                }
            }.awaitAll()
            
            results.forEach { result ->
                assertTrue(result is GenerateKeyResult.Success)
            }
        }
        
        val avgTimePerOperation = time.toDouble() / concurrentOperations
        
        println("Concurrent Key Generation:")
        println("  Total time: ${time}ms")
        println("  Operations: $concurrentOperations")
        println("  Average per operation: ${avgTimePerOperation}ms")
        
        assertTrue(avgTimePerOperation < 200, "Average concurrent operation should be under 200ms")
    }
    
    @Test
    fun `test concurrent signing`() = runBlocking {
        // Generate a key first
        val generateResult = kms.generateKey(Algorithm.Ed25519)
        assertTrue(generateResult is GenerateKeyResult.Success)
        val keyId = generateResult.keyHandle.id
        
        val data = "test data".toByteArray()
        val concurrentOperations = 50
        val time = kotlin.system.measureTimeMillis {
            val results = (1..concurrentOperations).map {
                async {
                    kms.sign(keyId, data)
                }
            }.awaitAll()
            
            results.forEach { result ->
                assertTrue(result is SignResult.Success)
            }
        }
        
        val avgTimePerOperation = time.toDouble() / concurrentOperations
        
        println("Concurrent Signing:")
        println("  Total time: ${time}ms")
        println("  Operations: $concurrentOperations")
        println("  Average per operation: ${avgTimePerOperation}ms")
        
        assertTrue(avgTimePerOperation < 100, "Average concurrent signing should be under 100ms")
    }
    
    @Test
    fun `test public key retrieval performance`() = runBlocking {
        // Generate a key first
        val generateResult = kms.generateKey(Algorithm.Ed25519)
        assertTrue(generateResult is GenerateKeyResult.Success)
        val keyId = generateResult.keyHandle.id
        
        val iterations = 100
        val times = mutableListOf<Long>()
        
        repeat(iterations) {
            val time = kotlin.system.measureTimeMillis {
                val result = kms.getPublicKey(keyId)
                assertTrue(result is GetPublicKeyResult.Success)
            }
            times.add(time)
        }
        
        val avgTime = times.average()
        val maxTime = times.maxOrNull() ?: 0L
        val minTime = times.minOrNull() ?: 0L
        
        println("Public Key Retrieval Performance:")
        println("  Average: ${avgTime}ms")
        println("  Min: ${minTime}ms")
        println("  Max: ${maxTime}ms")
        
        // With caching, this should be very fast
        assertTrue(avgTime < 50, "Average public key retrieval should be under 50ms (with caching)")
    }
    
    @Test
    fun `test cache effectiveness`() = runBlocking {
        // Generate a key first
        val generateResult = kms.generateKey(Algorithm.Ed25519)
        assertTrue(generateResult is GenerateKeyResult.Success)
        val keyId = generateResult.keyHandle.id
        
        // First retrieval (cache miss)
        val firstTime = kotlin.system.measureTimeMillis {
            val result = kms.getPublicKey(keyId)
            assertTrue(result is GetPublicKeyResult.Success)
        }
        
        // Subsequent retrievals (cache hits)
        val cachedTimes = mutableListOf<Long>()
        repeat(10) {
            val time = kotlin.system.measureTimeMillis {
                val result = kms.getPublicKey(keyId)
                assertTrue(result is GetPublicKeyResult.Success)
            }
            cachedTimes.add(time)
        }
        
        val avgCachedTime = cachedTimes.average()
        
        println("Cache Effectiveness:")
        println("  First retrieval (cache miss): ${firstTime}ms")
        println("  Average cached retrieval: ${avgCachedTime}ms")
        if (firstTime > 0) {
            val speedup = firstTime.toDouble() / avgCachedTime
            println("  Speedup: ${speedup}x")
        }
        
        // Cached retrievals should be faster (or at least not slower)
        // Handle edge case where firstTime is 0 (very fast operations like in-memory KMS)
        if (firstTime == 0L) {
            // If first retrieval was 0ms, just verify cached retrievals are also fast (< 10ms)
            assertTrue(avgCachedTime < 10, "Cached retrievals should be fast (< 10ms) for very fast operations")
        } else {
            assertTrue(avgCachedTime <= firstTime * 1.5, "Cached retrievals should not be significantly slower")
        }
    }
}


