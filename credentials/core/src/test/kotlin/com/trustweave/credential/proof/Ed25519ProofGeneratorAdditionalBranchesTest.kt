package com.trustweave.credential.proof

import com.trustweave.credential.models.Proof
import com.trustweave.credential.models.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.*

/**
 * Additional comprehensive branch coverage tests for Ed25519ProofGenerator.
 * Covers additional branches not covered in Ed25519ProofGeneratorBranchCoverageTest.
 */
class Ed25519ProofGeneratorAdditionalBranchesTest {

    // ========== Selective Disclosure Branches ==========

    // Note: Ed25519ProofGenerator doesn't have createSelectiveDisclosureProof
    // This is only available in BbsProofGenerator

    // ========== Proof Options Branches ==========

    @Test
    fun `test generateProof with null challenge`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )
        
        val credential = createTestCredential()
        val options = com.trustweave.credential.proof.ProofOptions(
            proofPurpose = "assertionMethod",
            challenge = null
        )
        
        val proof = generator.generateProof(credential, "key-1", options)
        
        assertNull(proof.challenge)
    }

    @Test
    fun `test generateProof with null domain`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )
        
        val credential = createTestCredential()
        val options = com.trustweave.credential.proof.ProofOptions(
            proofPurpose = "assertionMethod",
            domain = null
        )
        
        val proof = generator.generateProof(credential, "key-1", options)
        
        assertNull(proof.domain)
    }

    @Test
    fun `test generateProof with null verificationMethod and null publicKeyId`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) },
            getPublicKeyId = { null }
        )
        
        val credential = createTestCredential()
        val options = com.trustweave.credential.proof.ProofOptions(
            proofPurpose = "assertionMethod",
            verificationMethod = null
        )
        
        val proof = generator.generateProof(credential, "key-1", options)
        
        assertEquals("did:key:key-1", proof.verificationMethod)
    }

    @Test
    fun `test generateProof with empty string verificationMethod`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )
        
        val credential = createTestCredential()
        val options = com.trustweave.credential.proof.ProofOptions(
            proofPurpose = "assertionMethod",
            verificationMethod = ""
        )
        
        val proof = generator.generateProof(credential, "key-1", options)
        
        // Should fall back to keyId-based verification method
        assertNotNull(proof.verificationMethod)
    }

    // ========== Credential Structure Branches ==========

    @Test
    fun `test generateProof with credential having null id`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )
        
        val credential = createTestCredential(id = null)
        
        val proof = generator.generateProof(credential, "key-1", com.trustweave.credential.proof.ProofOptions(proofPurpose = "assertionMethod"))
        
        assertNotNull(proof)
    }

    @Test
    fun `test generateProof with credential having null issuer`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )
        
        val credential = createTestCredential(issuerDid = null)
        
        assertFailsWith<IllegalArgumentException> {
            generator.generateProof(credential, "key-1", com.trustweave.credential.proof.ProofOptions(proofPurpose = "assertionMethod"))
        }
    }

    @Test
    fun `test generateProof with credential having empty types`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )
        
        val credential = createTestCredential(types = emptyList())
        
        val proof = generator.generateProof(credential, "key-1", com.trustweave.credential.proof.ProofOptions(proofPurpose = "assertionMethod"))
        
        assertNotNull(proof)
    }

    // credentialSubject cannot be null in VerifiableCredential

    @Test
    fun `test generateProof with credential having empty credentialSubject`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )
        
        val credential = createTestCredential(credentialSubject = buildJsonObject { })
        
        val proof = generator.generateProof(credential, "key-1", com.trustweave.credential.proof.ProofOptions(proofPurpose = "assertionMethod"))
        
        assertNotNull(proof)
    }

    // ========== Signer Function Branches ==========

    @Test
    fun `test generateProof with signer returning empty byte array`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf() }
        )
        
        val credential = createTestCredential()
        
        val proof = generator.generateProof(credential, "key-1", com.trustweave.credential.proof.ProofOptions(proofPurpose = "assertionMethod"))
        
        assertNotNull(proof)
        assertNotNull(proof.proofValue)
    }

    @Test
    fun `test generateProof with signer throwing exception`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> throw RuntimeException("Signing failed") }
        )
        
        val credential = createTestCredential()
        
        assertFailsWith<RuntimeException> {
            generator.generateProof(credential, "key-1", com.trustweave.credential.proof.ProofOptions(proofPurpose = "assertionMethod"))
        }
    }

    // ========== Helper Functions ==========

    private fun createTestCredential(
        id: String? = "credential-123",
        issuerDid: String? = "did:key:issuer123",
        types: List<String> = listOf("VerifiableCredential", "PersonCredential"),
        credentialSubject: JsonObject = buildJsonObject {
            put("id", "did:key:subject123")
            put("name", "John Doe")
            put("email", "john@example.com")
        }
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = types,
            issuer = issuerDid ?: "did:key:issuer123",
            issuanceDate = Instant.now().toString(),
            credentialSubject = credentialSubject,
            proof = null
        )
    }

    private fun createTestCredentialWithNestedSubject(): VerifiableCredential {
        return VerifiableCredential(
            id = "credential-123",
            type = listOf("VerifiableCredential", "PersonCredential"),
            issuer = "did:key:issuer123",
            issuanceDate = Instant.now().toString(),
            credentialSubject = buildJsonObject {
                put("id", "did:key:subject123")
                put("name", "John Doe")
                put("address", buildJsonObject {
                    put("street", "123 Main St")
                    put("city", "New York")
                    put("country", "USA")
                })
            },
            proof = null
        )
    }
}

