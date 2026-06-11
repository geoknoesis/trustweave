package org.trustweave.kms.util

import org.trustweave.kms.Algorithm
import java.math.BigInteger

/**
 * Transcodes ECDSA signatures between ASN.1 DER (`SEQUENCE { INTEGER r, INTEGER s }`) and
 * IEEE P1363 (raw fixed-width `r || s`) encodings, and normalizes secp256k1 signatures to
 * low-s form.
 *
 * **Why this exists.** Different KMS backends emit ECDSA signatures in different encodings:
 * JCA providers, AWS KMS, Google Cloud KMS and Vault (`marshaling_algorithm=asn1`) return DER,
 * while Azure Key Vault returns raw P1363. The TrustWeave [org.trustweave.kms.KeyManagementService]
 * contract requires every provider to return **P1363** for EC keys, so plugins use this codec to
 * transcode before returning a signature.
 *
 * **Low-s normalization.** ECDSA signatures are malleable: `(r, s)` and `(r, n - s)` are both
 * valid. Ethereum (EIP-2) and Bitcoin reject high-s signatures, so secp256k1 signatures are
 * normalized to `s <= n/2`. The transformed signature still verifies against the same key and
 * message.
 *
 * **Caveat on detection.** [isDer] performs a full structural parse, so the probability of a raw
 * P1363 signature being misclassified as DER is negligible (it would have to start with a valid
 * SEQUENCE header *and* contain two well-formed nested INTEGERs whose lengths sum exactly to the
 * payload size), but it is not zero. Callers that know the encoding should transcode explicitly
 * instead of relying on detection.
 *
 * All operations are pure Kotlin + [BigInteger]; no crypto provider is required.
 */
object EcdsaSignatureCodec {

    /**
     * The order `n` of the secp256k1 group (SEC 2, version 2.0).
     */
    private val SECP256K1_ORDER = BigInteger(
        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141",
        16
    )

    private val SECP256K1_HALF_ORDER: BigInteger = SECP256K1_ORDER.shiftRight(1)

    /**
     * Returns the EC field element size in bytes for ECDSA-capable algorithms, or `null` for
     * algorithms that are not ECDSA (Ed25519, RSA, BLS, custom).
     *
     * - secp256k1, P-256 → 32 (P1363 signature = 64 bytes)
     * - P-384 → 48 (P1363 signature = 96 bytes)
     * - P-521 → 66 (P1363 signature = 132 bytes)
     */
    fun fieldSizeBytes(algorithm: Algorithm): Int? = when (algorithm) {
        Algorithm.Secp256k1, Algorithm.P256 -> 32
        Algorithm.P384 -> 48
        Algorithm.P521 -> 66
        else -> null
    }

    /**
     * Returns the expected P1363 (`r || s`) signature size in bytes for ECDSA-capable
     * algorithms, or `null` for non-ECDSA algorithms.
     */
    fun p1363SizeBytes(algorithm: Algorithm): Int? = fieldSizeBytes(algorithm)?.let { it * 2 }

    /**
     * Returns `true` if [bytes] is a structurally valid DER-encoded ECDSA signature:
     * a SEQUENCE containing exactly two positive INTEGERs, with no trailing bytes.
     */
    fun isDer(bytes: ByteArray): Boolean = parseDer(bytes) != null

    /**
     * Converts a DER-encoded ECDSA signature to P1363 (`r || s`), left-padding each component
     * to the field size of [algorithm].
     *
     * @throws IllegalArgumentException if [bytes] is not valid DER, if [algorithm] is not an
     *         ECDSA algorithm, or if `r`/`s` do not fit in the curve's field size.
     */
    fun derToP1363(bytes: ByteArray, algorithm: Algorithm): ByteArray {
        val fieldSize = fieldSizeBytes(algorithm)
            ?: throw IllegalArgumentException("Not an ECDSA algorithm: ${algorithm.name}")
        return derToP1363(bytes, fieldSize)
    }

    /**
     * Converts a DER-encoded ECDSA signature to P1363 (`r || s`), left-padding each component
     * to [fieldSizeBytes] bytes (32 for P-256/secp256k1, 48 for P-384, 66 for P-521).
     *
     * @throws IllegalArgumentException if [bytes] is not valid DER or `r`/`s` exceed
     *         [fieldSizeBytes].
     */
    fun derToP1363(bytes: ByteArray, fieldSizeBytes: Int): ByteArray {
        require(fieldSizeBytes > 0) { "fieldSizeBytes must be positive, got $fieldSizeBytes" }
        val (r, s) = parseDer(bytes)
            ?: throw IllegalArgumentException("Malformed DER ECDSA signature (${bytes.size} bytes)")
        val out = ByteArray(fieldSizeBytes * 2)
        writeFixedWidth(r, out, 0, fieldSizeBytes)
        writeFixedWidth(s, out, fieldSizeBytes, fieldSizeBytes)
        return out
    }

    /**
     * Converts a P1363 (`r || s`) ECDSA signature to DER (`SEQUENCE { INTEGER r, INTEGER s }`).
     *
     * Useful when verifying a P1363 signature through JCA `Signature.verify`, which expects DER.
     *
     * @throws IllegalArgumentException if the input length is odd or zero, or if `r`/`s` is zero
     *         (never produced by a valid ECDSA signer).
     */
    fun p1363ToDer(p1363: ByteArray): ByteArray {
        require(p1363.isNotEmpty() && p1363.size % 2 == 0) {
            "P1363 signature must have a positive, even length, got ${p1363.size}"
        }
        val half = p1363.size / 2
        val r = BigInteger(1, p1363.copyOfRange(0, half))
        val s = BigInteger(1, p1363.copyOfRange(half, p1363.size))
        require(r.signum() > 0 && s.signum() > 0) {
            "P1363 signature components must be positive (r=${r.signum()}, s=${s.signum()})"
        }
        val rDer = encodeDerInteger(r)
        val sDer = encodeDerInteger(s)
        val contentLength = rDer.size + sDer.size
        val header = encodeDerHeader(0x30, contentLength)
        val out = ByteArray(header.size + contentLength)
        System.arraycopy(header, 0, out, 0, header.size)
        System.arraycopy(rDer, 0, out, header.size, rDer.size)
        System.arraycopy(sDer, 0, out, header.size + rDer.size, sDer.size)
        return out
    }

    /**
     * Normalizes a 64-byte P1363 secp256k1 signature to low-s form: if `s > n/2`, `s` is
     * replaced with `n - s`. The normalized signature still verifies against the same key and
     * message; Ethereum (EIP-2) and Bitcoin require this form.
     *
     * @throws IllegalArgumentException if the signature is not 64 bytes or `s` is outside
     *         `(0, n)`.
     */
    fun normalizeSecp256k1LowS(p1363: ByteArray): ByteArray {
        require(p1363.size == 64) {
            "secp256k1 P1363 signature must be 64 bytes, got ${p1363.size}"
        }
        val s = BigInteger(1, p1363.copyOfRange(32, 64))
        require(s.signum() > 0 && s < SECP256K1_ORDER) {
            "secp256k1 signature s component out of range (0, n)"
        }
        if (s <= SECP256K1_HALF_ORDER) {
            return p1363
        }
        val out = p1363.copyOf()
        writeFixedWidth(SECP256K1_ORDER.subtract(s), out, 32, 32)
        return out
    }

    /**
     * Normalizes an EC signature to the [org.trustweave.kms.KeyManagementService.sign] contract:
     * P1363 encoding, with low-s for secp256k1.
     *
     * - For non-ECDSA algorithms (Ed25519, RSA, BLS, custom) the signature is returned unchanged.
     * - DER-encoded input is transcoded to P1363.
     * - Input that is already the expected P1363 size is passed through.
     * - Input in any other format is returned unchanged (the backend produced something this
     *   codec does not understand; callers should not silently destroy it).
     * - secp256k1 signatures are normalized to low-s.
     */
    fun normalize(signature: ByteArray, algorithm: Algorithm): ByteArray {
        val fieldSize = fieldSizeBytes(algorithm) ?: return signature
        val p1363 = when {
            isDer(signature) -> derToP1363(signature, fieldSize)
            signature.size == fieldSize * 2 -> signature
            else -> return signature
        }
        return if (algorithm == Algorithm.Secp256k1) normalizeSecp256k1LowS(p1363) else p1363
    }

    // ---------------------------------------------------------------------------------------
    // DER parsing / encoding internals
    // ---------------------------------------------------------------------------------------

    /**
     * Parses a DER `SEQUENCE { INTEGER r, INTEGER s }`. Returns `null` if the structure is
     * malformed, has trailing bytes, or either INTEGER is non-positive.
     */
    private fun parseDer(bytes: ByteArray): Pair<BigInteger, BigInteger>? {
        // Minimum: 30 06 02 01 xx 02 01 xx = 8 bytes
        if (bytes.size < 8) return null
        if ((bytes[0].toInt() and 0xFF) != 0x30) return null
        val (seqLen, seqContentStart) = readLength(bytes, 1) ?: return null
        if (seqContentStart + seqLen != bytes.size) return null

        val (r, afterR) = readInteger(bytes, seqContentStart) ?: return null
        val (s, afterS) = readInteger(bytes, afterR) ?: return null
        if (afterS != bytes.size) return null
        if (r.signum() <= 0 || s.signum() <= 0) return null
        return r to s
    }

    /**
     * Reads a DER length at [offset]. Supports short form and long form up to 4 length bytes.
     * Returns `(length, offsetOfContent)`, or `null` if malformed (including indefinite length
     * and non-minimal long form).
     */
    private fun readLength(bytes: ByteArray, offset: Int): Pair<Int, Int>? {
        if (offset >= bytes.size) return null
        val first = bytes[offset].toInt() and 0xFF
        if (first < 0x80) return first to offset + 1
        if (first == 0x80) return null // indefinite length is not DER
        val numBytes = first and 0x7F
        if (numBytes > 4 || offset + 1 + numBytes > bytes.size) return null
        var length = 0L
        for (i in 0 until numBytes) {
            length = (length shl 8) or (bytes[offset + 1 + i].toLong() and 0xFF)
        }
        if (length > Int.MAX_VALUE) return null
        // DER requires minimal length encoding: long form only when length >= 0x80, and no
        // leading zero length bytes.
        if (length < 0x80 || (bytes[offset + 1].toInt() and 0xFF) == 0) return null
        return length.toInt() to offset + 1 + numBytes
    }

    /**
     * Reads a DER INTEGER at [offset]. Returns `(value, offsetAfterInteger)`, or `null` if
     * malformed. Tolerates redundant leading zero bytes (some backends emit BER-padded
     * integers), but rejects empty values.
     */
    private fun readInteger(bytes: ByteArray, offset: Int): Pair<BigInteger, Int>? {
        if (offset >= bytes.size) return null
        if ((bytes[offset].toInt() and 0xFF) != 0x02) return null
        val (len, contentStart) = readLength(bytes, offset + 1) ?: return null
        if (len < 1 || contentStart + len > bytes.size) return null
        val value = BigInteger(bytes.copyOfRange(contentStart, contentStart + len))
        return value to contentStart + len
    }

    /**
     * Encodes a positive [BigInteger] as a DER INTEGER (tag + length + minimal two's-complement
     * content).
     */
    private fun encodeDerInteger(value: BigInteger): ByteArray {
        val content = value.toByteArray() // minimal two's complement; leading 0x00 kept if MSB set
        val header = encodeDerHeader(0x02, content.size)
        val out = ByteArray(header.size + content.size)
        System.arraycopy(header, 0, out, 0, header.size)
        System.arraycopy(content, 0, out, header.size, content.size)
        return out
    }

    /**
     * Encodes a DER tag + length header. Supports lengths up to 65535 bytes, which is far above
     * any ECDSA signature size.
     */
    private fun encodeDerHeader(tag: Int, length: Int): ByteArray = when {
        length < 0x80 -> byteArrayOf(tag.toByte(), length.toByte())
        length < 0x100 -> byteArrayOf(tag.toByte(), 0x81.toByte(), length.toByte())
        length < 0x10000 -> byteArrayOf(
            tag.toByte(),
            0x82.toByte(),
            (length ushr 8).toByte(),
            length.toByte()
        )
        else -> throw IllegalArgumentException("DER content too large: $length bytes")
    }

    /**
     * Writes the unsigned big-endian representation of [value] into [dest] at [offset],
     * left-padded with zeros to [width] bytes.
     *
     * @throws IllegalArgumentException if [value] does not fit in [width] bytes.
     */
    private fun writeFixedWidth(value: BigInteger, dest: ByteArray, offset: Int, width: Int) {
        val raw = value.toByteArray()
        // Strip the sign byte BigInteger prepends when the MSB of a positive value is set.
        val start = if (raw.size > 1 && raw[0] == 0.toByte()) 1 else 0
        val len = raw.size - start
        require(len <= width) {
            "ECDSA signature component too large for curve: $len bytes > $width bytes"
        }
        System.arraycopy(raw, start, dest, offset + (width - len), len)
    }
}
