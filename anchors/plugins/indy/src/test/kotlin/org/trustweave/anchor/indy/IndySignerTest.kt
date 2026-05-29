package org.trustweave.anchor.indy

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IndySignerTest {

    /** RFC-style deterministic seed for repeatable tests. */
    private val seed: ByteArray = ByteArray(32) { it.toByte() }

    @Test
    fun `signBase58 matches plain BouncyCastle Ed25519`() {
        val signer = IndySigner.fromSeed(seed)
        val payload = "indy-attrib-payload".toByteArray()
        val produced = Base58.decode(signer.signBase58(payload))

        val expected = Ed25519Signer().apply {
            init(true, Ed25519PrivateKeyParameters(seed, 0))
            update(payload, 0, payload.size)
        }.generateSignature()

        assertEquals(expected.size, produced.size, "signature length must match")
        assertEquals(64, produced.size, "Ed25519 signatures are 64 bytes")
        assertTrue(expected.contentEquals(produced), "signature bytes must match")
    }

    @Test
    fun `publicVerkeyBase58 returns 32-byte verkey`() {
        val signer = IndySigner.fromSeed(seed)
        val verkey = Base58.decode(signer.publicVerkeyBase58())
        assertEquals(32, verkey.size)
    }

    @Test
    fun `fromBase58Seed and fromSeed agree`() {
        val seedB58 = Base58.encode(seed)
        val a = IndySigner.fromSeed(seed)
        val b = IndySigner.fromBase58Seed(seedB58)
        val payload = "x".toByteArray()
        assertEquals(a.signBase58(payload), b.signBase58(payload))
    }

    @Test
    fun `fromBase58SigningKey extracts the seed half`() {
        val pub = Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded
        val full = seed + pub
        val a = IndySigner.fromSeed(seed)
        val b = IndySigner.fromBase58SigningKey(Base58.encode(full))
        val payload = "x".toByteArray()
        assertEquals(a.signBase58(payload), b.signBase58(payload))
    }

    @Test
    fun `fromSeed rejects wrong size`() {
        assertFailsWith<IllegalArgumentException> {
            IndySigner.fromSeed(ByteArray(16))
        }
    }
}
