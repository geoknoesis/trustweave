package io.geoknoesis.vericore.credential.presentation

import io.geoknoesis.vericore.credential.CredentialVerificationOptions
import io.geoknoesis.vericore.credential.PresentationOptions
import io.geoknoesis.vericore.credential.PresentationVerificationOptions
import io.geoknoesis.vericore.credential.models.Proof
import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.models.VerifiablePresentation
import io.geoknoesis.vericore.credential.proof.Ed25519ProofGenerator
import io.geoknoesis.vericore.credential.proof.ProofGenerator
import io.geoknoesis.vericore.credential.proof.ProofGeneratorRegistry
import io.geoknoesis.vericore.credential.verifier.CredentialVerifier
import io.geoknoesis.vericore.util.booleanDidResolver
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive branch coverage tests for PresentationService.
 * Tests all conditional branches and code paths.
 */
class PresentationServiceBranchCoverageTest {

    private lateinit var proofRegistry: ProofGeneratorRegistry

    @BeforeEach
    fun setup() {
        proofRegistry = ProofGeneratorRegistry()
        val generator = Ed25519ProofGenerator(
            signer = { _: ByteArray, _: String -> byteArrayOf(1, 2, 3) }
        )
        proofRegistry.register(generator)
    }

    private fun newPresentationService(
        proofGenerator: ProofGenerator? = null,
        verifier: CredentialVerifier? = null
    ): PresentationService = PresentationService(
        proofGenerator = proofGenerator,
        credentialVerifier = verifier,
        proofRegistry = proofRegistry
    )

    @Test
    fun `test branch createPresentation with proofGenerator`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _: ByteArray, _: String -> byteArrayOf(1, 2, 3) }
        )
        val service = newPresentationService(proofGenerator = generator)
        val credentials = listOf(createTestCredential())
        
        val presentation = service.createPresentation(
            credentials,
            "did:key:holder",
            PresentationOptions(
                holderDid = "did:key:holder",
                proofType = "Ed25519Signature2020",
                keyId = "key-1"
            )
        )
        
        assertNotNull(presentation.proof)
    }

    @Test
    fun `test branch createPresentation without proofGenerator uses registry`() = runBlocking {
        val service = newPresentationService(proofGenerator = null)
        val credentials = listOf(createTestCredential())
        
        val presentation = service.createPresentation(
            credentials,
            "did:key:holder",
            PresentationOptions(
                holderDid = "did:key:holder",
                proofType = "Ed25519Signature2020",
                keyId = "key-1"
            )
        )
        
        assertNotNull(presentation.proof)
    }

    @Test
    fun `test branch createPresentation with blank proofType`() = runBlocking {
        val service = newPresentationService()
        val credentials = listOf(createTestCredential())
        
        val presentation = service.createPresentation(
            credentials,
            "did:key:holder",
            PresentationOptions(
                holderDid = "did:key:holder",
                proofType = "",
                keyId = null
            )
        )
        
        assertNull(presentation.proof)
    }

    @Test
    fun `test branch createPresentation with null keyId`() = runBlocking {
        val service = newPresentationService()
        val credentials = listOf(createTestCredential())
        
        val presentation = service.createPresentation(
            credentials,
            "did:key:holder",
            PresentationOptions(
                holderDid = "did:key:holder",
                proofType = "Ed25519Signature2020",
                keyId = null
            )
        )
        
        assertNull(presentation.proof)
    }

    @Test
    fun `test branch verifyPresentation with proof`() = runBlocking {
        val service = newPresentationService()
        val presentation = createTestPresentation(
            proof = Proof(
                type = "Ed25519Signature2020",
                created = java.time.Instant.now().toString(),
                verificationMethod = "did:key:holder#key-1",
                proofPurpose = "authentication",
                proofValue = "test-proof"
            )
        )
        
        val result = service.verifyPresentation(presentation)
        
        assertTrue(result.presentationProofValid)
    }

    @Test
    fun `test branch verifyPresentation without proof`() = runBlocking {
        val service = newPresentationService()
        val presentation = createTestPresentation(proof = null)
        
        val result = service.verifyPresentation(presentation)
        
        assertFalse(result.presentationProofValid)
        assertTrue(result.errors.any { it.contains("no proof") })
    }

    @Test
    fun `test branch verifyPresentation with blank proof type`() = runBlocking {
        val service = newPresentationService()
        val presentation = createTestPresentation(
            proof = Proof(
                type = "",
                created = java.time.Instant.now().toString(),
                verificationMethod = "did:key:holder#key-1",
                proofPurpose = "authentication"
            )
        )
        
        val result = service.verifyPresentation(presentation)
        
        assertFalse(result.presentationProofValid)
    }

    @Test
    fun `test branch verifyPresentation with blank verificationMethod`() = runBlocking {
        val service = newPresentationService()
        val presentation = createTestPresentation(
            proof = Proof(
                type = "Ed25519Signature2020",
                created = java.time.Instant.now().toString(),
                verificationMethod = "",
                proofPurpose = "authentication"
            )
        )
        
        val result = service.verifyPresentation(presentation)
        
        assertFalse(result.presentationProofValid)
    }

    @Test
    fun `test branch verifyPresentation verifyChallenge true with match`() = runBlocking {
        val service = newPresentationService()
        val presentation = createTestPresentation(challenge = "challenge-123")
        
        val result = service.verifyPresentation(
            presentation,
            PresentationVerificationOptions(
                verifyChallenge = true,
                expectedChallenge = "challenge-123"
            )
        )
        
        assertTrue(result.challengeValid)
    }

    @Test
    fun `test branch verifyPresentation verifyChallenge true with mismatch`() = runBlocking {
        val service = newPresentationService()
        val presentation = createTestPresentation(challenge = "challenge-123")
        
        val result = service.verifyPresentation(
            presentation,
            PresentationVerificationOptions(
                verifyChallenge = true,
                expectedChallenge = "different-challenge"
            )
        )
        
        assertFalse(result.challengeValid)
    }

    @Test
    fun `test branch verifyPresentation verifyChallenge true with null expected`() = runBlocking {
        val service = newPresentationService()
        val presentation = createTestPresentation(challenge = null)
        
        val result = service.verifyPresentation(
            presentation,
            PresentationVerificationOptions(
                verifyChallenge = true,
                expectedChallenge = null
            )
        )
        
        assertTrue(result.challengeValid)
        assertTrue(result.warnings.any { it.contains("No challenge") })
    }

    @Test
    fun `test branch verifyPresentation verifyChallenge false`() = runBlocking {
        val service = newPresentationService()
        val presentation = createTestPresentation(challenge = "challenge-123")
        
        val result = service.verifyPresentation(
            presentation,
            PresentationVerificationOptions(
                verifyChallenge = false
            )
        )
        
        assertTrue(result.challengeValid)
    }

    @Test
    fun `test branch verifyPresentation verifyDomain true with match`() = runBlocking {
        val service = newPresentationService()
        val presentation = createTestPresentation(domain = "example.com")
        
        val result = service.verifyPresentation(
            presentation,
            PresentationVerificationOptions(
                verifyDomain = true,
                expectedDomain = "example.com"
            )
        )
        
        assertTrue(result.domainValid)
    }

    @Test
    fun `test branch verifyPresentation verifyDomain true with mismatch`() = runBlocking {
        val service = newPresentationService()
        val presentation = createTestPresentation(domain = "example.com")
        
        val result = service.verifyPresentation(
            presentation,
            PresentationVerificationOptions(
                verifyDomain = true,
                expectedDomain = "different.com"
            )
        )
        
        assertFalse(result.domainValid)
    }

    @Test
    fun `test branch verifyPresentation verifyDomain false`() = runBlocking {
        val service = newPresentationService()
        val presentation = createTestPresentation(domain = "example.com")
        
        val result = service.verifyPresentation(
            presentation,
            PresentationVerificationOptions(
                verifyDomain = false
            )
        )
        
        assertTrue(result.domainValid)
    }

    @Test
    fun `test branch verifyPresentation checkRevocation true with verifier`() = runBlocking {
        val verifier = CredentialVerifier(booleanDidResolver { true })
        val service = newPresentationService(verifier = verifier)
        val credential = createTestCredential()
        val presentation = createTestPresentation(credentials = listOf(credential))
        
        val result = service.verifyPresentation(
            presentation,
            PresentationVerificationOptions(
                checkRevocation = true
            )
        )
        
        assertTrue(result.credentialResults.isNotEmpty())
    }

    @Test
    fun `test branch verifyPresentation checkRevocation true without verifier`() = runBlocking {
        val service = newPresentationService(verifier = null)
        val presentation = createTestPresentation()
        
        val result = service.verifyPresentation(
            presentation,
            PresentationVerificationOptions(
                checkRevocation = true
            )
        )
        
        assertTrue(result.credentialResults.isEmpty())
    }

    @Test
    fun `test branch verifyPresentation checkRevocation false`() = runBlocking {
        val verifier = CredentialVerifier(booleanDidResolver { true })
        val service = newPresentationService(verifier = verifier)
        val presentation = createTestPresentation()
        
        val result = service.verifyPresentation(
            presentation,
            PresentationVerificationOptions(
                checkRevocation = false
            )
        )
        
        assertTrue(result.credentialResults.isEmpty())
    }

    @Test
    fun `test branch verifyPresentation with invalid credential`() = runBlocking {
        val verifier = CredentialVerifier(booleanDidResolver { false })
        val service = newPresentationService(verifier = verifier)
        val credential = createTestCredential()
        val presentation = createTestPresentation(
            credentials = listOf(credential),
            proof = Proof(
                type = "Ed25519Signature2020",
                created = java.time.Instant.now().toString(),
                verificationMethod = "did:key:holder#key-1",
                proofPurpose = "authentication",
                proofValue = "test-proof"
            )
        )
        
        val result = service.verifyPresentation(
            presentation,
            PresentationVerificationOptions(
                checkRevocation = true
            )
        )
        
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("verification failed") })
    }

    @Test
    fun `test branch createSelectiveDisclosure calls createPresentation`() = runBlocking {
        val service = newPresentationService()
        val credentials = listOf(createTestCredential())
        
        val presentation = service.createSelectiveDisclosure(
            credentials,
            listOf("credentialSubject.name"),
            "did:key:holder",
            PresentationOptions(holderDid = "did:key:holder")
        )
        
        assertNotNull(presentation)
    }

    private fun createTestCredential(): VerifiableCredential {
        return VerifiableCredential(
            type = listOf("VerifiableCredential", "PersonCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject {
                put("id", "did:key:subject")
                put("name", "John Doe")
            },
            issuanceDate = java.time.Instant.now().toString()
        )
    }

    private fun createTestPresentation(
        credentials: List<VerifiableCredential> = listOf(createTestCredential()),
        proof: Proof? = Proof(
            type = "Ed25519Signature2020",
            created = java.time.Instant.now().toString(),
            verificationMethod = "did:key:holder#key-1",
            proofPurpose = "authentication",
            proofValue = "test-proof"
        ),
        challenge: String? = "challenge-123",
        domain: String? = "example.com"
    ): VerifiablePresentation {
        return VerifiablePresentation(
            id = "presentation-1",
            type = listOf("VerifiablePresentation"),
            verifiableCredential = credentials,
            holder = "did:key:holder",
            proof = proof,
            challenge = challenge,
            domain = domain
        )
    }
}

