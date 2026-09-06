package org.trustweave.referencewallet.lib

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Base58Test {
    @Test
    fun knownVectorsPreserveLeadingZeroBytesAndInput() {
        val vectors =
            mapOf(
                "" to byteArrayOf(),
                "1" to byteArrayOf(0),
                "112" to byteArrayOf(0, 0, 1),
                "2g" to byteArrayOf(0x61),
                "a3gV" to byteArrayOf(0x62, 0x62, 0x62),
            )
        for ((encoded, bytes) in vectors) {
            val original = bytes.copyOf()
            assertEquals(encoded, Base58.encode(bytes))
            assertArrayEquals(original, bytes)
            assertArrayEquals(original, Base58.decode(encoded))
        }
    }

    @Test
    fun invalidAlphabetNeverProducesKeyBytes() {
        for (invalid in listOf("0", "O", "I", "l", " ", "\u00e9")) {
            assertThrows(IllegalArgumentException::class.java) { Base58.decode(invalid) }
        }
    }
}
