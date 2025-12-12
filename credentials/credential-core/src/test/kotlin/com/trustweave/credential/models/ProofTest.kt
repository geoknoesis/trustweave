package com.trustweave.credential.models

import com.trustweave.credential.model.vc.CredentialProof
import kotlinx.serialization.json.*
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for Proof model.
 */
class ProofTest {

    @Test
    fun `test create proof with proofValue`() {
        val proof = CredentialProof.LinkedDataProof(
            type = "Ed25519Signature2020",
            created = Instant.parse("2024-01-01T00:00:00Z"),
            verificationMethod = "did:key:issuer#key-1",
            proofPurpose = "assertionMethod",
            proofValue = "zSignatureValue",
            additionalProperties = emptyMap()
        )

        assertEquals("Ed25519Signature2020", proof.type)
        assertEquals("did:key:issuer#key-1", proof.verificationMethod)
        assertEquals("assertionMethod", proof.proofPurpose)
        assertEquals("zSignatureValue", proof.proofValue)
    }

    @Test
    fun `test create proof with JWS`() {
        val proof = CredentialProof.JwtProof(
            jwt = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
        )

        assertTrue(proof is CredentialProof.JwtProof)
        assertNotNull(proof.jwt)
    }

    @Test
    fun `test proof with challenge and domain`() {
        val proof = CredentialProof.LinkedDataProof(
            type = "Ed25519Signature2020",
            created = Instant.parse("2024-01-01T00:00:00Z"),
            verificationMethod = "did:key:issuer#key-1",
            proofPurpose = "authentication",
            proofValue = "test-proof",
            additionalProperties = mapOf(
                "challenge" to JsonPrimitive("challenge-123"),
                "domain" to JsonPrimitive("example.com")
            )
        )

        assertEquals("authentication", proof.proofPurpose)
        assertEquals("challenge-123", proof.additionalProperties["challenge"]?.jsonPrimitive?.content)
        assertEquals("example.com", proof.additionalProperties["domain"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test proof with minimal fields`() {
        val proof = CredentialProof.LinkedDataProof(
            type = "Ed25519Signature2020",
            created = Instant.parse("2024-01-01T00:00:00Z"),
            verificationMethod = "did:key:issuer#key-1",
            proofPurpose = "assertionMethod",
            proofValue = "test-proof",
            additionalProperties = emptyMap()
        )

        assertNotNull(proof.proofValue)
        assertTrue(proof.additionalProperties.isEmpty())
    }
}


