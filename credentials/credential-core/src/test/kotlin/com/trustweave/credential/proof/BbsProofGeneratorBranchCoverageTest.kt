package com.trustweave.credential.proof

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.did.identifiers.Did
import kotlinx.datetime.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlinx.datetime.Clock

/**
 * Branch coverage tests for BbsProofGenerator.
 */
class BbsProofGeneratorBranchCoverageTest {

    @Test
    fun `test BbsProofGenerator generateProof with verificationMethod in options`() = runBlocking {
        val generator = BbsProofGenerator(
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
        assertEquals("BbsBlsSignature2020", proof.type.identifier)
    }

    @Test
    fun `test BbsProofGenerator generateProof without verificationMethod uses publicKeyId`() = runBlocking {
        val generator = BbsProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) },
            getPublicKeyId = { "public-key-id" }
        )

        val credential = createTestCredential()
        val options = ProofGeneratorOptions(proofPurpose = "assertionMethod")

        val proof = generator.generateProof(credential, "key-1", options)

        assertEquals("did:key:public-key-id#key-1", proof.verificationMethod.value)
    }

    @Test
    fun `test BbsProofGenerator generateProof without verificationMethod and null publicKeyId`() = runBlocking {
        val generator = BbsProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) },
            getPublicKeyId = { null }
        )

        val credential = createTestCredential()
        val options = ProofGeneratorOptions(proofPurpose = "assertionMethod")

        val proof = generator.generateProof(credential, "key-1", options)

        assertEquals("did:key:key-1#key-1", proof.verificationMethod.value)
    }

    @Test
    fun `test BbsProofGenerator createSelectiveDisclosureProof`() = runBlocking {
        val generator = BbsProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )

        val credential = createTestCredential()
        val disclosedFields = listOf("credentialSubject.name")

        val proof = generator.createSelectiveDisclosureProof(credential, disclosedFields, "key-1")

        assertNotNull(proof)
        // createSelectiveDisclosureProof returns Proof from credential-models, not CredentialProof
        assertEquals("BbsBlsSignature2020", proof.type.identifier)
    }

    @Test
    fun `test BbsProofGenerator createSelectiveDisclosureProof with multiple fields`() = runBlocking {
        val generator = BbsProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )

        val credential = createTestCredential()
        val disclosedFields = listOf("credentialSubject.name", "credentialSubject.email")

        val proof = generator.createSelectiveDisclosureProof(credential, disclosedFields, "key-1")

        assertNotNull(proof)
    }

    @Test
    fun `test BbsProofGenerator createSelectiveDisclosureProof with empty fields`() = runBlocking {
        val generator = BbsProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )

        val credential = createTestCredential()

        val proof = generator.createSelectiveDisclosureProof(credential, emptyList(), "key-1")

        assertNotNull(proof)
    }

    private fun createTestCredential(
        id: String? = null,
        types: List<CredentialType> = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("PersonCredential")),
        issuerDid: String = "did:key:issuer",
        subject: CredentialSubject = CredentialSubject.fromDid(
            Did("did:key:subject"),
            claims = mapOf(
                "name" to JsonPrimitive("John Doe"),
                "email" to JsonPrimitive("john@example.com")
            )
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



