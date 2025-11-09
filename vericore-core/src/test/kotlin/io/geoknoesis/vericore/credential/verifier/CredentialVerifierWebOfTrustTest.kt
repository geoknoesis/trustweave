package io.geoknoesis.vericore.credential.verifier

import io.geoknoesis.vericore.credential.CredentialVerificationOptions
import io.geoknoesis.vericore.credential.CredentialVerificationResult
import io.geoknoesis.vericore.credential.models.Proof
import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.did.DidDocument
import io.geoknoesis.vericore.did.DidResolutionResult
import io.geoknoesis.vericore.testkit.trust.InMemoryTrustRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.time.Instant

/**
 * Comprehensive tests for CredentialVerifier with trust registry, delegation, and proof purpose validation.
 */
class CredentialVerifierWebOfTrustTest {
    
    @Test
    fun `test verify credential with trust registry enabled`() = runBlocking {
        val issuerDid = "did:key:issuer"
        val registry = InMemoryTrustRegistry()
        
        registry.addTrustAnchor(
            anchorDid = issuerDid,
            metadata = io.geoknoesis.vericore.trust.TrustAnchorMetadata(
                credentialTypes = listOf("TestCredential")
            )
        )
        
        val resolveDid: suspend (String) -> Boolean = { did ->
            did == issuerDid
        }
        
        val verifier = CredentialVerifier(resolveDid)
        
        val credential = VerifiableCredential(
            id = "https://example.com/credential-1",
            type = listOf("VerifiableCredential", "TestCredential"),
            issuer = issuerDid,
            credentialSubject = buildJsonObject { put("id", "did:key:holder") },
            issuanceDate = Instant.now().toString(),
            proof = Proof(
                type = "Ed25519Signature2020",
                created = Instant.now().toString(),
                verificationMethod = "$issuerDid#key-1",
                proofPurpose = "assertionMethod"
            )
        )
        
        val result = verifier.verify(
            credential = credential,
            options = CredentialVerificationOptions(
                checkTrustRegistry = true,
                trustRegistry = registry
            )
        )
        
        assertTrue(result.trustRegistryValid)
    }
    
    @Test
    fun `test verify credential with trust registry disabled`() = runBlocking {
        val issuerDid = "did:key:issuer"
        val registry = InMemoryTrustRegistry()
        
        // Don't add issuer as trust anchor
        
        val resolveDid: suspend (String) -> Boolean = { true }
        val verifier = CredentialVerifier(resolveDid)
        
        val credential = VerifiableCredential(
            id = "https://example.com/credential-1",
            type = listOf("VerifiableCredential", "TestCredential"),
            issuer = issuerDid,
            credentialSubject = buildJsonObject { put("id", "did:key:holder") },
            issuanceDate = Instant.now().toString(),
            proof = Proof(
                type = "Ed25519Signature2020",
                created = Instant.now().toString(),
                verificationMethod = "$issuerDid#key-1",
                proofPurpose = "assertionMethod"
            )
        )
        
        val result = verifier.verify(
            credential = credential,
            options = CredentialVerificationOptions(
                checkTrustRegistry = false // Disabled
            )
        )
        
        // Should still verify (trust registry check skipped)
        assertNotNull(result)
    }
    
    @Test
    fun `test verify credential with delegation enabled`() = runBlocking {
        val delegatorDid = "did:key:delegator"
        val delegateDid = "did:key:delegate"
        
        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            when (did) {
                delegatorDid -> DidResolutionResult(
                    document = DidDocument(
                        id = delegatorDid,
                        capabilityDelegation = listOf("$delegateDid#key-1")
                    )
                )
                delegateDid -> DidResolutionResult(
                    document = DidDocument(id = delegateDid)
                )
                else -> null
            }
        }
        
        val verifier = CredentialVerifier { did -> resolveDid(did) != null }
        
        // Credential issued by delegate, but verificationMethod references delegator
        // to show delegation chain
        val credential = VerifiableCredential(
            id = "https://example.com/delegated-credential",
            type = listOf("VerifiableCredential", "TestCredential"),
            issuer = delegateDid,
            credentialSubject = buildJsonObject { put("id", "did:key:holder") },
            issuanceDate = Instant.now().toString(),
            proof = Proof(
                type = "Ed25519Signature2020",
                created = Instant.now().toString(),
                verificationMethod = "$delegatorDid#key-1", // Delegator's key
                proofPurpose = "capabilityDelegation"
            )
        )
        
        val result = verifier.verify(
            credential = credential,
            options = CredentialVerificationOptions(
                verifyDelegation = true,
                resolveDid = resolveDid
            )
        )
        
        // Delegation should be valid if delegateDid is in delegatorDid's capabilityDelegation
        // The verification checks if issuer (delegateDid) is delegated by delegatorDid
        assertTrue(result.delegationValid)
    }
    
    @Test
    fun `test verify credential with proof purpose validation enabled`() = runBlocking {
        val issuerDid = "did:key:issuer"
        
        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            if (did == issuerDid) {
                DidResolutionResult(
                    document = DidDocument(
                        id = issuerDid,
                        assertionMethod = listOf("$issuerDid#key-1")
                    )
                )
            } else null
        }
        
        val verifier = CredentialVerifier { did -> resolveDid(did) != null }
        
        val credential = VerifiableCredential(
            id = "https://example.com/credential-1",
            type = listOf("VerifiableCredential", "TestCredential"),
            issuer = issuerDid,
            credentialSubject = buildJsonObject { put("id", "did:key:holder") },
            issuanceDate = Instant.now().toString(),
            proof = Proof(
                type = "Ed25519Signature2020",
                created = Instant.now().toString(),
                verificationMethod = "$issuerDid#key-1",
                proofPurpose = "assertionMethod"
            )
        )
        
        val result = verifier.verify(
            credential = credential,
            options = CredentialVerificationOptions(
                validateProofPurpose = true,
                resolveDid = resolveDid
            )
        )
        
        assertTrue(result.proofPurposeValid)
    }
    
    @Test
    fun `test verify credential with all web of trust features enabled`() = runBlocking {
        val issuerDid = "did:key:issuer"
        val registry = InMemoryTrustRegistry()
        
        registry.addTrustAnchor(
            anchorDid = issuerDid,
            metadata = io.geoknoesis.vericore.trust.TrustAnchorMetadata(
                credentialTypes = listOf("TestCredential")
            )
        )
        
        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            if (did == issuerDid) {
                DidResolutionResult(
                    document = DidDocument(
                        id = issuerDid,
                        assertionMethod = listOf("$issuerDid#key-1")
                    )
                )
            } else null
        }
        
        val verifier = CredentialVerifier { did -> resolveDid(did) != null }
        
        val credential = VerifiableCredential(
            id = "https://example.com/credential-1",
            type = listOf("VerifiableCredential", "TestCredential"),
            issuer = issuerDid,
            credentialSubject = buildJsonObject { put("id", "did:key:holder") },
            issuanceDate = Instant.now().toString(),
            proof = Proof(
                type = "Ed25519Signature2020",
                created = Instant.now().toString(),
                verificationMethod = "$issuerDid#key-1",
                proofPurpose = "assertionMethod"
            )
        )
        
        val result = verifier.verify(
            credential = credential,
            options = CredentialVerificationOptions(
                checkTrustRegistry = true,
                trustRegistry = registry,
                validateProofPurpose = true,
                resolveDid = resolveDid,
                checkExpiration = true
            )
        )
        
        assertTrue(result.trustRegistryValid)
        assertTrue(result.proofPurposeValid)
        assertNotNull(result)
    }
    
    @Test
    fun `test verify credential with untrusted issuer`() = runBlocking {
        val issuerDid = "did:key:untrusted"
        val registry = InMemoryTrustRegistry()
        
        // Don't add issuer as trust anchor
        
        val resolveDid: suspend (String) -> Boolean = { true }
        val verifier = CredentialVerifier(resolveDid)
        
        val credential = VerifiableCredential(
            id = "https://example.com/credential-1",
            type = listOf("VerifiableCredential", "TestCredential"),
            issuer = issuerDid,
            credentialSubject = buildJsonObject { put("id", "did:key:holder") },
            issuanceDate = Instant.now().toString(),
            proof = Proof(
                type = "Ed25519Signature2020",
                created = Instant.now().toString(),
                verificationMethod = "$issuerDid#key-1",
                proofPurpose = "assertionMethod"
            )
        )
        
        val result = verifier.verify(
            credential = credential,
            options = CredentialVerificationOptions(
                checkTrustRegistry = true,
                trustRegistry = registry
            )
        )
        
        assertFalse(result.trustRegistryValid)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("not trusted") })
    }
    
    @Test
    fun `test verify credential with invalid proof purpose`() = runBlocking {
        val issuerDid = "did:key:issuer"
        
        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            if (did == issuerDid) {
                DidResolutionResult(
                    document = DidDocument(
                        id = issuerDid,
                        assertionMethod = listOf("$issuerDid#key-1")
                        // Note: capabilityInvocation is empty
                    )
                )
            } else null
        }
        
        val verifier = CredentialVerifier { did -> resolveDid(did) != null }
        
        val credential = VerifiableCredential(
            id = "https://example.com/credential-1",
            type = listOf("VerifiableCredential", "TestCredential"),
            issuer = issuerDid,
            credentialSubject = buildJsonObject { put("id", "did:key:holder") },
            issuanceDate = Instant.now().toString(),
            proof = Proof(
                type = "Ed25519Signature2020",
                created = Instant.now().toString(),
                verificationMethod = "$issuerDid#key-1",
                proofPurpose = "capabilityInvocation" // Wrong purpose!
            )
        )
        
        val result = verifier.verify(
            credential = credential,
            options = CredentialVerificationOptions(
                validateProofPurpose = true,
                resolveDid = resolveDid
            )
        )
        
        assertFalse(result.proofPurposeValid)
        assertFalse(result.valid)
    }
    
    @Test
    fun `test verify credential with failed delegation`() = runBlocking {
        val delegatorDid = "did:key:delegator"
        val delegateDid = "did:key:delegate"
        
        val resolveDid: suspend (String) -> DidResolutionResult? = { did ->
            when (did) {
                delegatorDid -> DidResolutionResult(
                    document = DidDocument(
                        id = delegatorDid,
                        capabilityDelegation = emptyList() // No delegation!
                    )
                )
                delegateDid -> DidResolutionResult(
                    document = DidDocument(id = delegateDid)
                )
                else -> null
            }
        }
        
        val verifier = CredentialVerifier { did -> resolveDid(did) != null }
        
        val credential = VerifiableCredential(
            id = "https://example.com/delegated-credential",
            type = listOf("VerifiableCredential", "TestCredential"),
            issuer = delegateDid,
            credentialSubject = buildJsonObject { put("id", "did:key:holder") },
            issuanceDate = Instant.now().toString(),
            proof = Proof(
                type = "Ed25519Signature2020",
                created = Instant.now().toString(),
                verificationMethod = "$delegateDid#key-1",
                proofPurpose = "capabilityDelegation"
            )
        )
        
        val result = verifier.verify(
            credential = credential,
            options = CredentialVerificationOptions(
                verifyDelegation = true,
                resolveDid = resolveDid
            )
        )
        
        assertFalse(result.delegationValid)
        assertFalse(result.valid)
    }
}

