package org.trustweave.revocation.token

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Wire codec for the `status_list.lst` claim of an IETF Token Status List
 * (`draft-ietf-oauth-status-list`).
 *
 * **Write ([encode]):** the packed status byte array (status index `i` occupies the bits starting at
 * the least significant bit of byte `i / (8 / bits)`) is compressed with ZLIB (RFC 1950, DEFLATE)
 * at the best compression level and encoded as base64url without padding.
 *
 * **Read ([decode]):** base64url (padding tolerated) is decoded, then
 * - a ZLIB stream is inflated, with the output capped at [MAX_DECOMPRESSED_BYTES] (a larger list is
 *   rejected with [IllegalArgumentException], which defuses decompression bombs);
 * - otherwise the bytes are taken as the legacy uncompressed list that earlier TrustWeave versions
 *   emitted.
 *
 * Legacy detection: the data is treated as ZLIB only if it starts with a valid ZLIB header
 * (CM = 8, CINFO <= 7, header checksum divisible by 31, no preset dictionary) AND inflates to a
 * complete, checksum-valid stream. A legacy list that happens to begin with header-like bytes but
 * is not a valid ZLIB stream is therefore still read as legacy. A size-cap violation is never
 * downgraded to "legacy".
 */
object TokenStatusListCodec {
    /** Upper bound on the decompressed size of a status list (16 MiB, i.e. 134M one-bit entries). */
    const val MAX_DECOMPRESSED_BYTES: Int = 16 * 1024 * 1024

    /** Compress [packed] with ZLIB and encode as base64url without padding. */
    fun encode(packed: ByteArray): String {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        try {
            deflater.setInput(packed)
            deflater.finish()
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (!deflater.finished()) {
                val n = deflater.deflate(buf)
                out.write(buf, 0, n)
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
        } finally {
            deflater.end()
        }
    }

    /**
     * Decode an `lst` value (ZLIB-compressed per the spec, or legacy uncompressed) to the packed
     * status bytes.
     *
     * @throws IllegalArgumentException if [lst] is not base64url, or the decompressed list would
     *   exceed [maxBytes]
     */
    fun decode(
        lst: String,
        maxBytes: Int = MAX_DECOMPRESSED_BYTES,
    ): ByteArray {
        val raw = Base64.getUrlDecoder().decode(lst)
        if (!hasZlibHeader(raw)) return raw
        return inflateOrNull(raw, maxBytes) ?: raw
    }

    private fun hasZlibHeader(b: ByteArray): Boolean {
        if (b.size < 2) return false
        val cmf = b[0].toInt() and 0xFF
        val flg = b[1].toInt() and 0xFF
        return (cmf and 0x0F) == 8 &&
            (cmf shr 4) <= 7 &&
            (flg and 0x20) == 0 &&
            ((cmf shl 8) or flg) % 31 == 0
    }

    /** Inflates [data]; null when it is not a complete valid ZLIB stream. Throws when over [maxBytes]. */
    private fun inflateOrNull(
        data: ByteArray,
        maxBytes: Int,
    ): ByteArray? {
        val inflater = Inflater()
        try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (!inflater.finished()) {
                val n =
                    try {
                        inflater.inflate(buf)
                    } catch (_: DataFormatException) {
                        return null
                    }
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) return null
                if (out.size() + n > maxBytes) {
                    throw IllegalArgumentException(
                        "Status list decompresses to more than $maxBytes bytes; rejected as a possible decompression bomb",
                    )
                }
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        } finally {
            inflater.end()
        }
    }
}
