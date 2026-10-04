package org.trustweave.revocation.token

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokenStatusListCodecTest {
    private fun zlib(
        data: ByteArray,
        level: Int = Deflater.DEFAULT_COMPRESSION,
    ): ByteArray {
        val d = Deflater(level)
        d.setInput(data)
        d.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    private fun b64(b: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(b)

    @Test
    fun `known vector from the draft decodes - statuses 1 0 0 1 1 1 0 1 1 1 0 0 0 1 0 1`() {
        // draft-ietf-oauth-status-list example: bits=1, statuses[0..15] =
        // 1,0,0,1,1,1,0,1, 1,1,0,0,0,1,0,1 -> bytes 0xB9 0xA3 (LSB first)
        val lst = "eNrbuRgAAhcBXQ"
        val bytes = TokenStatusListCodec.decode(lst)
        assertContentEquals(byteArrayOf(0xB9.toByte(), 0xA3.toByte()), bytes)

        fun status(i: Int) = (bytes[i / 8].toInt() shr (i % 8)) and 1
        assertEquals(listOf(1, 0, 0, 1, 1, 1, 0, 1, 1, 1, 0, 0, 0, 1, 0, 1), (0 until 16).map(::status))
    }

    @Test
    fun `encode emits a valid unpadded ZLIB stream that round trips`() {
        val packed = ByteArray(1000) { (it * 31).toByte() }
        val lst = TokenStatusListCodec.encode(packed)
        assertFalse(lst.contains('='))
        val raw = Base64.getUrlDecoder().decode(lst)
        assertEquals(0x78, raw[0].toInt() and 0xFF, "ZLIB header (deflate, 32K window)")
        assertEquals(0, (((raw[0].toInt() and 0xFF) shl 8) or (raw[1].toInt() and 0xFF)) % 31)
        assertContentEquals(packed, TokenStatusListCodec.decode(lst))
    }

    @Test
    fun `all-zero list compresses`() {
        val packed = ByteArray(16384)
        val lst = TokenStatusListCodec.encode(packed)
        assertTrue(lst.length < 200)
        assertContentEquals(packed, TokenStatusListCodec.decode(lst))
    }

    @Test
    fun `legacy uncompressed list is still readable`() {
        val legacy = byteArrayOf(0x01, 0x00, 0x05, 0x7F)
        assertContentEquals(legacy, TokenStatusListCodec.decode(b64(legacy)))
        assertContentEquals(ByteArray(1), TokenStatusListCodec.decode(b64(ByteArray(1))))
    }

    @Test
    fun `a legacy list whose first bytes are not a ZLIB header is read as legacy`() {
        // 0x78 0x9D fails the header checksum (not divisible by 31), so this is legacy data.
        val legacy = byteArrayOf(0x78, 0x9D.toByte(), 0x00, 0x00, 0x00)
        assertContentEquals(legacy, TokenStatusListCodec.decode(b64(legacy)))
    }

    @Test
    fun `a valid ZLIB header followed by a corrupt stream is rejected, not read as legacy`() {
        val corrupt = byteArrayOf(0x78, 0x9C.toByte(), 0x00, 0x00, 0x00)
        assertFailsWith<IllegalArgumentException> { TokenStatusListCodec.decode(b64(corrupt)) }
    }

    @Test
    fun `a ZLIB stream with a failed Adler-32 checksum is rejected`() {
        val good = zlib(ByteArray(64) { it.toByte() })
        good[good.size - 1] = (good[good.size - 1].toInt() xor 0x01).toByte()
        assertFailsWith<IllegalArgumentException> { TokenStatusListCodec.decode(b64(good)) }
    }

    @Test
    fun `a truncated ZLIB stream is rejected`() {
        val good = zlib(ByteArray(2048) { (it % 7).toByte() })
        val truncated = good.copyOf(good.size - 6)
        assertFailsWith<IllegalArgumentException> { TokenStatusListCodec.decode(b64(truncated)) }
    }

    @Test
    fun `trailing bytes after a complete ZLIB stream are rejected`() {
        val good = zlib(ByteArray(64) { it.toByte() })
        val withTrailer = good + byteArrayOf(0x01, 0x02)
        val failure = assertFailsWith<IllegalArgumentException> { TokenStatusListCodec.decode(b64(withTrailer)) }
        assertTrue(failure.message!!.contains("trailing"), failure.message)
    }

    @Test
    fun `decompression bomb is rejected`() {
        val bomb = zlib(ByteArray(20 * 1024 * 1024), Deflater.BEST_COMPRESSION)
        assertTrue(bomb.size < 100 * 1024, "the bomb itself is small")
        assertFailsWith<IllegalArgumentException> { TokenStatusListCodec.decode(b64(bomb)) }
    }

    @Test
    fun `size cap is configurable and inclusive`() {
        val lst = TokenStatusListCodec.encode(ByteArray(100))
        assertEquals(100, TokenStatusListCodec.decode(lst, maxBytes = 100).size)
        assertFailsWith<IllegalArgumentException> { TokenStatusListCodec.decode(lst, maxBytes = 99) }
    }

    @Test
    fun `invalid base64 fails loudly`() {
        assertFailsWith<IllegalArgumentException> { TokenStatusListCodec.decode("***") }
    }
}
