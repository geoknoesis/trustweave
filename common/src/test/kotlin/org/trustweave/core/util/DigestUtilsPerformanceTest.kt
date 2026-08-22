package org.trustweave.core.util

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards against [DigestUtils] becoming pathological on large or deeply nested input, and pins the
 * behaviour of its digest cache.
 *
 * These are not benchmarks. The timing assertions exist to catch algorithmic blow-up — an accidental
 * quadratic in canonicalization would take minutes, not milliseconds — so the ceiling is deliberately
 * far above any plausible healthy runtime. Tight wall-clock bounds measure the machine and the load
 * on it rather than the code, and fail in CI for reasons that have nothing to do with a change.
 *
 * The cache is asserted through [DigestUtils.cacheSize] rather than by timing two calls against each
 * other. That comparison used to read `duration2 < duration1` at millisecond resolution, which is
 * false whenever both round to 0 — so the faster the machine, the more likely it failed.
 */
class DigestUtilsPerformanceTest {
    /** Far above any healthy runtime; only catches genuine blow-up. */
    private val pathologicalMs = 10_000L

    private var cacheEnabledBefore: Boolean = true
    private var maxCacheSizeBefore: Int = 1000

    @BeforeEach
    fun captureCacheSettings() {
        // These are process-wide mutable statics. The previous version of this file changed them
        // and never put them back, so whichever test ran next inherited a 100-entry cache.
        cacheEnabledBefore = DigestUtils.isDigestCacheEnabled
        maxCacheSizeBefore = DigestUtils.maxCacheSize
    }

    @AfterEach
    fun restoreCacheSettings() {
        DigestUtils.isDigestCacheEnabled = cacheEnabledBefore
        DigestUtils.maxCacheSize = maxCacheSizeBefore
        DigestUtils.clearCache()
    }

    @Test
    fun `canonicalizing a large object does not blow up`() {
        val large: JsonElement =
            buildJsonObject {
                repeat(1000) { i -> put("key$i", "value$i".repeat(10)) }
            }

        val start = System.currentTimeMillis()
        val canonical = DigestUtils.canonicalizeJson(large)
        val elapsed = System.currentTimeMillis() - start

        assertNotNull(canonical)
        assertTrue(elapsed < pathologicalMs, "Canonicalizing 1000 keys took ${elapsed}ms")
    }

    @Test
    fun `digesting the same element twice reuses one cache entry`() {
        DigestUtils.isDigestCacheEnabled = true
        DigestUtils.clearCache()
        val element: JsonElement =
            buildJsonObject {
                repeat(500) { i -> put("key$i", "value$i".repeat(20)) }
            }

        assertEquals(0, DigestUtils.cacheSize, "clearCache should leave nothing behind")

        val first = DigestUtils.sha256DigestMultibase(element)
        assertEquals(1, DigestUtils.cacheSize, "The first digest should be cached")

        val second = DigestUtils.sha256DigestMultibase(element)

        assertEquals(first, second)
        assertEquals(
            1,
            DigestUtils.cacheSize,
            "The same element must map to the same key rather than adding a second entry",
        )
    }

    @Test
    fun `distinct elements are cached separately`() {
        DigestUtils.isDigestCacheEnabled = true
        DigestUtils.clearCache()

        DigestUtils.sha256DigestMultibase(buildJsonObject { put("a", 1) })
        DigestUtils.sha256DigestMultibase(buildJsonObject { put("b", 2) })

        assertEquals(2, DigestUtils.cacheSize, "Different inputs must not collide onto one entry")
    }

    @Test
    fun `digesting a large string does not blow up`() {
        val veryLarge = "a".repeat(100_000)

        val start = System.currentTimeMillis()
        val digest = DigestUtils.sha256DigestMultibase(veryLarge)
        val elapsed = System.currentTimeMillis() - start

        assertTrue(digest.startsWith("z"))
        assertTrue(elapsed < pathologicalMs, "Digesting 100KB took ${elapsed}ms")
    }

    @Test
    fun `the cache never grows past its configured maximum`() {
        DigestUtils.isDigestCacheEnabled = true
        DigestUtils.maxCacheSize = 100
        DigestUtils.clearCache()

        repeat(200) { i -> DigestUtils.sha256DigestMultibase("""{"index": $i}""") }

        assertTrue(
            DigestUtils.cacheSize <= DigestUtils.maxCacheSize,
            "Cache holds ${DigestUtils.cacheSize} entries, max is ${DigestUtils.maxCacheSize}",
        )
    }

    @Test
    fun `canonicalizing nested objects does not blow up`() {
        val nested =
            buildString {
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

        val start = System.currentTimeMillis()
        val canonical = DigestUtils.canonicalizeJson(nested)
        val elapsed = System.currentTimeMillis() - start

        assertNotNull(canonical)
        assertTrue(elapsed < pathologicalMs, "Nested canonicalization took ${elapsed}ms")
    }
}
