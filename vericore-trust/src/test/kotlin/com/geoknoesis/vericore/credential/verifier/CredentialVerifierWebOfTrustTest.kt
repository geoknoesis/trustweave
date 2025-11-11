package com.geoknoesis.vericore.credential.verifier

import com.geoknoesis.vericore.credential.CredentialVerificationOptions
import com.geoknoesis.vericore.credential.CredentialVerificationResult
import com.geoknoesis.vericore.credential.did.CredentialDidResolution
import com.geoknoesis.vericore.credential.did.CredentialDidResolver
import com.geoknoesis.vericore.credential.models.Proof
import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.did.DidDocument
import com.geoknoesis.vericore.did.DidResolutionResult
import com.geoknoesis.vericore.did.toCredentialDidResolution
import com.geoknoesis.vericore.util.booleanDidResolver
import com.geoknoesis.vericore.testkit.trust.InMemoryTrustRegistry
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
            metadata = com.geoknoesis.vericore.trust.TrustAnchorMetadata(
                credentialTypes = listOf("TestCredential")
            )
        )
        
        val verifier = CredentialVerifier(booleanDidResolver { did -> did == issuerDid })
        
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
        
        val verifier = CredentialVerifier(booleanDidResolver { true })
        
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
        
        val didResolver = CredentialDidResolver { did ->
            resolveDid(did)?.toCredentialDidResolution()
        }
        val verifier = CredentialVerifier(didResolver)
        
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
                didResolver = didResolver
            )
        )
        
        // Delegation should be valid if delegateDid is in delegatorDid's capabilityDelegation
        // The verification checks if issuer (delegateDid) is delegated by delegatorDid
        assertTrue(result.delegationValid)
    }

    @Test
    fun `legacy boolean resolver fails delegation gracefully`() = runBlocking {
        val delegatorDid = "did:key:delegator"
        val delegateDid = "did:key:delegate"

        val verifier = CredentialVerifier(booleanDidResolver { true })

        val credential = VerifiableCredential(
            id = "https://example.com/delegated-credential",
            type = listOf("VerifiableCredential", "TestCredential"),
            issuer = delegateDid,
            credentialSubject = buildJsonObject { put("id", "did:key:holder") },
            issuanceDate = Instant.now().toString(),
            proof = Proof(
                type = "Ed25519Signature2020",
                created = Instant.now().toString(),
                verificationMethod = "$delegatorDid#key-1",
                proofPurpose = "capabilityDelegation"
            )
        )

        val result = verifier.verify(
            credential = credential,
            options = CredentialVerificationOptions(
                verifyDelegation = true,
                didResolver = booleanDidResolver { true }
            )
        )

        assertFalse(result.delegationValid)
        assertTrue(result.errors.any { it.contains("Delegator DID document", ignoreCase = true) })
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
        
        val didResolver = CredentialDidResolver { did ->
            resolveDid(did)?.toCredentialDidResolution()
        }
        val verifier = CredentialVerifier(didResolver)
        
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
                didResolver = didResolver
            )
        )
        
        assertTrue(result.proofPurposeValid)
    }

    @Test
    fun `legacy boolean resolver fails proof purpose gracefully`() = runBlocking {
        val issuerDid = "did:key:issuer"

        val verifier = CredentialVerifier(booleanDidResolver { true })

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
                didResolver = booleanDidResolver { true }
            )
        )

        assertFalse(result.proofPurposeValid)
        assertTrue(result.errors.any { it.contains("Issuer DID document", ignoreCase = true) })
    }
    
    @Test
    fun `test verify credential with all web of trust features enabled`() = runBlocking {
        val issuerDid = "did:key:issuer"
        val registry = InMemoryTrustRegistry()
        
        registry.addTrustAnchor(
            anchorDid = issuerDid,
            metadata = com.geoknoesis.vericore.trust.TrustAnchorMetadata(
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
        
        val verifyResolver = CredentialDidResolver { did ->
            resolveDid(did)?.toCredentialDidResolution()
        }
        val verifier = CredentialVerifier(verifyResolver)
        
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
                didResolver = verifyResolver,
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
        
        val verifier = CredentialVerifier(booleanDidResolver { true })
        
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
        
        val resolver = CredentialDidResolver { did ->
            resolveDid(did)?.toCredentialDidResolution()
        }
        val verifier = CredentialVerifier(resolver)
        
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
                didResolver = resolver
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
        
        val delegationResolver = CredentialDidResolver { did ->
            resolveDid(did)?.toCredentialDidResolution()
        }
        val verifier = CredentialVerifier(delegationResolver)
        
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
                didResolver = delegationResolver
            )
        )
        
        assertFalse(result.delegationValid)
        assertFalse(result.valid)
    }
}

