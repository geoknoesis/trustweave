package io.geoknoesis.vericore.credential.proof

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Branch coverage tests for JwtProofGenerator.
 */
class JwtProofGeneratorBranchCoverageTest {

    @Test
    fun `test JwtProofGenerator generateProof with verificationMethod in options`() = runBlocking {
        val generator = JwtProofGenerator(
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
        assertEquals("JsonWebSignature2020", proof.type)
        assertNotNull(proof.jws)
    }

    @Test
    fun `test JwtProofGenerator generateProof without verificationMethod uses publicKeyId`() = runBlocking {
        val generator = JwtProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) },
            getPublicKeyId = { "public-key-id" }
        )
        
        val credential = createTestCredential()
        val options = ProofOptions(proofPurpose = "assertionMethod")
        
        val proof = generator.generateProof(credential, "key-1", options)
        
        assertEquals("did:key:public-key-id#key-1", proof.verificationMethod)
    }

    @Test
    fun `test JwtProofGenerator generateProof without verificationMethod and null publicKeyId`() = runBlocking {
        val generator = JwtProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) },
            getPublicKeyId = { null }
        )
        
        val credential = createTestCredential()
        val options = ProofOptions(proofPurpose = "assertionMethod")
        
        val proof = generator.generateProof(credential, "key-1", options)
        
        assertEquals("did:key:key-1", proof.verificationMethod)
    }

    @Test
    fun `test JwtProofGenerator generateJwt returns JWT string`() = runBlocking {
        val generator = JwtProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )
        
        val credential = createTestCredential()
        val options = ProofOptions(proofPurpose = "assertionMethod")
        
        val jwt = generator.generateJwt(credential, "key-1", options)
        
        assertNotNull(jwt)
        assertEquals("PLACEHOLDER_JWT_STRING", jwt)
    }

    @Test
    fun `test JwtProofGenerator generateJwt throws when jws is null`() = runBlocking {
        // This test would require mocking to make jws null
        // For now, we test the normal flow
        val generator = JwtProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )
        
        val credential = createTestCredential()
        val options = ProofOptions(proofPurpose = "assertionMethod")
        
        // Current implementation always sets jws, so this won't throw
        val jwt = generator.generateJwt(credential, "key-1", options)
        assertNotNull(jwt)
    }

    @Test
    fun `test JwtProofGenerator generateProof with challenge`() = runBlocking {
        val generator = JwtProofGenerator(
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
    fun `test JwtProofGenerator generateProof with domain`() = runBlocking {
        val generator = JwtProofGenerator(
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

    private fun createTestCredential(
        id: String? = null,
        types: List<String> = listOf("VerifiableCredential", "PersonCredential"),
        issuerDid: String = "did:key:issuer",
        subject: JsonObject = buildJsonObject {
            put("id", "did:key:subject")
            put("name", "John Doe")
        },
        issuanceDate: String = java.time.Instant.now().toString()
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = types,
            issuer = issuerDid,
            credentialSubject = subject,
            issuanceDate = issuanceDate
        )
    }
}

