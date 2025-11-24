package com.trustweave.credential.presentation

import com.trustweave.credential.CredentialVerificationOptions
import com.trustweave.util.booleanDidResolver
import com.trustweave.credential.PresentationOptions
import com.trustweave.credential.models.Proof
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.models.VerifiablePresentation
import com.trustweave.credential.proof.Ed25519ProofGenerator
import com.trustweave.credential.proof.ProofGeneratorRegistry
import com.trustweave.credential.proof.ProofGenerator
import com.trustweave.credential.verifier.CredentialVerifier
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.*

/**
 * Additional comprehensive branch coverage tests for PresentationService.
 * Covers additional branches not covered in PresentationServiceBranchCoverageTest and PresentationServiceAdditionalBranchCoverageTest.
 */
class PresentationServiceMoreBranchesTest {

    private lateinit var presentationService: PresentationService
    private val holderDid = "did:key:holder123"
    private val issuerDid = "did:key:issuer123"
    private lateinit var proofRegistry: ProofGeneratorRegistry
    private lateinit var credentialVerifier: CredentialVerifier

    @BeforeEach
    fun setup() {
        proofRegistry = ProofGeneratorRegistry()
        
        val signer: suspend (ByteArray, String) -> ByteArray = { data, _ ->
            "mock-signature-${UUID.randomUUID()}".toByteArray()
        }
        
        val proofGenerator = Ed25519ProofGenerator(
            signer = signer,
            getPublicKeyId = { "did:key:holder123#key-1" }
        )
        proofRegistry.register(proofGenerator)

        credentialVerifier = CredentialVerifier(
            booleanDidResolver { did -> did == issuerDid || did == holderDid }
        )

        presentationService = newService(
            proofGenerator = proofGenerator,
            verifier = credentialVerifier
        )
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

    // ========== Presentation Creation Branches ==========

    @Test
    fun `test createPresentation with single credential`() = runBlocking {
        val credential = createTestCredential()
        val credentials = listOf(credential)
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = "key-1"
        )
        
        val presentation = presentationService.createPresentation(credentials, holderDid, options)
        
        assertNotNull(presentation)
        assertEquals(1, presentation.verifiableCredential?.size)
        assertNotNull(presentation.proof)
    }

    @Test
    fun `test createPresentation with multiple credentials`() = runBlocking {
        val credential1 = createTestCredential(id = "credential-1")
        val credential2 = createTestCredential(id = "credential-2")
        val credentials = listOf(credential1, credential2)
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = "key-1"
        )
        
        val presentation = presentationService.createPresentation(credentials, holderDid, options)
        
        assertNotNull(presentation)
        assertEquals(2, presentation.verifiableCredential?.size)
        assertNotNull(presentation.proof)
    }

    @Test
    fun `test createPresentation with options containing challenge`() = runBlocking {
        val credential = createTestCredential()
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            challenge = "challenge-123",
            keyId = "key-1"
        )
        
        val presentation = presentationService.createPresentation(listOf(credential), holderDid, options)
        
        // Challenge is set on presentation and proof
        assertEquals("challenge-123", presentation.challenge)
        assertEquals("challenge-123", presentation.proof?.challenge)
    }

    @Test
    fun `test createPresentation with options containing domain`() = runBlocking {
        val credential = createTestCredential()
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            domain = "example.com",
            keyId = "key-1"
        )
        
        val presentation = presentationService.createPresentation(listOf(credential), holderDid, options)
        
        // Domain is set on presentation and proof
        assertEquals("example.com", presentation.domain)
        assertEquals("example.com", presentation.proof?.domain)
    }

    @Test
    fun `test createPresentation with options containing keyId`() = runBlocking {
        val credential = createTestCredential()
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = "custom-key-id"
        )
        
        val presentation = presentationService.createPresentation(listOf(credential), holderDid, options)
        
        assertNotNull(presentation.proof)
    }

    // ========== Presentation Verification Branches ==========

    @Test
    fun `test verifyPresentation with options verifyChallenge false`() = runBlocking {
        val credential = createTestCredential()
        val presentation = createTestPresentation(
            credentials = listOf(credential),
            challenge = "challenge-123"
        )
        val options = com.trustweave.credential.PresentationVerificationOptions(
            verifyChallenge = false
        )
        
        val result = presentationService.verifyPresentation(presentation, options)
        
        assertNotNull(result)
    }

    @Test
    fun `test verifyPresentation with options verifyDomain false`() = runBlocking {
        val credential = createTestCredential()
        val presentation = createTestPresentation(
            credentials = listOf(credential),
            domain = "example.com"
        )
        val options = com.trustweave.credential.PresentationVerificationOptions(
            verifyDomain = false
        )
        
        val result = presentationService.verifyPresentation(presentation, options)
        
        assertNotNull(result)
    }

    @Test
    fun `test verifyPresentation with options checkRevocation false`() = runBlocking {
        val credential = createTestCredential()
        val presentation = createTestPresentation(credentials = listOf(credential))
        val options = com.trustweave.credential.PresentationVerificationOptions(
            checkRevocation = false
        )
        
        val result = presentationService.verifyPresentation(presentation, options)
        
        assertNotNull(result)
    }

    @Test
    fun `test verifyPresentation with options expectedChallenge matching`() = runBlocking {
        val credential = createTestCredential()
        val presentation = createTestPresentation(
            credentials = listOf(credential),
            challenge = "expected-challenge"
        )
        val options = com.trustweave.credential.PresentationVerificationOptions(
            verifyChallenge = true,
            expectedChallenge = "expected-challenge"
        )
        
        val result = presentationService.verifyPresentation(presentation, options)
        
        assertNotNull(result)
    }

    @Test
    fun `test verifyPresentation with options expectedChallenge not matching`() = runBlocking {
        val credential = createTestCredential()
        val presentation = createTestPresentation(
            credentials = listOf(credential),
            challenge = "different-challenge"
        )
        val options = com.trustweave.credential.PresentationVerificationOptions(
            verifyChallenge = true,
            expectedChallenge = "expected-challenge"
        )
        
        val result = presentationService.verifyPresentation(presentation, options)
        
        assertFalse(result.valid)
    }

    @Test
    fun `test verifyPresentation with options expectedDomain matching`() = runBlocking {
        val credential = createTestCredential()
        val presentation = createTestPresentation(
            credentials = listOf(credential),
            domain = "expected-domain.com"
        )
        val options = com.trustweave.credential.PresentationVerificationOptions(
            verifyDomain = true,
            expectedDomain = "expected-domain.com"
        )
        
        val result = presentationService.verifyPresentation(presentation, options)
        
        assertNotNull(result)
    }

    @Test
    fun `test verifyPresentation with options expectedDomain not matching`() = runBlocking {
        val credential = createTestCredential()
        val presentation = createTestPresentation(
            credentials = listOf(credential),
            domain = "different-domain.com"
        )
        val options = com.trustweave.credential.PresentationVerificationOptions(
            verifyDomain = true,
            expectedDomain = "expected-domain.com"
        )
        
        val result = presentationService.verifyPresentation(presentation, options)
        
        assertFalse(result.valid)
    }

    // ========== Selective Disclosure Branches ==========

    @Test
    fun `test createSelectiveDisclosure with single reveal field`() = runBlocking {
        val credential = createTestCredential()
        val reveal = listOf("credentialSubject.name")
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020"
        )
        
        val presentation = presentationService.createSelectiveDisclosure(
            credentials = listOf(credential),
            disclosedFields = reveal,
            holderDid = holderDid,
            options = options
        )
        
        assertNotNull(presentation)
        assertNotNull(presentation.proof)
    }

    @Test
    fun `test createSelectiveDisclosure with multiple reveal fields`() = runBlocking {
        val credential = createTestCredential()
        val reveal = listOf("credentialSubject.name", "credentialSubject.email", "issuanceDate")
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020"
        )
        
        val presentation = presentationService.createSelectiveDisclosure(
            credentials = listOf(credential),
            disclosedFields = reveal,
            holderDid = holderDid,
            options = options
        )
        
        assertNotNull(presentation)
        assertNotNull(presentation.proof)
    }

    @Test
    fun `test createSelectiveDisclosure with empty reveal list`() = runBlocking {
        val credential = createTestCredential()
        val reveal = emptyList<String>()
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020"
        )
        
        val presentation = presentationService.createSelectiveDisclosure(
            credentials = listOf(credential),
            disclosedFields = reveal,
            holderDid = holderDid,
            options = options
        )
        
        assertNotNull(presentation)
    }

    // ========== Helper Functions ==========

    private fun createTestCredential(
        id: String = "credential-${UUID.randomUUID()}",
        issuerDid: String = this.issuerDid
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = listOf("VerifiableCredential", "PersonCredential"),
            issuer = issuerDid,
            issuanceDate = Instant.now().toString(),
            credentialSubject = buildJsonObject {
                put("id", "did:key:subject123")
                put("name", "John Doe")
            },
            proof = Proof(
                type = "Ed25519Signature2020",
                created = Instant.now().toString(),
                verificationMethod = "did:key:issuer123#key-1",
                proofPurpose = "assertionMethod"
            )
        )
    }

    private fun createTestPresentation(
        credentials: List<VerifiableCredential>,
        challenge: String? = null,
        domain: String? = null
    ): VerifiablePresentation {
        return VerifiablePresentation(
            id = "presentation-123",
            type = listOf("VerifiablePresentation"),
            holder = holderDid,
            verifiableCredential = credentials,
            challenge = challenge,
            domain = domain,
            proof = Proof(
                type = "Ed25519Signature2020",
                created = Instant.now().toString(),
                verificationMethod = "did:key:holder123#key-1",
                proofPurpose = "authentication",
                challenge = challenge,
                domain = domain
            )
        )
    }
}

