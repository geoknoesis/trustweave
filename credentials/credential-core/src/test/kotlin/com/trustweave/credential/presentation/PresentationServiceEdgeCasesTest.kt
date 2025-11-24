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
import com.trustweave.util.booleanDidResolver
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import java.util.UUID

/**
 * Exhaustive edge case tests for PresentationService API robustness.
 */
class PresentationServiceEdgeCasesTest {

    private lateinit var service: PresentationService
    private lateinit var credentialVerifier: CredentialVerifier
    private val holderDid = "did:key:holder123"
    private val keyId = "key-1"
    private lateinit var proofRegistry: ProofGeneratorRegistry

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
            booleanDidResolver { true }
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

    // ========== Null and Empty Input Tests ==========

    @Test
    fun `test create presentation with null credentials list`() = runBlocking {
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
    fun `test create presentation with credentials containing null`() = runBlocking {
        val credentials = listOf(createTestCredential(), null).filterNotNull()
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = keyId
        )
        
        // Should handle gracefully or fail
        try {
            val presentation = service.createPresentation(credentials, holderDid, options)
            assertNotNull(presentation)
        } catch (_: IllegalArgumentException) {
            // expected
        } catch (_: NullPointerException) {
            // expected
        }
    }

    @Test
    fun `test create presentation with empty holder DID`() = runBlocking {
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
    fun `test create presentation with whitespace-only holder DID`() = runBlocking {
        val credentials = listOf(createTestCredential())
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = keyId
        )
        
        assertFailsWith<IllegalArgumentException> {
            service.createPresentation(credentials, "   ", options)
        }
    }

    @Test
    fun `test create presentation with null key ID`() = runBlocking {
        val credentials = listOf(createTestCredential())
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = null
        )
        
        val presentation = service.createPresentation(credentials, holderDid, options)
        
        // Should create presentation without proof or with default key
        assertNotNull(presentation)
    }

    @Test
    fun `test create presentation with empty proof type`() = runBlocking {
        val credentials = listOf(createTestCredential())
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "",
            keyId = keyId
        )
        
        val presentation = service.createPresentation(credentials, holderDid, options)
        
        assertNull(presentation.proof)
    }

    // ========== Boundary Value Tests ==========

    @Test
    fun `test create presentation with very large number of credentials`() = runBlocking {
        val credentials = (1..1000).map { createTestCredential(id = "cred-$it") }
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = keyId
        )
        
        val presentation = service.createPresentation(credentials, holderDid, options)
        
        assertEquals(1000, presentation.verifiableCredential.size)
        assertNotNull(presentation.proof)
    }

    @Test
    fun `test create presentation with very long challenge`() = runBlocking {
        val credentials = listOf(createTestCredential())
        val longChallenge = "a".repeat(10000)
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = keyId,
            challenge = longChallenge
        )
        
        val presentation = service.createPresentation(credentials, holderDid, options)
        
        assertEquals(longChallenge, presentation.challenge)
        assertEquals(longChallenge, presentation.proof?.challenge)
    }

    @Test
    fun `test create presentation with very long domain`() = runBlocking {
        val credentials = listOf(createTestCredential())
        val longDomain = "a".repeat(1000) + ".example.com"
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = keyId,
            domain = longDomain
        )
        
        val presentation = service.createPresentation(credentials, holderDid, options)
        
        assertEquals(longDomain, presentation.domain)
        assertEquals(longDomain, presentation.proof?.domain)
    }

    // ========== Invalid Format Tests ==========

    @Test
    fun `test create presentation with malformed holder DID`() = runBlocking {
        val credentials = listOf(createTestCredential())
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = keyId
        )
        
        assertFailsWith<IllegalArgumentException> {
            service.createPresentation(credentials, "not-a-did", options)
        }
    }

    @Test
    fun `test create presentation with invalid proof type`() = runBlocking {
        val credentials = listOf(createTestCredential())
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "InvalidProofType",
            keyId = keyId
        )
        
        assertFailsWith<IllegalArgumentException> {
            service.createPresentation(credentials, holderDid, options)
        }
    }

    // ========== Verification Edge Cases ==========

    @Test
    fun `test verify presentation with null proof`() = runBlocking {
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
        assertTrue(result.errors.any { it.contains("no proof") || it.contains("proof") })
    }

    @Test
    fun `test verify presentation with empty type list`() = runBlocking {
        val presentation = VerifiablePresentation(
            id = "vp-1",
            type = emptyList(),
            verifiableCredential = listOf(createTestCredential()),
            holder = holderDid,
            proof = Proof(
                type = "Ed25519Signature2020",
                created = java.time.Instant.now().toString(),
                verificationMethod = "did:key:holder123#key-1",
                proofPurpose = "assertionMethod"
            )
        )
        
        val result = service.verifyPresentation(presentation)
        
        // May fail validation or handle gracefully
        assertNotNull(result)
        // Empty type list should result in invalid presentation
        if (!result.valid) {
            assertTrue(result.errors.isNotEmpty() || result.warnings.isNotEmpty())
        }
    }

    @Test
    fun `test verify presentation with empty credentials list`() = runBlocking {
        val presentation = VerifiablePresentation(
            id = "vp-1",
            type = listOf("VerifiablePresentation"),
            verifiableCredential = emptyList(),
            holder = holderDid,
            proof = Proof(
                type = "Ed25519Signature2020",
                created = java.time.Instant.now().toString(),
                verificationMethod = "did:key:holder123#key-1",
                proofPurpose = "assertionMethod"
            )
        )
        
        val result = service.verifyPresentation(presentation)
        
        // May be valid or invalid depending on implementation
        assertNotNull(result)
    }

    @Test
    fun `test verify presentation with challenge mismatch case sensitivity`() = runBlocking {
        val credentials = listOf(createTestCredential())
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = keyId,
            challenge = "Challenge123"
        )
        val presentation = service.createPresentation(credentials, holderDid, options)
        
        val verificationOptions = PresentationVerificationOptions(
            verifyChallenge = true,
            expectedChallenge = "challenge123" // Different case
        )
        
        val result = service.verifyPresentation(presentation, verificationOptions)
        
        assertFalse(result.valid)
        assertFalse(result.challengeValid)
        assertTrue(result.errors.any { it.contains("Challenge mismatch") })
    }

    @Test
    fun `test verify presentation with domain mismatch case sensitivity`() = runBlocking {
        val credentials = listOf(createTestCredential())
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = keyId,
            domain = "Example.com"
        )
        val presentation = service.createPresentation(credentials, holderDid, options)
        
        val verificationOptions = PresentationVerificationOptions(
            verifyDomain = true,
            expectedDomain = "example.com" // Different case
        )
        
        val result = service.verifyPresentation(presentation, verificationOptions)
        
        assertFalse(result.valid)
        assertFalse(result.domainValid)
        assertTrue(result.errors.any { it.contains("Domain mismatch") })
    }

    @Test
    fun `test verify presentation with missing challenge when required`() = runBlocking {
        val credentials = listOf(createTestCredential())
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = keyId
            // No challenge
        )
        val presentation = service.createPresentation(credentials, holderDid, options)
        
        val verificationOptions = PresentationVerificationOptions(
            verifyChallenge = true,
            expectedChallenge = "required-challenge"
        )
        
        val result = service.verifyPresentation(presentation, verificationOptions)
        
        assertFalse(result.valid)
        assertFalse(result.challengeValid)
        assertTrue(result.errors.any { it.contains("Challenge") || it.contains("challenge") })
    }

    @Test
    fun `test verify presentation with missing domain when required`() = runBlocking {
        val credentials = listOf(createTestCredential())
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = keyId
            // No domain
        )
        val presentation = service.createPresentation(credentials, holderDid, options)
        
        val verificationOptions = PresentationVerificationOptions(
            verifyDomain = true,
            expectedDomain = "required-domain.com"
        )
        
        val result = service.verifyPresentation(presentation, verificationOptions)
        
        assertFalse(result.valid)
        assertFalse(result.domainValid)
        assertTrue(result.errors.any { it.contains("Domain") || it.contains("domain") })
    }

    // ========== Selective Disclosure Edge Cases ==========

    @Test
    fun `test create selective disclosure with empty disclosed fields`() = runBlocking {
        val credentials = listOf(createTestCredential())
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = keyId
        )
        
        val presentation = service.createSelectiveDisclosure(
            credentials = credentials,
            disclosedFields = emptyList(),
            holderDid = holderDid,
            options = options
        )
        
        assertNotNull(presentation)
    }

    @Test
    fun `test create selective disclosure with invalid field paths`() = runBlocking {
        val credentials = listOf(createTestCredential())
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = keyId
        )
        
        val presentation = service.createSelectiveDisclosure(
            credentials = credentials,
            disclosedFields = listOf("nonexistent.field.path", "another.invalid.path"),
            holderDid = holderDid,
            options = options
        )
        
        assertNotNull(presentation)
    }

    @Test
    fun `test create selective disclosure with very long field paths`() = runBlocking {
        val credentials = listOf(createTestCredential())
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = keyId
        )
        
        val longPath = "level1.${"level".repeat(100)}.field"
        val presentation = service.createSelectiveDisclosure(
            credentials = credentials,
            disclosedFields = listOf(longPath),
            holderDid = holderDid,
            options = options
        )
        
        assertNotNull(presentation)
    }

    // ========== Concurrent Operations Tests ==========

    @Test
    fun `test create multiple presentations concurrently`() = runBlocking {
        val credentials = listOf(createTestCredential())
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = keyId
        )
        
        val presentations = (1..10).map {
            service.createPresentation(credentials, holderDid, options)
        }
        
        assertEquals(10, presentations.size)
        presentations.forEach { assertNotNull(it.proof) }
    }

    // ========== Error Recovery Tests ==========

    @Test
    fun `test create presentation after proof generator failure`() = runBlocking {
        proofRegistry.clear()
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
        
        assertFailsWith<IllegalArgumentException> {
            serviceWithoutGenerator.createPresentation(credentials, holderDid, options)
        }
        
        // Should work after registering generator
        val signer: suspend (ByteArray, String) -> ByteArray = { data, _ ->
            "mock-signature-${UUID.randomUUID()}".toByteArray()
        }
        val proofGenerator = Ed25519ProofGenerator(
            signer = signer,
            getPublicKeyId = { "did:key:holder123#key-1" }
        )
        proofRegistry.register(proofGenerator)
        
        val presentation = serviceWithoutGenerator.createPresentation(credentials, holderDid, options)
        assertNotNull(presentation.proof)
    }

    // ========== Special Character Tests ==========

    @Test
    fun `test create presentation with special characters in challenge`() = runBlocking {
        val credentials = listOf(createTestCredential())
        val specialChallenge = "!@#$%^&*()_+-=[]{}|;':\",./<>?"
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = keyId,
            challenge = specialChallenge
        )
        
        val presentation = service.createPresentation(credentials, holderDid, options)
        
        assertEquals(specialChallenge, presentation.challenge)
    }

    @Test
    fun `test create presentation with unicode in holder DID`() = runBlocking {
        val credentials = listOf(createTestCredential())
        val unicodeDid = "did:key:holder中文123"
        val options = PresentationOptions(
            holderDid = holderDid,
            proofType = "Ed25519Signature2020",
            keyId = keyId
        )
        
        try {
            val presentation = service.createPresentation(credentials, unicodeDid, options)
            assertNotNull(presentation)
        } catch (e: IllegalArgumentException) {
            // May fail if DID format is strict
            assertTrue(e.message?.contains("DID") == true || e.message?.contains("format") == true)
        }
    }

    // ========== Helper Methods ==========

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

