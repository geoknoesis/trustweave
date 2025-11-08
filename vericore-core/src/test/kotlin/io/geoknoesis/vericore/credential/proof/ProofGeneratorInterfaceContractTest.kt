package io.geoknoesis.vericore.credential.proof

import io.geoknoesis.vericore.credential.models.Proof
import io.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive interface contract tests for ProofGenerator.
 * Tests all methods, branches, and edge cases.
 */
class ProofGeneratorInterfaceContractTest {

    @Test
    fun `test ProofGenerator proofType returns type`() = runBlocking {
        val generator = createMockGenerator("Ed25519Signature2020")
        
        assertEquals("Ed25519Signature2020", generator.proofType)
    }

    @Test
    fun `test ProofGenerator generateProof returns proof`() = runBlocking {
        val generator = createMockGenerator("Ed25519Signature2020")
        val credential = createTestCredential()
        val options = ProofOptions(
            proofPurpose = "assertionMethod",
            challenge = "challenge-123",
            domain = "example.com"
        )
        
        val proof = generator.generateProof(credential, "key-1", options)
        
        assertNotNull(proof)
        assertEquals("Ed25519Signature2020", proof.type)
        assertEquals("assertionMethod", proof.proofPurpose)
        assertEquals("challenge-123", proof.challenge)
        assertEquals("example.com", proof.domain)
    }

    @Test
    fun `test ProofGenerator generateProof with minimal options`() = runBlocking {
        val generator = createMockGenerator("Ed25519Signature2020")
        val credential = createTestCredential()
        val options = ProofOptions()
        
        val proof = generator.generateProof(credential, "key-1", options)
        
        assertNotNull(proof)
        assertEquals("assertionMethod", proof.proofPurpose) // Default
    }

    @Test
    fun `test ProofGenerator generateProof with verificationMethod in options`() = runBlocking {
        val generator = createMockGenerator("Ed25519Signature2020")
        val credential = createTestCredential()
        val options = ProofOptions(
            verificationMethod = "did:key:custom#key-1"
        )
        
        val proof = generator.generateProof(credential, "key-1", options)
        
        assertNotNull(proof)
        assertEquals("did:key:custom#key-1", proof.verificationMethod)
    }

    @Test
    fun `test ProofGenerator generateProof with additionalOptions`() = runBlocking {
        val generator = createMockGenerator("Ed25519Signature2020")
        val credential = createTestCredential()
        val options = ProofOptions(
            additionalOptions = mapOf("customField" to "customValue")
        )
        
        val proof = generator.generateProof(credential, "key-1", options)
        
        assertNotNull(proof)
    }

    @Test
    fun `test ProofGenerator generateProof with empty credential`() = runBlocking {
        val generator = createMockGenerator("Ed25519Signature2020")
        val credential = VerifiableCredential(
            type = listOf("VerifiableCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject {},
            issuanceDate = java.time.Instant.now().toString()
        )
        val options = ProofOptions()
        
        val proof = generator.generateProof(credential, "key-1", options)
        
        assertNotNull(proof)
    }

    @Test
    fun `test ProofGenerator generateProof with complex credential`() = runBlocking {
        val generator = createMockGenerator("Ed25519Signature2020")
        val credential = createTestCredential(
            types = listOf("VerifiableCredential", "PersonCredential", "DegreeCredential"),
            subject = buildJsonObject {
                put("id", "did:key:subject")
                put("name", "John Doe")
                put("email", "john@example.com")
                put("age", 30)
                put("address", buildJsonObject {
                    put("street", "123 Main St")
                    put("city", "New York")
                })
            }
        )
        val options = ProofOptions()
        
        val proof = generator.generateProof(credential, "key-1", options)
        
        assertNotNull(proof)
    }

    private fun createMockGenerator(proofType: String): ProofGenerator {
        return object : ProofGenerator {
            override val proofType: String = proofType
            
            override suspend fun generateProof(
                credential: VerifiableCredential,
                keyId: String,
                options: ProofOptions
            ): Proof {
                return Proof(
                    type = proofType,
                    created = java.time.Instant.now().toString(),
                    verificationMethod = options.verificationMethod ?: "did:key:issuer#$keyId",
                    proofPurpose = options.proofPurpose,
                    proofValue = "test-proof-value",
                    challenge = options.challenge,
                    domain = options.domain
                )
            }
        }
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

