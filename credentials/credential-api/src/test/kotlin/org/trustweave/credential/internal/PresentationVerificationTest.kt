package org.trustweave.credential.internal

import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.requests.VerificationOptions
import org.trustweave.credential.results.VerificationResult
import org.trustweave.credential.spi.proof.ProofEngine
import org.trustweave.credential.format.ProofSuiteId
import org.trustweave.core.identifiers.Iri
import kotlinx.datetime.Clock
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Comprehensive tests for PresentationVerification utility.
 */
class PresentationVerificationTest {

    @Test
    fun `kbJwtMaxAge defaults to 10 minutes when not configured`() {
        val options = VerificationOptions()

        assertEquals(
            PresentationVerification.DEFAULT_KB_JWT_MAX_AGE,
            PresentationVerification.kbJwtMaxAge(options),
            "Default KB-JWT max age must apply when the option is absent"
        )
        assertEquals(kotlin.time.Duration.parse("PT10M"), PresentationVerification.DEFAULT_KB_JWT_MAX_AGE)
    }

    @Test
    fun `kbJwtMaxAge honours a configured Duration`() {
        val options = VerificationOptions(
            additionalOptions = mapOf(
                PresentationVerification.KB_JWT_MAX_AGE_OPTION to kotlin.time.Duration.parse("PT2M")
            )
        )

        assertEquals(kotlin.time.Duration.parse("PT2M"), PresentationVerification.kbJwtMaxAge(options))
    }

    @Test
    fun `kbJwtMaxAge rejects non-positive or non-Duration values`() {
        // A present-but-invalid value must fail loudly: a verifier intending a
        // stricter policy must not be silently weakened to the default.
        val zero = VerificationOptions(
            additionalOptions = mapOf(PresentationVerification.KB_JWT_MAX_AGE_OPTION to kotlin.time.Duration.ZERO)
        )
        val wrongType = VerificationOptions(
            additionalOptions = mapOf(PresentationVerification.KB_JWT_MAX_AGE_OPTION to 600)
        )

        assertFailsWith<IllegalArgumentException> { PresentationVerification.kbJwtMaxAge(zero) }
        assertFailsWith<IllegalArgumentException> { PresentationVerification.kbJwtMaxAge(wrongType) }
    }

    @Test
    fun `test verifyChallenge with verifyChallenge disabled`() {
        val presentation = createTestPresentation(challenge = "test-challenge")
        val options = VerificationOptions(
            verifyChallenge = false
        )
        
        val result = PresentationVerification.verifyChallenge(presentation, options)
        
        assertNull(result, "Should not verify challenge when disabled")
    }
    
    @Test
    fun `test verifyChallenge with matching challenge`() {
        val presentation = createTestPresentation(challenge = "expected-challenge")
        val options = VerificationOptions(
            verifyChallenge = true,
            expectedChallenge = "expected-challenge"
        )
        
        val result = PresentationVerification.verifyChallenge(presentation, options)
        
        assertNull(result, "Should pass when challenge matches")
    }
    
    @Test
    fun `test verifyChallenge with mismatched challenge`() {
        val presentation = createTestPresentation(challenge = "wrong-challenge")
        val options = VerificationOptions(
            verifyChallenge = true,
            expectedChallenge = "expected-challenge"
        )
        
        val result = PresentationVerification.verifyChallenge(presentation, options)
        
        assertNotNull(result, "Should fail when challenge mismatches")
        assertTrue(result is VerificationResult.Invalid.InvalidProof)
        assertTrue(result.reason.contains("Challenge mismatch") || result.errors.any { it.contains("Challenge mismatch") })
        assertTrue(result.errors.any { it.contains("expected-challenge") })
        assertTrue(result.errors.any { it.contains("wrong-challenge") })
    }
    
    @Test
    fun `test verifyChallenge with no expected challenge provided`() {
        val presentation = createTestPresentation(challenge = "any-challenge")
        val options = VerificationOptions(
            verifyChallenge = true,
            expectedChallenge = null
        )
        
        val result = PresentationVerification.verifyChallenge(presentation, options)

        // Fail-closed: verifyChallenge is enabled but no expectedChallenge was supplied.
        assertNotNull(result, "Should fail when verifyChallenge is enabled without an expectedChallenge")
        assertTrue(result is VerificationResult.Invalid.InvalidProof)
        assertTrue(result.errors.any { it.contains("expectedChallenge") })
    }
    
    @Test
    fun `test verifyDomain with verifyDomain disabled`() {
        val presentation = createTestPresentation(domain = "test-domain.com")
        val options = VerificationOptions(
            verifyDomain = false
        )
        
        val result = PresentationVerification.verifyDomain(presentation, options)
        
        assertNull(result, "Should not verify domain when disabled")
    }
    
    @Test
    fun `test verifyDomain with matching domain`() {
        val presentation = createTestPresentation(domain = "expected-domain.com")
        val options = VerificationOptions(
            verifyDomain = true,
            expectedDomain = "expected-domain.com"
        )
        
        val result = PresentationVerification.verifyDomain(presentation, options)
        
        assertNull(result, "Should pass when domain matches")
    }
    
    @Test
    fun `test verifyDomain with mismatched domain`() {
        val presentation = createTestPresentation(domain = "wrong-domain.com")
        val options = VerificationOptions(
            verifyDomain = true,
            expectedDomain = "expected-domain.com"
        )
        
        val result = PresentationVerification.verifyDomain(presentation, options)
        
        assertNotNull(result, "Should fail when domain mismatches")
        assertTrue(result is VerificationResult.Invalid.InvalidProof)
        assertTrue(result.reason.contains("Domain mismatch") || result.errors.any { it.contains("Domain mismatch") })
        assertTrue(result.errors.any { it.contains("expected-domain.com") })
        assertTrue(result.errors.any { it.contains("wrong-domain.com") })
    }
    
    @Test
    fun `test verifyDomain with no expected domain provided`() {
        val presentation = createTestPresentation(domain = "any-domain.com")
        val options = VerificationOptions(
            verifyDomain = true,
            expectedDomain = null
        )
        
        val result = PresentationVerification.verifyDomain(presentation, options)

        // Fail-closed: verifyDomain is enabled but no expectedDomain was supplied.
        assertNotNull(result, "Should fail when verifyDomain is enabled without an expectedDomain")
        assertTrue(result is VerificationResult.Invalid.InvalidProof)
        assertTrue(result.errors.any { it.contains("expectedDomain") })
    }
    
    @Test
    fun `test verifyProofFormatSupported with empty engines map`() {
        val presentation = createTestPresentation()
        val proofFormat = ProofSuiteId.VC_LD
        val engines = emptyMap<ProofSuiteId, ProofEngine>()
        
        val result = PresentationVerification.verifyProofFormatSupported(
            proofFormat = proofFormat,
            engines = engines,
            presentation = presentation
        )
        
        assertNotNull(result, "Should fail when no engines available")
        assertTrue(result is VerificationResult.Invalid.UnsupportedFormat)
    }
    
    // Note: Tests for verifyProofFormatSupported with actual engines require integration testing
    // as ProofEngine implementations are internal. The empty map test above validates the
    // core logic path.
    
    @Test
    fun `test verifyPresentationSignature with blank proofValue`() {
        val verificationMethod = createTestVerificationMethod()
        val proof = createTestLinkedDataProof(
            proofValue = "",
            proofType = CredentialConstants.ProofTypes.ED25519_SIGNATURE_2020
        )

        val result = PresentationVerification.verifyPresentationSignature(
            vpDocument = createTestVpDocument(),
            proof = proof,
            verificationMethod = verificationMethod
        )

        assertTrue(!result, "Should fail with blank proofValue")
    }

    @Test
    fun `test verifyPresentationSignature with unsupported proof type`() {
        val verificationMethod = createTestVerificationMethod()
        val proof = createTestLinkedDataProof(
            proofValue = "valid-signature",
            proofType = "UnsupportedProofType"
        )

        val result = PresentationVerification.verifyPresentationSignature(
            vpDocument = createTestVpDocument(),
            proof = proof,
            verificationMethod = verificationMethod
        )

        assertTrue(!result, "Should fail with unsupported proof type")
    }

    @Test
    fun `test verifyPresentationSignature with garbage signature fails closed`() {
        val verificationMethod = createTestVerificationMethod()
        val proof = createTestLinkedDataProof(
            proofValue = "not-a-real-signature",
            proofType = CredentialConstants.ProofTypes.ED25519_SIGNATURE_2020
        )

        val result = PresentationVerification.verifyPresentationSignature(
            vpDocument = createTestVpDocument(),
            proof = proof,
            verificationMethod = verificationMethod
        )

        assertTrue(!result, "Should fail with an invalid signature value")
    }

    @Test
    fun `test resolvePresentationProofVerificationMethod rejects non-authentication purpose`() = kotlinx.coroutines.runBlocking {
        val didResolver = object : org.trustweave.did.resolver.DidResolver {
            override suspend fun resolve(did: org.trustweave.did.identifiers.Did): org.trustweave.did.resolver.DidResolutionResult {
                throw NotImplementedError("Resolution must not be reached for a rejected proof purpose")
            }
        }

        val result = PresentationVerification.resolvePresentationProofVerificationMethod(
            holderIri = Iri("did:key:test"),
            verificationMethodId = "did:key:test#key-1",
            didResolver = didResolver,
            declaredProofPurpose = "keyAgreement"
        )

        assertNull(result, "A presentation proof with proofPurpose != 'authentication' must be rejected")
    }

    // Helper functions

    private fun createTestVerificationMethod(): org.trustweave.did.model.VerificationMethod {
        val did = org.trustweave.did.identifiers.Did("did:key:test")
        val vmId = org.trustweave.did.identifiers.VerificationMethodId.parse("did:key:test#key-1", did)
        return org.trustweave.did.model.VerificationMethod(
            id = vmId,
            type = "Ed25519VerificationKey2020",
            controller = did
        )
    }

    private fun createTestVpDocument(): kotlinx.serialization.json.JsonObject =
        kotlinx.serialization.json.buildJsonObject {
            put("@context", kotlinx.serialization.json.buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive(CredentialConstants.VcContexts.VC_1_1))
                add(kotlinx.serialization.json.JsonPrimitive(CredentialConstants.SecuritySuites.ED25519_2020_V1))
            })
            put("type", kotlinx.serialization.json.buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive("VerifiablePresentation"))
            })
            put("holder", kotlinx.serialization.json.JsonPrimitive("did:key:test"))
        }

    private fun createTestLinkedDataProof(
        proofValue: String,
        proofType: String
    ): org.trustweave.credential.model.vc.CredentialProof.LinkedDataProof =
        org.trustweave.credential.model.vc.CredentialProof.LinkedDataProof(
            type = proofType,
            created = Clock.System.now(),
            verificationMethod = "did:key:test#key-1",
            proofPurpose = "authentication",
            proofValue = proofValue,
            additionalProperties = emptyMap()
        )

    private fun createTestPresentation(
        challenge: String? = null,
        domain: String? = null
    ): VerifiablePresentation {
        val credential = createTestCredential()
        // Per W3C VP spec, challenge/domain live inside the proof, not the outer envelope.
        // PresentationVerification reads them from LinkedDataProof.additionalProperties.
        val proof = if (challenge != null || domain != null) {
            val extras = buildMap<String, kotlinx.serialization.json.JsonElement> {
                challenge?.let { put("challenge", kotlinx.serialization.json.JsonPrimitive(it)) }
                domain?.let { put("domain", kotlinx.serialization.json.JsonPrimitive(it)) }
            }
            org.trustweave.credential.model.vc.CredentialProof.LinkedDataProof(
                type = "Ed25519Signature2020",
                created = Clock.System.now(),
                verificationMethod = "did:key:test#key-1",
                proofPurpose = "authentication",
                proofValue = "z-test-proof-value",
                additionalProperties = extras
            )
        } else {
            null
        }
        return VerifiablePresentation(
            context = listOf(CredentialConstants.VcContexts.VC_1_1),
            type = listOf(CredentialType.fromString("VerifiablePresentation")),
            holder = Iri("did:key:test"),
            verifiableCredential = listOf(credential),
            challenge = challenge,
            domain = domain,
            proof = proof
        )
    }
    
    private fun createTestCredential(): VerifiableCredential {
        return VerifiableCredential(
            context = listOf(CredentialConstants.VcContexts.VC_1_1),
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            issuer = Issuer.IriIssuer(Iri("did:key:issuer")),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject(
                id = Iri("did:key:subject"),
                claims = emptyMap()
            )
        )
    }
    
}

