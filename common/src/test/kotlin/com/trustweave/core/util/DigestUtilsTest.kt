package com.trustweave.core.util

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DigestUtilsTest {

    @Test
    fun `canonicalizeJson should produce same output for same content with different key order`() {
        val json1 = """{"b":2,"a":1,"c":3}"""
        val json2 = """{"c":3,"a":1,"b":2}"""
        val json3 = """{"a":1,"b":2,"c":3}"""

        val canonical1 = DigestUtils.canonicalizeJson(json1)
        val canonical2 = DigestUtils.canonicalizeJson(json2)
        val canonical3 = DigestUtils.canonicalizeJson(json3)

        assertEquals(canonical1, canonical2)
        assertEquals(canonical2, canonical3)
        assertEquals("""{"a":1,"b":2,"c":3}""", canonical1)
    }

    @Test
    fun `canonicalizeJson should handle nested objects`() {
        val json = """{"z":1,"a":{"c":3,"b":2}}"""
        val canonical = DigestUtils.canonicalizeJson(json)
        assertEquals("""{"a":{"b":2,"c":3},"z":1}""", canonical)
    }

    @Test
    fun `canonicalizeJson should handle arrays`() {
        val json = """{"arr":[3,1,2],"key":"value"}"""
        val canonical = DigestUtils.canonicalizeJson(json)
        assertEquals("""{"arr":[3,1,2],"key":"value"}""", canonical)
    }

    @Test
    fun `canonicalizeJson should handle strings with special characters`() {
        val json = """{"key":"value with \"quotes\" and\nnewlines"}"""
        val canonical = DigestUtils.canonicalizeJson(json)
        // Should escape properly
        assertTrue(canonical.contains("\\\""), "Should escape quotes in JSON strings")
    }

    @Test
    fun `sha256DigestMultibase should produce consistent digests`() {
        val json1 = """{"b":2,"a":1}"""
        val json2 = """{"a":1,"b":2}"""

        val digest1 = DigestUtils.sha256DigestMultibase(json1)
        val digest2 = DigestUtils.sha256DigestMultibase(json2)

        // After canonicalization, they should be the same
        assertEquals(digest1, digest2)
        assertTrue(digest1.startsWith("u"), "Digest should start with multibase prefix 'u'")
    }

    @Test
    fun `sha256DigestMultibase should produce different digests for different content`() {
        val json1 = """{"a":1}"""
        val json2 = """{"a":2}"""

        val digest1 = DigestUtils.sha256DigestMultibase(json1)
        val digest2 = DigestUtils.sha256DigestMultibase(json2)

        assertNotEquals(digest1, digest2)
    }

    @Test
    fun `sha256DigestMultibase with JsonElement should work`() {
        val element1 = buildJsonObject {
            put("a", 1)
            put("b", 2)
        }
        val element2 = buildJsonObject {
            put("b", 2)
            put("a", 1)
        }

        val digest1 = DigestUtils.sha256DigestMultibase(element1)
        val digest2 = DigestUtils.sha256DigestMultibase(element2)

        assertEquals(digest1, digest2)
    }

    @Test
    fun `sha256DigestMultibase produces expected format`() {
        val data = "test"
        val digest = DigestUtils.sha256DigestMultibase(data)

        // Should start with 'u' (multibase prefix for base58btc)
        assertTrue(digest.startsWith("u"), "Digest should start with multibase prefix 'u'")
        // Should be base58 encoded (only contains base58 characters)
        val base58Chars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        val encodedPart = digest.substring(1)
        assertTrue(encodedPart.all { it in base58Chars }, 
            "Encoded part should only contain base58 characters")
    }

    @Test
    fun `canonicalizeJson handles empty object`() {
        val json = "{}"
        val canonical = DigestUtils.canonicalizeJson(json)
        assertEquals("{}", canonical)
    }

    @Test
    fun `canonicalizeJson handles empty array`() {
        val json = """{"arr":[]}"""
        val canonical = DigestUtils.canonicalizeJson(json)
        assertEquals("""{"arr":[]}""", canonical)
    }

    @Test
    fun `canonicalizeJson handles null values`() {
        val json = """{"key":null}"""
        val canonical = DigestUtils.canonicalizeJson(json)
        assertEquals("""{"key":null}""", canonical)
    }

    @Test
    fun `canonicalizeJson handles boolean values`() {
        val json = """{"true":true,"false":false}"""
        val canonical = DigestUtils.canonicalizeJson(json)
        assertEquals("""{"false":false,"true":true}""", canonical)
    }

    @Test
    fun `canonicalizeJson handles numeric values`() {
        val json = """{"int":42,"float":3.14,"negative":-1}"""
        val canonical = DigestUtils.canonicalizeJson(json)
        assertTrue(canonical.contains("42"))
        assertTrue(canonical.contains("3.14"))
        assertTrue(canonical.contains("-1"))
    }
}

