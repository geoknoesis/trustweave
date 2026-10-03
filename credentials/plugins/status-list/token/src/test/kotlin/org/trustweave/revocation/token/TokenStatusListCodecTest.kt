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
    fun `legacy list that merely resembles a ZLIB header is read as legacy`() {
        // 0x78 0x9C is a valid ZLIB header, but the rest is not a valid stream.
        val legacy = byteArrayOf(0x78, 0x9C.toByte(), 0x00, 0x00, 0x00)
        assertContentEquals(legacy, TokenStatusListCodec.decode(b64(legacy)))
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
