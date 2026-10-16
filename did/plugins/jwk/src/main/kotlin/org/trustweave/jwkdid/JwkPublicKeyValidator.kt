package org.trustweave.jwkdid

import java.math.BigInteger
import java.util.Base64

/**
 * Checks that a JWK is a well-formed *public* key: coordinates of the right length, and a point
 * that really lies on its curve. A did:jwk identifier is attacker-controlled input, so a key that
 * only looks like a key must not become a verification method.
 */
internal object JwkPublicKeyValidator {
    /** @return `null` when valid, otherwise why the key is invalid. */
    fun validate(
        kty: String,
        crv: String?,
        member: (String) -> String?,
    ): String? =
        when {
            kty == "OKP" && crv == "Ed25519" -> ed25519(coordinate(member("x"), 32) ?: return "'x' must be 32 bytes of base64url")
            kty == "OKP" && crv == "X25519" ->
                if (coordinate(member("x"), 32) == null) "'x' must be 32 bytes of base64url" else null
            kty == "EC" -> ec(crv, member)
            kty == "RSA" -> rsa(member)
            else -> "unsupported key type"
        }

    private fun decode(value: String?): ByteArray? = value?.let { runCatching { Base64.getUrlDecoder().decode(it) }.getOrNull() }

    private fun coordinate(
        value: String?,
        length: Int,
    ): ByteArray? = decode(value)?.takeIf { it.size == length }

    private fun ec(
        crv: String?,
        member: (String) -> String?,
    ): String? {
        val curve = CURVES[crv] ?: return "unsupported EC curve"
        val x = coordinate(member("x"), curve.size) ?: return "'x' must be ${curve.size} bytes of base64url"
        val y = coordinate(member("y"), curve.size) ?: return "'y' must be ${curve.size} bytes of base64url"
        val bx = BigInteger(1, x)
        val by = BigInteger(1, y)
        if (bx >= curve.p || by >= curve.p) return "point coordinates are not reduced modulo the field prime"
        val left = by.multiply(by).mod(curve.p)
        val right =
            bx
                .pow(3)
                .add(curve.a.multiply(bx))
                .add(curve.b)
                .mod(curve.p)
        return if (left == right) null else "the point is not on curve $crv"
    }

    private fun ed25519(key: ByteArray): String? {
        val y = key.reversedArray().also { it[0] = (it[0].toInt() and 0x7f).toByte() }.let { BigInteger(1, it) }
        if (y >= ED_P) return "the Ed25519 y coordinate is not canonical"
        val y2 = y.multiply(y).mod(ED_P)
        val u = y2.subtract(BigInteger.ONE).mod(ED_P)
        val v = ED_D.multiply(y2).add(BigInteger.ONE).mod(ED_P)
        val x2 = u.multiply(v.modInverse(ED_P)).mod(ED_P)
        val signBit = (key[31].toInt() and 0x80) != 0
        if (x2.signum() == 0) return if (signBit) "the Ed25519 point encoding is invalid" else null
        val isSquare = x2.modPow(ED_P.subtract(BigInteger.ONE).shiftRight(1), ED_P) == BigInteger.ONE
        return if (isSquare) null else "the Ed25519 key is not a point on the curve"
    }

    private fun rsa(member: (String) -> String?): String? {
        val n = decode(member("n"))?.let { BigInteger(1, it) } ?: return "'n' must be base64url"
        val e = decode(member("e"))?.let { BigInteger(1, it) } ?: return "'e' must be base64url"
        if (n.bitLength() < 2048) return "RSA modulus is shorter than 2048 bits"
        if (!e.testBit(0) || e < BigInteger.valueOf(3)) return "RSA exponent must be odd and at least 3"
        return null
    }

    private class Curve(
        val size: Int,
        val p: BigInteger,
        val a: BigInteger,
        val b: BigInteger,
    )

    private fun hex(value: String) = BigInteger(value, 16)

    private val CURVES: Map<String, Curve> =
        mapOf(
            "P-256" to
                Curve(
                    32,
                    hex("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff"),
                    hex("ffffffff00000001000000000000000000000000fffffffffffffffffffffffc"),
                    hex("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b"),
                ),
            "P-384" to
                Curve(
                    48,
                    hex(
                        "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe" +
                            "ffffffff0000000000000000ffffffff",
                    ),
                    hex(
                        "fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe" +
                            "ffffffff0000000000000000fffffffc",
                    ),
                    hex(
                        "b3312fa7e23ee7e4988e056be3f82d19181d9c6efe8141120314088f5013875a" +
                            "c656398d8a2ed19d2a85c8edd3ec2aef",
                    ),
                ),
            "P-521" to
                Curve(
                    66,
                    BigInteger.ONE.shiftLeft(521).subtract(BigInteger.ONE),
                    BigInteger.ONE.shiftLeft(521).subtract(BigInteger.valueOf(4)),
                    hex(
                        "0051953eb9618e1c9a1f929a21a0b68540eea2da725b99b315f3b8b489918ef1" +
                            "09e156193951ec7e937b1652c0bd3bb1bf073573df883d2c34f1ef451fd46b50" +
                            "3f00",
                    ),
                ),
            "secp256k1" to
                Curve(
                    32,
                    hex("fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f"),
                    BigInteger.ZERO,
                    BigInteger.valueOf(7),
                ),
        )

    private val ED_P: BigInteger = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
    private val ED_D: BigInteger =
        BigInteger.valueOf(-121665).multiply(BigInteger.valueOf(121666).modInverse(ED_P)).mod(ED_P)
}
