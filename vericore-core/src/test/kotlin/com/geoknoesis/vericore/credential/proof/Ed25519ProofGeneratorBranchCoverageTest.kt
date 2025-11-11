package com.geoknoesis.vericore.credential.proof

import com.geoknoesis.vericore.credential.models.Proof
import com.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Branch coverage tests for Ed25519ProofGenerator focusing on edge cases.
 */
class Ed25519ProofGeneratorBranchCoverageTest {

    @Test
    fun `test Ed25519ProofGenerator generateProof with verificationMethod in options`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) },
            getPublicKeyId = { null }
        )
        
        val credential = createTestCredential()
        val options = ProofOptions(
            proofPurpose = "assertionMethod",
            verificationMethod = "did:key:custom#key-1"
        )
        
        val proof = generator.generateProof(credential, "key-1", options)
        
        assertEquals("did:key:custom#key-1", proof.verificationMethod)
    }

    @Test
    fun `test Ed25519ProofGenerator generateProof without verificationMethod uses publicKeyId`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) },
            getPublicKeyId = { "public-key-id" }
        )
        
        val credential = createTestCredential()
        val options = ProofOptions(proofPurpose = "assertionMethod")
        
        val proof = generator.generateProof(credential, "key-1", options)
        
        assertEquals("did:key:public-key-id#key-1", proof.verificationMethod)
    }

    @Test
    fun `test Ed25519ProofGenerator generateProof without verificationMethod and null publicKeyId`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) },
            getPublicKeyId = { null }
        )
        
        val credential = createTestCredential()
        val options = ProofOptions(proofPurpose = "assertionMethod")
        
        val proof = generator.generateProof(credential, "key-1", options)
        
        assertEquals("did:key:key-1", proof.verificationMethod)
    }

    @Test
    fun `test Ed25519ProofGenerator generateProof with challenge`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )
        
        val credential = createTestCredential()
        val options = ProofOptions(
            proofPurpose = "assertionMethod",
            challenge = "challenge-123"
        )
        
        val proof = generator.generateProof(credential, "key-1", options)
        
        assertEquals("challenge-123", proof.challenge)
    }

    @Test
    fun `test Ed25519ProofGenerator generateProof with domain`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )
        
        val credential = createTestCredential()
        val options = ProofOptions(
            proofPurpose = "assertionMethod",
            domain = "example.com"
        )
        
        val proof = generator.generateProof(credential, "key-1", options)
        
        assertEquals("example.com", proof.domain)
    }

    @Test
    fun `test Ed25519ProofGenerator generateProof with all optional credential fields`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )
        
        val credential = createTestCredential(
            id = "https://example.com/credentials/1",
            expirationDate = java.time.Instant.now().plusSeconds(86400).toString(),
            credentialStatus = com.geoknoesis.vericore.credential.models.CredentialStatus(
                id = "status-list-1",
                type = "StatusList2021Entry",
                statusPurpose = "revocation",
                statusListIndex = "0"
            ),
            credentialSchema = com.geoknoesis.vericore.credential.models.CredentialSchema(
                id = "schema-1",
                type = "JsonSchemaValidator2018"
            )
        )
        val options = ProofOptions(proofPurpose = "assertionMethod")
        
        val proof = generator.generateProof(credential, "key-1", options)
        
        assertNotNull(proof)
        assertEquals("Ed25519Signature2020", proof.type)
    }

    @Test
    fun `test Ed25519ProofGenerator generateProof with minimal credential`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )
        
        val credential = VerifiableCredential(
            type = listOf("VerifiableCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject {},
            issuanceDate = java.time.Instant.now().toString()
        )
        val options = ProofOptions(proofPurpose = "assertionMethod")
        
        val proof = generator.generateProof(credential, "key-1", options)
        
        assertNotNull(proof)
    }

    private fun createTestCredential(
        id: String? = null,
        types: List<String> = listOf("VerifiableCredential", "PersonCredential"),
        issuerDid: String = "did:key:issuer",
        subject: JsonObject = buildJsonObject {
            put("id", "did:key:subject")
            put("name", "John Doe")
        },
        issuanceDate: String = java.time.Instant.now().toString(),
        expirationDate: String? = null,
        credentialStatus: com.geoknoesis.vericore.credential.models.CredentialStatus? = null,
        credentialSchema: com.geoknoesis.vericore.credential.models.CredentialSchema? = null
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = types,
            issuer = issuerDid,
            credentialSubject = subject,
            issuanceDate = issuanceDate,
            expirationDate = expirationDate,
            credentialStatus = credentialStatus,
            credentialSchema = credentialSchema
        )
    }
}



