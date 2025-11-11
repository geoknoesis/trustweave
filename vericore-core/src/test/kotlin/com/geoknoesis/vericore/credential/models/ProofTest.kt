package com.geoknoesis.vericore.credential.models

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for Proof model.
 */
class ProofTest {

    @Test
    fun `test create proof with proofValue`() {
        val proof = Proof(
            type = "Ed25519Signature2020",
            created = "2024-01-01T00:00:00Z",
            verificationMethod = "did:key:issuer#key-1",
            proofPurpose = "assertionMethod",
            proofValue = "zSignatureValue"
        )
        
        assertEquals("Ed25519Signature2020", proof.type)
        assertEquals("did:key:issuer#key-1", proof.verificationMethod)
        assertEquals("assertionMethod", proof.proofPurpose)
        assertEquals("zSignatureValue", proof.proofValue)
        assertNull(proof.jws)
    }

    @Test
    fun `test create proof with JWS`() {
        val proof = Proof(
            type = "JsonWebSignature2020",
            created = "2024-01-01T00:00:00Z",
            verificationMethod = "did:key:issuer#key-1",
            proofPurpose = "assertionMethod",
            jws = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
        )
        
        assertEquals("JsonWebSignature2020", proof.type)
        assertNotNull(proof.jws)
        assertNull(proof.proofValue)
    }

    @Test
    fun `test proof with challenge and domain`() {
        val proof = Proof(
            type = "Ed25519Signature2020",
            created = "2024-01-01T00:00:00Z",
            verificationMethod = "did:key:issuer#key-1",
            proofPurpose = "authentication",
            challenge = "challenge-123",
            domain = "example.com"
        )
        
        assertEquals("authentication", proof.proofPurpose)
        assertEquals("challenge-123", proof.challenge)
        assertEquals("example.com", proof.domain)
    }

    @Test
    fun `test proof with minimal fields`() {
        val proof = Proof(
            type = "Ed25519Signature2020",
            created = "2024-01-01T00:00:00Z",
            verificationMethod = "did:key:issuer#key-1",
            proofPurpose = "assertionMethod"
        )
        
        assertNull(proof.proofValue)
        assertNull(proof.jws)
        assertNull(proof.challenge)
        assertNull(proof.domain)
    }
}


