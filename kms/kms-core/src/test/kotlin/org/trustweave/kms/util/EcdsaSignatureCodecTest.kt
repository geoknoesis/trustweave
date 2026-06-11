package org.trustweave.kms.util

import org.trustweave.kms.Algorithm
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EcdsaSignatureCodecTest {

    // ------------------------------------------------------------------------------------
    // Known vectors
    // ------------------------------------------------------------------------------------

    /**
     * secp256k1 group order n (SEC 2).
     */
    private val secp256k1Order = BigInteger(
        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141",
        16
    )

    private val halfOrder = secp256k1Order.shiftRight(1)

    /**
     * Builds a P1363 signature from r and s, each left-padded to [fieldSize] bytes.
     */
    private fun p1363Of(r: BigInteger, s: BigInteger, fieldSize: Int = 32): ByteArray {
        fun fixed(v: BigInteger): ByteArray {
            val raw = v.toByteArray()
            val start = if (raw.size > 1 && raw[0] == 0.toByte()) 1 else 0
            val out = ByteArray(fieldSize)
            System.arraycopy(raw, start, out, fieldSize - (raw.size - start), raw.size - start)
            return out
        }
        return fixed(r) + fixed(s)
    }

    /**
     * A hand-built DER signature: SEQUENCE { INTEGER 0x01, INTEGER 0x02 }.
     */
    private val minimalDer = byteArrayOf(0x30, 0x06, 0x02, 0x01, 0x01, 0x02, 0x01, 0x02)

    /**
     * Known transcoding vector: r = 1, s = 2 on a 32-byte field. P1363 form is 64 bytes of
     * zeros except r[31] = 1 and s[31] = 2.
     */
    private val minimalP1363 = ByteArray(64).also {
        it[31] = 1
        it[63] = 2
    }

    // ------------------------------------------------------------------------------------
    // isDer detection
    // ------------------------------------------------------------------------------------

    @Test
    fun `isDer accepts a minimal DER signature`() {
        assertTrue(EcdsaSignatureCodec.isDer(minimalDer))
    }

    @Test
    fun `isDer accepts a DER signature with high-bit integers (leading zero pad)`() {
        // r and s with MSB set require a 0x00 pad byte in DER.
        val der = byteArrayOf(
            0x30, 0x08,
            0x02, 0x02, 0x00, 0x80.toByte(),
            0x02, 0x02, 0x00, 0xFF.toByte()
        )
        assertTrue(EcdsaSignatureCodec.isDer(der))
        val p1363 = EcdsaSignatureCodec.derToP1363(der, 32)
        assertEquals(BigInteger.valueOf(0x80), BigInteger(1, p1363.copyOfRange(0, 32)))
        assertEquals(BigInteger.valueOf(0xFF), BigInteger(1, p1363.copyOfRange(32, 64)))
    }

    @Test
    fun `isDer accepts long-form length (P-521 sized signature)`() {
        // P-521 signatures can exceed 127 content bytes, forcing the 0x81 long form.
        val r = BigInteger.ONE.shiftLeft(520).subtract(BigInteger.ONE) // 65 bytes of 0xFF
        val s = BigInteger.ONE.shiftLeft(519).add(BigInteger.ONE)
        val der = EcdsaSignatureCodec.p1363ToDer(p1363Of(r, s, fieldSize = 66))
        assertTrue(der.size > 130, "expected long-form DER, got ${der.size} bytes")
        assertEquals(0x81.toByte(), der[1], "expected 0x81 long-form length byte")
        assertTrue(EcdsaSignatureCodec.isDer(der))
        assertContentEquals(p1363Of(r, s, fieldSize = 66), EcdsaSignatureCodec.derToP1363(der, 66))
    }

    @Test
    fun `isDer rejects raw P1363 signatures`() {
        // Typical P1363 content does not start with 0x30; even when it does, the structural
        // parse fails for random content.
        assertFalse(EcdsaSignatureCodec.isDer(minimalP1363))
        assertFalse(EcdsaSignatureCodec.isDer(ByteArray(64) { 0xAB.toByte() }))
    }

    @Test
    fun `isDer rejects malformed inputs`() {
        assertFalse(EcdsaSignatureCodec.isDer(ByteArray(0)))
        assertFalse(EcdsaSignatureCodec.isDer(byteArrayOf(0x30)))
        // Wrong outer tag
        assertFalse(EcdsaSignatureCodec.isDer(byteArrayOf(0x31, 0x06, 0x02, 0x01, 0x01, 0x02, 0x01, 0x02)))
        // Sequence length mismatch (claims 7, has 6)
        assertFalse(EcdsaSignatureCodec.isDer(byteArrayOf(0x30, 0x07, 0x02, 0x01, 0x01, 0x02, 0x01, 0x02)))
        // Trailing garbage after second INTEGER
        assertFalse(EcdsaSignatureCodec.isDer(minimalDer + byteArrayOf(0x00)))
        // First element is not an INTEGER
        assertFalse(EcdsaSignatureCodec.isDer(byteArrayOf(0x30, 0x06, 0x03, 0x01, 0x01, 0x02, 0x01, 0x02)))
        // Only one INTEGER
        assertFalse(EcdsaSignatureCodec.isDer(byteArrayOf(0x30, 0x03, 0x02, 0x01, 0x01)))
        // Indefinite length is not DER
        assertFalse(EcdsaSignatureCodec.isDer(byteArrayOf(0x30, 0x80.toByte(), 0x02, 0x01, 0x01, 0x02, 0x01, 0x02)))
        // Negative INTEGER (MSB set without pad) is never a valid ECDSA component
        assertFalse(EcdsaSignatureCodec.isDer(byteArrayOf(0x30, 0x06, 0x02, 0x01, 0x80.toByte(), 0x02, 0x01, 0x02)))
        // Zero INTEGER is never a valid ECDSA component
        assertFalse(EcdsaSignatureCodec.isDer(byteArrayOf(0x30, 0x06, 0x02, 0x01, 0x00, 0x02, 0x01, 0x02)))
    }

    // ------------------------------------------------------------------------------------
    // DER <-> P1363 round trips
    // ------------------------------------------------------------------------------------

    @Test
    fun `derToP1363 transcodes the known minimal vector`() {
        assertContentEquals(minimalP1363, EcdsaSignatureCodec.derToP1363(minimalDer, 32))
        assertContentEquals(minimalP1363, EcdsaSignatureCodec.derToP1363(minimalDer, Algorithm.P256))
    }

    @Test
    fun `p1363ToDer transcodes the known minimal vector`() {
        assertContentEquals(minimalDer, EcdsaSignatureCodec.p1363ToDer(minimalP1363))
    }

    @Test
    fun `round trip DER to P1363 to DER preserves the signature for every curve size`() {
        val vectors = listOf(
            Triple(Algorithm.P256, 32, 256),
            Triple(Algorithm.Secp256k1, 32, 256),
            Triple(Algorithm.P384, 48, 384),
            Triple(Algorithm.P521, 66, 521)
        )
        for ((algorithm, fieldSize, bits) in vectors) {
            // Deterministic, near-maximal r/s for the curve size; MSB set to exercise the
            // DER sign-byte padding path.
            val r = BigInteger.ONE.shiftLeft(bits - 1).add(BigInteger.valueOf(12345))
            val s = BigInteger.ONE.shiftLeft(bits - 2).add(BigInteger.valueOf(67890))
            val p1363 = p1363Of(r, s, fieldSize)
            val der = EcdsaSignatureCodec.p1363ToDer(p1363)
            assertTrue(EcdsaSignatureCodec.isDer(der), "round-trip DER must self-detect ($algorithm)")
            assertContentEquals(
                p1363,
                EcdsaSignatureCodec.derToP1363(der, algorithm),
                "DER->P1363 must invert P1363->DER for $algorithm"
            )
            assertContentEquals(
                der,
                EcdsaSignatureCodec.p1363ToDer(EcdsaSignatureCodec.derToP1363(der, fieldSize)),
                "P1363->DER must invert DER->P1363 for $algorithm"
            )
        }
    }

    @Test
    fun `derToP1363 rejects malformed DER`() {
        assertFailsWith<IllegalArgumentException> {
            EcdsaSignatureCodec.derToP1363(ByteArray(64) { 0xAB.toByte() }, 32)
        }
        assertFailsWith<IllegalArgumentException> {
            EcdsaSignatureCodec.derToP1363(byteArrayOf(0x30, 0x02, 0x02, 0x00), 32)
        }
    }

    @Test
    fun `derToP1363 rejects components wider than the field size`() {
        val r = BigInteger.ONE.shiftLeft(384) // 49 bytes — too wide for P-256's 32
        val der = EcdsaSignatureCodec.p1363ToDer(p1363Of(r, BigInteger.TWO, fieldSize = 66))
        assertFailsWith<IllegalArgumentException> {
            EcdsaSignatureCodec.derToP1363(der, 32)
        }
    }

    @Test
    fun `derToP1363 rejects non-EC algorithms`() {
        assertFailsWith<IllegalArgumentException> {
            EcdsaSignatureCodec.derToP1363(minimalDer, Algorithm.Ed25519)
        }
    }

    @Test
    fun `p1363ToDer rejects odd, empty, and zero-component inputs`() {
        assertFailsWith<IllegalArgumentException> { EcdsaSignatureCodec.p1363ToDer(ByteArray(0)) }
        assertFailsWith<IllegalArgumentException> { EcdsaSignatureCodec.p1363ToDer(ByteArray(63)) }
        assertFailsWith<IllegalArgumentException> { EcdsaSignatureCodec.p1363ToDer(ByteArray(64)) } // r = s = 0
    }

    // ------------------------------------------------------------------------------------
    // secp256k1 low-s normalization
    // ------------------------------------------------------------------------------------

    @Test
    fun `high-s secp256k1 signature is normalized to n minus s`() {
        val r = BigInteger.valueOf(123456789)
        val highS = halfOrder.add(BigInteger.valueOf(42)) // strictly greater than n/2
        val normalized = EcdsaSignatureCodec.normalizeSecp256k1LowS(p1363Of(r, highS))

        val expectedS = secp256k1Order.subtract(highS)
        assertEquals(expectedS, BigInteger(1, normalized.copyOfRange(32, 64)))
        // r is untouched
        assertEquals(r, BigInteger(1, normalized.copyOfRange(0, 32)))
        assertTrue(BigInteger(1, normalized.copyOfRange(32, 64)) <= halfOrder)
    }

    @Test
    fun `low-s secp256k1 signature is returned unchanged`() {
        val sig = p1363Of(BigInteger.valueOf(99), halfOrder) // s == n/2 is allowed
        assertSame(sig, EcdsaSignatureCodec.normalizeSecp256k1LowS(sig))
    }

    @Test
    fun `low-s normalization rejects invalid sizes and out-of-range s`() {
        assertFailsWith<IllegalArgumentException> {
            EcdsaSignatureCodec.normalizeSecp256k1LowS(ByteArray(96))
        }
        assertFailsWith<IllegalArgumentException> {
            EcdsaSignatureCodec.normalizeSecp256k1LowS(p1363Of(BigInteger.ONE, BigInteger.ZERO))
        }
        assertFailsWith<IllegalArgumentException> {
            EcdsaSignatureCodec.normalizeSecp256k1LowS(p1363Of(BigInteger.ONE, secp256k1Order))
        }
    }

    // ------------------------------------------------------------------------------------
    // normalize() end-to-end behaviour
    // ------------------------------------------------------------------------------------

    @Test
    fun `normalize transcodes DER to P1363 for P-256`() {
        val normalized = EcdsaSignatureCodec.normalize(minimalDer, Algorithm.P256)
        assertContentEquals(minimalP1363, normalized)
    }

    @Test
    fun `normalize transcodes DER and applies low-s for secp256k1`() {
        val r = BigInteger.valueOf(7)
        val highS = halfOrder.add(BigInteger.valueOf(1000))
        val der = EcdsaSignatureCodec.p1363ToDer(p1363Of(r, highS))

        val normalized = EcdsaSignatureCodec.normalize(der, Algorithm.Secp256k1)

        assertEquals(64, normalized.size)
        assertEquals(secp256k1Order.subtract(highS), BigInteger(1, normalized.copyOfRange(32, 64)))
        assertNotEquals(highS, BigInteger(1, normalized.copyOfRange(32, 64)))
    }

    @Test
    fun `normalize passes P1363 input through for NIST curves`() {
        val sig = ByteArray(96) { (it + 1).toByte() }
        assertSame(sig, EcdsaSignatureCodec.normalize(sig, Algorithm.P384))
    }

    @Test
    fun `normalize leaves non-EC algorithms untouched`() {
        val ed25519Sig = ByteArray(64) { it.toByte() }
        assertSame(ed25519Sig, EcdsaSignatureCodec.normalize(ed25519Sig, Algorithm.Ed25519))
        val rsaSig = ByteArray(256) { it.toByte() }
        assertSame(rsaSig, EcdsaSignatureCodec.normalize(rsaSig, Algorithm.RSA.RSA_2048))
    }

    @Test
    fun `normalize leaves unrecognized formats untouched`() {
        // Not DER and not the expected 64-byte P1363 size for P-256.
        val weird = ByteArray(71) { 0x5A }
        assertSame(weird, EcdsaSignatureCodec.normalize(weird, Algorithm.P256))
    }

    // ------------------------------------------------------------------------------------
    // Size tables
    // ------------------------------------------------------------------------------------

    @Test
    fun `field and P1363 sizes match the curve table`() {
        assertEquals(32, EcdsaSignatureCodec.fieldSizeBytes(Algorithm.P256))
        assertEquals(32, EcdsaSignatureCodec.fieldSizeBytes(Algorithm.Secp256k1))
        assertEquals(48, EcdsaSignatureCodec.fieldSizeBytes(Algorithm.P384))
        assertEquals(66, EcdsaSignatureCodec.fieldSizeBytes(Algorithm.P521))
        assertEquals(null, EcdsaSignatureCodec.fieldSizeBytes(Algorithm.Ed25519))
        assertEquals(null, EcdsaSignatureCodec.fieldSizeBytes(Algorithm.RSA.RSA_2048))
        assertEquals(null, EcdsaSignatureCodec.fieldSizeBytes(Algorithm.BLS12_381))

        assertEquals(64, EcdsaSignatureCodec.p1363SizeBytes(Algorithm.P256))
        assertEquals(64, EcdsaSignatureCodec.p1363SizeBytes(Algorithm.Secp256k1))
        assertEquals(96, EcdsaSignatureCodec.p1363SizeBytes(Algorithm.P384))
        assertEquals(132, EcdsaSignatureCodec.p1363SizeBytes(Algorithm.P521))
        assertEquals(null, EcdsaSignatureCodec.p1363SizeBytes(Algorithm.Ed25519))
    }
}
