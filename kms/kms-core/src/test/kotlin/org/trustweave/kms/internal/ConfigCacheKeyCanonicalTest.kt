package org.trustweave.kms.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class ConfigCacheKeyCanonicalTest {
    private fun key(options: Map<String, Any?>) = ConfigCacheKey.create("p", options)

    @Test
    fun `delimiters inside values cannot forge another configuration`() {
        assertNotEquals(key(mapOf("a" to "x:b=y")), key(mapOf("a" to "x", "b" to "y")))
        assertNotEquals(key(mapOf("a" to listOf("x,y"))), key(mapOf("a" to listOf("x", "y"))))
        assertNotEquals(key(mapOf("a" to mapOf("k" to "v,k2=v2"))), key(mapOf("a" to mapOf("k" to "v", "k2" to "v2"))))
    }

    @Test
    fun `null and the string null differ`() {
        assertNotEquals(key(mapOf("a" to null)), key(mapOf("a" to "null")))
    }

    @Test
    fun `a value and its string form differ by type`() {
        assertNotEquals(key(mapOf("a" to 1)), key(mapOf("a" to "1")))
        assertNotEquals(key(mapOf("a" to true)), key(mapOf("a" to "true")))
    }

    @Test
    fun `list order matters but set order and map order do not`() {
        assertNotEquals(key(mapOf("a" to listOf("x", "y"))), key(mapOf("a" to listOf("y", "x"))))
        assertEquals(key(mapOf("a" to linkedSetOf("x", "y"))), key(mapOf("a" to linkedSetOf("y", "x"))))
        assertEquals(
            key(mapOf("a" to linkedMapOf("k1" to 1, "k2" to 2))),
            key(mapOf("a" to linkedMapOf("k2" to 2, "k1" to 1))),
        )
    }

    @Test
    fun `equal configurations still produce equal keys and secrets stay out of the key`() {
        val k = key(mapOf("token" to "s3cr3t-value", "n" to listOf(1, 2)))
        assertEquals(k, key(mapOf("n" to listOf(1, 2), "token" to "s3cr3t-value")))
        assertFalse("s3cr3t-value" in k)
    }

    @Test
    fun `an unrecognised object is encoded by class and value`() {
        data class Opt(
            val v: Int,
        )
        assertEquals(key(mapOf("a" to Opt(1))), key(mapOf("a" to Opt(1))))
        assertNotEquals(key(mapOf("a" to Opt(1))), key(mapOf("a" to Opt(2))))
    }
}
