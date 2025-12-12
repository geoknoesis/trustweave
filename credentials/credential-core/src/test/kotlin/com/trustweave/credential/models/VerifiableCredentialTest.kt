package com.trustweave.credential.models

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.CredentialProof
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.vc.CredentialStatus
import com.trustweave.credential.model.vc.CredentialSchema
import com.trustweave.credential.model.vc.TermsOfUse
import com.trustweave.credential.model.vc.RefreshService
import com.trustweave.credential.model.Evidence
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.credential.identifiers.IssuerId
import com.trustweave.credential.identifiers.SchemaId
import com.trustweave.credential.identifiers.StatusListId
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.model.ProofType
import com.trustweave.credential.model.ProofTypes
import com.trustweave.credential.model.StatusPurpose
import com.trustweave.did.identifiers.VerificationMethodId
import com.trustweave.did.identifiers.Did
import com.trustweave.core.identifiers.Iri
import kotlinx.serialization.json.*
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for VerifiableCredential model.
 */
class VerifiableCredentialTest {

    @Test
    fun `test create credential with all fields`() {
        val issuerDid = com.trustweave.did.identifiers.Did("did:key:issuer")
        val proof = CredentialProof.LinkedDataProof(
            type = "Ed25519Signature2020",
            created = Instant.parse("2024-01-01T00:00:00Z"),
            verificationMethod = "did:key:issuer#key-1",
            proofPurpose = "assertionMethod",
            proofValue = "test-proof",
            additionalProperties = emptyMap()
        )

        val credential = VerifiableCredential(
            id = CredentialId("https://example.com/credentials/1"),
            type = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("PersonCredential")),
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromDid(
                Did("did:key:subject"),
                claims = mapOf("name" to JsonPrimitive("John Doe"))
            ),
            issuanceDate = Instant.parse("2024-01-01T00:00:00Z"),
            expirationDate = Instant.parse("2025-01-01T00:00:00Z"),
            credentialStatus = CredentialStatus(
                id = StatusListId("https://example.com/status/1"),
                type = "StatusList2021Entry"
            ),
            credentialSchema = CredentialSchema(
                id = SchemaId("https://example.com/schemas/person"),
                type = "JsonSchemaValidator2018"
            ),
            evidence = listOf(
                Evidence(
                    id = CredentialId("evidence-1"),
                    type = listOf("DocumentVerification"),
                    evidenceDocument = buildJsonObject { put("type", "passport") }
                )
            ),
            proof = proof,
            termsOfUse = listOf(
                TermsOfUse(
                    id = "terms-1",
                    type = "IssuerPolicy",
                    additionalProperties = mapOf("url" to JsonPrimitive("https://example.com/terms"))
                )
            ),
            refreshService = RefreshService(
                id = Iri("refresh-1"),
                type = "CredentialRefreshService2020"
            )
        )

        assertEquals("https://example.com/credentials/1", credential.id?.value)
        assertEquals(2, credential.type.size)
        assertEquals("did:key:issuer", credential.issuer.id.value)
        assertNotNull(credential.expirationDate)
        assertNotNull(credential.proof)
        assertNotNull(credential.evidence)
        assertEquals(1, credential.evidence?.size)
    }

    @Test
    fun `test create credential with minimal fields`() {
        val credential = VerifiableCredential(
            type = listOf(CredentialType.VerifiableCredential),
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromDid(
                Did("did:key:subject"),
                claims = emptyMap()
            ),
            issuanceDate = Instant.parse("2024-01-01T00:00:00Z")
        )

        assertNull(credential.id)
        assertNull(credential.expirationDate)
        assertNull(credential.proof)
        assertNull(credential.evidence)
    }

    @Test
    fun `test credential with proof`() {
        val issuerDid2 = com.trustweave.did.identifiers.Did("did:key:issuer")
        val proof = CredentialProof.LinkedDataProof(
            type = "Ed25519Signature2020",
            created = Instant.parse("2024-01-01T00:00:00Z"),
            verificationMethod = "did:key:issuer#key-1",
            proofPurpose = "assertionMethod",
            proofValue = "zSignatureValue",
            additionalProperties = emptyMap()
        )

        val credential = VerifiableCredential(
            type = listOf(CredentialType.VerifiableCredential),
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromDid(
                Did("did:key:subject"),
                claims = emptyMap()
            ),
            issuanceDate = Instant.parse("2024-01-01T00:00:00Z"),
            proof = proof
        )

        assertEquals(proof, credential.proof)
        val linkedDataProof = credential.proof as? CredentialProof.LinkedDataProof
        assertNotNull(linkedDataProof)
        assertEquals("zSignatureValue", linkedDataProof.proofValue)
    }

    @Test
    fun `test credential with JWT proof`() {
        val issuerDid3 = com.trustweave.did.identifiers.Did("did:key:issuer")
        val proof = CredentialProof.JwtProof(
            jwt = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9..."
        )

        val credential = VerifiableCredential(
            type = listOf(CredentialType.VerifiableCredential),
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromDid(
                Did("did:key:subject"),
                claims = emptyMap()
            ),
            issuanceDate = Instant.parse("2024-01-01T00:00:00Z"),
            proof = proof
        )

        val jwtProof = credential.proof as? CredentialProof.JwtProof
        assertNotNull(jwtProof)
        assertNotNull(jwtProof.jwt)
    }

    @Test
    fun `test credential with challenge and domain in proof`() {
        val issuerDid4 = com.trustweave.did.identifiers.Did("did:key:issuer")
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

        val credential = VerifiableCredential(
            type = listOf(CredentialType.VerifiableCredential),
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromDid(
                Did("did:key:subject"),
                claims = emptyMap()
            ),
            issuanceDate = Instant.parse("2024-01-01T00:00:00Z"),
            proof = proof
        )

        val linkedDataProof = credential.proof as? CredentialProof.LinkedDataProof
        assertNotNull(linkedDataProof)
        assertEquals("challenge-123", linkedDataProof.additionalProperties["challenge"]?.jsonPrimitive?.content)
        assertEquals("example.com", linkedDataProof.additionalProperties["domain"]?.jsonPrimitive?.content)
    }
}


