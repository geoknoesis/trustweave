package com.trustweave.credential.proof

import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for ProofOptions.
 */
class ProofOptionsTest {

    @Test
    fun `test ProofGeneratorOptions with all fields`() {
        val options = ProofGeneratorOptions(
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
    fun `test ProofGeneratorOptions with defaults`() {
        val options = ProofGeneratorOptions()

        assertEquals("assertionMethod", options.proofPurpose)
        assertNull(options.challenge)
        assertNull(options.domain)
        assertNull(options.verificationMethod)
    }

    @Test
    fun `test ProofOptions with authentication purpose`() {
        val options = ProofGeneratorOptions(
            proofPurpose = "authentication",
            challenge = "challenge-123",
            domain = "example.com"
        )

        assertEquals("authentication", options.proofPurpose)
        assertEquals("challenge-123", options.challenge)
        assertEquals("example.com", options.domain)
    }
}



