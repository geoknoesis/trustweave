package com.geoknoesis.vericore.credential.proof

import com.geoknoesis.vericore.credential.models.Proof
import com.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import java.util.UUID

/**
 * Comprehensive tests for BbsProofGenerator API.
 */
class BbsProofGeneratorTest {

    private lateinit var generator: BbsProofGenerator

    @BeforeEach
    fun setup() {
        generator = BbsProofGenerator(
            signer = { data, _ -> "bbs-signature-${UUID.randomUUID()}".toByteArray() },
            getPublicKeyId = { "did:key:bbs#key-1" }
        )
    }

    @Test
    fun `test generate proof`() = runBlocking {
        val credential = createTestCredential()
        
        val proof = generator.generateProof(
            credential = credential,
            keyId = "key-1",
            options = ProofOptions(proofPurpose = "assertionMethod")
        )
        
        assertNotNull(proof)
        assertEquals("BbsBlsSignature2020", proof.type)
        assertNotNull(proof.proofValue)
        assertEquals("PLACEHOLDER_BBS_SIGNATURE", proof.proofValue)
    }

    @Test
    fun `test generate proof with options`() = runBlocking {
        val credential = createTestCredential()
        
        val proof = generator.generateProof(
            credential = credential,
            keyId = "key-1",
            options = ProofOptions(
                proofPurpose = "authentication",
                challenge = "challenge-123",
                domain = "example.com",
                verificationMethod = "did:key:custom#key-1"
            )
        )
        
        assertEquals("authentication", proof.proofPurpose)
        assertEquals("challenge-123", proof.challenge)
        assertEquals("example.com", proof.domain)
        assertEquals("did:key:custom#key-1", proof.verificationMethod)
    }

    @Test
    fun `test create selective disclosure proof`() = runBlocking {
        val credential = createTestCredential()
        
        val proof = generator.createSelectiveDisclosureProof(
            credential = credential,
            disclosedFields = listOf("credentialSubject.name", "credentialSubject.email"),
            keyId = "key-1"
        )
        
        assertNotNull(proof)
        assertEquals("BbsBlsSignature2020", proof.type)
    }

    @Test
    fun `test proof type is correct`() = runBlocking {
        assertEquals("BbsBlsSignature2020", generator.proofType)
    }

    private fun createTestCredential(
        id: String? = "https://example.com/credentials/1",
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

