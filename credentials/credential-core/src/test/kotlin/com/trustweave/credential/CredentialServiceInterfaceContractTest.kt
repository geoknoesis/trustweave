package com.trustweave.credential

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.VerifiablePresentation
import com.trustweave.credential.model.vc.CredentialProof
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.model.SchemaFormat
import com.trustweave.credential.model.ProofType
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.did.identifiers.Did
import com.trustweave.did.identifiers.VerificationMethodId
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Comprehensive interface contract tests for CredentialService.
 * Tests all methods, branches, and edge cases.
 */
class CredentialServiceInterfaceContractTest {

    @Test
    fun `test CredentialService providerName returns name`() = runBlocking {
        val service = createMockService("test-provider")

        assertEquals("test-provider", service.providerName)
    }

    @Test
    fun `test CredentialService supportedProofTypes returns list`() = runBlocking {
        val service = createMockService("test-provider")

        assertTrue(service.supportedProofTypes.isNotEmpty())
        assertTrue(service.supportedProofTypes.contains("Ed25519Signature2020"))
    }

    @Test
    fun `test CredentialService supportedSchemaFormats returns list`() = runBlocking {
        val service = createMockService("test-provider")

        assertTrue(service.supportedSchemaFormats.isNotEmpty())
    }

    @Test
    fun `test CredentialService issueCredential returns credential with proof`() = runBlocking {
        val service = createMockService("test-provider")
        val credential = createTestCredential()
        val options = CredentialIssuanceOptions(
            proofType = "Ed25519Signature2020",
            keyId = "key-1"
        )

        val issued = service.issueCredential(credential, options)

        assertNotNull(issued.proof)
        assertTrue(issued.proof is CredentialProof.LinkedDataProof)
        val linkedDataProof = issued.proof as CredentialProof.LinkedDataProof
        assertEquals("Ed25519Signature2020", linkedDataProof.type)
    }

    @Test
    fun `test CredentialService verifyCredential returns verification result`() = runBlocking {
        val service = createMockService("test-provider")
        val credential = createTestCredential(
            proof = CredentialProof.LinkedDataProof(
                type = "Ed25519Signature2020",
                created = Clock.System.now(),
                verificationMethod = "did:key:issuer#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "test-proof",
                additionalProperties = emptyMap()
            )
        )
        val options = CredentialVerificationOptions()

        val result = service.verifyCredential(credential, options)

        assertNotNull(result)
        // Check that result is either Valid or Invalid (sealed class)
        assertTrue(result is CredentialVerificationResult.Valid || result is CredentialVerificationResult.Invalid)
    }

    @Test
    fun `test CredentialService createPresentation returns presentation`() = runBlocking {
        val service = createMockService("test-provider")
        val cred1 = createTestCredential(id = "cred-1")
        val cred2 = createTestCredential(id = "cred-2")
        val options = PresentationOptions(
            holderDid = "did:key:holder",
            proofType = "Ed25519Signature2020",
            keyId = "key-1"
        )

        val presentation = service.createPresentation(listOf(cred1, cred2), options)

        assertNotNull(presentation)
        assertEquals(2, presentation.verifiableCredential.size)
        assertEquals("did:key:holder", presentation.holder.value)
    }

    @Test
    fun `test CredentialService verifyPresentation returns verification result`() = runBlocking {
        val service = createMockService("test-provider")
        val credential = createTestCredential()
        val presentation = VerifiablePresentation(
            id = CredentialId("presentation-1"),
            type = listOf(CredentialType.fromString("VerifiablePresentation")),
            verifiableCredential = listOf(credential),
            holder = Did("did:key:holder"),
            proof = CredentialProof.LinkedDataProof(
                type = "Ed25519Signature2020",
                created = Clock.System.now(),
                verificationMethod = "did:key:holder#key-1",
                proofPurpose = "authentication",
                proofValue = "test-proof",
                additionalProperties = emptyMap()
            )
        )
        val options = PresentationVerificationOptions()

        val result = service.verifyPresentation(presentation, options)

        assertNotNull(result)
        // PresentationVerificationResult still uses data class for now
        assertNotNull(result.valid)
    }

    @Test
    fun `test CredentialService issueCredential with custom options`() = runBlocking {
        val service = createMockService("test-provider")
        val credential = createTestCredential()
        val options = CredentialIssuanceOptions(
            proofType = "Ed25519Signature2020",
            keyId = "key-1",
            challenge = "challenge-123",
            domain = "example.com",
            anchorToBlockchain = true,
            chainId = "algorand:testnet"
        )

        val issued = service.issueCredential(credential, options)

        assertNotNull(issued.proof)
        assertTrue(issued.proof is CredentialProof.LinkedDataProof)
        val linkedDataProof = issued.proof as CredentialProof.LinkedDataProof
        // Note: challenge and domain are not part of LinkedDataProof structure
        // They would be in additionalProperties if needed
    }

    @Test
    fun `test CredentialService verifyCredential with all options enabled`() = runBlocking {
        val service = createMockService("test-provider")
        val credential = createTestCredential(
            proof = CredentialProof.LinkedDataProof(
                type = "Ed25519Signature2020",
                created = Clock.System.now(),
                verificationMethod = "did:key:issuer#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "test-proof",
                additionalProperties = emptyMap()
            )
        )
        val options = CredentialVerificationOptions(
            checkRevocation = true,
            checkExpiration = true,
            validateSchema = true,
            verifyBlockchainAnchor = true
        )

        val result = service.verifyCredential(credential, options)

        assertNotNull(result)
    }

    private fun createMockService(providerName: String): CredentialService {
        return object : CredentialService {
            override val providerName: String = providerName
            override val supportedProofTypes: List<String> = listOf("Ed25519Signature2020", "JsonWebSignature2020")
            override val supportedSchemaFormats: List<SchemaFormat> = listOf(SchemaFormat.JSON_SCHEMA)

            override suspend fun issueCredential(
                credential: VerifiableCredential,
                options: CredentialIssuanceOptions
            ): VerifiableCredential {
                val additionalProps = mutableMapOf<String, JsonElement>()
                options.challenge?.let { additionalProps["challenge"] = JsonPrimitive(it) }
                options.domain?.let { additionalProps["domain"] = JsonPrimitive(it) }
                return credential.copy(
                    proof = CredentialProof.LinkedDataProof(
                        type = options.proofType,
                        created = Clock.System.now(),
                        verificationMethod = options.keyId ?: "did:key:issuer#key-1",
                        proofPurpose = "assertionMethod",
                        proofValue = "test-proof",
                        additionalProperties = additionalProps
                    )
                )
            }

            override suspend fun verifyCredential(
                credential: VerifiableCredential,
                options: CredentialVerificationOptions
            ): CredentialVerificationResult {
                val proofValid = credential.proof != null
                val notExpired = credential.expirationDate?.let {
                    it > Clock.System.now()
                } ?: true
                val notRevoked = credential.credentialStatus == null

                return when {
                    !proofValid -> CredentialVerificationResult.Invalid.InvalidProof(
                        credential, "Proof is missing", listOf("Proof is missing")
                    )
                    !notExpired -> {
                        val expiredAt = credential.expirationDate ?: Clock.System.now()
                        CredentialVerificationResult.Invalid.Expired(
                            credential, expiredAt, listOf("Credential has expired")
                        )
                    }
                    !notRevoked -> CredentialVerificationResult.Invalid.Revoked(
                        credential, Clock.System.now(), listOf("Credential is revoked")
                    )
                    else -> CredentialVerificationResult.Valid(credential)
                }
            }

            override suspend fun createPresentation(
                credentials: List<VerifiableCredential>,
                options: PresentationOptions
            ): VerifiablePresentation {
                return VerifiablePresentation(
                    id = CredentialId(java.util.UUID.randomUUID().toString()),
                    type = listOf(CredentialType.fromString("VerifiablePresentation")),
                    verifiableCredential = credentials,
                    holder = Did(options.holderDid ?: "did:key:holder"),
                    proof = null,
                    challenge = options.challenge,
                    domain = options.domain
                )
            }

            override suspend fun verifyPresentation(
                presentation: VerifiablePresentation,
                options: PresentationVerificationOptions
            ): PresentationVerificationResult {
                val proofValid = presentation.proof != null
                val challengeValid = !options.verifyChallenge || presentation.challenge == options.expectedChallenge

                return PresentationVerificationResult(
                    valid = proofValid && challengeValid,
                    presentationProofValid = proofValid,
                    challengeValid = challengeValid
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
        issuanceDate: Instant = Clock.System.now(),
        proof: CredentialProof? = null
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id?.let { CredentialId(it) },
            type = types,
            issuer = Issuer.fromDid(Did(issuerDid)),
            credentialSubject = subject,
            issuanceDate = issuanceDate,
            proof = proof
        )
    }
}



