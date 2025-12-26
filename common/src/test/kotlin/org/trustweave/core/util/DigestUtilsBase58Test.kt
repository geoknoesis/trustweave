package org.trustweave.core.util

import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Edge case tests for Base58 encoding in DigestUtils.
 */
class DigestUtilsBase58Test {

    @Test
    fun `test digest with all zero bytes produces correct base58`() {
        // Create a string that will produce a digest with leading zeros
        // SHA-256 of empty string: e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        val data = ""
        val digest = DigestUtils.sha256DigestMultibase(data)
        
        assertTrue(digest.startsWith("u"))
        val base58Part = digest.substring(1)
        assertTrue(base58Part.isNotEmpty())
        // Base58 should only contain valid characters
        val base58Chars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        assertTrue(base58Part.all { it in base58Chars })
    }

    @Test
    fun `test digest with single character input`() {
        val data = "a"
        val digest = DigestUtils.sha256DigestMultibase(data)
        
        assertTrue(digest.startsWith("u"))
        assertTrue(digest.length > 1)
    }

    @Test
    fun `test digest with very long input`() {
        val longData = "a".repeat(10000)
        val digest = DigestUtils.sha256DigestMultibase(longData)
        
        assertTrue(digest.startsWith("u"))
        // Base58 encoding of 32-byte SHA-256 should be ~44 characters
        assertTrue(digest.length >= 40 && digest.length <= 50)
    }

    @Test
    fun `test digest consistency for same input`() {
        val data = "test input"
        val digest1 = DigestUtils.sha256DigestMultibase(data)
        val digest2 = DigestUtils.sha256DigestMultibase(data)
        
        assertEquals(digest1, digest2)
    }

    @Test
    fun `test digest with unicode characters`() {
        val unicodeData = "Hello 世界 🌍"
        val digest = DigestUtils.sha256DigestMultibase(unicodeData)
        
        assertTrue(digest.startsWith("u"))
        val base58Part = digest.substring(1)
        // Base58 should only contain ASCII characters
        assertTrue(base58Part.all { it.code < 128 })
    }

    @Test
    fun `test digest with special JSON characters`() {
        val jsonWithSpecialChars = """{"key": "value with \"quotes\" and\nnewlines"}"""
        val digest = DigestUtils.sha256DigestMultibase(jsonWithSpecialChars)
        
        assertTrue(digest.startsWith("u"))
    }

    @Test
    fun `test digest with binary-like data`() {
        // Create data that might produce edge cases in base58 encoding
        val binaryLikeData = ByteArray(256) { it.toByte() }.toString(Charsets.ISO_8859_1)
        val digest = DigestUtils.sha256DigestMultibase(binaryLikeData)
        
        assertTrue(digest.startsWith("u"))
    }

    @Test
    fun `test base58 encoding produces consistent length`() {
        // SHA-256 always produces 32 bytes, base58 encoding should be consistent
        val inputs = listOf("a", "ab", "abc", "abcd", "abcde")
        val digests = inputs.map { DigestUtils.sha256DigestMultibase(it) }
        
        // All digests should have similar length (base58 encoding of 32 bytes)
        val lengths = digests.map { it.length }
        val minLength = lengths.minOrNull() ?: 0
        val maxLength = lengths.maxOrNull() ?: 0
        
        // Base58 encoding of 32 bytes should be around 44 characters
        assertTrue(minLength >= 40)
        assertTrue(maxLength <= 50)
        // Length variation should be small (base58 encoding is deterministic)
        assertTrue(maxLength - minLength <= 5)
    }

    @Test
    fun `test base58 encoding handles leading zeros correctly`() {
        // Test with inputs that might produce digests with leading zero bytes
        // We can't control the exact digest, but we can test that the encoding works
        val testInputs = listOf(
            "",
            "0",
            "00",
            "000",
            "\u0000",
            "\u0000\u0000"
        )
        
        testInputs.forEach { input ->
            val digest = DigestUtils.sha256DigestMultibase(input)
            assertTrue(digest.startsWith("u"))
            val base58Part = digest.substring(1)
            assertTrue(base58Part.isNotEmpty())
            // Base58 should not contain invalid characters
            val base58Chars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
            assertTrue(base58Part.all { it in base58Chars }, 
                "Base58 encoding contains invalid characters for input: $input")
        }
    }
}

