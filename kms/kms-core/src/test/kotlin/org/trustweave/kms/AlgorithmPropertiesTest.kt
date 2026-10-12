package org.trustweave.kms

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlgorithmPropertiesTest {
    @Test
    fun `RSA security level grows with the key size`() {
        assertEquals(Algorithm.SecurityLevel.LEGACY, Algorithm.RSA.RSA_2048.securityLevel)
        assertEquals(Algorithm.SecurityLevel.STANDARD, Algorithm.RSA.RSA_3072.securityLevel)
        assertEquals(Algorithm.SecurityLevel.HIGH, Algorithm.RSA.RSA_4096.securityLevel)
        assertEquals(4096, Algorithm.RSA.RSA_4096.keySize)
    }

    @Test
    fun `curve based and custom algorithms are standard level`() {
        val all =
            listOf(
                Algorithm.Ed25519,
                Algorithm.Secp256k1,
                Algorithm.P256,
                Algorithm.P384,
                Algorithm.P521,
                Algorithm.BLS12_381,
                Algorithm.Custom("x"),
            )
        all.forEach { assertEquals(Algorithm.SecurityLevel.STANDARD, it.securityLevel, it.name) }
    }

    @Test
    fun `curve names follow JOSE conventions and RSA or custom have none`() {
        assertEquals("Ed25519", Algorithm.Ed25519.curveName)
        assertEquals("secp256k1", Algorithm.Secp256k1.curveName)
        assertEquals("P-256", Algorithm.P256.curveName)
        assertEquals("P-384", Algorithm.P384.curveName)
        assertEquals("P-521", Algorithm.P521.curveName)
        assertEquals("BLS12-381", Algorithm.BLS12_381.curveName)
        assertNull(Algorithm.RSA.RSA_2048.curveName)
        assertNull(Algorithm.Custom("x").curveName)
    }

    @Test
    fun `RSA rejects unsupported key sizes`() {
        assertFailsWith<IllegalArgumentException> { Algorithm.RSA(1024) }
        assertFailsWith<IllegalArgumentException> { Algorithm.RSA(2049) }
        assertEquals("RSA-3072", Algorithm.RSA(3072).name)
    }

    @Test
    fun `compatibility is reflexive and requires the same RSA size`() {
        assertTrue(Algorithm.P256.isCompatibleWith(Algorithm.P256))
        assertTrue(Algorithm.RSA.RSA_2048.isCompatibleWith(Algorithm.RSA(2048)))
        assertFalse(Algorithm.RSA.RSA_2048.isCompatibleWith(Algorithm.RSA.RSA_4096))
    }

    @Test
    fun `Ed25519 and BLS never mix with other curves in either direction`() {
        assertFalse(Algorithm.Ed25519.isCompatibleWith(Algorithm.P256))
        assertFalse(Algorithm.P256.isCompatibleWith(Algorithm.Ed25519))
        assertFalse(Algorithm.BLS12_381.isCompatibleWith(Algorithm.Secp256k1))
        assertFalse(Algorithm.Secp256k1.isCompatibleWith(Algorithm.BLS12_381))
        assertFalse(Algorithm.Ed25519.isCompatibleWith(Algorithm.BLS12_381))
    }

    @Test
    fun `different EC curves and RSA versus EC are incompatible`() {
        assertFalse(Algorithm.P256.isCompatibleWith(Algorithm.P384))
        assertFalse(Algorithm.P256.isCompatibleWith(Algorithm.RSA.RSA_2048))
        assertFalse(Algorithm.RSA.RSA_2048.isCompatibleWith(Algorithm.P256))
    }

    @Test
    fun `custom algorithms are compatible only with a custom of the same name`() {
        assertTrue(Algorithm.Custom("foo").isCompatibleWith(Algorithm.Custom("foo")))
        assertFalse(Algorithm.Custom("foo").isCompatibleWith(Algorithm.Custom("bar")))
        assertFalse(Algorithm.Custom("foo").isCompatibleWith(Algorithm.P256))
        assertFalse(Algorithm.P256.isCompatibleWith(Algorithm.Custom("P-256")))
    }

    @Test
    fun `a custom algorithm named like a standard one is never equal to it`() {
        assertNotEquals<Algorithm>(Algorithm.Custom("Ed25519"), Algorithm.Ed25519)
        assertNotEquals<Algorithm>(Algorithm.Ed25519, Algorithm.Custom("Ed25519"))
        assertNotEquals<Algorithm>(Algorithm.Custom("RSA-2048"), Algorithm.RSA.RSA_2048)
        assertNotEquals(Algorithm.Custom("Ed25519").hashCode(), Algorithm.Ed25519.hashCode())
        assertNotEquals(Algorithm.RSA.RSA_2048.hashCode(), Algorithm.RSA.RSA_3072.hashCode())
        assertEquals(Algorithm.RSA(2048).hashCode(), Algorithm.RSA.RSA_2048.hashCode())
        assertEquals("P-384", Algorithm.P384.toString())
    }

    @Test
    fun `parse is case insensitive for standard names and accepts aliases`() {
        assertEquals(Algorithm.Ed25519, Algorithm.parse("ed25519"))
        assertEquals(Algorithm.Secp256k1, Algorithm.parse("SECP256K1"))
        assertEquals(Algorithm.P256, Algorithm.parse("p256"))
        assertEquals(Algorithm.P384, Algorithm.parse("P384"))
        assertEquals(Algorithm.P521, Algorithm.parse("p-521"))
        assertEquals(Algorithm.BLS12_381, Algorithm.parse("bls12_381"))
        assertEquals(Algorithm.RSA(4096), Algorithm.parse("rsa-4096"))
    }

    @Test
    fun `parse rejects malformed names instead of inventing algorithms`() {
        assertNull(Algorithm.parse("RSA"))
        assertNull(Algorithm.parse("RSA-1024"))
        assertNull(Algorithm.parse("RSA-abc"))
        assertNull(Algorithm.parse(""))
        assertNull(Algorithm.parse("   "))
        assertNull(Algorithm.parse("has space"))
        assertNull(Algorithm.parse("semi;colon"))
    }

    @Test
    fun `parse yields a custom algorithm for unknown but well formed names`() {
        assertEquals(Algorithm.Custom("my-algo_1"), Algorithm.parse("my-algo_1"))
    }
}
