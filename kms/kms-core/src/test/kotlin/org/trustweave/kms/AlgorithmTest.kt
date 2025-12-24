package org.trustweave.kms

import kotlin.test.*

/**
 * Tests for Algorithm class, including equality and parsing.
 */
class AlgorithmTest {

    @Test
    fun `test Algorithm equality - standard algorithms`() {
        assertEquals(Algorithm.Ed25519, Algorithm.Ed25519)
        assertEquals(Algorithm.Secp256k1, Algorithm.Secp256k1)
        assertEquals(Algorithm.P256, Algorithm.P256)
        
        // Case-insensitive comparison
        val parsed = Algorithm.parse("ed25519")
        assertEquals(Algorithm.Ed25519, parsed)
    }

    @Test
    fun `test Algorithm equality - Custom algorithms never equal standard`() {
        // CRITICAL: Custom("Ed25519") should NOT equal Algorithm.Ed25519
        val custom: Algorithm = Algorithm.Custom("Ed25519")
        val standard: Algorithm = Algorithm.Ed25519
        
        assertNotEquals(custom, standard, "Custom algorithms must not equal standard algorithms")
        assertNotEquals(standard, custom, "Standard algorithms must not equal custom algorithms")
    }

    @Test
    fun `test Algorithm equality - Custom algorithms with same name`() {
        val custom1 = Algorithm.Custom("MyCustomAlg")
        val custom2 = Algorithm.Custom("MyCustomAlg")
        
        assertEquals(custom1, custom2, "Custom algorithms with same name should be equal")
    }

    @Test
    fun `test Algorithm equality - Custom algorithms with different names`() {
        val custom1 = Algorithm.Custom("Alg1")
        val custom2 = Algorithm.Custom("Alg2")
        
        assertNotEquals(custom1, custom2, "Custom algorithms with different names should not be equal")
    }

    @Test
    fun `test Algorithm parse - standard algorithms`() {
        assertEquals(Algorithm.Ed25519, Algorithm.parse("Ed25519"))
        assertEquals(Algorithm.Ed25519, Algorithm.parse("ed25519"))
        assertEquals(Algorithm.Ed25519, Algorithm.parse("ED25519"))
        assertEquals(Algorithm.Secp256k1, Algorithm.parse("secp256k1"))
        assertEquals(Algorithm.P256, Algorithm.parse("P-256"))
        assertEquals(Algorithm.P256, Algorithm.parse("P256"))
    }

    @Test
    fun `test Algorithm parse - RSA algorithms`() {
        assertEquals(Algorithm.RSA.RSA_2048, Algorithm.parse("RSA-2048"))
        assertEquals(Algorithm.RSA.RSA_3072, Algorithm.parse("RSA-3072"))
        assertEquals(Algorithm.RSA.RSA_4096, Algorithm.parse("RSA-4096"))
    }

    @Test
    fun `test Algorithm parse - RSA without key size returns null`() {
        // "RSA" without key size should return null (not Custom)
        assertNull(Algorithm.parse("RSA"), "RSA without key size should return null")
    }

    @Test
    fun `test Algorithm parse - custom algorithm validation`() {
        // Valid custom algorithm
        val custom = Algorithm.parse("MyCustomAlg")
        assertNotNull(custom)
        assertTrue(custom is Algorithm.Custom)
        assertEquals("MyCustomAlg", custom.name)
    }

    @Test
    fun `test Algorithm parse - standard algorithm names parse correctly`() {
        // Standard algorithm names should parse to their standard algorithm objects
        assertEquals(Algorithm.Ed25519, Algorithm.parse("ed25519"))
        assertEquals(Algorithm.Ed25519, Algorithm.parse("ED25519"))
        assertEquals(Algorithm.Secp256k1, Algorithm.parse("secp256k1"))
        
        // The validation prevents creating Custom("Ed25519"), but parsing "Ed25519" 
        // should return Algorithm.Ed25519, not null
    }
    
    @Test
    fun `test Algorithm parse - cannot create custom with standard name`() {
        // When trying to create a custom algorithm, if the name matches a standard name,
        // parse should return the standard algorithm, not a custom one
        val parsed = Algorithm.parse("Ed25519")
        assertNotNull(parsed)
        assertTrue(parsed is Algorithm.Ed25519, "Should parse to standard algorithm, not custom")
        // Note: parsed is Algorithm.Ed25519, so it cannot be Algorithm.Custom (sealed class)
    }

    @Test
    fun `test Algorithm parse - invalid custom algorithm names`() {
        // Empty or blank names
        assertNull(Algorithm.parse(""))
        assertNull(Algorithm.parse("   "))
        
        // Names with invalid characters
        assertNull(Algorithm.parse("alg with spaces"))
        assertNull(Algorithm.parse("alg@special"))
        assertNull(Algorithm.parse("alg.with.dots"))
    }

    @Test
    fun `test Algorithm hashCode - custom and standard have different hash codes`() {
        val custom = Algorithm.Custom("Ed25519")
        val standard = Algorithm.Ed25519
        
        assertNotEquals(custom.hashCode(), standard.hashCode(), 
            "Custom and standard algorithms must have different hash codes")
    }

    @Test
    fun `test Algorithm hashCode - same custom algorithms have same hash code`() {
        val custom1 = Algorithm.Custom("MyAlg")
        val custom2 = Algorithm.Custom("MyAlg")
        
        assertEquals(custom1.hashCode(), custom2.hashCode())
    }
}

