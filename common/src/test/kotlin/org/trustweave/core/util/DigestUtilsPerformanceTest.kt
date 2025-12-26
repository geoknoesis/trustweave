package org.trustweave.core.util

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Performance tests for DigestUtils with large inputs.
 */
class DigestUtilsPerformanceTest {

    @Test
    fun `test canonicalization performance with large JSON`() {
        val largeJsonElement: JsonElement = buildJsonObject {
            repeat(1000) { i ->
                put("key$i", "value$i".repeat(10))
            }
        }

        val startTime = System.currentTimeMillis()
        val canonical = DigestUtils.canonicalizeJson(largeJsonElement)
        val duration = System.currentTimeMillis() - startTime

        assertNotNull(canonical)
        // Should complete in reasonable time (< 1 second for 1000 keys)
        assertTrue(duration < 1000, "Canonicalization took too long: ${duration}ms")
    }

    @Test
    fun `test digest performance with large JSON`() {
        val largeJsonElement: JsonElement = buildJsonObject {
            repeat(500) { i ->
                put("key$i", "value$i".repeat(20))
            }
        }

        DigestUtils.enableDigestCache = true
        DigestUtils.clearCache()

        // First computation (cache miss)
        val startTime1 = System.currentTimeMillis()
        val digest1 = DigestUtils.sha256DigestMultibase(largeJsonElement)
        val duration1 = System.currentTimeMillis() - startTime1

        // Second computation (cache hit)
        val startTime2 = System.currentTimeMillis()
        val digest2 = DigestUtils.sha256DigestMultibase(largeJsonElement)
        val duration2 = System.currentTimeMillis() - startTime2

        assertEquals(digest1, digest2)
        // Cached computation should be significantly faster
        assertTrue(duration2 < duration1, 
            "Cached computation should be faster. First: ${duration1}ms, Second: ${duration2}ms")
    }

    @Test
    fun `test digest performance with very large string`() {
        val veryLargeString = "a".repeat(100000) // 100KB

        val startTime = System.currentTimeMillis()
        val digest = DigestUtils.sha256DigestMultibase(veryLargeString)
        val duration = System.currentTimeMillis() - startTime

        assertTrue(digest.startsWith("u"))
        // Should complete in reasonable time (< 500ms for 100KB)
        assertTrue(duration < 500, "Digest computation took too long: ${duration}ms")
    }

    @Test
    fun `test cache eviction performance`() {
        DigestUtils.enableDigestCache = true
        DigestUtils.maxCacheSize = 100
        DigestUtils.clearCache()

        // Fill cache beyond max size
        val startTime = System.currentTimeMillis()
        repeat(200) { i ->
            val json = """{"index": $i}"""
            DigestUtils.sha256DigestMultibase(json)
        }
        val duration = System.currentTimeMillis() - startTime

        // Should complete in reasonable time
        assertTrue(duration < 2000, "Cache eviction took too long: ${duration}ms")
        // Cache should be at max size
        assertTrue(DigestUtils.getCacheSize() <= DigestUtils.maxCacheSize)
    }

    @Test
    fun `test nested JSON canonicalization performance`() {
        val deeplyNested = buildString {
            append("{")
            repeat(10) { i ->
                if (i > 0) append(",")
                append("\"level$i\": {")
                repeat(10) { j ->
                    if (j > 0) append(",")
                    append("\"key$j\": \"value$j\"")
                }
                append("}")
            }
            append("}")
        }

        val startTime = System.currentTimeMillis()
        val canonical = DigestUtils.canonicalizeJson(deeplyNested)
        val duration = System.currentTimeMillis() - startTime

        assertNotNull(canonical)
        // Should complete quickly even with deep nesting
        assertTrue(duration < 500, "Nested canonicalization took too long: ${duration}ms")
    }
}
