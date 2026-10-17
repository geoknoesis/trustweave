package org.trustweave.core.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DigestCacheConfigurationTest {
    private var previousEnabled = true
    private var previousCapacity = 1000

    @BeforeEach
    fun setUp() {
        previousEnabled = DigestUtils.isDigestCacheEnabled
        previousCapacity = DigestUtils.maxCacheSize
        DigestUtils.isDigestCacheEnabled = true
        DigestUtils.maxCacheSize = 16
        DigestUtils.clearCache()
    }

    @AfterEach
    fun tearDown() {
        DigestUtils.isDigestCacheEnabled = previousEnabled
        DigestUtils.maxCacheSize = previousCapacity
        DigestUtils.clearCache()
    }

    @Test
    fun `zero capacity clears the cache and prevents subsequent insertion`() {
        DigestUtils.sha256DigestMultibase("plain text")
        DigestUtils.sha256DigestMultibase(JsonPrimitive("json value"))
        assertEquals(2, DigestUtils.cacheSize)

        DigestUtils.maxCacheSize = 0
        DigestUtils.sha256DigestMultibase("plain text")
        DigestUtils.sha256DigestMultibase(JsonPrimitive("json value"))
        assertEquals(0, DigestUtils.cacheSize)
    }

    @Test
    fun `invalid capacity leaves settings and cached values unchanged`() {
        DigestUtils.sha256DigestMultibase("plain text")
        assertFailsWith<IllegalArgumentException> { DigestUtils.maxCacheSize = -1 }
        assertEquals(16, DigestUtils.maxCacheSize)
        assertEquals(1, DigestUtils.cacheSize)
    }

    @Test
    fun `resizing and disabling during digest computation preserves results and clears entries`() =
        runBlocking {
            val inputs = (0 until 32).map { "concurrent digest input $it" }
            val expected =
                inputs.associateWith {
                    "z" + MessageDigest.getInstance("SHA-256").digest(it.toByteArray(Charsets.UTF_8)).encodeBase58()
                }
            val start = CompletableDeferred<Unit>()
            val disabled = CompletableDeferred<Unit>()
            val workers =
                (0 until 4).map { worker ->
                    async(Dispatchers.Default) {
                        start.await()
                        repeat(200) { iteration ->
                            val input = inputs[(worker + iteration) % inputs.size]
                            assertEquals(expected.getValue(input), DigestUtils.sha256DigestMultibase(input))
                            yield()
                        }
                        disabled.await()
                        // Completed calls after disabling must never repopulate the cache.
                        inputs.forEach { input ->
                            assertEquals(expected.getValue(input), DigestUtils.sha256DigestMultibase(input))
                        }
                    }
                }
            val configure =
                async(Dispatchers.Default) {
                    start.await()
                    repeat(200) { iteration ->
                        DigestUtils.maxCacheSize = iteration % 17
                        DigestUtils.isDigestCacheEnabled = iteration % 3 != 0
                        yield()
                    }
                    DigestUtils.isDigestCacheEnabled = false
                    disabled.complete(Unit)
                }
            start.complete(Unit)
            (workers + configure).awaitAll()
            assertEquals(0, DigestUtils.cacheSize)

            DigestUtils.isDigestCacheEnabled = true
            DigestUtils.maxCacheSize = 1
            inputs.forEach { DigestUtils.sha256DigestMultibase(it) }
            assertEquals(1, DigestUtils.cacheSize)
        }
}
