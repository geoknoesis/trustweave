package io.geoknoesis.vericore.credential

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.models.VerifiablePresentation
import io.geoknoesis.vericore.credential.issuer.CredentialIssuer
import io.geoknoesis.vericore.credential.verifier.CredentialVerifier
import io.geoknoesis.vericore.credential.presentation.PresentationService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for CredentialRegistry API.
 */
class CredentialRegistryTest {

    private lateinit var mockService: CredentialService

    @BeforeEach
    fun setup() {
        CredentialRegistry.clear()
        
        mockService = object : CredentialService {
            override val providerName = "test-provider"
            override val supportedProofTypes: List<String> = listOf("Ed25519Signature2020")
            override val supportedSchemaFormats: List<io.geoknoesis.vericore.spi.SchemaFormat> = emptyList()
            
            override suspend fun issueCredential(
                credential: VerifiableCredential,
                options: CredentialIssuanceOptions
            ): VerifiableCredential {
                return credential.copy(
                    proof = io.geoknoesis.vericore.credential.models.Proof(
                        type = "Ed25519Signature2020",
                        created = java.time.Instant.now().toString(),
                        verificationMethod = "did:key:test#key-1",
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
                    errors = emptyList(),
                    warnings = emptyList()
                )
            }
            
            override suspend fun createPresentation(
                credentials: List<VerifiableCredential>,
                options: PresentationOptions
            ): VerifiablePresentation {
                return VerifiablePresentation(
                    id = "vp-1",
                    type = listOf("VerifiablePresentation"),
                    verifiableCredential = credentials,
                    holder = options.holderDid,
                    proof = null
                )
            }
            
            override suspend fun verifyPresentation(
                presentation: VerifiablePresentation,
                options: PresentationVerificationOptions
            ): PresentationVerificationResult {
                return PresentationVerificationResult(
                    valid = true,
                    errors = emptyList(),
                    warnings = emptyList()
                )
            }
        }
    }

    @AfterEach
    fun cleanup() {
        CredentialRegistry.clear()
    }

    @Test
    fun `test register service`() = runBlocking {
        CredentialRegistry.register(mockService)
        
        assertTrue(CredentialRegistry.isRegistered("test-provider"))
        assertEquals(mockService, CredentialRegistry.get("test-provider"))
    }

    @Test
    fun `test get service by name`() = runBlocking {
        CredentialRegistry.register(mockService)
        
        val retrieved = CredentialRegistry.get("test-provider")
        
        assertNotNull(retrieved)
        assertEquals("test-provider", retrieved?.providerName)
    }

    @Test
    fun `test get default service`() = runBlocking {
        CredentialRegistry.register(mockService)
        
        val default = CredentialRegistry.get()
        
        assertNotNull(default)
        assertEquals("test-provider", default?.providerName)
    }

    @Test
    fun `test get returns null when not registered`() = runBlocking {
        assertNull(CredentialRegistry.get("nonexistent"))
        assertNull(CredentialRegistry.get())
    }

    @Test
    fun `test getAll returns all services`() = runBlocking {
        val service1 = mockService
        val service2 = object : CredentialService {
            override val providerName = "provider-2"
            override val supportedProofTypes: List<String> = emptyList()
            override val supportedSchemaFormats: List<io.geoknoesis.vericore.spi.SchemaFormat> = emptyList()
            override suspend fun issueCredential(credential: VerifiableCredential, options: CredentialIssuanceOptions) = credential
            override suspend fun verifyCredential(credential: VerifiableCredential, options: CredentialVerificationOptions) = CredentialVerificationResult(valid = true)
            override suspend fun createPresentation(credentials: List<VerifiableCredential>, options: PresentationOptions) = VerifiablePresentation(id = "vp", type = listOf("VP"), verifiableCredential = credentials, holder = "")
            override suspend fun verifyPresentation(presentation: VerifiablePresentation, options: PresentationVerificationOptions) = PresentationVerificationResult(valid = true)
        }
        
        CredentialRegistry.register(service1)
        CredentialRegistry.register(service2)
        
        val all = CredentialRegistry.getAll()
        
        assertEquals(2, all.size)
        assertTrue(all.containsKey("test-provider"))
        assertTrue(all.containsKey("provider-2"))
    }

    @Test
    fun `test unregister service`() = runBlocking {
        CredentialRegistry.register(mockService)
        assertTrue(CredentialRegistry.isRegistered("test-provider"))
        
        CredentialRegistry.unregister("test-provider")
        
        assertFalse(CredentialRegistry.isRegistered("test-provider"))
        assertNull(CredentialRegistry.get("test-provider"))
    }

    @Test
    fun `test clear all services`() = runBlocking {
        CredentialRegistry.register(mockService)
        assertEquals(1, CredentialRegistry.getAll().size)
        
        CredentialRegistry.clear()
        
        assertEquals(0, CredentialRegistry.getAll().size)
        assertFalse(CredentialRegistry.isRegistered("test-provider"))
    }

    @Test
    fun `test issue credential through registry`() = runBlocking {
        CredentialRegistry.register(mockService)
        val credential = createTestCredential()
        
        val issued = CredentialRegistry.issue(
            credential = credential,
            options = CredentialIssuanceOptions(providerName = "test-provider")
        )
        
        assertNotNull(issued.proof)
    }

    @Test
    fun `test issue credential fails when no service registered`() = runBlocking {
        val credential = createTestCredential()
        
        assertFailsWith<IllegalArgumentException> {
            CredentialRegistry.issue(credential)
        }
    }

    @Test
    fun `test verify credential through registry`() = runBlocking {
        CredentialRegistry.register(mockService)
        val credential = createTestCredential(
            proof = io.geoknoesis.vericore.credential.models.Proof(
                type = "Ed25519Signature2020",
                created = java.time.Instant.now().toString(),
                verificationMethod = "did:key:test#key-1",
                proofPurpose = "assertionMethod"
            )
        )
        
        val result = CredentialRegistry.verify(
            credential = credential,
            options = CredentialVerificationOptions(providerName = "test-provider")
        )
        
        assertTrue(result.valid)
    }

    @Test
    fun `test verify credential fails when no service registered`() = runBlocking {
        val credential = createTestCredential()
        
        assertFailsWith<IllegalArgumentException> {
            CredentialRegistry.verify(credential)
        }
    }

    @Test
    fun `test create presentation through registry`() = runBlocking {
        CredentialRegistry.register(mockService)
        val credentials = listOf(createTestCredential())
        
        val presentation = CredentialRegistry.createPresentation(
            credentials = credentials,
            options = PresentationOptions(holderDid = "did:key:holder")
        )
        
        assertNotNull(presentation)
        assertEquals(1, presentation.verifiableCredential.size)
    }

    @Test
    fun `test create presentation fails when no service registered`() = runBlocking {
        val credentials = listOf(createTestCredential())
        
        assertFailsWith<IllegalArgumentException> {
            CredentialRegistry.createPresentation(
                credentials = credentials,
                options = PresentationOptions(holderDid = "did:key:holder")
            )
        }
    }

    @Test
    fun `test verify presentation through registry`() = runBlocking {
        CredentialRegistry.register(mockService)
        val presentation = VerifiablePresentation(
            id = "vp-1",
            type = listOf("VerifiablePresentation"),
            verifiableCredential = listOf(createTestCredential()),
            holder = "did:key:holder",
            proof = null
        )
        
        val result = CredentialRegistry.verifyPresentation(
            presentation = presentation,
            options = PresentationVerificationOptions(providerName = "test-provider")
        )
        
        assertTrue(result.valid)
    }

    @Test
    fun `test verify presentation fails when no service registered`() = runBlocking {
        val presentation = VerifiablePresentation(
            id = "vp-1",
            type = listOf("VerifiablePresentation"),
            verifiableCredential = emptyList(),
            holder = "did:key:holder",
            proof = null
        )
        
        assertFailsWith<IllegalArgumentException> {
            CredentialRegistry.verifyPresentation(presentation)
        }
    }

    @Test
    fun `test get default service when multiple registered`() = runBlocking {
        val service1 = mockService
        val service2 = object : CredentialService {
            override val providerName = "provider-2"
            override val supportedProofTypes: List<String> = emptyList()
            override val supportedSchemaFormats: List<io.geoknoesis.vericore.spi.SchemaFormat> = emptyList()
            override suspend fun issueCredential(credential: VerifiableCredential, options: CredentialIssuanceOptions) = credential
            override suspend fun verifyCredential(credential: VerifiableCredential, options: CredentialVerificationOptions) = CredentialVerificationResult(valid = true)
            override suspend fun createPresentation(credentials: List<VerifiableCredential>, options: PresentationOptions) = VerifiablePresentation(id = "vp", type = listOf("VP"), verifiableCredential = credentials, holder = "")
            override suspend fun verifyPresentation(presentation: VerifiablePresentation, options: PresentationVerificationOptions) = PresentationVerificationResult(valid = true)
        }
        
        CredentialRegistry.register(service1)
        CredentialRegistry.register(service2)
        
        val default = CredentialRegistry.get()
        
        // Should return first registered (service1)
        assertNotNull(default)
    }

    private fun createTestCredential(
        id: String? = "https://example.com/credentials/1",
        types: List<String> = listOf("VerifiableCredential", "PersonCredential"),
        issuerDid: String = "did:key:issuer",
        subject: JsonObject = buildJsonObject {
            put("id", "did:key:subject")
            put("name", "John Doe")
        },
        issuanceDate: String = java.time.Instant.now().toString(),
        proof: io.geoknoesis.vericore.credential.models.Proof? = null
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = types,
            issuer = issuerDid,
            credentialSubject = subject,
            issuanceDate = issuanceDate,
            proof = proof
        )
    }
}

