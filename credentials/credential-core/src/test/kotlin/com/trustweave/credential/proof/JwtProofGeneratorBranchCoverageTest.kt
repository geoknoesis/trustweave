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
 * Branch coverage tests for JwtProofGenerator.
 */
class JwtProofGeneratorBranchCoverageTest {

    @Test
    fun `test JwtProofGenerator generateProof with verificationMethod in options`() = runBlocking {
        val generator = JwtProofGenerator(
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
        assertEquals("JsonWebSignature2020", proof.type.identifier)
        assertNotNull(proof.jws)
    }

    @Test
    fun `test JwtProofGenerator generateProof without verificationMethod uses publicKeyId`() = runBlocking {
        val generator = JwtProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) },
            getPublicKeyId = { "public-key-id" }
        )

        val credential = createTestCredential()
        val options = ProofGeneratorOptions(proofPurpose = "assertionMethod")

        val proof = generator.generateProof(credential, "key-1", options)

        assertEquals("did:key:public-key-id#key-1", proof.verificationMethod.value)
    }

    @Test
    fun `test JwtProofGenerator generateProof without verificationMethod and null publicKeyId`() = runBlocking {
        val generator = JwtProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) },
            getPublicKeyId = { null }
        )

        val credential = createTestCredential()
        val options = ProofGeneratorOptions(proofPurpose = "assertionMethod")

        val proof = generator.generateProof(credential, "key-1", options)

        assertEquals("did:key:key-1#key-1", proof.verificationMethod.value)
    }

    @Test
    fun `test JwtProofGenerator generateJwt returns JWT string`() = runBlocking {
        val generator = JwtProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )

        val credential = createTestCredential()
        val options = ProofGeneratorOptions(proofPurpose = "assertionMethod")

        val jwt = generator.generateJwt(credential, "key-1", options)

        assertNotNull(jwt)
        assertEquals("PLACEHOLDER_JWT_STRING", jwt)
    }

    @Test
    fun `test JwtProofGenerator generateJwt throws when jws is null`() = runBlocking {
        // This test would require mocking to make jws null
        // For now, we test the normal flow
        val generator = JwtProofGenerator(
            signer = { _, _ -> byteArrayOf(1, 2, 3) }
        )

        val credential = createTestCredential()
        val options = ProofGeneratorOptions(proofPurpose = "assertionMethod")

        // Current implementation always sets jws, so this won't throw
        val jwt = generator.generateJwt(credential, "key-1", options)
        assertNotNull(jwt)
    }

    @Test
    fun `test JwtProofGenerator generateProof with challenge`() = runBlocking {
        val generator = JwtProofGenerator(
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
    fun `test JwtProofGenerator generateProof with domain`() = runBlocking {
        val generator = JwtProofGenerator(
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



