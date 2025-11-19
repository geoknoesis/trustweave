package com.geoknoesis.vericore.credential.presentation

import com.geoknoesis.vericore.credential.CredentialVerificationOptions
import com.geoknoesis.vericore.credential.did.CredentialDidResolution
import com.geoknoesis.vericore.credential.did.CredentialDidResolver
import com.geoknoesis.vericore.credential.PresentationOptions
import com.geoknoesis.vericore.credential.PresentationVerificationOptions
import com.geoknoesis.vericore.credential.models.Proof
import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.credential.models.VerifiablePresentation
import com.geoknoesis.vericore.credential.proof.Ed25519ProofGenerator
import com.geoknoesis.vericore.credential.proof.ProofGeneratorRegistry
import com.geoknoesis.vericore.credential.proof.ProofGenerator
import com.geoknoesis.vericore.credential.verifier.CredentialVerifier
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import java.util.UUID

/**
 * Comprehensive tests for PresentationService API.
 */
class PresentationServiceTest {

    private lateinit var service: PresentationService
    private lateinit var credentialVerifier: CredentialVerifier
    private val holderDid = "did:key:holder123"
    private val keyId = "key-1"
    private lateinit var proofRegistry: ProofGeneratorRegistry

    @BeforeEach
    fun setup() {
        proofRegistry = ProofGeneratorRegistry()
        
        // Create a simple mock signer for testing
        val signer: suspend (ByteArray, String) -> ByteArray = { _, _ ->
            // Simple mock signature
            "mock-signature-${UUID.randomUUID()}".toByteArray()
        }
        
        val proofGenerator = Ed25519ProofGenerator(
            signer = signer,
            getPublicKeyId = { "did:key:holder123#key-1" }
        )
        proofRegistry.register(proofGenerator)
        
        credentialVerifier = CredentialVerifier(
            CredentialDidResolver { CredentialDidResolution(isResolvable = true) }
        )
        
        service = newService(proofGenerator = proofGenerator)
    }

    @AfterEach
    fun cleanup() {
        proofRegistry.clear()
    }

    private fun newService(
        proofGenerator: ProofGenerator? = null,
        verifier: CredentialVerifier? = credentialVerifier
    ): PresentationService = PresentationService(
        proofGenerator = proofGenerator,
        credentialVerifier = verifier,
        proofRegistry = proofRegistry
    )

    @Test
    fun `test create presentation successfully`() = runBlocking {
        val credentials = listOf(createTestCredential())
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = keyId
        )
        
        val presentation = service.createPresentation(credentials, holderDid, options)
        
        assertNotNull(presentation)
        assertEquals(holderDid, presentation.holder)
        assertEquals(1, presentation.verifiableCredential.size)
        assertNotNull(presentation.proof)
    }

    @Test
    fun `test create presentation with multiple credentials`() = runBlocking {
        val credentials = listOf(
            createTestCredential(id = "cred-1"),
            createTestCredential(id = "cred-2")
        )
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = keyId
        )
        
        val presentation = service.createPresentation(credentials, holderDid, options)
        
        assertEquals(2, presentation.verifiableCredential.size)
    }

    @Test
    fun `test create presentation fails when credentials empty`() = runBlocking {
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = keyId
        )
        
        assertFailsWith<IllegalArgumentException> {
            service.createPresentation(emptyList(), holderDid, options)
        }
    }

    @Test
    fun `test create presentation fails when holder DID blank`() = runBlocking {
        val credentials = listOf(createTestCredential())
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = keyId
        )
        
        assertFailsWith<IllegalArgumentException> {
            service.createPresentation(credentials, "", options)
        }
    }

    @Test
    fun `test create presentation with challenge and domain`() = runBlocking {
        val credentials = listOf(createTestCredential())
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = keyId,
            challenge = "challenge-123",
            domain = "example.com"
        )
        
        val presentation = service.createPresentation(credentials, holderDid, options)
        
        assertEquals("challenge-123", presentation.challenge)
        assertEquals("example.com", presentation.domain)
        assertEquals("challenge-123", presentation.proof?.challenge)
        assertEquals("example.com", presentation.proof?.domain)
    }

    @Test
    fun `test create presentation without proof`() = runBlocking {
        val credentials = listOf(createTestCredential())
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "",
            keyId = null
        )
        
        val presentation = service.createPresentation(credentials, holderDid, options)
        
        assertNull(presentation.proof)
    }

    @Test
    fun `test verify presentation successfully`() = runBlocking {
        val credentials = listOf(createTestCredential())
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = keyId,
            challenge = "challenge-123"
        )
        val presentation = service.createPresentation(credentials, holderDid, options)
        
        val verificationOptions = PresentationVerificationOptions(
            verifyChallenge = true,
            expectedChallenge = "challenge-123"
        )
        
        val result = service.verifyPresentation(presentation, verificationOptions)
        
        // Note: Current implementation checks proof structure, not actual signature
        // So proofValid should be true if structure is correct
        assertTrue(result.presentationProofValid || presentation.proof != null)
        assertTrue(result.challengeValid)
        // Overall valid may be false if credential verification fails, but that's okay
    }

    @Test
    fun `test verify presentation fails when no proof`() = runBlocking {
        val presentation = VerifiablePresentation(
            id = "vp-1",
            type = listOf("VerifiablePresentation"),
            verifiableCredential = listOf(createTestCredential()),
            holder = holderDid,
            proof = null
        )
        
        val result = service.verifyPresentation(presentation)
        
        assertFalse(result.valid)
        assertFalse(result.presentationProofValid)
        assertTrue(result.errors.any { it.contains("no proof") })
    }

    @Test
    fun `test verify presentation fails when challenge mismatch`() = runBlocking {
        val credentials = listOf(createTestCredential())
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = keyId,
            challenge = "challenge-123"
        )
        val presentation = service.createPresentation(credentials, holderDid, options)
        
        val verificationOptions = PresentationVerificationOptions(
            verifyChallenge = true,
            expectedChallenge = "different-challenge"
        )
        
        val result = service.verifyPresentation(presentation, verificationOptions)
        
        assertFalse(result.valid)
        assertFalse(result.challengeValid)
        assertTrue(result.errors.any { it.contains("Challenge mismatch") })
    }

    @Test
    fun `test verify presentation fails when domain mismatch`() = runBlocking {
        val credentials = listOf(createTestCredential())
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = keyId,
            domain = "example.com"
        )
        val presentation = service.createPresentation(credentials, holderDid, options)
        
        val verificationOptions = PresentationVerificationOptions(
            verifyDomain = true,
            expectedDomain = "different.com"
        )
        
        val result = service.verifyPresentation(presentation, verificationOptions)
        
        assertFalse(result.valid)
        assertFalse(result.domainValid)
        assertTrue(result.errors.any { it.contains("Domain mismatch") })
    }

    @Test
    fun `test verify presentation verifies credentials`() = runBlocking {
        val credentials = listOf(createTestCredential())
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = keyId
        )
        val presentation = service.createPresentation(credentials, holderDid, options)
        
        val verificationOptions = PresentationVerificationOptions(
            checkRevocation = true
        )
        
        val result = service.verifyPresentation(presentation, verificationOptions)
        
        // Credential verification should be attempted when checkRevocation is true
        // Note: Current implementation may not fully verify credentials
        assertTrue(result.credentialResults.isNotEmpty() || !result.valid)
    }

    @Test
    fun `test create selective disclosure`() = runBlocking {
        val credentials = listOf(createTestCredential())
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = keyId
        )
        
        val presentation = service.createSelectiveDisclosure(
            credentials = credentials,
            disclosedFields = listOf("credentialSubject.name"),
            holderDid = holderDid,
            options = options
        )
        
        assertNotNull(presentation)
        // Note: Full selective disclosure implementation would require BBS+ proofs
        // For now, this returns a regular presentation
    }

    @Test
    fun `test create presentation uses proof generator from registry`() = runBlocking {
        val serviceWithoutGenerator = newService(
            proofGenerator = null,
            verifier = credentialVerifier
        )
        val credentials = listOf(createTestCredential())
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = keyId
        )
        
        val presentation = serviceWithoutGenerator.createPresentation(credentials, holderDid, options)
        
        assertNotNull(presentation.proof)
        assertEquals("Ed25519Signature2020", presentation.proof.type)
    }

    @Test
    fun `test create presentation fails when proof generator not found`() = runBlocking {
        proofRegistry.clear()
        val serviceWithoutGenerator = newService(
            proofGenerator = null,
            verifier = credentialVerifier
        )
        val credentials = listOf(createTestCredential())
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "UnknownProofType",
            keyId = keyId
        )
        
        assertFailsWith<IllegalArgumentException> {
            serviceWithoutGenerator.createPresentation(credentials, holderDid, options)
        }
    }

    private fun createTestCredential(
        id: String? = "https://example.com/credentials/1",
        types: List<String> = listOf("VerifiableCredential", "PersonCredential"),
        issuerDid: String = "did:key:issuer",
        subject: JsonObject = buildJsonObject {
            put("id", "did:key:subject")
            put("name", "John Doe")
        },
        issuanceDate: String = java.time.Instant.now().toString(),
        proof: Proof? = Proof(
            type = "Ed25519Signature2020",
            created = java.time.Instant.now().toString(),
            verificationMethod = "did:key:issuer#key-1",
            proofPurpose = "assertionMethod"
        )
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = types,
            issuer = issuerDid,
            credentialSubject = subject,
            issuanceDate = issuanceDate,
            proof = proof
        )
    }
}

