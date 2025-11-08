package io.geoknoesis.vericore.credential.models

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for VerifiableCredential model.
 */
class VerifiableCredentialTest {

    @Test
    fun `test create credential with all fields`() {
        val proof = Proof(
            type = "Ed25519Signature2020",
            created = "2024-01-01T00:00:00Z",
            verificationMethod = "did:key:issuer#key-1",
            proofPurpose = "assertionMethod"
        )
        
        val credential = VerifiableCredential(
            id = "https://example.com/credentials/1",
            type = listOf("VerifiableCredential", "PersonCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject {
                put("id", "did:key:subject")
                put("name", "John Doe")
            },
            issuanceDate = "2024-01-01T00:00:00Z",
            expirationDate = "2025-01-01T00:00:00Z",
            credentialStatus = CredentialStatus(
                id = "https://example.com/status/1",
                type = "StatusList2021Entry"
            ),
            credentialSchema = CredentialSchema(
                id = "https://example.com/schemas/person",
                type = "JsonSchemaValidator2018"
            ),
            evidence = listOf(
                Evidence(
                    id = "evidence-1",
                    type = listOf("DocumentVerification"),
                    evidenceDocument = buildJsonObject { put("type", "passport") }
                )
            ),
            proof = proof,
            termsOfUse = TermsOfUse(
                id = "terms-1",
                type = "IssuerPolicy",
                termsOfUse = buildJsonObject { put("url", "https://example.com/terms") }
            ),
            refreshService = RefreshService(
                id = "refresh-1",
                type = "CredentialRefreshService2020",
                serviceEndpoint = "https://example.com/refresh"
            )
        )
        
        assertEquals("https://example.com/credentials/1", credential.id)
        assertEquals(2, credential.type.size)
        assertEquals("did:key:issuer", credential.issuer)
        assertNotNull(credential.expirationDate)
        assertNotNull(credential.proof)
        assertNotNull(credential.evidence)
        assertEquals(1, credential.evidence?.size)
    }

    @Test
    fun `test create credential with minimal fields`() {
        val credential = VerifiableCredential(
            type = listOf("VerifiableCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject {
                put("id", "did:key:subject")
            },
            issuanceDate = "2024-01-01T00:00:00Z"
        )
        
        assertNull(credential.id)
        assertNull(credential.expirationDate)
        assertNull(credential.proof)
        assertNull(credential.evidence)
    }

    @Test
    fun `test credential with proof`() {
        val proof = Proof(
            type = "Ed25519Signature2020",
            created = "2024-01-01T00:00:00Z",
            verificationMethod = "did:key:issuer#key-1",
            proofPurpose = "assertionMethod",
            proofValue = "zSignatureValue"
        )
        
        val credential = VerifiableCredential(
            type = listOf("VerifiableCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject { put("id", "did:key:subject") },
            issuanceDate = "2024-01-01T00:00:00Z",
            proof = proof
        )
        
        assertEquals(proof, credential.proof)
        assertEquals("zSignatureValue", credential.proof?.proofValue)
    }

    @Test
    fun `test credential with JWT proof`() {
        val proof = Proof(
            type = "JsonWebSignature2020",
            created = "2024-01-01T00:00:00Z",
            verificationMethod = "did:key:issuer#key-1",
            proofPurpose = "assertionMethod",
            jws = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
        )
        
        val credential = VerifiableCredential(
            type = listOf("VerifiableCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject { put("id", "did:key:subject") },
            issuanceDate = "2024-01-01T00:00:00Z",
            proof = proof
        )
        
        assertNotNull(credential.proof?.jws)
        assertNull(credential.proof?.proofValue)
    }

    @Test
    fun `test credential with challenge and domain in proof`() {
        val proof = Proof(
            type = "Ed25519Signature2020",
            created = "2024-01-01T00:00:00Z",
            verificationMethod = "did:key:issuer#key-1",
            proofPurpose = "authentication",
            challenge = "challenge-123",
            domain = "example.com"
        )
        
        val credential = VerifiableCredential(
            type = listOf("VerifiableCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject { put("id", "did:key:subject") },
            issuanceDate = "2024-01-01T00:00:00Z",
            proof = proof
        )
        
        assertEquals("challenge-123", credential.proof?.challenge)
        assertEquals("example.com", credential.proof?.domain)
    }
}

