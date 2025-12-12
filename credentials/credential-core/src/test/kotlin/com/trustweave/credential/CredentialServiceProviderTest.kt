package com.trustweave.credential

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.VerifiablePresentation
import com.trustweave.credential.model.vc.CredentialProof
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.model.SchemaFormat
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.did.identifiers.Did
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlinx.datetime.Clock

/**
 * Tests for CredentialServiceProvider interface and CredentialService interface edge cases.
 */
class CredentialServiceProviderTest {

    @Test
    fun `test CredentialServiceProvider interface contract`() {
        val provider = object : CredentialServiceProvider {
            override val name = "test-provider"
            override fun create(options: CredentialServiceCreationOptions): CredentialService? {
                return null
            }
        }

        assertEquals("test-provider", provider.name)
        assertNull(provider.create())
    }

    @Test
    fun `test CredentialServiceProvider create with options`() {
        val provider = object : CredentialServiceProvider {
            override val name = "test-provider"
            override fun create(options: CredentialServiceCreationOptions): CredentialService? {
                return if (options.enabled) {
                    createMockService()
                } else {
                    null
                }
            }
        }

        assertNull(provider.create(CredentialServiceCreationOptions(enabled = false)))
        assertNotNull(provider.create())
    }

    @Test
    fun `test CredentialService interface properties`() = runBlocking {
        val service = createMockService()

        assertEquals("mock-service", service.providerName)
        assertTrue(service.supportedProofTypes.isNotEmpty())
        assertTrue(service.supportedSchemaFormats.isNotEmpty())
    }

    @Test
    fun `test CredentialService issueCredential`() = runBlocking {
        val service = createMockService()
        val credential = createTestCredential()

        val issued = service.issueCredential(
            credential,
            CredentialIssuanceOptions(proofType = "Ed25519Signature2020")
        )

        assertNotNull(issued.proof)
    }

    @Test
    fun `test CredentialService verifyCredential`() = runBlocking {
        val service = createMockService()
        val credential = createTestCredential()

        val result = service.verifyCredential(
            credential,
            CredentialVerificationOptions()
        )

        assertNotNull(result)
    }

    @Test
    fun `test CredentialService createPresentation`() = runBlocking {
        val service = createMockService()
        val credentials = listOf(createTestCredential())

        val presentation = service.createPresentation(
            credentials,
            PresentationOptions(holderDid = "did:key:holder")
        )

        assertNotNull(presentation)
    }

    @Test
    fun `test CredentialService verifyPresentation`() = runBlocking {
        val service = createMockService()
        val presentation = VerifiablePresentation(
            type = listOf(CredentialType.fromString("VerifiablePresentation")),
            verifiableCredential = listOf(createTestCredential()),
            holder = Did("did:key:holder")
        )

        val result = service.verifyPresentation(
            presentation,
            PresentationVerificationOptions()
        )

        assertNotNull(result)
    }

    private fun createMockService(): CredentialService {
        return object : CredentialService {
            override val providerName = "mock-service"
            override val supportedProofTypes = listOf("Ed25519Signature2020", "JsonWebSignature2020")
            override val supportedSchemaFormats = listOf(SchemaFormat.JSON_SCHEMA)

            override suspend fun issueCredential(
                credential: VerifiableCredential,
                options: CredentialIssuanceOptions
            ): VerifiableCredential {
                return credential.copy(
                    proof = CredentialProof.LinkedDataProof(
                        type = options.proofType,
                        created = Clock.System.now(),
                        verificationMethod = "did:key:issuer#key-1",
                        proofPurpose = "assertionMethod",
                        proofValue = "test-proof",
                        additionalProperties = emptyMap()
                    )
                )
            }

            override suspend fun verifyCredential(
                credential: VerifiableCredential,
                options: CredentialVerificationOptions
            ): CredentialVerificationResult {
                return if (credential.proof != null) {
                    CredentialVerificationResult.Valid(credential)
                } else {
                    CredentialVerificationResult.Invalid.InvalidProof(
                        credential,
                        "Credential has no proof",
                        listOf("Credential has no proof")
                    )
                }
            }

            override suspend fun createPresentation(
                credentials: List<VerifiableCredential>,
                options: PresentationOptions
            ): VerifiablePresentation {
                return VerifiablePresentation(
                    type = listOf(CredentialType.fromString("VerifiablePresentation")),
                    verifiableCredential = credentials,
                    holder = Did(options.holderDid ?: "did:key:holder")
                )
            }

            override suspend fun verifyPresentation(
                presentation: VerifiablePresentation,
                options: PresentationVerificationOptions
            ): PresentationVerificationResult {
                return PresentationVerificationResult(
                    valid = presentation.proof != null,
                    presentationProofValid = presentation.proof != null
                )
            }
        }
    }

    private fun createTestCredential(): VerifiableCredential {
        return VerifiableCredential(
            type = listOf(CredentialType.VerifiableCredential),
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject.fromDid(
                Did("did:key:subject"),
                claims = emptyMap()
            )
        )
    }
}

