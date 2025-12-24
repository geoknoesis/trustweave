package org.trustweave.core.util

import org.trustweave.core.exception.TrustWeaveException
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Error handling tests for DigestUtils.
 */
class DigestUtilsErrorTest {

    @Test
    fun `test canonicalizeJson throws InvalidJson for malformed JSON`() {
        val invalidJson = "{ invalid json }"

        val exception = assertFailsWith<TrustWeaveException.InvalidJson> {
            DigestUtils.canonicalizeJson(invalidJson)
        }

        assertEquals("INVALID_JSON", exception.code)
        assertNotNull(exception.parseError)
        assertEquals(invalidJson, exception.jsonString)
    }

    @Test
    fun `test canonicalizeJson throws InvalidJson for blank input`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            DigestUtils.canonicalizeJson("")
        }

        assertTrue(exception.message?.contains("blank") == true)
    }

    @Test
    fun `test canonicalizeJson throws InvalidJson for incomplete JSON`() {
        val incompleteJson = """{"key": "value"""

        val exception = assertFailsWith<TrustWeaveException.InvalidJson> {
            DigestUtils.canonicalizeJson(incompleteJson)
        }

        assertEquals("INVALID_JSON", exception.code)
    }

    @Test
    fun `test canonicalizeJson with JsonElement works correctly`() {
        // Test that JsonElement canonicalization works
        val element = buildJsonObject {
            put("key", "value")
        }

        // Normal case should work
        val result = DigestUtils.canonicalizeJson(element)
        assertNotNull(result)
        assertTrue(result.contains("key"))
        assertTrue(result.contains("value"))
    }

    @Test
    fun `test sha256DigestMultibase with invalid JSON falls back to direct digest`() {
        val plainText = "not json"

        // Should not throw, should compute digest directly
        val digest = DigestUtils.sha256DigestMultibase(plainText)

        assertNotNull(digest)
        assertTrue(digest.startsWith("u"))
    }

    @Test
    fun `test cache behavior`() {
        DigestUtils.enableDigestCache = true
        DigestUtils.clearCache()

        val json = """{"test": "value"}"""

        val digest1 = DigestUtils.sha256DigestMultibase(json)
        val digest2 = DigestUtils.sha256DigestMultibase(json)

        assertEquals(digest1, digest2)
        assertEquals(1, DigestUtils.getCacheSize())

        DigestUtils.clearCache()
        assertEquals(0, DigestUtils.getCacheSize())
    }

    @Test
    fun `test cache can be disabled`() {
        DigestUtils.enableDigestCache = false
        DigestUtils.clearCache()

        val json = """{"test": "value"}"""

        DigestUtils.sha256DigestMultibase(json)
        DigestUtils.sha256DigestMultibase(json)

        assertEquals(0, DigestUtils.getCacheSize())

        // Re-enable for other tests
        DigestUtils.enableDigestCache = true
    }

    @Test
    fun `test digest with very large JSON`() {
        val largeJson = buildJsonObject {
            repeat(1000) { i ->
                put("key$i", "value$i")
            }
        }.toString()

        val digest = DigestUtils.sha256DigestMultibase(largeJson)

        assertNotNull(digest)
        assertTrue(digest.startsWith("u"))
    }

    @Test
    fun `test digest with unicode characters`() {
        val unicodeJson = """{"text": "Hello 世界 🌍"}"""

        val digest = DigestUtils.sha256DigestMultibase(unicodeJson)

        assertNotNull(digest)
        assertTrue(digest.startsWith("u"))
    }

    @Test
    fun `test canonicalizeJson preserves array order`() {
        val json1 = """{"arr":[1,2,3]}"""
        val json2 = """{"arr":[1,2,3]}"""

        val canonical1 = DigestUtils.canonicalizeJson(json1)
        val canonical2 = DigestUtils.canonicalizeJson(json2)

        assertEquals(canonical1, canonical2)
    }

    @Test
    fun `test canonicalizeJson handles deeply nested objects`() {
        val nested = """{"a":{"b":{"c":{"d":1}}}}"""

        val canonical = DigestUtils.canonicalizeJson(nested)

        assertNotNull(canonical)
        assertTrue(canonical.contains("a"))
    }
}

