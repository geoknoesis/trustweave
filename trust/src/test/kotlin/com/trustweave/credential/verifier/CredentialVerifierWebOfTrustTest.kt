package com.trustweave.credential.verifier

import com.trustweave.credential.CredentialVerificationOptions
import com.trustweave.credential.CredentialVerificationResult
import com.trustweave.credential.models.Proof
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.proof.Ed25519ProofGenerator
import com.trustweave.credential.proof.ProofOptions
import com.trustweave.did.DidDocument
import com.trustweave.did.VerificationMethod
import com.trustweave.did.resolver.DidResolver
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.util.resultDidResolver
import com.trustweave.util.booleanDidResolver
import com.trustweave.testkit.trust.InMemoryTrustRegistry
import com.trustweave.testkit.kms.InMemoryKeyManagementService
import com.trustweave.kms.Algorithm
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

    private lateinit var kms: InMemoryKeyManagementService
    private var keyId: com.trustweave.core.types.KeyId? = null
    private lateinit var publicKeyJwk: Map<String, Any?>
    private lateinit var proofGenerator: Ed25519ProofGenerator

    init {
        runBlocking {
            setup()
        }
    }

    private suspend fun setup() {
        // Create a real KMS and generate a real Ed25519 key
        kms = InMemoryKeyManagementService()
        val keyHandle = kms.generateKey(Algorithm.Ed25519, mapOf("keyId" to "key-1"))
        keyId = keyHandle.id
        publicKeyJwk = keyHandle.publicKeyJwk ?: emptyMap()

        // Create proof generator that uses the KMS to sign
        val keyIdValue = keyId!!
        proofGenerator = Ed25519ProofGenerator(
            signer = { data, _ -> kms.sign(keyIdValue, data) },
            getPublicKeyId = { keyIdValue.value }
        )
    }

    /**
     * Creates a test credential with a properly signed proof.
     */
    private suspend fun createTestCredential(
        issuerDid: String,
        credentialSubject: JsonObject = buildJsonObject { put("id", "did:key:holder") },
        proofPurpose: String = "assertionMethod"
    ): VerifiableCredential {
        // Create credential without proof first
        val credentialWithoutProof = VerifiableCredential(
            id = "https://example.com/credential-1",
            type = listOf("VerifiableCredential", "TestCredential"),
            issuer = issuerDid,
            credentialSubject = credentialSubject,
            issuanceDate = Instant.now().toString(),
            proof = null
        )

        // Generate a properly signed proof
        val proof = proofGenerator.generateProof(
            credential = credentialWithoutProof,
            keyId = keyId!!.value,
            options = ProofOptions(
                proofPurpose = proofPurpose,
                verificationMethod = "$issuerDid#key-1"
            )
        )

        // Return credential with proof
        return credentialWithoutProof.copy(proof = proof)
    }

    /**
     * Creates a test DID resolver that returns a DID document with verification methods.
     */
    private fun createTestDidResolver(issuerDid: String): DidResolver {
        return resultDidResolver { did ->
            if (did == issuerDid) {
                DidResolutionResult.Success(
                    document = DidDocument(
                        id = did,
                        verificationMethod = listOf(
                            VerificationMethod(
                                id = "$did#key-1",
                                type = "Ed25519VerificationKey2020",
                                controller = did,
                                publicKeyJwk = publicKeyJwk
                            )
                        )
                    )
                )
            } else {
                null
            }
        }
    }

    @Test
    fun `test verify credential with trust registry enabled`() = runBlocking {
        val issuerDid = "did:key:issuer"
        val registry = InMemoryTrustRegistry()

        registry.addTrustAnchor(
            anchorDid = issuerDid,
            metadata = com.trustweave.trust.TrustAnchorMetadata(
                credentialTypes = listOf("TestCredential")
            )
        )

        val didResolver = createTestDidResolver(issuerDid)
        val verifier = CredentialVerifier(didResolver)

        val credential = createTestCredential(issuerDid)

        val result = verifier.verify(
            credential = credential,
            options = CredentialVerificationOptions(
                checkTrustRegistry = true,
                trustRegistry = registry
            )
        )

        assertTrue(result.valid)
    }

    @Test
    fun `test verify credential with trust registry disabled`() = runBlocking {
        val issuerDid = "did:key:issuer"
        val registry = InMemoryTrustRegistry()

        // Don't add issuer as trust anchor

        val didResolver = createTestDidResolver(issuerDid)
        val verifier = CredentialVerifier(didResolver)

        val credential = createTestCredential(issuerDid)

        val result = verifier.verify(
            credential = credential,
            options = CredentialVerificationOptions(
                checkTrustRegistry = false // Disabled
            )
        )

        // Should still verify (trust registry check skipped)
        assertNotNull(result)
        assertTrue(result.valid)
    }

    @Test
    fun `test verify credential with delegation enabled`() = runBlocking {
        val delegatorDid = "did:key:delegator"
        val delegateDid = "did:key:delegate"

        val resolveDid: (String) -> DidResolutionResult? = { did ->
            when (did) {
                delegatorDid -> DidResolutionResult.Success(
                    document = DidDocument(
                        id = delegatorDid,
                        capabilityDelegation = listOf("$delegateDid#key-1"),
                        verificationMethod = listOf(
                            VerificationMethod(
                                id = "$delegatorDid#key-1",
                                type = "Ed25519VerificationKey2020",
                                controller = delegatorDid,
                                publicKeyJwk = publicKeyJwk
                            )
                        )
                    )
                )
                delegateDid -> DidResolutionResult.Success(
                    document = DidDocument(
                        id = delegateDid,
                        verificationMethod = listOf(
                            VerificationMethod(
                                id = "$delegateDid#key-1",
                                type = "Ed25519VerificationKey2020",
                                controller = delegateDid,
                                publicKeyJwk = publicKeyJwk
                            )
                        )
                    )
                )
                else -> null
            }
        }

        val didResolver = resultDidResolver(resolveDid)
        val verifier = CredentialVerifier(didResolver)

        // Credential issued by delegate, but verificationMethod references delegator
        // to show delegation chain
        val credentialWithoutProof = VerifiableCredential(
            id = "https://example.com/delegated-credential",
            type = listOf("VerifiableCredential", "TestCredential"),
            issuer = delegateDid,
            credentialSubject = buildJsonObject { put("id", "did:key:holder") },
            issuanceDate = Instant.now().toString(),
            proof = null
        )

        // Generate proof using delegator's key
        val proof = proofGenerator.generateProof(
            credential = credentialWithoutProof,
            keyId = keyId!!.value,
            options = ProofOptions(
                proofPurpose = "capabilityDelegation",
                verificationMethod = "$delegatorDid#key-1"
            )
        )

        val credential = credentialWithoutProof.copy(proof = proof)

        val result = verifier.verify(
            credential = credential,
            options = CredentialVerificationOptions(
                verifyDelegation = true,
            )
        )

        // Delegation should be valid if delegateDid is in delegatorDid's capabilityDelegation
        // The verification checks if issuer (delegateDid) is delegated by delegatorDid
        assertTrue(result.valid)
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
                proofPurpose = "capabilityDelegation",
                proofValue = "zMockSignatureValueForTesting" // Mock proofValue to pass structure validation
            )
        )

        val result = verifier.verify(
            credential = credential,
            options = CredentialVerificationOptions(
                verifyDelegation = true,
            )
        )

        assertFalse(result.valid)
        assertTrue(result.allErrors.any { it.contains("Delegator", ignoreCase = true) && it.contains("capabilityDelegation", ignoreCase = true) })
    }

    @Test
    fun `test verify credential with proof purpose validation enabled`() = runBlocking {
        val issuerDid = "did:key:issuer"

        val resolveDid: (String) -> DidResolutionResult? = { did ->
            if (did == issuerDid) {
                DidResolutionResult.Success(
                    document = DidDocument(
                        id = issuerDid,
                        assertionMethod = listOf("$issuerDid#key-1"),
                        verificationMethod = listOf(
                            VerificationMethod(
                                id = "$issuerDid#key-1",
                                type = "Ed25519VerificationKey2020",
                                controller = issuerDid,
                                publicKeyJwk = publicKeyJwk
                            )
                        )
                    )
                )
            } else null
        }

        val didResolver = resultDidResolver(resolveDid)
        val verifier = CredentialVerifier(didResolver)

        val credential = createTestCredential(issuerDid, proofPurpose = "assertionMethod")

        val result = verifier.verify(
            credential = credential,
            options = CredentialVerificationOptions(
                validateProofPurpose = true,
            )
        )

        assertTrue(result.valid)
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
                proofPurpose = "assertionMethod",
                proofValue = "zMockSignatureValueForTesting" // Mock proofValue to pass structure validation
            )
        )

        val result = verifier.verify(
            credential = credential,
            options = CredentialVerificationOptions(
                validateProofPurpose = true,
            )
        )

        assertFalse(result.valid)
        assertTrue(result.allErrors.any { it.contains("proof purpose", ignoreCase = true) && it.contains("verification relationship", ignoreCase = true) })
    }

    @Test
    fun `test verify credential with all web of trust features enabled`() = runBlocking {
        val issuerDid = "did:key:issuer"
        val registry = InMemoryTrustRegistry()

        registry.addTrustAnchor(
            anchorDid = issuerDid,
            metadata = com.trustweave.trust.TrustAnchorMetadata(
                credentialTypes = listOf("TestCredential")
            )
        )

        val resolveDid: (String) -> DidResolutionResult? = { did ->
            if (did == issuerDid) {
                DidResolutionResult.Success(
                    document = DidDocument(
                        id = issuerDid,
                        assertionMethod = listOf("$issuerDid#key-1"),
                        verificationMethod = listOf(
                            VerificationMethod(
                                id = "$issuerDid#key-1",
                                type = "Ed25519VerificationKey2020",
                                controller = issuerDid,
                                publicKeyJwk = publicKeyJwk
                            )
                        )
                    )
                )
            } else null
        }

        val verifyResolver = resultDidResolver(resolveDid)
        val verifier = CredentialVerifier(verifyResolver)

        val credential = createTestCredential(issuerDid, proofPurpose = "assertionMethod")

        val result = verifier.verify(
            credential = credential,
            options = CredentialVerificationOptions(
                checkTrustRegistry = true,
                trustRegistry = registry,
                validateProofPurpose = true,
                checkExpiration = true
            )
        )

        assertTrue(result.valid)
        assertNotNull(result)
    }

    @Test
    fun `test verify credential with untrusted issuer`() = runBlocking {
        val issuerDid = "did:key:untrusted"
        val registry = InMemoryTrustRegistry()

        // Don't add issuer as trust anchor

        val didResolver = createTestDidResolver(issuerDid)
        val verifier = CredentialVerifier(didResolver)

        val credential = createTestCredential(issuerDid)

        val result = verifier.verify(
            credential = credential,
            options = CredentialVerificationOptions(
                checkTrustRegistry = true,
                trustRegistry = registry
            )
        )

        assertFalse(result.valid)
        assertTrue(result.allErrors.any { it.contains("not trusted") })
    }

    @Test
    fun `test verify credential with invalid proof purpose`() = runBlocking {
        val issuerDid = "did:key:issuer"

        val resolveDid: (String) -> DidResolutionResult? = { did ->
            if (did == issuerDid) {
                DidResolutionResult.Success(
                    document = DidDocument(
                        id = issuerDid,
                        assertionMethod = listOf("$issuerDid#key-1"),
                        // Note: capabilityInvocation is empty
                        verificationMethod = listOf(
                            VerificationMethod(
                                id = "$issuerDid#key-1",
                                type = "Ed25519VerificationKey2020",
                                controller = issuerDid,
                                publicKeyJwk = publicKeyJwk
                            )
                        )
                    )
                )
            } else null
        }

        val resolver = resultDidResolver(resolveDid)
        val verifier = CredentialVerifier(resolver)

        // Create credential with wrong proof purpose (capabilityInvocation instead of assertionMethod)
        val credential = createTestCredential(issuerDid, proofPurpose = "capabilityInvocation")

        val result = verifier.verify(
            credential = credential,
            options = CredentialVerificationOptions(
                validateProofPurpose = true
            )
        )

        assertFalse(result.valid)
    }

    @Test
    fun `test verify credential with failed delegation`() = runBlocking {
        val delegatorDid = "did:key:delegator"
        val delegateDid = "did:key:delegate"

        val resolveDid: (String) -> DidResolutionResult? = { did ->
            when (did) {
                delegatorDid -> DidResolutionResult.Success(
                    document = DidDocument(
                        id = delegatorDid,
                        capabilityDelegation = emptyList() // No delegation!
                    )
                )
                delegateDid -> DidResolutionResult.Success(
                    document = DidDocument(id = delegateDid)
                )
                else -> null
            }
        }

        val delegationResolver = resultDidResolver(resolveDid)
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
                proofPurpose = "capabilityDelegation",
                proofValue = "zMockSignatureValueForTesting" // Mock proofValue to pass structure validation
            )
        )

        val result = verifier.verify(
            credential = credential,
            options = CredentialVerificationOptions(
                verifyDelegation = true
            )
        )

        assertFalse(result.valid)
        assertFalse(result.valid)
    }
}

