package org.trustweave.core.util

import java.util.HexFormat
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Base58CompatibilityTest {
    @Test
    fun `known encodings preserve leading zeros and unsigned bytes`() {
        val vectors =
            mapOf(
                "" to "",
                "00" to "1",
                "0000" to "11",
                "000001" to "112",
                "61" to "2g",
                "626262" to "a3gV",
                "636363" to "aPEr",
                "80" to "3D",
                "ff" to "5Q",
            )
        vectors.forEach { (hex, encoded) ->
            val bytes = HexFormat.of().parseHex(hex)
            assertEquals(encoded, bytes.encodeBase58(), hex)
            assertContentEquals(bytes, encoded.decodeBase58(), encoded)
        }
    }

    @Test
    fun `random byte arrays round trip with and without leading zeros`() {
        val random = Random(58)
        listOf(0, 1, 2, 16, 32, 64, 256).forEach { size ->
            repeat(16) { leadingZeros ->
                val bytes = ByteArray(leadingZeros) + random.nextBytes(size)
                assertContentEquals(bytes, bytes.encodeBase58().decodeBase58())
            }
        }
    }

    @Test
    fun `invalid ASCII and Unicode characters are rejected`() {
        listOf("0", "O", "I", "l", " ", "\n", "\u0080", "\uFFFF", "\uD800").forEach { invalid ->
            assertFailsWith<IllegalArgumentException> { ("11" + invalid).decodeBase58() }
        }
    }
}
