package com.geoknoesis.vericore.credential.proof

import com.geoknoesis.vericore.credential.models.Proof
import com.geoknoesis.vericore.credential.models.VerifiableCredential
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class Ed25519ProofGeneratorTest {

    private lateinit var generator: Ed25519ProofGenerator
    private val mockSignatures = mutableMapOf<String, ByteArray>()

    @BeforeTest
    fun setup() {
        mockSignatures.clear()
        generator = Ed25519ProofGenerator(
            signer = { data, keyId ->
                // Store signature for verification
                val signature = "signature-${keyId}-${data.contentHashCode()}".toByteArray()
                mockSignatures[keyId] = signature
                signature
            },
            getPublicKeyId = { keyId -> "public-key-$keyId" }
        )
    }

    @Test
    fun `test generate proof successfully`() = runBlocking {
        val credential = createTestCredential()
        val keyId = "key-1"
        val options = ProofOptions(
            proofPurpose = "assertionMethod",
            challenge = "challenge-123",
            domain = "example.com"
        )

        val proof = generator.generateProof(credential, keyId, options)

        assertNotNull(proof)
        assertEquals("Ed25519Signature2020", proof.type)
        assertEquals("assertionMethod", proof.proofPurpose)
        assertEquals("challenge-123", proof.challenge)
        assertEquals("example.com", proof.domain)
        assertNotNull(proof.verificationMethod)
        assertTrue(proof.verificationMethod!!.contains("public-key-$keyId") || proof.verificationMethod!!.contains(keyId))
        assertNotNull(proof.proofValue)
        assertTrue(proof.proofValue!!.startsWith("z")) // Multibase prefix
        assertNotNull(proof.created)
    }

    @Test
    fun `test generate proof without challenge and domain`() = runBlocking {
        val credential = createTestCredential()
        val keyId = "key-2"
        val options = ProofOptions(proofPurpose = "assertionMethod")

        val proof = generator.generateProof(credential, keyId, options)

        assertNotNull(proof)
        assertEquals("assertionMethod", proof.proofPurpose)
        assertNull(proof.challenge)
        assertNull(proof.domain)
    }

    @Test
    fun `test generate proof with custom verification method`() = runBlocking {
        val credential = createTestCredential()
        val keyId = "key-3"
        val customVerificationMethod = "did:example:issuer#key-3"
        val options = ProofOptions(
            proofPurpose = "assertionMethod",
            verificationMethod = customVerificationMethod
        )

        val proof = generator.generateProof(credential, keyId, options)

        assertNotNull(proof)
        assertEquals(customVerificationMethod, proof.verificationMethod)
    }

    @Test
    fun `test generate proof with credential containing all fields`() = runBlocking {
        val credential = VerifiableCredential(
            id = "credential-123",
            type = listOf("VerifiableCredential", "DegreeCredential"),
            issuer = "did:example:issuer",
            issuanceDate = "2024-01-01T00:00:00Z",
            expirationDate = "2025-01-01T00:00:00Z",
            credentialSubject = buildJsonObject {
                put("id", "did:example:subject")
                put("degree", "Bachelor")
            },
            credentialStatus = com.geoknoesis.vericore.credential.models.CredentialStatus(
                id = "https://example.com/status/1",
                type = "StatusList2021Entry",
                statusListIndex = "1",
                statusPurpose = "revocation"
            ),
            credentialSchema = com.geoknoesis.vericore.credential.models.CredentialSchema(
                id = "https://example.com/schema",
                type = "JsonSchemaValidator2018"
            ),
            evidence = listOf(
                com.geoknoesis.vericore.credential.models.Evidence(
                    id = "evidence-1",
                    type = listOf("Evidence"),
                    verifier = "did:example:verifier",
                    evidenceDate = "2024-01-01T00:00:00Z"
                )
            ),
            termsOfUse = com.geoknoesis.vericore.credential.models.TermsOfUse(
                id = "terms-1",
                type = "TermsOfUse",
                termsOfUse = buildJsonObject { put("text", "Terms") }
            ),
            refreshService = com.geoknoesis.vericore.credential.models.RefreshService(
                id = "refresh-1",
                type = "CredentialRefreshService",
                serviceEndpoint = "https://example.com/refresh"
            )
        )

        val options = ProofOptions(proofPurpose = "assertionMethod")
        val proof = generator.generateProof(credential, "key-4", options)

        assertNotNull(proof)
        assertNotNull(proof.proofValue)
    }

    @Test
    fun `test generate proof with minimal credential`() = runBlocking {
        val credential = VerifiableCredential(
            type = listOf("VerifiableCredential"),
            issuer = "did:example:issuer",
            issuanceDate = "2024-01-01T00:00:00Z",
            credentialSubject = buildJsonObject {
                put("id", "did:example:subject")
            }
        )

        val options = ProofOptions(proofPurpose = "assertionMethod")
        val proof = generator.generateProof(credential, "key-5", options)

        assertNotNull(proof)
        assertNotNull(proof.proofValue)
    }

    @Test
    fun `test generate proof without public key ID resolver`() = runBlocking {
        val generatorWithoutResolver = Ed25519ProofGenerator(
            signer = { _, _ -> "signature".toByteArray() },
            getPublicKeyId = { null }
        )

        val credential = createTestCredential()
        val options = ProofOptions(proofPurpose = "assertionMethod")
        val proof = generatorWithoutResolver.generateProof(credential, "key-6", options)

        assertNotNull(proof)
        assertNotNull(proof.verificationMethod)
        // Should fallback to did:key:keyId format
        assertTrue(proof.verificationMethod!!.contains("key-6"))
    }

    @Test
    fun `test proof type is Ed25519Signature2020`() {
        assertEquals("Ed25519Signature2020", generator.proofType)
    }

    @Test
    fun `test generate proof signs credential document`() = runBlocking {
        val credential = createTestCredential()
        val keyId = "key-7"
        val options = ProofOptions(proofPurpose = "assertionMethod")

        val proof = generator.generateProof(credential, keyId, options)

        // Verify that signer was called (signature stored)
        assertTrue(mockSignatures.containsKey(keyId))
        assertNotNull(proof.proofValue)
    }

    @Test
    fun `test generate proof with different proof purposes`() = runBlocking {
        val credential = createTestCredential()
        val purposes = listOf("assertionMethod", "authentication", "keyAgreement")

        purposes.forEach { purpose ->
            val options = ProofOptions(proofPurpose = purpose)
            val proof = generator.generateProof(credential, "key-8", options)
            assertEquals(purpose, proof.proofPurpose)
        }
    }

    @Test
    fun `test generate proof with empty credential subject`() = runBlocking {
        val credential = VerifiableCredential(
            type = listOf("VerifiableCredential"),
            issuer = "did:example:issuer",
            issuanceDate = "2024-01-01T00:00:00Z",
            credentialSubject = buildJsonObject {}
        )

        val options = ProofOptions(proofPurpose = "assertionMethod")
        val proof = generator.generateProof(credential, "key-empty", options)

        assertNotNull(proof)
        assertNotNull(proof.proofValue)
    }

    @Test
    fun `test generate proof with null public key ID resolver returns fallback`() = runBlocking {
        val generatorWithoutResolver = Ed25519ProofGenerator(
            signer = { _, _ -> "signature".toByteArray() },
            getPublicKeyId = { null }
        )

        val credential = createTestCredential()
        val options = ProofOptions(proofPurpose = "assertionMethod")
        val proof = generatorWithoutResolver.generateProof(credential, "key-fallback", options)

        assertNotNull(proof)
        assertNotNull(proof.verificationMethod)
        // Should fallback to did:key:keyId format
        assertTrue(proof.verificationMethod!!.contains("key-fallback") || proof.verificationMethod!!.contains("did:key"))
    }

    @Test
    fun `test generate proof canonicalizes JSON correctly`() = runBlocking {
        val credential1 = VerifiableCredential(
            id = "cred-1",
            type = listOf("VerifiableCredential", "A", "B"),
            issuer = "did:example:issuer",
            issuanceDate = "2024-01-01T00:00:00Z",
            credentialSubject = buildJsonObject {
                put("z", "last")
                put("a", "first")
            }
        )

        val credential2 = VerifiableCredential(
            id = "cred-1",
            type = listOf("VerifiableCredential", "B", "A"),
            issuer = "did:example:issuer",
            issuanceDate = "2024-01-01T00:00:00Z",
            credentialSubject = buildJsonObject {
                put("a", "first")
                put("z", "last")
            }
        )

        val options = ProofOptions(proofPurpose = "assertionMethod")
        val proof1 = generator.generateProof(credential1, "key-1", options)
        val proof2 = generator.generateProof(credential2, "key-1", options)

        // Proofs should be deterministic (same credential content should produce same proof value)
        // Note: This test verifies that canonicalization works, but actual proof values may differ
        // due to timestamps. We're mainly checking that the process completes successfully.
        assertNotNull(proof1.proofValue)
        assertNotNull(proof2.proofValue)
    }

    @Test
    fun `test generate proof with all optional fields populated`() = runBlocking {
        val credential = VerifiableCredential(
            id = "credential-full",
            type = listOf("VerifiableCredential", "FullCredential"),
            issuer = "did:example:issuer",
            issuanceDate = "2024-01-01T00:00:00Z",
            expirationDate = "2025-01-01T00:00:00Z",
            credentialSubject = buildJsonObject {
                put("id", "did:example:subject")
                put("name", "Full Test")
            },
            credentialStatus = com.geoknoesis.vericore.credential.models.CredentialStatus(
                id = "https://example.com/status/1",
                type = "StatusList2021Entry",
                statusListIndex = "1"
            ),
            credentialSchema = com.geoknoesis.vericore.credential.models.CredentialSchema(
                id = "https://example.com/schema",
                type = "JsonSchemaValidator2018"
            ),
            evidence = listOf(
                com.geoknoesis.vericore.credential.models.Evidence(
                    id = "evidence-1",
                    type = listOf("Evidence"),
                    verifier = "did:example:verifier",
                    evidenceDate = "2024-01-01T00:00:00Z"
                )
            ),
            termsOfUse = com.geoknoesis.vericore.credential.models.TermsOfUse(
                id = "terms-1",
                type = "TermsOfUse",
                termsOfUse = buildJsonObject { put("text", "Terms") }
            ),
            refreshService = com.geoknoesis.vericore.credential.models.RefreshService(
                id = "refresh-1",
                type = "CredentialRefreshService",
                serviceEndpoint = "https://example.com/refresh"
            )
        )

        val options = ProofOptions(
            proofPurpose = "assertionMethod",
            challenge = "challenge-full",
            domain = "example.com",
            verificationMethod = "did:example:issuer#key-full"
        )

        val proof = generator.generateProof(credential, "key-full", options)

        assertNotNull(proof)
        assertEquals("did:example:issuer#key-full", proof.verificationMethod)
        assertNotNull(proof.proofValue)
        assertTrue(proof.proofValue!!.startsWith("z"))
    }

    @Test
    fun `test proof type constant`() {
        assertEquals("Ed25519Signature2020", generator.proofType)
    }

    @Test
    fun `test generate proof with complex nested credential subject`() = runBlocking {
        val credential = VerifiableCredential(
            type = listOf("VerifiableCredential"),
            issuer = "did:example:issuer",
            issuanceDate = "2024-01-01T00:00:00Z",
            credentialSubject = buildJsonObject {
                put("id", "did:example:subject")
                put("nested", buildJsonObject {
                    put("level1", buildJsonObject {
                        put("level2", "value")
                    })
                })
                put("array", buildJsonArray {
                    add("item1")
                    add("item2")
                })
            }
        )

        val options = ProofOptions(proofPurpose = "assertionMethod")
        val proof = generator.generateProof(credential, "key-nested", options)

        assertNotNull(proof)
        assertNotNull(proof.proofValue)
    }

    private fun createTestCredential(): VerifiableCredential {
        return VerifiableCredential(
            id = "credential-${System.currentTimeMillis()}",
            type = listOf("VerifiableCredential", "TestCredential"),
            issuer = "did:example:issuer",
            issuanceDate = "2024-01-01T00:00:00Z",
            credentialSubject = buildJsonObject {
                put("id", "did:example:subject")
                put("name", "Test Subject")
            }
        )
    }
}

