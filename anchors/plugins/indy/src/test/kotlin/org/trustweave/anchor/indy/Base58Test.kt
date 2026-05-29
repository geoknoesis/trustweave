package org.trustweave.anchor.indy

import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class Base58Test {

    @Test
    fun `round trip hello world`() {
        val bytes = "Hello, world!".toByteArray()
        val encoded = Base58.encode(bytes)
        val decoded = Base58.decode(encoded)
        assertContentEquals(bytes, decoded)
    }

    @Test
    fun `encodes empty input to empty string`() {
        assertEquals("", Base58.encode(ByteArray(0)))
        assertContentEquals(ByteArray(0), Base58.decode(""))
    }

    @Test
    fun `preserves leading zero bytes`() {
        val bytes = byteArrayOf(0, 0, 1, 2, 3)
        val encoded = Base58.encode(bytes)
        // The first two zero bytes become "11" in Base58
        assertTrue(encoded.startsWith("11"))
        assertContentEquals(bytes, Base58.decode(encoded))
    }

    @Test
    fun `decode rejects illegal characters`() {
        assertFailsWith<IllegalArgumentException> { Base58.decode("0OIl") }
    }

    @Test
    fun `matches deterministic 32-byte round-trip`() {
        // 32 ascending bytes — equivalent to a typical Ed25519 seed value.
        val seed = ByteArray(32) { it.toByte() }
        val encoded = Base58.encode(seed)
        val decoded = Base58.decode(encoded)
        assertContentEquals(seed, decoded)
        assertTrue(encoded.length in 40..45, "32 raw bytes encode to ~44 Base58 chars, was ${encoded.length}")
    }
}
