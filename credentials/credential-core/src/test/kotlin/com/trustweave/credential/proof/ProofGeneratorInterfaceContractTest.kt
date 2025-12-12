package com.trustweave.credential.proof

import com.trustweave.credential.models.Proof
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.model.ProofTypes
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.did.identifiers.Did
import kotlinx.datetime.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlinx.datetime.Clock

/**
 * Comprehensive interface contract tests for ProofGenerator.
 * Tests all methods, branches, and edge cases.
 */
class ProofGeneratorInterfaceContractTest {

    @Test
    fun `test ProofGenerator proofType returns type`() = runBlocking {
        val generator = createMockGenerator("Ed25519Signature2020")

        assertEquals("Ed25519Signature2020", generator.proofType)
    }

    @Test
    fun `test ProofGenerator generateProof returns proof`() = runBlocking {
        val generator = createMockGenerator("Ed25519Signature2020")
        val credential = createTestCredential()
        val options = ProofGeneratorOptions(
            proofPurpose = "assertionMethod",
            challenge = "challenge-123",
            domain = "example.com"
        )

        val proof = generator.generateProof(credential, "key-1", options)

        assertNotNull(proof)
        assertEquals("Ed25519Signature2020", proof.type.identifier)
        assertEquals("assertionMethod", proof.proofPurpose)
        assertEquals("challenge-123", proof.challenge)
        assertEquals("example.com", proof.domain)
    }

    @Test
    fun `test ProofGenerator generateProof with minimal options`() = runBlocking {
        val generator = createMockGenerator("Ed25519Signature2020")
        val credential = createTestCredential()
        val options = ProofGeneratorOptions()

        val proof = generator.generateProof(credential, "key-1", options)

        assertNotNull(proof)
        assertEquals("assertionMethod", proof.proofPurpose) // Default
    }

    @Test
    fun `test ProofGenerator generateProof with verificationMethod in options`() = runBlocking {
        val generator = createMockGenerator("Ed25519Signature2020")
        val credential = createTestCredential()
        val options = ProofGeneratorOptions(
            verificationMethod = "did:key:custom#key-1"
        )

        val proof = generator.generateProof(credential, "key-1", options)

        assertNotNull(proof)
        assertEquals("did:key:custom#key-1", proof.verificationMethod.value)
    }

    @Test
    fun `test ProofGenerator generateProof with additionalOptions`() = runBlocking {
        val generator = createMockGenerator("Ed25519Signature2020")
        val credential = createTestCredential()
        val options = ProofGeneratorOptions(
            additionalOptions = mapOf("customField" to "customValue")
        )

        val proof = generator.generateProof(credential, "key-1", options)

        assertNotNull(proof)
    }

    @Test
    fun `test ProofGenerator generateProof with empty credential`() = runBlocking {
        val generator = createMockGenerator("Ed25519Signature2020")
        val credential = VerifiableCredential(
            type = listOf(CredentialType.VerifiableCredential),
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromDid(Did("did:key:subject")),
            issuanceDate = Clock.System.now()
        )
        val options = ProofGeneratorOptions()

        val proof = generator.generateProof(credential, "key-1", options)

        assertNotNull(proof)
    }

    @Test
    fun `test ProofGenerator generateProof with complex credential`() = runBlocking {
        val generator = createMockGenerator("Ed25519Signature2020")
        val credential = createTestCredential(
            types = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("PersonCredential"), CredentialType.Custom("DegreeCredential")),
            subject = CredentialSubject.fromDid(
                Did("did:key:subject"),
                claims = mapOf(
                    "name" to JsonPrimitive("John Doe"),
                    "email" to JsonPrimitive("john@example.com"),
                    "age" to JsonPrimitive(30),
                    "address" to buildJsonObject {
                        put("street", "123 Main St")
                        put("city", "New York")
                    }
                )
            )
        )
        val options = ProofGeneratorOptions()

        val proof = generator.generateProof(credential, "key-1", options)

        assertNotNull(proof)
    }

    private fun createMockGenerator(proofType: String): ProofGenerator {
        return object : ProofGenerator {
            override val proofType: String = proofType

            override suspend fun generateProof(
                credential: VerifiableCredential,
                keyId: String,
                options: ProofGeneratorOptions
            ): Proof {
                return Proof(
                    type = ProofTypes.fromString(proofType),
                    created = Clock.System.now().toString(),
                    verificationMethod = com.trustweave.did.identifiers.VerificationMethodId.parse(
                        options.verificationMethod ?: "did:key:issuer#$keyId",
                        Did("did:key:issuer")
                    ),
                    proofPurpose = options.proofPurpose,
                    proofValue = "test-proof-value",
                    challenge = options.challenge,
                    domain = options.domain
                )
            }
        }
    }

    private fun createTestCredential(
        id: String? = null,
        types: List<CredentialType> = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("PersonCredential")),
        issuerDid: String = "did:key:issuer",
        subject: CredentialSubject = CredentialSubject.fromDid(
            Did("did:key:subject"),
            claims = mapOf("name" to JsonPrimitive("John Doe"))
        ),
        issuanceDate: Instant = Clock.System.now()
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id?.let { CredentialId(it) },
            type = types,
            issuer = Issuer.fromDid(Did(issuerDid)),
            credentialSubject = subject,
            issuanceDate = issuanceDate
        )
    }
}



