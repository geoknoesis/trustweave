package io.geoknoesis.vericore.credential.models

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for VerifiablePresentation model.
 */
class VerifiablePresentationTest {

    @Test
    fun `test create presentation with all fields`() {
        val credential = VerifiableCredential(
            type = listOf("VerifiableCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject { put("id", "did:key:subject") },
            issuanceDate = "2024-01-01T00:00:00Z"
        )
        
        val proof = Proof(
            type = "Ed25519Signature2020",
            created = "2024-01-01T00:00:00Z",
            verificationMethod = "did:key:holder#key-1",
            proofPurpose = "authentication",
            challenge = "challenge-123",
            domain = "example.com"
        )
        
        val presentation = VerifiablePresentation(
            id = "https://example.com/presentations/1",
            type = listOf("VerifiablePresentation"),
            verifiableCredential = listOf(credential),
            holder = "did:key:holder",
            proof = proof,
            challenge = "challenge-123",
            domain = "example.com"
        )
        
        assertEquals("https://example.com/presentations/1", presentation.id)
        assertEquals(1, presentation.verifiableCredential.size)
        assertEquals("did:key:holder", presentation.holder)
        assertNotNull(presentation.proof)
        assertEquals("challenge-123", presentation.challenge)
        assertEquals("example.com", presentation.domain)
    }

    @Test
    fun `test create presentation with multiple credentials`() {
        val cred1 = VerifiableCredential(
            type = listOf("VerifiableCredential", "PersonCredential"),
            issuer = "did:key:issuer1",
            credentialSubject = buildJsonObject { put("id", "did:key:subject") },
            issuanceDate = "2024-01-01T00:00:00Z"
        )
        
        val cred2 = VerifiableCredential(
            type = listOf("VerifiableCredential", "EducationCredential"),
            issuer = "did:key:issuer2",
            credentialSubject = buildJsonObject { put("id", "did:key:subject") },
            issuanceDate = "2024-01-01T00:00:00Z"
        )
        
        val presentation = VerifiablePresentation(
            type = listOf("VerifiablePresentation"),
            verifiableCredential = listOf(cred1, cred2),
            holder = "did:key:holder"
        )
        
        assertEquals(2, presentation.verifiableCredential.size)
    }

    @Test
    fun `test create presentation with minimal fields`() {
        val presentation = VerifiablePresentation(
            type = listOf("VerifiablePresentation"),
            verifiableCredential = emptyList(),
            holder = "did:key:holder"
        )
        
        assertNull(presentation.id)
        assertNull(presentation.proof)
        assertNull(presentation.challenge)
        assertNull(presentation.domain)
        assertTrue(presentation.verifiableCredential.isEmpty())
    }

    @Test
    fun `test presentation with challenge only`() {
        val presentation = VerifiablePresentation(
            type = listOf("VerifiablePresentation"),
            verifiableCredential = emptyList(),
            holder = "did:key:holder",
            challenge = "challenge-123"
        )
        
        assertEquals("challenge-123", presentation.challenge)
        assertNull(presentation.domain)
    }

    @Test
    fun `test presentation with domain only`() {
        val presentation = VerifiablePresentation(
            type = listOf("VerifiablePresentation"),
            verifiableCredential = emptyList(),
            holder = "did:key:holder",
            domain = "example.com"
        )
        
        assertEquals("example.com", presentation.domain)
        assertNull(presentation.challenge)
    }
}

