package org.trustweave.kms.util

import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Validates the pure-BigInteger secp256k1 arithmetic in [Secp256k1Curve] against well-known,
 * independently-verifiable constants before trusting it to back [Secp256k1SignatureAudit]'s
 * verification.
 */
class Secp256k1CurveTest {
    private fun hex(s: String) = BigInteger(s, 16)

    /**
     * `2*G` in uncompressed SEC1 form — a widely published secp256k1 constant, reproduced here
     * from an independent point-doubling computation so this is a cross-check, not a tautology.
     */
    private val twoG =
        Secp256k1Curve.AffinePoint(
            hex("C6047F9441ED7D6D3045406E95C07CD85C778E4B8CEF3CA7ABAC09B95C709EE5"),
            hex("1AE168FEA63DC339A3C58419466CEAEEF7F632653266D0E1236431A950CFE52A"),
        )

    @Test
    fun `generator point is on the curve`() {
        assertTrue(Secp256k1Curve.isOnCurve(Secp256k1Curve.G))
    }

    @Test
    fun `n times G is the point at infinity`() {
        assertNull(Secp256k1Curve.multiply(Secp256k1Curve.ORDER, Secp256k1Curve.G))
    }

    @Test
    fun `doubling G via add matches doubling via multiply and the known constant`() {
        val viaAdd = Secp256k1Curve.add(Secp256k1Curve.G, Secp256k1Curve.G)
        val viaMultiply = Secp256k1Curve.multiply(BigInteger.TWO, Secp256k1Curve.G)
        assertEquals(twoG, viaAdd)
        assertEquals(twoG, viaMultiply)
        assertTrue(Secp256k1Curve.isOnCurve(twoG))
    }

    @Test
    fun `1 times G is G`() {
        assertEquals(Secp256k1Curve.G, Secp256k1Curve.multiply(BigInteger.ONE, Secp256k1Curve.G))
    }

    @Test
    fun `add with a point and its own null infinity is the identity`() {
        assertEquals(Secp256k1Curve.G, Secp256k1Curve.add(null, Secp256k1Curve.G))
        assertEquals(Secp256k1Curve.G, Secp256k1Curve.add(Secp256k1Curve.G, null))
    }

    @Test
    fun `a point added to its negation is infinity`() {
        val negated = Secp256k1Curve.AffinePoint(Secp256k1Curve.G.x, Secp256k1Curve.P.subtract(Secp256k1Curve.G.y))
        assertTrue(Secp256k1Curve.isOnCurve(negated))
        assertNull(Secp256k1Curve.add(Secp256k1Curve.G, negated))
    }

    @Test
    fun `isOnCurve rejects coordinates outside the field`() {
        assertFalse(Secp256k1Curve.isOnCurve(Secp256k1Curve.AffinePoint(Secp256k1Curve.P, BigInteger.ZERO)))
        assertFalse(Secp256k1Curve.isOnCurve(Secp256k1Curve.AffinePoint(BigInteger.ZERO, Secp256k1Curve.P)))
        assertFalse(Secp256k1Curve.isOnCurve(Secp256k1Curve.AffinePoint(BigInteger.ONE, BigInteger.ONE)))
    }

    // ------------------------------------------------------------------------------------
    // Point decoding
    // ------------------------------------------------------------------------------------

    @Test
    fun `decodePoint parses uncompressed G`() {
        val bytes = byteArrayOf(0x04) + fixed32(Secp256k1Curve.G.x) + fixed32(Secp256k1Curve.G.y)
        assertEquals(Secp256k1Curve.G, Secp256k1Curve.decodePoint(bytes))
    }

    @Test
    fun `decodePoint parses compressed G and recovers the same point as uncompressed`() {
        val prefix = if (Secp256k1Curve.G.y.testBit(0)) 0x03 else 0x02
        val compressed = byteArrayOf(prefix.toByte()) + fixed32(Secp256k1Curve.G.x)
        assertEquals(Secp256k1Curve.G, Secp256k1Curve.decodePoint(compressed))
    }

    @Test
    fun `decodePoint parses compressed 2G and recovers the same point as uncompressed`() {
        val prefix = if (twoG.y.testBit(0)) 0x03 else 0x02
        val compressed = byteArrayOf(prefix.toByte()) + fixed32(twoG.x)
        assertEquals(twoG, Secp256k1Curve.decodePoint(compressed))
        val uncompressed = byteArrayOf(0x04) + fixed32(twoG.x) + fixed32(twoG.y)
        assertEquals(twoG, Secp256k1Curve.decodePoint(uncompressed))
    }

    @Test
    fun `decodePoint rejects the wrong SEC1 prefix for the parity of Y`() {
        // Using the opposite parity prefix must decompress to the OTHER root, not G.
        val wrongPrefix = if (Secp256k1Curve.G.y.testBit(0)) 0x02 else 0x03
        val compressed = byteArrayOf(wrongPrefix.toByte()) + fixed32(Secp256k1Curve.G.x)
        val decoded = assertNotNull(Secp256k1Curve.decodePoint(compressed))
        assertNotEquals(Secp256k1Curve.G.y, decoded.y)
        assertTrue(Secp256k1Curve.isOnCurve(decoded))
    }

    @Test
    fun `decodePoint rejects malformed input`() {
        assertNull(Secp256k1Curve.decodePoint(ByteArray(0)))
        assertNull(Secp256k1Curve.decodePoint(ByteArray(64))) // wrong length, no valid prefix
        assertNull(Secp256k1Curve.decodePoint(byteArrayOf(0x05) + ByteArray(64))) // bad uncompressed prefix
        assertNull(Secp256k1Curve.decodePoint(byteArrayOf(0x01) + ByteArray(32))) // bad compressed prefix
        // A compressed point whose x has no square root mod p (not every x is a valid
        // X-coordinate): x=5 gives x^3+7=132, which is not a quadratic residue mod p.
        assertNull(Secp256k1Curve.decodePoint(byteArrayOf(0x02) + fixed32(BigInteger.valueOf(5))))
    }

    private fun fixed32(v: BigInteger): ByteArray {
        val raw = v.toByteArray()
        val start = if (raw.size > 1 && raw[0] == 0.toByte()) 1 else 0
        val out = ByteArray(32)
        System.arraycopy(raw, start, out, 32 - (raw.size - start), raw.size - start)
        return out
    }

    private fun assertNotEquals(
        expected: BigInteger,
        actual: BigInteger,
    ) {
        assertTrue(expected != actual, "expected $actual to differ from $expected")
    }

    // ------------------------------------------------------------------------------------
    // ECDSA verification equation
    // ------------------------------------------------------------------------------------

    /**
     * A genuine ECDSA signature for private key `d = 1` (public key = [Secp256k1Curve.G]) over a
     * fixed digest, computed independently in Python and cross-checked there against the same
     * curve parameters before being hardcoded here.
     */
    private val genuineDigest = hex("0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20")
    private val genuineR = hex("2426978711ec6592c35616e1a4ef002d2bf892deb50e2be529f83c4797d737bc")
    private val genuineS = hex("009d5b320f805f26fb8951a6ef49ca486709feaaa95b8b850b63d8bb61510659")

    @Test
    fun `verifySignature accepts a genuine signature and rejects tampering`() {
        assertTrue(Secp256k1Curve.verifySignature(Secp256k1Curve.G, genuineDigest, genuineR, genuineS))
        // Wrong key, wrong digest, and a flipped s bit must each independently break verification.
        assertFalse(Secp256k1Curve.verifySignature(twoG, genuineDigest, genuineR, genuineS))
        val tamperedDigest = genuineDigest.add(BigInteger.ONE)
        assertFalse(Secp256k1Curve.verifySignature(Secp256k1Curve.G, tamperedDigest, genuineR, genuineS))
        val tamperedS = genuineS.xor(BigInteger.ONE)
        assertFalse(Secp256k1Curve.verifySignature(Secp256k1Curve.G, genuineDigest, genuineR, tamperedS))
    }

    @Test
    fun `verifySignature returns false for out-of-range r or s without throwing`() {
        val digest = BigInteger.ONE
        assertFalse(Secp256k1Curve.verifySignature(Secp256k1Curve.G, digest, BigInteger.ZERO, BigInteger.ONE))
        assertFalse(Secp256k1Curve.verifySignature(Secp256k1Curve.G, digest, BigInteger.ONE, BigInteger.ZERO))
        assertFalse(
            Secp256k1Curve.verifySignature(Secp256k1Curve.G, digest, Secp256k1Curve.ORDER, BigInteger.ONE),
        )
        assertFalse(
            Secp256k1Curve.verifySignature(Secp256k1Curve.G, digest, BigInteger.ONE, Secp256k1Curve.ORDER),
        )
    }
}
