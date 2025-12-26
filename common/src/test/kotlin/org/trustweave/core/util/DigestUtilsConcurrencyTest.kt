package org.trustweave.core.util

import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Concurrent access tests for DigestUtils cache.
 */
class DigestUtilsConcurrencyTest {

    @Test
    fun `test concurrent digest computation with cache`() = runBlocking {
        DigestUtils.enableDigestCache = true
        DigestUtils.maxCacheSize = 1000
        DigestUtils.clearCache()

        val json = """{"test": "value"}"""
        val iterations = 100
        val threads = 10

        val results = (1..threads).map { threadId ->
            async(Dispatchers.Default) {
                repeat(iterations) {
                    val digest = DigestUtils.sha256DigestMultibase(json)
                    assertTrue(digest.startsWith("u"))
                }
                threadId
            }
        }

        results.awaitAll()

        // All threads should have computed the same digest
        assertEquals(1, DigestUtils.getCacheSize())
    }

    @Test
    fun `test concurrent digest computation with different inputs`() = runBlocking {
        DigestUtils.enableDigestCache = true
        DigestUtils.maxCacheSize = 1000
        DigestUtils.clearCache()

        val threads = 20
        val inputsPerThread = 10

        val results = (1..threads).map { threadId ->
            async(Dispatchers.Default) {
                repeat(inputsPerThread) { i ->
                    val json = """{"thread": $threadId, "iteration": $i}"""
                    val digest = DigestUtils.sha256DigestMultibase(json)
                    assertTrue(digest.startsWith("u"))
                }
                threadId
            }
        }

        results.awaitAll()

        // Should have cached all unique inputs
        val expectedCacheSize = threads * inputsPerThread
        assertTrue(DigestUtils.getCacheSize() <= expectedCacheSize)
    }

    @Test
    fun `test concurrent cache access and eviction`() = runBlocking {
        DigestUtils.enableDigestCache = true
        DigestUtils.maxCacheSize = 50
        DigestUtils.clearCache()

        val threads = 10
        val inputsPerThread = 20

        val results = (1..threads).map { threadId ->
            async(Dispatchers.Default) {
                repeat(inputsPerThread) { i ->
                    val json = """{"thread": $threadId, "iteration": $i}"""
                    DigestUtils.sha256DigestMultibase(json)
                }
                threadId
            }
        }

        results.awaitAll()

        // Cache should be at max size or less (LRU eviction)
        assertTrue(DigestUtils.getCacheSize() <= DigestUtils.maxCacheSize)
    }

    @Test
    fun `test concurrent canonicalization`() = runBlocking {
        val json1 = """{"b":2,"a":1}"""
        val json2 = """{"a":1,"b":2}"""
        val threads = 20

        val results = (1..threads).map {
            async(Dispatchers.Default) {
                val canonical1 = DigestUtils.canonicalizeJson(json1)
                val canonical2 = DigestUtils.canonicalizeJson(json2)
                assertEquals(canonical1, canonical2)
            }
        }

        results.awaitAll()
    }

    @Test
    fun `test cache thread safety when disabled`() = runBlocking {
        DigestUtils.enableDigestCache = false
        DigestUtils.clearCache()

        val json = """{"test": "value"}"""
        val threads = 10

        val results = (1..threads).map {
            async(Dispatchers.Default) {
                repeat(10) {
                    val digest = DigestUtils.sha256DigestMultibase(json)
                    assertTrue(digest.startsWith("u"))
                }
            }
        }

        results.awaitAll()

        assertEquals(0, DigestUtils.getCacheSize())
        
        // Re-enable for other tests
        DigestUtils.enableDigestCache = true
    }
}

