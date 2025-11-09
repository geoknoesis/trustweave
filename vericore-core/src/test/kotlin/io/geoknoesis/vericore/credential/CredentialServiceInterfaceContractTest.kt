package io.geoknoesis.vericore.credential

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.models.VerifiablePresentation
import io.geoknoesis.vericore.spi.SchemaFormat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

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
        assertEquals("Ed25519Signature2020", issued.proof?.type)
    }

    @Test
    fun `test CredentialService verifyCredential returns verification result`() = runBlocking {
        val service = createMockService("test-provider")
        val credential = createTestCredential(
            proof = io.geoknoesis.vericore.credential.models.Proof(
                type = "Ed25519Signature2020",
                created = java.time.Instant.now().toString(),
                verificationMethod = "did:key:issuer#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "test-proof"
            )
        )
        val options = CredentialVerificationOptions()
        
        val result = service.verifyCredential(credential, options)
        
        assertNotNull(result)
        assertNotNull(result.valid)
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
        assertEquals("did:key:holder", presentation.holder)
    }

    @Test
    fun `test CredentialService verifyPresentation returns verification result`() = runBlocking {
        val service = createMockService("test-provider")
        val credential = createTestCredential()
        val presentation = VerifiablePresentation(
            id = "presentation-1",
            type = listOf("VerifiablePresentation"),
            verifiableCredential = listOf(credential),
            holder = "did:key:holder",
            proof = io.geoknoesis.vericore.credential.models.Proof(
                type = "Ed25519Signature2020",
                created = java.time.Instant.now().toString(),
                verificationMethod = "did:key:holder#key-1",
                proofPurpose = "authentication",
                proofValue = "test-proof"
            )
        )
        val options = PresentationVerificationOptions()
        
        val result = service.verifyPresentation(presentation, options)
        
        assertNotNull(result)
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
        assertEquals("challenge-123", issued.proof?.challenge)
        assertEquals("example.com", issued.proof?.domain)
    }

    @Test
    fun `test CredentialService verifyCredential with all options enabled`() = runBlocking {
        val service = createMockService("test-provider")
        val credential = createTestCredential(
            proof = io.geoknoesis.vericore.credential.models.Proof(
                type = "Ed25519Signature2020",
                created = java.time.Instant.now().toString(),
                verificationMethod = "did:key:issuer#key-1",
                proofPurpose = "assertionMethod",
                proofValue = "test-proof"
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
                return credential.copy(
                    proof = io.geoknoesis.vericore.credential.models.Proof(
                        type = options.proofType,
                        created = java.time.Instant.now().toString(),
                        verificationMethod = options.keyId ?: "did:key:issuer#key-1",
                        proofPurpose = "assertionMethod",
                        proofValue = "test-proof",
                        challenge = options.challenge,
                        domain = options.domain
                    )
                )
            }
            
            override suspend fun verifyCredential(
                credential: VerifiableCredential,
                options: CredentialVerificationOptions
            ): CredentialVerificationResult {
                val proofValid = credential.proof != null
                val notExpired = credential.expirationDate?.let {
                    try {
                        java.time.Instant.parse(it).isAfter(java.time.Instant.now())
                    } catch (e: Exception) {
                        true
                    }
                } ?: true
                val notRevoked = credential.credentialStatus == null
                
                return CredentialVerificationResult(
                    valid = proofValid && notExpired && notRevoked,
                    proofValid = proofValid,
                    notExpired = notExpired,
                    notRevoked = notRevoked
                )
            }
            
            override suspend fun createPresentation(
                credentials: List<VerifiableCredential>,
                options: PresentationOptions
            ): VerifiablePresentation {
                return VerifiablePresentation(
                    id = java.util.UUID.randomUUID().toString(),
                    type = listOf("VerifiablePresentation"),
                    verifiableCredential = credentials,
                    holder = options.holderDid,
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


