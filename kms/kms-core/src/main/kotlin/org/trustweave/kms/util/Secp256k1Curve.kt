package org.trustweave.kms.util

import java.math.BigInteger

/**
 * Minimal secp256k1 elliptic-curve arithmetic, in pure Kotlin + [BigInteger], sufficient to
 * verify an ECDSA signature and to decode a SEC 1 public key point.
 *
 * **Why this exists.** [Secp256k1SignatureAudit] needs to answer "does this signature verify"
 * offline, against an export of persisted signatures, with no KMS provider and no network access.
 * secp256k1 is not guaranteed to be registered with the JDK's built-in `SunEC` provider on every
 * JVM, and pulling in a crypto library (BouncyCastle, as the KMS plugin modules do) only for this
 * one read-only check would add a dependency to a module ([EcdsaSignatureCodec] included) that
 * has deliberately stayed dependency-free. So this implements exactly the curve operations ECDSA
 * verification needs — point addition, doubling, scalar multiplication, and point decoding — and
 * nothing else.
 *
 * **This is not a general-purpose EC library.** There is no signing here (it never needs a
 * private key), no constant-time guarantees, and no side-channel hardening. That is an acceptable
 * trade for an offline audit tool that only ever handles public data, but this object must not be
 * reused anywhere that signs, or anywhere latency/timing matters against an adversary.
 *
 * Curve parameters are the SEC 2 (v2.0) recommendation for secp256k1: `y^2 = x^3 + 7 (mod p)`.
 *
 * This is public rather than `internal` purely so it can be unit-tested directly (this module
 * builds with `kotlin.build.archivesTaskOutputAsFriendModule=false`, so `internal` is not visible
 * from `src/test`) — it is still a low-level primitive, not part of the intended consumer API.
 */
object Secp256k1Curve {
    /** Field prime `p`. */
    val P: BigInteger = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16)

    /** Group order `n`. */
    val ORDER: BigInteger = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16)

    /** Generator point `G`. */
    val G: AffinePoint =
        AffinePoint(
            BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16),
            BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16),
        )

    private val THREE: BigInteger = BigInteger.valueOf(3)
    private val SEVEN: BigInteger = BigInteger.valueOf(7)

    /**
     * A secp256k1 point in affine coordinates. There is no dedicated point-at-infinity instance:
     * infinity is represented as `null` wherever a computed point is optional (see [add]).
     */
    data class AffinePoint(
        val x: BigInteger,
        val y: BigInteger,
    )

    /**
     * Returns `true` if [point] satisfies `y^2 = x^3 + 7 (mod p)` with both coordinates in
     * `[0, p)`. Every point this object *produces* satisfies this by construction; callers use
     * this to validate externally-supplied points (i.e. decoded public keys).
     */
    fun isOnCurve(point: AffinePoint): Boolean {
        if (point.x.signum() < 0 || point.x >= P) return false
        if (point.y.signum() < 0 || point.y >= P) return false
        val lhs = point.y.multiply(point.y).mod(P)
        val xCubed = point.x.modPow(THREE, P)
        val rhs = xCubed.add(SEVEN).mod(P)
        return lhs == rhs
    }

    /**
     * Decodes a SEC 1 elliptic-curve point: uncompressed (65 bytes, `0x04 || X || Y`) or
     * compressed (33 bytes, `0x02`/`0x03 || X`, sign of `Y` in the prefix). Returns `null` if
     * [bytes] is not a validly-encoded, on-curve point — wrong length, unrecognized prefix,
     * `x >= p`, or (compressed form only) `x` has no square root mod `p`.
     */
    fun decodePoint(bytes: ByteArray): AffinePoint? {
        if (bytes.size == 65 && bytes[0] == 0x04.toByte()) {
            val x = BigInteger(1, bytes.copyOfRange(1, 33))
            val y = BigInteger(1, bytes.copyOfRange(33, 65))
            val point = AffinePoint(x, y)
            return if (isOnCurve(point)) point else null
        }
        if (bytes.size == 33 && (bytes[0] == 0x02.toByte() || bytes[0] == 0x03.toByte())) {
            return decompress(bytes)
        }
        return null
    }

    /**
     * Decompresses a 33-byte SEC 1 point. secp256k1's `p` is `3 (mod 4)`, so a square root of a
     * quadratic residue `a` is simply `a^((p+1)/4) mod p` — no general Tonelli-Shanks needed.
     */
    private fun decompress(bytes: ByteArray): AffinePoint? {
        val x = BigInteger(1, bytes.copyOfRange(1, 33))
        if (x.signum() < 0 || x >= P) return null
        val xCubed = x.modPow(THREE, P)
        val rhs = xCubed.add(SEVEN).mod(P)
        val sqrtExponent = P.add(BigInteger.ONE).shiftRight(2)
        val candidateY = rhs.modPow(sqrtExponent, P)
        val check = candidateY.multiply(candidateY).mod(P)
        if (check != rhs) return null // rhs was not a quadratic residue
        val wantOdd = bytes[0] == 0x03.toByte()
        val y = if (candidateY.testBit(0) == wantOdd) candidateY else P.subtract(candidateY)
        return AffinePoint(x, y)
    }

    /**
     * Adds two points, or doubles [p1] when `p1 == p2`. Returns `null` for the point at infinity,
     * i.e. when the two points are inverses of each other (same `x`, `y` values sum to `0 mod p`).
     */
    fun add(
        p1: AffinePoint?,
        p2: AffinePoint?,
    ): AffinePoint? {
        if (p1 == null) return p2
        if (p2 == null) return p1
        if (p1.x == p2.x) {
            val ySum = p1.y.add(p2.y).mod(P)
            if (ySum == BigInteger.ZERO) return null
            return double(p1)
        }
        val yDiff = p2.y.subtract(p1.y).mod(P)
        val xDiff = p2.x.subtract(p1.x).mod(P)
        val lambda = yDiff.multiply(xDiff.modInverse(P)).mod(P)
        return combine(lambda, p1, p2)
    }

    private fun double(p: AffinePoint): AffinePoint? {
        if (p.y.signum() == 0) return null
        val numerator = THREE.multiply(p.x).multiply(p.x).mod(P)
        val denominatorInverse = p.y.shiftLeft(1).modInverse(P)
        val lambda = numerator.multiply(denominatorInverse).mod(P)
        return combine(lambda, p, p)
    }

    private fun combine(
        lambda: BigInteger,
        p1: AffinePoint,
        p2: AffinePoint,
    ): AffinePoint {
        val lambdaSquared = lambda.multiply(lambda)
        val x3Unreduced = lambdaSquared.subtract(p1.x).subtract(p2.x)
        val x3 = x3Unreduced.mod(P)
        val xDiff = p1.x.subtract(x3)
        val y3 = lambda.multiply(xDiff).subtract(p1.y).mod(P)
        return AffinePoint(x3, y3)
    }

    /**
     * Scalar multiplication `k * point` via double-and-add. Returns `null` (point at infinity) if
     * `k mod n == 0`.
     */
    fun multiply(
        k: BigInteger,
        point: AffinePoint,
    ): AffinePoint? {
        var scalar = k.mod(ORDER)
        var addend: AffinePoint? = point
        var result: AffinePoint? = null
        while (scalar.signum() > 0) {
            if (scalar.testBit(0)) {
                result = add(result, addend)
            }
            addend = add(addend, addend)
            scalar = scalar.shiftRight(1)
        }
        return result
    }

    /**
     * Checks the ECDSA verification equation: with `w = s^-1 mod n`, `u1 = z*w mod n`,
     * `u2 = r*w mod n`, the signature is valid iff `(u1*G + u2*Q).x mod n == r`.
     *
     * Returns `false` (never throws) for out-of-range `r`/`s` — an out-of-range component simply
     * is not a valid signature, not an error condition.
     */
    fun verifySignature(
        publicKey: AffinePoint,
        digest: BigInteger,
        r: BigInteger,
        s: BigInteger,
    ): Boolean {
        if (r.signum() <= 0 || r >= ORDER || s.signum() <= 0 || s >= ORDER) return false
        val w = s.modInverse(ORDER)
        val u1 = digest.multiply(w).mod(ORDER)
        val u2 = r.multiply(w).mod(ORDER)
        val point = add(multiply(u1, G), multiply(u2, publicKey)) ?: return false
        return point.x.mod(ORDER) == r
    }
}
