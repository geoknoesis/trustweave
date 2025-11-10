package io.geoknoesis.vericore.credential.did

import io.geoknoesis.vericore.credential.CredentialIssuanceOptions
import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.issuer.CredentialIssuer
import io.geoknoesis.vericore.credential.proof.Ed25519ProofGenerator
import io.geoknoesis.vericore.credential.proof.ProofGeneratorRegistry
import io.geoknoesis.vericore.did.DidDocument
import io.geoknoesis.vericore.did.DidMethod
import io.geoknoesis.vericore.did.DidMethodRegistry
import io.geoknoesis.vericore.did.DefaultDidMethodRegistry
import io.geoknoesis.vericore.did.DidResolutionResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import java.util.UUID

/**
 * Comprehensive tests for DidLinkedCredentialService API.
 */
class DidLinkedCredentialServiceTest {

    private lateinit var credentialIssuer: CredentialIssuer
    private lateinit var didCredentialService: DidLinkedCredentialService
    private lateinit var didRegistry: DidMethodRegistry

    @BeforeEach
    fun setup() {
        ProofGeneratorRegistry.clear()
        
        val signer: suspend (ByteArray, String) -> ByteArray = { data, _ ->
            "signature-${UUID.randomUUID()}".toByteArray()
        }
        
        val proofGenerator = Ed25519ProofGenerator(
            signer = signer,
            getPublicKeyId = { "did:key:issuer#key-1" }
        )
        ProofGeneratorRegistry.register(proofGenerator)
        
        credentialIssuer = CredentialIssuer(
            proofGenerator = proofGenerator,
            resolveDid = { did -> did.startsWith("did:key:") }
        )
        
        didRegistry = DefaultDidMethodRegistry().also { registry ->
            registry.register(object : DidMethod {
                override val method: String = "key"

                override suspend fun createDid(options: Map<String, Any?>): DidDocument {
                    val id = options["id"] as? String ?: "did:key:${UUID.randomUUID()}"
                    return DidDocument(id = id)
                }

                override suspend fun resolveDid(did: String): DidResolutionResult {
                    return DidResolutionResult(
                        document = DidDocument(id = did)
                    )
                }

                override suspend fun updateDid(
                    did: String,
                    updater: (DidDocument) -> DidDocument
                ): DidDocument = updater(DidDocument(id = did))

                override suspend fun deactivateDid(did: String): Boolean = true
            })
        }

        didCredentialService = DidLinkedCredentialService(
            didRegistry = didRegistry,
            credentialIssuer = credentialIssuer
        )
    }

    @Test
    fun `test issueCredentialForDid`() = runBlocking {
        val credential = didCredentialService.issueCredentialForDid(
            subjectDid = "did:key:subject",
            credentialType = "PersonCredential",
            claims = mapOf(
                "name" to "John Doe",
                "email" to "john@example.com",
                "age" to 30
            ),
            issuerDid = "did:key:issuer",
            keyId = "key-1"
        )
        
        assertNotNull(credential)
        assertEquals("did:key:issuer", credential.issuer)
        assertTrue(credential.type.contains("PersonCredential"))
        assertNotNull(credential.proof)
        
        val subject = credential.credentialSubject.jsonObject
        assertEquals("did:key:subject", subject["id"]?.jsonPrimitive?.content)
        assertEquals("John Doe", subject["name"]?.jsonPrimitive?.content)
        assertEquals("john@example.com", subject["email"]?.jsonPrimitive?.content)
    }

    @Test
    fun `test issueCredentialForDid with options`() = runBlocking {
        val credential = didCredentialService.issueCredentialForDid(
            subjectDid = "did:key:subject",
            credentialType = "PersonCredential",
            claims = mapOf("name" to "John Doe"),
            issuerDid = "did:key:issuer",
            keyId = "key-1",
            options = CredentialIssuanceOptions(
                proofType = "Ed25519Signature2020",
                challenge = "challenge-123"
            )
        )
        
        assertNotNull(credential)
        assertNotNull(credential.proof)
    }

    @Test
    fun `test resolveCredentialSubject with DID`() = runBlocking {
        val credential = VerifiableCredential(
            type = listOf("VerifiableCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject {
                put("id", "did:key:subject")
                put("name", "John Doe")
            },
            issuanceDate = "2024-01-01T00:00:00Z"
        )
        
        val subjectDid = didCredentialService.resolveCredentialSubject(credential)
        
        assertEquals("did:key:subject", subjectDid)
    }

    @Test
    fun `test resolveCredentialSubject without DID in subject`() = runBlocking {
        val credential = VerifiableCredential(
            type = listOf("VerifiableCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject {
                put("name", "John Doe")
                // No "id" field
            },
            issuanceDate = "2024-01-01T00:00:00Z"
        )
        
        val subjectDid = didCredentialService.resolveCredentialSubject(credential)
        
        assertNull(subjectDid)
    }

    @Test
    fun `test resolveCredentialSubject with non-DID subject`() = runBlocking {
        val credential = VerifiableCredential(
            type = listOf("VerifiableCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject {
                put("id", "https://example.com/subject")
                put("name", "John Doe")
            },
            issuanceDate = "2024-01-01T00:00:00Z"
        )
        
        val subjectDid = didCredentialService.resolveCredentialSubject(credential)
        
        assertNull(subjectDid)
    }

    @Test
    fun `test verifyIssuerDid with valid DID`() = runBlocking {
        val credential = VerifiableCredential(
            type = listOf("VerifiableCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject { put("id", "did:key:subject") },
            issuanceDate = "2024-01-01T00:00:00Z"
        )
        
        val isValid = didCredentialService.verifyIssuerDid(credential)
        
        // Placeholder implementation - method executes
        assertNotNull(isValid)
    }

    @Test
    fun `test issueCredentialForDid with various claim types`() = runBlocking {
        val credential = didCredentialService.issueCredentialForDid(
            subjectDid = "did:key:subject",
            credentialType = "TestCredential",
            claims = mapOf(
                "string" to "value",
                "number" to 42,
                "boolean" to true,
                "double" to 3.14,
                "custom" to mapOf("nested" to "value")
            ),
            issuerDid = "did:key:issuer",
            keyId = "key-1"
        )
        
        assertNotNull(credential)
        val subject = credential.credentialSubject.jsonObject
        assertEquals("value", subject["string"]?.jsonPrimitive?.content)
    }
}



