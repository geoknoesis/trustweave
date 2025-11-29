package com.trustweave.credential.presentation

import com.trustweave.credential.PresentationOptions
import com.trustweave.credential.PresentationVerificationOptions
import com.trustweave.credential.models.Proof
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.models.VerifiablePresentation
import com.trustweave.credential.proof.Ed25519ProofGenerator
import com.trustweave.credential.proof.ProofGeneratorRegistry
import com.trustweave.credential.proof.ProofGenerator
import com.trustweave.credential.verifier.CredentialVerifier
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Additional branch coverage tests for PresentationService.
 */
class PresentationServiceAdditionalBranchCoverageTest {

    private lateinit var proofRegistry: ProofGeneratorRegistry

    @BeforeEach
    fun setup() {
        proofRegistry = ProofGeneratorRegistry()
    }

    private fun newService(
        proofGenerator: ProofGenerator? = null,
        verifier: CredentialVerifier? = null
    ): PresentationService = PresentationService(
        proofGenerator = proofGenerator,
        credentialVerifier = verifier,
        proofRegistry = proofRegistry
    )

    @Test
    fun `test PresentationService createPresentation with proofGenerator in constructor`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )
        val service = newService(proofGenerator = generator)

        val credential = createTestCredential()
        val options = PresentationOptions(
            proofType = "Ed25519Signature2020",
            keyId = "key-1",
            holderDid = "did:key:holder"
        )

        val presentation = service.createPresentation(
            listOf(credential),
            "did:key:holder",
            options
        )

        assertNotNull(presentation.proof)
    }

    @Test
    fun `test PresentationService createPresentation uses ProofGeneratorRegistry when proofGenerator is null`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )
        proofRegistry.register(generator)

        val service = newService(proofGenerator = null)

        val credential = createTestCredential()
        val options = PresentationOptions(
            proofType = "Ed25519Signature2020",
            keyId = "key-1",
            holderDid = "did:key:holder"
        )

        val presentation = service.createPresentation(
            listOf(credential),
            "did:key:holder",
            options
        )

        assertNotNull(presentation.proof)
    }

    @Test
    fun `test PresentationService createPresentation without proofType`() = runBlocking {
        val service = newService()

        val credential = createTestCredential()
        val options = PresentationOptions(
            proofType = "",
            holderDid = "did:key:holder"
        )

        val presentation = service.createPresentation(
            listOf(credential),
            "did:key:holder",
            options
        )

        assertNull(presentation.proof)
    }

    @Test
    fun `test PresentationService createPresentation without keyId`() = runBlocking {
        val service = newService()

        val credential = createTestCredential()
        val options = PresentationOptions(
            proofType = "Ed25519Signature2020",
            keyId = null,
            holderDid = "did:key:holder"
        )

        val presentation = service.createPresentation(
            listOf(credential),
            "did:key:holder",
            options
        )

        assertNull(presentation.proof)
    }

    @Test
    fun `test PresentationService verifyPresentation with proof`() = runBlocking {
        val service = newService()

        val credential = createTestCredential()
        val presentation = VerifiablePresentation(
            id = "presentation-1",
            type = listOf("VerifiablePresentation"),
            verifiableCredential = listOf(credential),
            holder = "did:key:holder",
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
    fun `test PresentationService verifyPresentation without proof`() = runBlocking {
        val service = newService()

        val credential = createTestCredential()
        val presentation = VerifiablePresentation(
            id = "presentation-1",
            type = listOf("VerifiablePresentation"),
            verifiableCredential = listOf(credential),
            holder = "did:key:holder",
            proof = null
        )

        val result = service.verifyPresentation(presentation)

        assertFalse(result.presentationProofValid)
        assertTrue(result.errors.any { it.contains("no proof") })
    }

    @Test
    fun `test PresentationService verifyPresentation with invalid proof`() = runBlocking {
        val service = newService()

        val credential = createTestCredential()
        val presentation = VerifiablePresentation(
            id = "presentation-1",
            type = listOf("VerifiablePresentation"),
            verifiableCredential = listOf(credential),
            holder = "did:key:holder",
            proof = Proof(
                type = "",
                created = java.time.Instant.now().toString(),
                verificationMethod = "",
                proofPurpose = "authentication"
            )
        )

        val result = service.verifyPresentation(presentation)

        assertFalse(result.presentationProofValid)
    }

    @Test
    fun `test PresentationService verifyPresentation with challenge verification`() = runBlocking {
        val service = newService()

        val credential = createTestCredential()
        val presentation = VerifiablePresentation(
            id = "presentation-1",
            type = listOf("VerifiablePresentation"),
            verifiableCredential = listOf(credential),
            holder = "did:key:holder",
            proof = Proof(
                type = "Ed25519Signature2020",
                created = java.time.Instant.now().toString(),
                verificationMethod = "did:key:holder#key-1",
                proofPurpose = "authentication",
                proofValue = "test-proof"
            ),
            challenge = "challenge-123"
        )

        val options = PresentationVerificationOptions(
            verifyChallenge = true,
            expectedChallenge = "challenge-123"
        )

        val result = service.verifyPresentation(presentation, options)

        assertTrue(result.challengeValid)
    }

    @Test
    fun `test PresentationService verifyPresentation with challenge mismatch`() = runBlocking {
        val service = newService()

        val credential = createTestCredential()
        val presentation = VerifiablePresentation(
            id = "presentation-1",
            type = listOf("VerifiablePresentation"),
            verifiableCredential = listOf(credential),
            holder = "did:key:holder",
            proof = Proof(
                type = "Ed25519Signature2020",
                created = java.time.Instant.now().toString(),
                verificationMethod = "did:key:holder#key-1",
                proofPurpose = "authentication",
                proofValue = "test-proof"
            ),
            challenge = "challenge-123"
        )

        val options = PresentationVerificationOptions(
            verifyChallenge = true,
            expectedChallenge = "different-challenge"
        )

        val result = service.verifyPresentation(presentation, options)

        assertFalse(result.challengeValid)
        assertTrue(result.errors.any { it.contains("Challenge mismatch") })
    }

    @Test
    fun `test PresentationService verifyPresentation with no challenge when verifyChallenge is true`() = runBlocking {
        val service = newService()

        val credential = createTestCredential()
        val presentation = VerifiablePresentation(
            id = "presentation-1",
            type = listOf("VerifiablePresentation"),
            verifiableCredential = listOf(credential),
            holder = "did:key:holder",
            proof = Proof(
                type = "Ed25519Signature2020",
                created = java.time.Instant.now().toString(),
                verificationMethod = "did:key:holder#key-1",
                proofPurpose = "authentication",
                proofValue = "test-proof"
            ),
            challenge = null
        )

        val options = PresentationVerificationOptions(
            verifyChallenge = true,
            expectedChallenge = null
        )

        val result = service.verifyPresentation(presentation, options)

        assertTrue(result.warnings.any { it.contains("No challenge") })
    }

    @Test
    fun `test PresentationService verifyPresentation with domain verification`() = runBlocking {
        val service = newService()

        val credential = createTestCredential()
        val presentation = VerifiablePresentation(
            id = "presentation-1",
            type = listOf("VerifiablePresentation"),
            verifiableCredential = listOf(credential),
            holder = "did:key:holder",
            proof = Proof(
                type = "Ed25519Signature2020",
                created = java.time.Instant.now().toString(),
                verificationMethod = "did:key:holder#key-1",
                proofPurpose = "authentication",
                proofValue = "test-proof"
            ),
            domain = "example.com"
        )

        val options = PresentationVerificationOptions(
            verifyDomain = true,
            expectedDomain = "example.com"
        )

        val result = service.verifyPresentation(presentation, options)

        assertTrue(result.domainValid)
    }

    @Test
    fun `test PresentationService verifyPresentation with domain mismatch`() = runBlocking {
        val service = newService()

        val credential = createTestCredential()
        val presentation = VerifiablePresentation(
            id = "presentation-1",
            type = listOf("VerifiablePresentation"),
            verifiableCredential = listOf(credential),
            holder = "did:key:holder",
            proof = Proof(
                type = "Ed25519Signature2020",
                created = java.time.Instant.now().toString(),
                verificationMethod = "did:key:holder#key-1",
                proofPurpose = "authentication",
                proofValue = "test-proof"
            ),
            domain = "example.com"
        )

        val options = PresentationVerificationOptions(
            verifyDomain = true,
            expectedDomain = "different.com"
        )

        val result = service.verifyPresentation(presentation, options)

        assertFalse(result.domainValid)
        assertTrue(result.errors.any { it.contains("Domain mismatch") })
    }

    @Test
    fun `test PresentationService verifyPresentation with credential verification`() = runBlocking {
        val verifier = CredentialVerifier()
        val service = newService(verifier = verifier)

        val credential = createTestCredential()
        val presentation = VerifiablePresentation(
            id = "presentation-1",
            type = listOf("VerifiablePresentation"),
            verifiableCredential = listOf(credential),
            holder = "did:key:holder",
            proof = Proof(
                type = "Ed25519Signature2020",
                created = java.time.Instant.now().toString(),
                verificationMethod = "did:key:holder#key-1",
                proofPurpose = "authentication",
                proofValue = "test-proof"
            )
        )

        val options = PresentationVerificationOptions(
            checkRevocation = true
        )

        val result = service.verifyPresentation(presentation, options)

        assertNotNull(result.credentialResults)
    }

    @Test
    fun `test PresentationService verifyPresentation without credentialVerifier`() = runBlocking {
        val service = newService(verifier = null)

        val credential = createTestCredential()
        val presentation = VerifiablePresentation(
            id = "presentation-1",
            type = listOf("VerifiablePresentation"),
            verifiableCredential = listOf(credential),
            holder = "did:key:holder",
            proof = Proof(
                type = "Ed25519Signature2020",
                created = java.time.Instant.now().toString(),
                verificationMethod = "did:key:holder#key-1",
                proofPurpose = "authentication",
                proofValue = "test-proof"
            )
        )

        val options = PresentationVerificationOptions(
            checkRevocation = true
        )

        val result = service.verifyPresentation(presentation, options)

        assertTrue(result.credentialResults.isEmpty())
    }

    @Test
    fun `test PresentationService createSelectiveDisclosure`() = runBlocking {
        val service = newService()

        val credential = createTestCredential()
        val options = PresentationOptions(holderDid = "did:key:holder")

        val presentation = service.createSelectiveDisclosure(
            listOf(credential),
            listOf("credentialSubject.name"),
            "did:key:holder",
            options
        )

        assertNotNull(presentation)
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

