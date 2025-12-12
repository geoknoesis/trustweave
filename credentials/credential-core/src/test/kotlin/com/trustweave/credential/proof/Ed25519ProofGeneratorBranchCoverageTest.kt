package com.trustweave.credential.proof

import com.trustweave.credential.models.Proof
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.credential.model.vc.CredentialSchema
import com.trustweave.credential.identifiers.SchemaId
import com.trustweave.did.identifiers.Did
import kotlinx.datetime.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Branch coverage tests for Ed25519ProofGenerator focusing on edge cases.
 */
class Ed25519ProofGeneratorBranchCoverageTest {

    @Test
    fun `test Ed25519ProofGenerator generateProof with verificationMethod in options`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) },
            getPublicKeyId = { null }
        )

        val credential = createTestCredential()
        val options = ProofGeneratorOptions(
            proofPurpose = "assertionMethod",
            verificationMethod = "did:key:custom#key-1"
        )

        val proof = generator.generateProof(credential, "key-1", options)

        assertEquals("did:key:custom#key-1", proof.verificationMethod.value)
    }

    @Test
    fun `test Ed25519ProofGenerator generateProof without verificationMethod uses publicKeyId`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) },
            getPublicKeyId = { "public-key-id" }
        )

        val credential = createTestCredential()
        val options = ProofGeneratorOptions(proofPurpose = "assertionMethod")

        val proof = generator.generateProof(credential, "key-1", options)

        assertEquals("did:key:public-key-id#key-1", proof.verificationMethod.value)
    }

    @Test
    fun `test Ed25519ProofGenerator generateProof without verificationMethod and null publicKeyId`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) },
            getPublicKeyId = { null }
        )

        val credential = createTestCredential()
        val options = ProofGeneratorOptions(proofPurpose = "assertionMethod")

        val proof = generator.generateProof(credential, "key-1", options)

        assertEquals("did:key:key-1#key-1", proof.verificationMethod.value)
    }

    @Test
    fun `test Ed25519ProofGenerator generateProof with challenge`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )

        val credential = createTestCredential()
        val options = ProofGeneratorOptions(
            proofPurpose = "assertionMethod",
            challenge = "challenge-123"
        )

        val proof = generator.generateProof(credential, "key-1", options)

        assertEquals("challenge-123", proof.challenge)
    }

    @Test
    fun `test Ed25519ProofGenerator generateProof with domain`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )

        val credential = createTestCredential()
        val options = ProofGeneratorOptions(
            proofPurpose = "assertionMethod",
            domain = "example.com"
        )

        val proof = generator.generateProof(credential, "key-1", options)

        assertEquals("example.com", proof.domain)
    }

    @Test
    fun `test Ed25519ProofGenerator generateProof with all optional credential fields`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )

        val statusListId = com.trustweave.credential.identifiers.StatusListId("status-list-1")
        val schemaId = com.trustweave.credential.identifiers.SchemaId("schema-1")
        val credential = createTestCredential(
            id = "https://example.com/credentials/1",
            expirationDate = Clock.System.now().plus(86400.seconds),
            credentialStatus = com.trustweave.credential.model.vc.CredentialStatus(
                id = statusListId,
                type = "StatusList2021Entry",
                statusPurpose = com.trustweave.credential.model.StatusPurpose.REVOCATION,
                statusListIndex = "0"
            ),
            credentialSchema = com.trustweave.credential.model.vc.CredentialSchema(
                id = schemaId,
                type = "JsonSchemaValidator2018"
            )
        )
        val options = ProofGeneratorOptions(proofPurpose = "assertionMethod")

        val proof = generator.generateProof(credential, "key-1", options)

        assertNotNull(proof)
        assertEquals("Ed25519Signature2020", proof.type.identifier)
    }

    @Test
    fun `test Ed25519ProofGenerator generateProof with minimal credential`() = runBlocking {
        val generator = Ed25519ProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )

        val credential = VerifiableCredential(
            type = listOf(com.trustweave.credential.model.CredentialType.VerifiableCredential),
            issuer = com.trustweave.credential.model.vc.Issuer.fromDid(com.trustweave.did.identifiers.Did("did:key:issuer")),
            credentialSubject = com.trustweave.credential.model.vc.CredentialSubject.fromDid(com.trustweave.did.identifiers.Did("did:key:subject")),
            issuanceDate = Clock.System.now()
        )
        val options = ProofGeneratorOptions(proofPurpose = "assertionMethod")

        val proof = generator.generateProof(credential, "key-1", options)

        assertNotNull(proof)
    }

    private fun createTestCredential(
        id: String? = null,
        types: List<CredentialType> = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("PersonCredential")),
        issuerDid: String = "did:key:issuer",
        subject: CredentialSubject = CredentialSubject.fromDid(
            Did("did:key:subject"),
            claims = mapOf("name" to JsonPrimitive("John Doe"))
        ),
        issuanceDate: Instant = Clock.System.now(),
        expirationDate: Instant? = null,
        credentialStatus: com.trustweave.credential.model.vc.CredentialStatus? = null,
        credentialSchema: CredentialSchema? = null
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id?.let { CredentialId(it) },
            type = types,
            issuer = Issuer.fromDid(Did(issuerDid)),
            credentialSubject = subject,
            issuanceDate = issuanceDate,
            expirationDate = expirationDate,
            credentialStatus = credentialStatus,
            credentialSchema = credentialSchema
        )
    }
}



