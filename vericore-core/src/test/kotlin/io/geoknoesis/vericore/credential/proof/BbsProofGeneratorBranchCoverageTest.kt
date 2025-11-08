package io.geoknoesis.vericore.credential.proof

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Branch coverage tests for BbsProofGenerator.
 */
class BbsProofGeneratorBranchCoverageTest {

    @Test
    fun `test BbsProofGenerator generateProof with verificationMethod in options`() = runBlocking {
        val generator = BbsProofGenerator(
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
        assertEquals("BbsBlsSignature2020", proof.type)
    }

    @Test
    fun `test BbsProofGenerator generateProof without verificationMethod uses publicKeyId`() = runBlocking {
        val generator = BbsProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) },
            getPublicKeyId = { "public-key-id" }
        )
        
        val credential = createTestCredential()
        val options = ProofOptions(proofPurpose = "assertionMethod")
        
        val proof = generator.generateProof(credential, "key-1", options)
        
        assertEquals("did:key:public-key-id#key-1", proof.verificationMethod)
    }

    @Test
    fun `test BbsProofGenerator generateProof without verificationMethod and null publicKeyId`() = runBlocking {
        val generator = BbsProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) },
            getPublicKeyId = { null }
        )
        
        val credential = createTestCredential()
        val options = ProofOptions(proofPurpose = "assertionMethod")
        
        val proof = generator.generateProof(credential, "key-1", options)
        
        assertEquals("did:key:key-1", proof.verificationMethod)
    }

    @Test
    fun `test BbsProofGenerator createSelectiveDisclosureProof`() = runBlocking {
        val generator = BbsProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )
        
        val credential = createTestCredential()
        val disclosedFields = listOf("credentialSubject.name")
        
        val proof = generator.createSelectiveDisclosureProof(credential, disclosedFields, "key-1")
        
        assertNotNull(proof)
        assertEquals("BbsBlsSignature2020", proof.type)
    }

    @Test
    fun `test BbsProofGenerator createSelectiveDisclosureProof with multiple fields`() = runBlocking {
        val generator = BbsProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )
        
        val credential = createTestCredential()
        val disclosedFields = listOf("credentialSubject.name", "credentialSubject.email")
        
        val proof = generator.createSelectiveDisclosureProof(credential, disclosedFields, "key-1")
        
        assertNotNull(proof)
    }

    @Test
    fun `test BbsProofGenerator createSelectiveDisclosureProof with empty fields`() = runBlocking {
        val generator = BbsProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )
        
        val credential = createTestCredential()
        
        val proof = generator.createSelectiveDisclosureProof(credential, emptyList(), "key-1")
        
        assertNotNull(proof)
    }

    private fun createTestCredential(
        id: String? = null,
        types: List<String> = listOf("VerifiableCredential", "PersonCredential"),
        issuerDid: String = "did:key:issuer",
        subject: JsonObject = buildJsonObject {
            put("id", "did:key:subject")
            put("name", "John Doe")
            put("email", "john@example.com")
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

