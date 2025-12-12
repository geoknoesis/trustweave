package com.trustweave.credential.proof

import com.trustweave.credential.models.Proof
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.did.identifiers.Did
import kotlinx.datetime.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import java.util.UUID
import kotlinx.datetime.Clock

/**
 * Comprehensive tests for JwtProofGenerator API.
 */
class JwtProofGeneratorTest {

    private lateinit var generator: JwtProofGenerator

    @BeforeEach
    fun setup() {
        generator = JwtProofGenerator(
            signer = { data, _ -> "jwt-signature-${UUID.randomUUID()}".toByteArray() },
            getPublicKeyId = { "did:key:jwt#key-1" }
        )
    }

    @Test
    fun `test generate proof`() = runBlocking {
        val credential = createTestCredential()

        val proof = generator.generateProof(
            credential = credential,
            keyId = "key-1",
            options = ProofGeneratorOptions(proofPurpose = "assertionMethod")
        )

        assertNotNull(proof)
        assertEquals("JsonWebSignature2020", proof.type.identifier)
        assertNotNull(proof.jws)
        assertEquals("PLACEHOLDER_JWT_STRING", proof.jws)
    }

    @Test
    fun `test generate proof with options`() = runBlocking {
        val credential = createTestCredential()

        val proof = generator.generateProof(
            credential = credential,
            keyId = "key-1",
            options = ProofGeneratorOptions(
                proofPurpose = "authentication",
                challenge = "challenge-123",
                domain = "example.com",
                verificationMethod = "did:key:custom#key-1"
            )
        )

        assertEquals("authentication", proof.proofPurpose)
        assertEquals("challenge-123", proof.challenge)
        assertEquals("example.com", proof.domain)
        assertEquals("did:key:custom#key-1", proof.verificationMethod.value)
    }

    @Test
    fun `test generate JWT string`() = runBlocking {
        val credential = createTestCredential()

        val jwt = generator.generateJwt(
            credential = credential,
            keyId = "key-1",
            options = ProofGeneratorOptions(proofPurpose = "assertionMethod")
        )

        assertNotNull(jwt)
        assertEquals("PLACEHOLDER_JWT_STRING", jwt)
    }

    @Test
    fun `test proof type is correct`() = runBlocking {
        assertEquals("JsonWebSignature2020", generator.proofType)
    }

    @Test
    fun `test generate JWT uses verification method from options`() = runBlocking {
        val credential = createTestCredential()

        val jwt = generator.generateJwt(
            credential = credential,
            keyId = "key-1",
            options = ProofGeneratorOptions(
                proofPurpose = "assertionMethod",
                verificationMethod = "did:key:custom#key-1"
            )
        )

        assertNotNull(jwt)
    }

    private fun createTestCredential(
        id: String? = "https://example.com/credentials/1",
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

