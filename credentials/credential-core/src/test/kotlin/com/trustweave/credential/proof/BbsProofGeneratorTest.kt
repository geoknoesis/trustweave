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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import java.util.UUID
import kotlinx.datetime.Clock

/**
 * Comprehensive tests for BbsProofGenerator API.
 */
class BbsProofGeneratorTest {

    private lateinit var generator: BbsProofGenerator

    @BeforeEach
    fun setup() {
        generator = BbsProofGenerator(
            signer = { data, _ -> "bbs-signature-${UUID.randomUUID()}".toByteArray() },
            getPublicKeyId = { "did:key:bbs#key-1" }
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
        assertEquals("BbsBlsSignature2020", proof.type.identifier)
        assertNotNull(proof.proofValue)
        // proofValue is encoded as multibase (base58btc with 'z' prefix)
        assertTrue(proof.proofValue!!.startsWith("z"), "proofValue should start with 'z' (multibase base58btc prefix)")
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
    fun `test create selective disclosure proof`() = runBlocking {
        val credential = createTestCredential()

        val proof = generator.createSelectiveDisclosureProof(
            credential = credential,
            disclosedFields = listOf("credentialSubject.name", "credentialSubject.email"),
            keyId = "key-1"
        )

        assertNotNull(proof)
        assertEquals("BbsBlsSignature2020", proof.type.identifier)
    }

    @Test
    fun `test proof type is correct`() = runBlocking {
        assertEquals("BbsBlsSignature2020", generator.proofType)
    }

    private fun createTestCredential(
        id: String? = "https://example.com/credentials/1",
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

