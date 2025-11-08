package io.geoknoesis.vericore.credential

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.models.VerifiablePresentation
import io.geoknoesis.vericore.spi.SchemaFormat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

/**
 * Tests for CredentialServiceProvider interface and CredentialService interface edge cases.
 */
class CredentialServiceProviderTest {

    @Test
    fun `test CredentialServiceProvider interface contract`() {
        val provider = object : CredentialServiceProvider {
            override val name = "test-provider"
            override fun create(options: Map<String, Any?>): CredentialService? {
                return null
            }
        }

        assertEquals("test-provider", provider.name)
        assertNull(provider.create(emptyMap()))
    }

    @Test
    fun `test CredentialServiceProvider create with options`() {
        val provider = object : CredentialServiceProvider {
            override val name = "test-provider"
            override fun create(options: Map<String, Any?>): CredentialService? {
                return if (options.containsKey("enabled") && options["enabled"] == true) {
                    createMockService()
                } else {
                    null
                }
            }
        }

        assertNull(provider.create(emptyMap()))
        assertNull(provider.create(mapOf("enabled" to false)))
        assertNotNull(provider.create(mapOf("enabled" to true)))
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
            type = listOf("VerifiablePresentation"),
            verifiableCredential = listOf(createTestCredential()),
            holder = "did:key:holder"
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
                    proof = io.geoknoesis.vericore.credential.models.Proof(
                        type = options.proofType,
                        created = java.time.Instant.now().toString(),
                        verificationMethod = "did:key:issuer#key-1",
                        proofPurpose = "assertionMethod"
                    )
                )
            }

            override suspend fun verifyCredential(
                credential: VerifiableCredential,
                options: CredentialVerificationOptions
            ): CredentialVerificationResult {
                return CredentialVerificationResult(
                    valid = credential.proof != null,
                    proofValid = credential.proof != null
                )
            }

            override suspend fun createPresentation(
                credentials: List<VerifiableCredential>,
                options: PresentationOptions
            ): VerifiablePresentation {
                return VerifiablePresentation(
                    type = listOf("VerifiablePresentation"),
                    verifiableCredential = credentials,
                    holder = options.holderDid
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
            type = listOf("VerifiableCredential"),
            issuer = "did:key:issuer",
            issuanceDate = java.time.Instant.now().toString(),
            credentialSubject = buildJsonObject {
                put("id", "did:key:subject")
            }
        )
    }
}

