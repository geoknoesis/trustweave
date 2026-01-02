package org.trustweave.kms

import org.trustweave.core.identifiers.KeyId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KeySpecTest {
    private val testKeyId = KeyId("test-key-id")
    private val testKeyHandle = KeyHandle(testKeyId, Algorithm.Ed25519.name, emptyMap())

    @Test
    fun `test KeySpec creation`() {
        val keySpec = KeySpec(testKeyId, Algorithm.Ed25519)
        assertEquals(testKeyId, keySpec.id)
        assertEquals(Algorithm.Ed25519, keySpec.algorithm)
    }

    @Test
    fun `test KeySpec supports with matching algorithm`() {
        val keySpec = KeySpec(testKeyId, Algorithm.Ed25519)
        assertTrue(keySpec.supports(Algorithm.Ed25519), "Should support matching algorithm")
    }

    @Test
    fun `test KeySpec supports with different algorithm`() {
        val keySpec = KeySpec(testKeyId, Algorithm.Ed25519)
        assertFalse(keySpec.supports(Algorithm.Secp256k1), "Should not support different algorithm")
    }

    @Test
    fun `test KeySpec requireSupports with matching algorithm`() {
        val keySpec = KeySpec(testKeyId, Algorithm.Ed25519)
        // Should not throw
        keySpec.requireSupports(Algorithm.Ed25519)
    }

    @Test
    fun `test KeySpec requireSupports with different algorithm throws`() {
        val keySpec = KeySpec(testKeyId, Algorithm.Ed25519)
        assertFailsWith<UnsupportedAlgorithmException> {
            keySpec.requireSupports(Algorithm.Secp256k1)
        }
    }

    @Test
    fun `test KeySpec fromKeyHandle`() {
        val keySpec = KeySpec.fromKeyHandle(testKeyHandle)
        assertEquals(testKeyId, keySpec.id)
        assertEquals(Algorithm.Ed25519, keySpec.algorithm)
    }

    @Test
    fun `test KeySpec fromKeyHandle with invalid algorithm throws`() {
        // Use an algorithm name that will cause Algorithm.parse() to return null
        // (e.g., blank string or invalid characters)
        val invalidKeyHandle = KeyHandle(testKeyId, "", emptyMap())
        assertFailsWith<IllegalArgumentException> {
            KeySpec.fromKeyHandle(invalidKeyHandle)
        }
    }

    @Test
    fun `test KeySpec fromKeyHandle with Secp256k1`() {
        val keyHandle = KeyHandle(testKeyId, Algorithm.Secp256k1.name, emptyMap())
        val keySpec = KeySpec.fromKeyHandle(keyHandle)
        assertEquals(Algorithm.Secp256k1, keySpec.algorithm)
    }
}

