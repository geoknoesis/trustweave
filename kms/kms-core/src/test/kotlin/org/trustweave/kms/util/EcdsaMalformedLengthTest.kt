package org.trustweave.kms.util

import org.trustweave.kms.Algorithm
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame

class EcdsaMalformedLengthTest {
    @Test
    fun `oversized integer lengths are rejected without overflowing bounds checks`() {
        for (length in listOf(Int.MAX_VALUE, Int.MAX_VALUE - 1, Int.MAX_VALUE - 7)) {
            // Keep the outer sequence valid so parsing reaches the hostile INTEGER length.
            val bytes =
                byteArrayOf(
                    0x30,
                    0x0c,
                    0x02,
                    0x84.toByte(),
                    (length ushr 24).toByte(),
                    (length ushr 16).toByte(),
                    (length ushr 8).toByte(),
                    length.toByte(),
                    0x02,
                    0x01,
                    0x01,
                    0,
                    0,
                    0,
                )
            assertFalse(EcdsaSignatureCodec.isDer(bytes))
            assertSame(bytes, EcdsaSignatureCodec.normalize(bytes, Algorithm.P256))
            assertFailsWith<IllegalArgumentException> { EcdsaSignatureCodec.derToP1363(bytes, Algorithm.P256) }
        }
    }

    @Test
    fun `oversized sequence lengths are rejected`() {
        val bytes = byteArrayOf(0x30, 0x84.toByte(), 0x7f, 0xff.toByte(), 0xff.toByte(), 0xff.toByte(), 0x02, 0x01, 0x01)
        assertFalse(EcdsaSignatureCodec.isDer(bytes))
    }
}
