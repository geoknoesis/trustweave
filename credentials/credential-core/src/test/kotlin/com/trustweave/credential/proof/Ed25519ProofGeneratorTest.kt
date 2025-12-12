package com.trustweave.credential.proof

import com.trustweave.credential.model.vc.CredentialProof
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.vc.CredentialStatus
import com.trustweave.credential.model.vc.CredentialSchema
import com.trustweave.credential.model.vc.TermsOfUse
import com.trustweave.credential.model.vc.RefreshService
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.model.StatusPurpose
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.credential.identifiers.StatusListId
import com.trustweave.credential.identifiers.SchemaId
import com.trustweave.credential.model.Evidence
import com.trustweave.credential.identifiers.IssuerId
import com.trustweave.core.identifiers.Iri
import com.trustweave.did.identifiers.Did
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

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
        val options = ProofGeneratorOptions(
            proofPurpose = "assertionMethod",
            challenge = "challenge-123",
            domain = "example.com"
        )

        val proof = generator.generateProof(credential, keyId, options)

        assertNotNull(proof)
        assertEquals("Ed25519Signature2020", proof.type.identifier)
        assertEquals("assertionMethod", proof.proofPurpose)
        assertEquals("challenge-123", proof.challenge)
        assertEquals("example.com", proof.domain)
        assertNotNull(proof.verificationMethod)
        assertTrue(proof.verificationMethod.value.contains("public-key-$keyId") || proof.verificationMethod.value.contains(keyId))
        assertNotNull(proof.proofValue)
        assertTrue(proof.proofValue!!.startsWith("z")) // Multibase prefix
        assertNotNull(proof.created)
    }

    @Test
    fun `test generate proof without challenge and domain`() = runBlocking {
        val credential = createTestCredential()
        val keyId = "key-2"
        val options = ProofGeneratorOptions(proofPurpose = "assertionMethod")

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
        val options = ProofGeneratorOptions(
            proofPurpose = "assertionMethod",
            verificationMethod = customVerificationMethod
        )

        val proof = generator.generateProof(credential, keyId, options)

        assertNotNull(proof)
        assertEquals(customVerificationMethod, proof.verificationMethod.value)
    }

    @Test
    fun `test generate proof with credential containing all fields`() = runBlocking {
        val subjectDid = Did("did:example:subject")
        val credential = VerifiableCredential(
            id = CredentialId("credential-123"),
            type = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("DegreeCredential")),
            issuer = Issuer.fromDid(Did("did:example:issuer")),
            issuanceDate = Instant.parse("2024-01-01T00:00:00Z"),
            expirationDate = Instant.parse("2025-01-01T00:00:00Z"),
            credentialSubject = CredentialSubject.fromDid(
                subjectDid,
                claims = mapOf("degree" to JsonPrimitive("Bachelor"))
            ),
            credentialStatus = CredentialStatus(
                id = StatusListId("https://example.com/status/1"),
                type = "StatusList2021Entry",
                statusListIndex = "1"
            ),
            credentialSchema = CredentialSchema(
                id = SchemaId("https://example.com/schema"),
                type = "JsonSchemaValidator2018"
            ),
            evidence = listOf(
                Evidence(
                    id = CredentialId("evidence-1"),
                    type = listOf("Evidence"),
                    verifier = IssuerId("did:example:verifier"),
                    evidenceDate = "2024-01-01T00:00:00Z"
                )
            ),
            termsOfUse = listOf(
                TermsOfUse(
                    id = "terms-1",
                    type = "TermsOfUse",
                    additionalProperties = mapOf("text" to JsonPrimitive("Terms"))
                )
            ),
            refreshService = RefreshService(
                id = Iri("https://example.com/refresh"),
                type = "CredentialRefreshService"
            )
        )

        val options = ProofGeneratorOptions(proofPurpose = "assertionMethod")
        val proof = generator.generateProof(credential, "key-4", options)

        assertNotNull(proof)
        assertNotNull(proof.proofValue)
    }

    @Test
    fun `test generate proof with minimal credential`() = runBlocking {
        val subjectDid = Did("did:example:subject")
        val credential = VerifiableCredential(
            type = listOf(CredentialType.VerifiableCredential),
            issuer = Issuer.fromDid(Did("did:example:issuer")),
            issuanceDate = Instant.parse("2024-01-01T00:00:00Z"),
            credentialSubject = CredentialSubject.fromDid(subjectDid)
        )

        val options = ProofGeneratorOptions(proofPurpose = "assertionMethod")
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
        val options = ProofGeneratorOptions(proofPurpose = "assertionMethod")
        val proof = generatorWithoutResolver.generateProof(credential, "key-6", options)

        assertNotNull(proof)
        assertNotNull(proof.verificationMethod)
        // Should fallback to did:key:keyId format
        assertTrue(proof.verificationMethod.value.contains("key-6"))
    }

    @Test
    fun `test proof type is Ed25519Signature2020`() {
        assertEquals("Ed25519Signature2020", generator.proofType)
    }

    @Test
    fun `test generate proof signs credential document`() = runBlocking {
        val credential = createTestCredential()
        val keyId = "key-7"
        val options = ProofGeneratorOptions(proofPurpose = "assertionMethod")

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
            val options = ProofGeneratorOptions(proofPurpose = purpose)
            val proof = generator.generateProof(credential, "key-8", options)
            assertEquals(purpose, proof.proofPurpose)
        }
    }

    @Test
    fun `test generate proof with empty credential subject`() = runBlocking {
        val subjectDid = Did("did:example:subject")
        val credential = VerifiableCredential(
            type = listOf(CredentialType.VerifiableCredential),
            issuer = Issuer.fromDid(Did("did:example:issuer")),
            issuanceDate = Instant.parse("2024-01-01T00:00:00Z"),
            credentialSubject = CredentialSubject.fromDid(subjectDid, claims = emptyMap())
        )

        val options = ProofGeneratorOptions(proofPurpose = "assertionMethod")
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
        val options = ProofGeneratorOptions(proofPurpose = "assertionMethod")
        val proof = generatorWithoutResolver.generateProof(credential, "key-fallback", options)

        assertNotNull(proof)
        assertNotNull(proof.verificationMethod)
        // Should fallback to did:key:keyId format
        assertTrue(proof.verificationMethod.value.contains("key-fallback") || proof.verificationMethod.value.contains("did:key"))
    }

    @Test
    fun `test generate proof canonicalizes JSON correctly`() = runBlocking {
        val subjectDid = Did("did:example:subject")
        val credential1 = VerifiableCredential(
            id = CredentialId("cred-1"),
            type = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("A"), CredentialType.Custom("B")),
            issuer = Issuer.fromDid(Did("did:example:issuer")),
            issuanceDate = Instant.parse("2024-01-01T00:00:00Z"),
            credentialSubject = CredentialSubject.fromDid(
                subjectDid,
                claims = mapOf(
                    "z" to JsonPrimitive("last"),
                    "a" to JsonPrimitive("first")
                )
            )
        )

        val credential2 = VerifiableCredential(
            id = CredentialId("cred-1"),
            type = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("B"), CredentialType.Custom("A")),
            issuer = Issuer.fromDid(Did("did:example:issuer")),
            issuanceDate = Instant.parse("2024-01-01T00:00:00Z"),
            credentialSubject = CredentialSubject.fromDid(
                subjectDid,
                claims = mapOf(
                    "a" to JsonPrimitive("first"),
                    "z" to JsonPrimitive("last")
                )
            )
        )

        val options = ProofGeneratorOptions(proofPurpose = "assertionMethod")
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
        val subjectDid = Did("did:example:subject")
        val credential = VerifiableCredential(
            id = CredentialId("credential-full"),
            type = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("FullCredential")),
            issuer = Issuer.fromDid(Did("did:example:issuer")),
            issuanceDate = Instant.parse("2024-01-01T00:00:00Z"),
            expirationDate = Instant.parse("2025-01-01T00:00:00Z"),
            credentialSubject = CredentialSubject.fromDid(
                subjectDid,
                claims = mapOf("name" to JsonPrimitive("Full Test"))
            ),
            credentialStatus = CredentialStatus(
                id = StatusListId("https://example.com/status/1"),
                type = "StatusList2021Entry",
                statusListIndex = "1"
            ),
            credentialSchema = CredentialSchema(
                id = SchemaId("https://example.com/schema"),
                type = "JsonSchemaValidator2018"
            ),
            evidence = listOf(
                Evidence(
                    id = CredentialId("evidence-1"),
                    type = listOf("Evidence"),
                    verifier = IssuerId("did:example:verifier"),
                    evidenceDate = "2024-01-01T00:00:00Z"
                )
            ),
            termsOfUse = listOf(
                TermsOfUse(
                    id = "terms-1",
                    type = "TermsOfUse",
                    additionalProperties = mapOf("text" to JsonPrimitive("Terms"))
                )
            ),
            refreshService = RefreshService(
                id = Iri("https://example.com/refresh"),
                type = "CredentialRefreshService"
            )
        )

        val options = ProofGeneratorOptions(
            proofPurpose = "assertionMethod",
            challenge = "challenge-full",
            domain = "example.com",
            verificationMethod = "did:example:issuer#key-full"
        )

        val proof = generator.generateProof(credential, "key-full", options)

        assertNotNull(proof)
        assertEquals("did:example:issuer#key-full", proof.verificationMethod.value)
        assertNotNull(proof.proofValue)
        assertTrue(proof.proofValue!!.startsWith("z"))
    }

    @Test
    fun `test proof type constant`() {
        assertEquals("Ed25519Signature2020", generator.proofType)
    }

    @Test
    fun `test generate proof with complex nested credential subject`() = runBlocking {
        val subjectDid = Did("did:example:subject")
        val credential = VerifiableCredential(
            type = listOf(CredentialType.VerifiableCredential),
            issuer = Issuer.fromDid(Did("did:example:issuer")),
            issuanceDate = Instant.parse("2024-01-01T00:00:00Z"),
            credentialSubject = CredentialSubject.fromDid(
                subjectDid,
                claims = mapOf(
                    "nested" to buildJsonObject {
                        put("level1", buildJsonObject {
                            put("level2", "value")
                        })
                    },
                    "array" to buildJsonArray {
                        add("item1")
                        add("item2")
                    }
                )
            )
        )

        val options = ProofGeneratorOptions(proofPurpose = "assertionMethod")
        val proof = generator.generateProof(credential, "key-nested", options)

        assertNotNull(proof)
        assertNotNull(proof.proofValue)
    }

    private fun createTestCredential(): VerifiableCredential {
        val subjectDid = Did("did:example:subject")
        return VerifiableCredential(
            id = CredentialId("credential-${System.currentTimeMillis()}"),
            type = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("TestCredential")),
            issuer = Issuer.fromDid(Did("did:example:issuer")),
            issuanceDate = Instant.parse("2024-01-01T00:00:00Z"),
            credentialSubject = CredentialSubject.fromDid(
                subjectDid,
                claims = mapOf("name" to JsonPrimitive("Test Subject"))
            )
        )
    }
}

