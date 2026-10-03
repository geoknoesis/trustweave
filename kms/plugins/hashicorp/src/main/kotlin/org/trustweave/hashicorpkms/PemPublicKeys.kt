package org.trustweave.hashicorpkms

import java.math.BigInteger
import java.util.Base64

/**
 * Strict parsing of the public keys Vault Transit returns.
 *
 * PEM armour is matched as a whole (`-----BEGIN X-----` … `-----END X-----` with the same label),
 * all whitespace in the body (spaces, `\n`, `\r`, tabs) is ignored, and the decoded key is
 * checked against the algorithm the caller expects: an EC key must carry the named-curve OID of
 * that curve and a point on it, an RSA key must have the expected modulus size.
 */
internal object PemPublicKeys {
    private val ARMOR = Regex("""-----BEGIN ([A-Z0-9 ]+)-----(.*?)-----END ([A-Z0-9 ]+)-----""", RegexOption.DOT_MATCHES_ALL)

    /** ecPublicKey, 1.2.840.10045.2.1 */
    private val EC_PUBLIC_KEY_OID = byteArrayOf(0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x02, 0x01)

    /**
     * Returns the DER bytes of [pem]: the body of its single PEM block (whose label must be one of
     * [labels]), or, when there is no armour at all, the whole input as base64.
     *
     * @throws IllegalArgumentException for mismatched or unexpected labels, several blocks,
     *   stray text around the block, or invalid base64
     */
    fun der(
        pem: String,
        labels: Set<String>,
    ): ByteArray {
        val trimmed = pem.trim()
        val blocks = ARMOR.findAll(trimmed).toList()
        val body =
            when {
                blocks.isEmpty() -> {
                    require(!trimmed.contains("-----")) { "Malformed PEM armour" }
                    trimmed
                }
                blocks.size > 1 -> throw IllegalArgumentException("Expected one PEM block, found ${blocks.size}")
                else -> {
                    val block = blocks.single()
                    val (begin, content, end) = block.destructured
                    require(begin == end) { "PEM BEGIN/END labels differ: '$begin' vs '$end'" }
                    require(begin in labels) { "Unexpected PEM label '$begin', expected one of $labels" }
                    require(block.range.first == 0 && block.range.last == trimmed.length - 1) {
                        "Unexpected text around the PEM block"
                    }
                    content
                }
            }
        val compact = body.filterNot { it.isWhitespace() }
        require(compact.isNotEmpty()) { "Empty PEM body" }
        return Base64.getDecoder().decode(compact)
    }

    /** Named-curve parameters used to check an EC public key. */
    class Curve(
        val oid: ByteArray,
        val coordinateSize: Int,
        val p: BigInteger,
        val a: BigInteger,
        val b: BigInteger,
    ) {
        fun isOnCurve(
            x: BigInteger,
            y: BigInteger,
        ): Boolean {
            if (x.signum() < 0 || x >= p || y.signum() < 0 || y >= p) return false
            val lhs = y.modPow(BigInteger.TWO, p)
            val rhs =
                x
                    .modPow(BigInteger.valueOf(3), p)
                    .add(a.multiply(x))
                    .add(b)
                    .mod(p)
            return lhs == rhs
        }
    }

    private fun hex(value: String) = BigInteger(value, 16)

    val P256 =
        Curve(
            oid = byteArrayOf(0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x03, 0x01, 0x07),
            coordinateSize = 32,
            p = hex("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff"),
            a = hex("ffffffff00000001000000000000000000000000fffffffffffffffffffffffc"),
            b = hex("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b"),
        )
    val P384 =
        Curve(
            oid = byteArrayOf(0x2b, 0x81.toByte(), 0x04, 0x00, 0x22),
            coordinateSize = 48,
            p = hex("fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffeffffffff0000000000000000ffffffff"),
            a = hex("fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffeffffffff0000000000000000fffffffc"),
            b = hex("b3312fa7e23ee7e4988e056be3f82d19181d9c6efe8141120314088f5013875ac656398d8a2ed19d2a85c8edd3ec2aef"),
        )
    val P521 =
        Curve(
            oid = byteArrayOf(0x2b, 0x81.toByte(), 0x04, 0x00, 0x23),
            coordinateSize = 66,
            p = BigInteger.TWO.pow(521).subtract(BigInteger.ONE),
            a = BigInteger.TWO.pow(521).subtract(BigInteger.valueOf(4)),
            b =
                hex(
                    "0051953eb9618e1c9a1f929a21a0b68540eea2da725b99b315f3b8b489918ef109e156193951ec7e937b1652c0bd3bb1bf073573df883d2c34f1ef451fd46b503f00",
                ),
        )
    val SECP256K1 =
        Curve(
            oid = byteArrayOf(0x2b, 0x81.toByte(), 0x04, 0x00, 0x0a),
            coordinateSize = 32,
            p = hex("fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f"),
            a = BigInteger.ZERO,
            b = BigInteger.valueOf(7),
        )

    /**
     * Parses an EC SubjectPublicKeyInfo and returns the uncompressed point (x, y), checking that
     * the key is an ecPublicKey on exactly [curve] and that the point lies on it.
     */
    fun ecPoint(
        spki: ByteArray,
        curve: Curve,
    ): Pair<ByteArray, ByteArray> {
        val outer = Der(spki).sequence()
        val algorithm = outer.sequence()
        val keyType = algorithm.objectIdentifier()
        require(keyType.contentEquals(EC_PUBLIC_KEY_OID)) { "Not an EC public key" }
        val curveOid = algorithm.objectIdentifier()
        require(curveOid.contentEquals(curve.oid)) { "EC public key is on a different curve than expected" }
        val bits = outer.bitString()
        require(bits.size == 1 + 2 * curve.coordinateSize && bits[0] == 0x04.toByte()) {
            "Expected an uncompressed ${curve.coordinateSize * 8}-bit-coordinate EC point"
        }
        val x = bits.copyOfRange(1, 1 + curve.coordinateSize)
        val y = bits.copyOfRange(1 + curve.coordinateSize, bits.size)
        require(curve.isOnCurve(BigInteger(1, x), BigInteger(1, y))) { "EC public key point is not on the curve" }
        return x to y
    }

    /** Minimal DER reader for the fixed shape of a SubjectPublicKeyInfo. */
    private class Der(
        private val bytes: ByteArray,
        private var pos: Int = 0,
        private val end: Int = bytes.size,
    ) {
        private fun read(tag: Int): Pair<Int, Int> {
            require(pos < end && (bytes[pos].toInt() and 0xff) == tag) { "Malformed DER: expected tag $tag" }
            pos++
            require(pos < end) { "Malformed DER: truncated length" }
            var length = bytes[pos++].toInt() and 0xff
            if (length and 0x80 != 0) {
                val count = length and 0x7f
                require(count in 1..3 && pos + count <= end) { "Malformed DER: bad length" }
                length = 0
                repeat(count) { length = (length shl 8) or (bytes[pos++].toInt() and 0xff) }
            }
            require(length >= 0 && pos + length <= end) { "Malformed DER: length past end" }
            val start = pos
            pos += length
            return start to length
        }

        fun sequence(): Der {
            val (start, length) = read(0x30)
            return Der(bytes, start, start + length)
        }

        fun objectIdentifier(): ByteArray {
            val (start, length) = read(0x06)
            return bytes.copyOfRange(start, start + length)
        }

        fun bitString(): ByteArray {
            val (start, length) = read(0x03)
            require(length >= 1 && bytes[start].toInt() == 0) { "Malformed DER: unexpected unused bits" }
            return bytes.copyOfRange(start + 1, start + length)
        }
    }
}
