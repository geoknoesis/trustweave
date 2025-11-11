package com.geoknoesis.vericore.credential.proof

import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for ProofOptions.
 */
class ProofOptionsTest {

    @Test
    fun `test ProofOptions with all fields`() {
        val options = ProofOptions(
            proofPurpose = "assertionMethod",
            challenge = "challenge-123",
            domain = "example.com",
            verificationMethod = "did:key:issuer#key-1"
        )
        
        assertEquals("assertionMethod", options.proofPurpose)
        assertEquals("challenge-123", options.challenge)
        assertEquals("example.com", options.domain)
        assertEquals("did:key:issuer#key-1", options.verificationMethod)
    }

    @Test
    fun `test ProofOptions with defaults`() {
        val options = ProofOptions()
        
        assertEquals("assertionMethod", options.proofPurpose)
        assertNull(options.challenge)
        assertNull(options.domain)
        assertNull(options.verificationMethod)
    }

    @Test
    fun `test ProofOptions with authentication purpose`() {
        val options = ProofOptions(
            proofPurpose = "authentication",
            challenge = "challenge-123",
            domain = "example.com"
        )
        
        assertEquals("authentication", options.proofPurpose)
        assertEquals("challenge-123", options.challenge)
        assertEquals("example.com", options.domain)
    }
}



