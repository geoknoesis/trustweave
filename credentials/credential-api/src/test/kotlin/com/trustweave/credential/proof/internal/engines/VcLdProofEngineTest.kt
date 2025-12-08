package com.trustweave.credential.proof.internal.engines

import com.trustweave.credential.format.ProofSuiteId
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.core.identifiers.Iri
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.proof.ProofPurpose
import com.trustweave.credential.proof.proofOptions
import com.trustweave.credential.requests.IssuanceRequest
import com.trustweave.credential.requests.PresentationRequest
import com.trustweave.credential.requests.VerificationOptions
import com.trustweave.credential.results.VerificationResult
import com.trustweave.credential.spi.proof.ProofEngineConfig
import com.trustweave.did.identifiers.Did
import com.trustweave.did.identifiers.VerificationMethodId
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlin.test.*

/**
 * Comprehensive unit tests for VcLdProofEngine.
 */
class VcLdProofEngineTest {

    private val engine = VcLdProofEngine()

    @Test
    fun `test engine properties`() {
        assertEquals(ProofSuiteId.VC_LD, engine.format)
        assertEquals("Verifiable Credentials (Linked Data)", engine.formatName)
        assertEquals("2.0", engine.formatVersion)
        assertTrue(engine.capabilities.selectiveDisclosure)
        assertFalse(engine.capabilities.zeroKnowledge)
        assertTrue(engine.capabilities.revocation)
        assertTrue(engine.capabilities.presentation)
        assertTrue(engine.capabilities.predicates)
    }

    @Test
    fun `test engine is ready by default`() {
        assertTrue(engine.isReady())
    }

    @Test
    fun `test initialize and close`() = runBlocking {
        engine.initialize()
        engine.close()
        // Should not throw
    }

    @Test
    fun `test initialize with config`() = runBlocking {
        val config = ProofEngineConfig(properties = mapOf("test" to "value"))
        engine.initialize(config)
        // Should not throw
    }

    @Test
    fun `test issue with valid request`() = runBlocking {
        val request = createValidIssuanceRequest()
        
        // Note: This will fail because getSigner returns null in the implementation
        // This is expected - the engine needs actual KMS integration for signing
        val exception = assertThrows<IllegalArgumentException> {
            engine.issue(request)
        }
        assertTrue(exception.message?.contains("No signer available") == true)
    }

    @Test
    fun `test issue with wrong format`() = runBlocking {
        val request = createValidIssuanceRequest().copy(
            format = ProofSuiteId.VC_JWT
        )
        
        val exception = assertThrows<IllegalArgumentException> {
            engine.issue(request)
        }
        assertTrue(exception.message?.contains("does not match engine format") == true)
    }

    @Test
    fun `test issue with proof options`() = runBlocking {
        val request = createValidIssuanceRequest().copy(
            proofOptions = proofOptions {
                purpose = ProofPurpose.Authentication
                challenge = "challenge-123"
                domain = "example.com"
            }
        )
        
        val exception = assertThrows<IllegalArgumentException> {
            engine.issue(request)
        }
        assertTrue(exception.message?.contains("No signer available") == true)
    }

    @Test
    fun `test verify with valid credential`() = runBlocking {
        val credential = createValidCredential()
        val options = VerificationOptions()
        
        // Note: Verification will fail because proof verification is not fully implemented
        // This is expected for a skeleton implementation
        val result = engine.verify(credential, options)
        
        // Should return InvalidProof or similar since proof verification isn't implemented
        assertTrue(result is VerificationResult.Invalid)
    }

    @Test
    fun `test verify with expired credential`() = runBlocking {
        val credential = createValidCredential().copy(
            expirationDate = Clock.System.now().minus(kotlin.time.Duration.parse("PT1H")) // Expired 1 hour ago
        )
        val options = VerificationOptions(checkExpiration = true)
        
        val result = engine.verify(credential, options)
        
        assertTrue(result is VerificationResult.Invalid.Expired)
    }

    @Test
    fun `test verify with credential missing proof`() = runBlocking {
        val credential = createValidCredential().copy(proof = null)
        val options = VerificationOptions()
        
        val result = engine.verify(credential, options)
        
        assertTrue(result is VerificationResult.Invalid)
    }

    @Test
    fun `test verify with credential missing issuer`() = runBlocking {
        val credential = createValidCredential().copy(
            issuer = Issuer.IriIssuer(Iri(""))
        )
        val options = VerificationOptions()
        
        val result = engine.verify(credential, options)
        
        assertTrue(result is VerificationResult.Invalid)
    }

    @Test
    fun `test createPresentation`() = runBlocking {
        val credentials = listOf(createValidCredential())
        val request = PresentationRequest()
        
        // VC-LD supports presentations
        val exception = assertThrows<UnsupportedOperationException> {
            engine.createPresentation(credentials, request)
        }
        assertTrue(exception.message?.contains("not implemented") == true)
    }

    @Test
    fun `test createPresentation with selective disclosure`() = runBlocking {
        val credentials = listOf(createValidCredential())
        val request = PresentationRequest(
            disclosedClaims = setOf("name", "email")
        )
        
        val exception = assertThrows<UnsupportedOperationException> {
            engine.createPresentation(credentials, request)
        }
        assertTrue(exception.message?.contains("not implemented") == true)
    }

    @Test
    fun `test createPresentation with empty credentials`() = runBlocking {
        val request = PresentationRequest()
        
        val exception = assertThrows<IllegalArgumentException> {
            engine.createPresentation(emptyList(), request)
        }
    }

    // Helper functions

    private fun createValidIssuanceRequest(): IssuanceRequest {
        val issuerDid = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
        val subjectDid = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
        
        return IssuanceRequest(
            format = ProofSuiteId.VC_LD,
            issuer = Issuer.fromDid(issuerDid),
            issuerKeyId = VerificationMethodId.parse("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK#key-1"),
            credentialSubject = CredentialSubject.fromDid(
                subjectDid,
                claims = mapOf(
                    "name" to JsonPrimitive("John Doe"),
                    "email" to JsonPrimitive("john@example.com")
                )
            ),
            type = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("PersonCredential")),
            issuedAt = Clock.System.now(),
            validUntil = Clock.System.now().plus(kotlin.time.Duration.parse("PT${86400 * 365}S")) // 1 year
        )
    }

    private fun createValidCredential(): VerifiableCredential {
        val issuerDid = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
        val subjectDid = Did("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
        
        return VerifiableCredential(
            id = CredentialId("urn:uuid:test-credential-123"),
            type = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("PersonCredential")),
            issuer = Issuer.fromDid(issuerDid),
            issuanceDate = Clock.System.now(),
            expirationDate = Clock.System.now().plus(kotlin.time.Duration.parse("PT${86400 * 365}S")), // 1 year
            credentialSubject = CredentialSubject.fromDid(
                subjectDid,
                claims = mapOf(
                    "name" to JsonPrimitive("John Doe"),
                    "email" to JsonPrimitive("john@example.com")
                )
            ),
            proof = com.trustweave.credential.model.vc.CredentialProof.LinkedDataProof(
                type = "Ed25519Signature2020",
                created = Clock.System.now(),
                verificationMethod = "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "test-signature-value",
                additionalProperties = emptyMap()
            )
        )
    }
}

