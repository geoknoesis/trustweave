package io.geoknoesis.vericore.credential

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.models.VerifiablePresentation
import io.geoknoesis.vericore.spi.SchemaFormat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Branch coverage tests for CredentialRegistry.
 */
class CredentialRegistryBranchCoverageTest {

    @BeforeEach
    fun setup() {
        CredentialRegistry.clear()
    }

    @AfterEach
    fun cleanup() {
        CredentialRegistry.clear()
    }

    @Test
    fun `test CredentialRegistry register service`() {
        val service = createMockCredentialService("test-provider")
        CredentialRegistry.register(service)
        
        assertNotNull(CredentialRegistry.get("test-provider"))
    }

    @Test
    fun `test CredentialRegistry unregister service`() {
        val service = createMockCredentialService("test-provider")
        CredentialRegistry.register(service)
        assertTrue(CredentialRegistry.isRegistered("test-provider"))
        
        CredentialRegistry.unregister("test-provider")
        
        assertFalse(CredentialRegistry.isRegistered("test-provider"))
        assertNull(CredentialRegistry.get("test-provider"))
    }

    @Test
    fun `test CredentialRegistry get with provider name`() {
        val service1 = createMockCredentialService("provider1")
        val service2 = createMockCredentialService("provider2")
        CredentialRegistry.register(service1)
        CredentialRegistry.register(service2)
        
        val retrieved = CredentialRegistry.get("provider1")
        
        assertNotNull(retrieved)
        assertEquals("provider1", retrieved?.providerName)
    }

    @Test
    fun `test CredentialRegistry get with null returns first`() {
        val service1 = createMockCredentialService("provider1")
        val service2 = createMockCredentialService("provider2")
        CredentialRegistry.register(service1)
        CredentialRegistry.register(service2)
        
        val retrieved = CredentialRegistry.get(null)
        
        assertNotNull(retrieved)
        // Should return first registered
    }

    @Test
    fun `test CredentialRegistry get returns null for unregistered`() {
        assertNull(CredentialRegistry.get("nonexistent"))
    }

    @Test
    fun `test CredentialRegistry get returns null when empty`() {
        assertNull(CredentialRegistry.get(null))
    }

    @Test
    fun `test CredentialRegistry getAll returns all services`() {
        val service1 = createMockCredentialService("provider1")
        val service2 = createMockCredentialService("provider2")
        CredentialRegistry.register(service1)
        CredentialRegistry.register(service2)
        
        val all = CredentialRegistry.getAll()
        
        assertEquals(2, all.size)
        assertTrue(all.containsKey("provider1"))
        assertTrue(all.containsKey("provider2"))
    }

    @Test
    fun `test CredentialRegistry getAll returns empty when empty`() {
        val all = CredentialRegistry.getAll()
        assertTrue(all.isEmpty())
    }

    @Test
    fun `test CredentialRegistry isRegistered returns true for registered`() {
        val service = createMockCredentialService("test-provider")
        CredentialRegistry.register(service)
        
        assertTrue(CredentialRegistry.isRegistered("test-provider"))
    }

    @Test
    fun `test CredentialRegistry isRegistered returns false for unregistered`() {
        assertFalse(CredentialRegistry.isRegistered("nonexistent"))
    }

    @Test
    fun `test CredentialRegistry issue with provider name`() = runBlocking {
        val service = createMockCredentialService("test-provider")
        CredentialRegistry.register(service)
        
        val credential = createTestCredential()
        val options = CredentialIssuanceOptions(providerName = "test-provider")
        
        val result = CredentialRegistry.issue(credential, options)
        
        assertNotNull(result)
    }

    @Test
    fun `test CredentialRegistry issue without provider name uses default`() = runBlocking {
        val service = createMockCredentialService("test-provider")
        CredentialRegistry.register(service)
        
        val credential = createTestCredential()
        val options = CredentialIssuanceOptions()
        
        val result = CredentialRegistry.issue(credential, options)
        
        assertNotNull(result)
    }

    @Test
    fun `test CredentialRegistry issue throws when no service registered`() = runBlocking {
        val credential = createTestCredential()
        val options = CredentialIssuanceOptions()
        
        assertFailsWith<IllegalArgumentException> {
            CredentialRegistry.issue(credential, options)
        }
    }

    @Test
    fun `test CredentialRegistry issue throws when provider not found`() = runBlocking {
        val service = createMockCredentialService("provider1")
        CredentialRegistry.register(service)
        
        val credential = createTestCredential()
        val options = CredentialIssuanceOptions(providerName = "nonexistent")
        
        assertFailsWith<IllegalArgumentException> {
            CredentialRegistry.issue(credential, options)
        }
    }

    @Test
    fun `test CredentialRegistry verify with provider name`() = runBlocking {
        val service = createMockCredentialService("test-provider")
        CredentialRegistry.register(service)
        
        val credential = createTestCredential()
        val options = CredentialVerificationOptions(providerName = "test-provider")
        
        val result = CredentialRegistry.verify(credential, options)
        
        assertNotNull(result)
    }

    @Test
    fun `test CredentialRegistry verify without provider name uses default`() = runBlocking {
        val service = createMockCredentialService("test-provider")
        CredentialRegistry.register(service)
        
        val credential = createTestCredential()
        val options = CredentialVerificationOptions()
        
        val result = CredentialRegistry.verify(credential, options)
        
        assertNotNull(result)
    }

    @Test
    fun `test CredentialRegistry verify throws when no service registered`() = runBlocking {
        val credential = createTestCredential()
        val options = CredentialVerificationOptions()
        
        assertFailsWith<IllegalArgumentException> {
            CredentialRegistry.verify(credential, options)
        }
    }

    @Test
    fun `test CredentialRegistry verify throws when provider not found`() = runBlocking {
        val service = createMockCredentialService("provider1")
        CredentialRegistry.register(service)
        
        val credential = createTestCredential()
        val options = CredentialVerificationOptions(providerName = "nonexistent")
        
        assertFailsWith<IllegalArgumentException> {
            CredentialRegistry.verify(credential, options)
        }
    }

    @Test
    fun `test CredentialRegistry createPresentation uses default service`() = runBlocking {
        val service = createMockCredentialService("test-provider")
        CredentialRegistry.register(service)
        
        val credentials = listOf(createTestCredential())
        val options = PresentationOptions(
            holderDid = "did:key:holder",
            challenge = "challenge-123"
        )
        
        val result = CredentialRegistry.createPresentation(credentials, options)
        
        assertNotNull(result)
    }

    @Test
    fun `test CredentialRegistry createPresentation throws when no service registered`() = runBlocking {
        val credentials = listOf(createTestCredential())
        val options = PresentationOptions(
            holderDid = "did:key:holder",
            challenge = "challenge-123"
        )
        
        assertFailsWith<IllegalArgumentException> {
            CredentialRegistry.createPresentation(credentials, options)
        }
    }

    @Test
    fun `test CredentialRegistry verifyPresentation with provider name`() = runBlocking {
        val service = createMockCredentialService("test-provider")
        CredentialRegistry.register(service)
        
        val presentation = createTestPresentation()
        val options = PresentationVerificationOptions(providerName = "test-provider")
        
        val result = CredentialRegistry.verifyPresentation(presentation, options)
        
        assertNotNull(result)
    }

    @Test
    fun `test CredentialRegistry verifyPresentation without provider name uses default`() = runBlocking {
        val service = createMockCredentialService("test-provider")
        CredentialRegistry.register(service)
        
        val presentation = createTestPresentation()
        val options = PresentationVerificationOptions()
        
        val result = CredentialRegistry.verifyPresentation(presentation, options)
        
        assertNotNull(result)
    }

    @Test
    fun `test CredentialRegistry verifyPresentation throws when no service registered`() = runBlocking {
        val presentation = createTestPresentation()
        val options = PresentationVerificationOptions()
        
        assertFailsWith<IllegalArgumentException> {
            CredentialRegistry.verifyPresentation(presentation, options)
        }
    }

    @Test
    fun `test CredentialRegistry verifyPresentation throws when provider not found`() = runBlocking {
        val service = createMockCredentialService("provider1")
        CredentialRegistry.register(service)
        
        val presentation = createTestPresentation()
        val options = PresentationVerificationOptions(providerName = "nonexistent")
        
        assertFailsWith<IllegalArgumentException> {
            CredentialRegistry.verifyPresentation(presentation, options)
        }
    }

    @Test
    fun `test CredentialRegistry clear removes all services`() {
        val service1 = createMockCredentialService("provider1")
        val service2 = createMockCredentialService("provider2")
        CredentialRegistry.register(service1)
        CredentialRegistry.register(service2)
        
        assertEquals(2, CredentialRegistry.getAll().size)
        
        CredentialRegistry.clear()
        
        assertEquals(0, CredentialRegistry.getAll().size)
    }

    private fun createMockCredentialService(providerName: String): CredentialService {
        return object : CredentialService {
            override val providerName: String = providerName
            override val supportedProofTypes: List<String> = listOf("Ed25519Signature2020")
            override val supportedSchemaFormats: List<io.geoknoesis.vericore.spi.SchemaFormat> = listOf(SchemaFormat.JSON_SCHEMA)

            override suspend fun issueCredential(
                credential: VerifiableCredential,
                options: CredentialIssuanceOptions
            ): VerifiableCredential {
                return credential.copy(
                    proof = io.geoknoesis.vericore.credential.models.Proof(
                        type = "Ed25519Signature2020",
                        created = java.time.Instant.now().toString(),
                        verificationMethod = "did:key:issuer#key-1",
                        proofPurpose = "assertionMethod",
                        proofValue = "mock-proof"
                    )
                )
            }

            override suspend fun verifyCredential(
                credential: VerifiableCredential,
                options: CredentialVerificationOptions
            ): CredentialVerificationResult {
                return CredentialVerificationResult(
                    valid = true,
                    errors = emptyList(),
                    warnings = emptyList()
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
                    valid = true,
                    errors = emptyList(),
                    warnings = emptyList()
                )
            }
        }
    }

    private fun createTestCredential(): VerifiableCredential {
        return VerifiableCredential(
            type = listOf("VerifiableCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject {
                put("id", "did:key:subject")
            },
            issuanceDate = java.time.Instant.now().toString()
        )
    }

    private fun createTestPresentation(): VerifiablePresentation {
        return VerifiablePresentation(
            type = listOf("VerifiablePresentation"),
            verifiableCredential = listOf(createTestCredential()),
            holder = "did:key:holder"
        )
    }
}

