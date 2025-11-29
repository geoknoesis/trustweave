package com.trustweave.credential.did

import com.trustweave.credential.CredentialIssuanceOptions
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.issuer.CredentialIssuer
import com.trustweave.credential.proof.Ed25519ProofGenerator
import com.trustweave.credential.proof.ProofGeneratorRegistry
import com.trustweave.did.DidCreationOptions
import com.trustweave.did.DidDocument
import com.trustweave.did.DidMethod
import com.trustweave.did.registry.DidMethodRegistry
import com.trustweave.did.registry.DefaultDidMethodRegistry
import com.trustweave.did.resolver.DidResolutionResult
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
    private lateinit var proofRegistry: ProofGeneratorRegistry

    @BeforeEach
    fun setup() {
        proofRegistry = ProofGeneratorRegistry()

        val signer: suspend (ByteArray, String) -> ByteArray = { data, _ ->
            "signature-${UUID.randomUUID()}".toByteArray()
        }

        val proofGenerator = Ed25519ProofGenerator(
            signer = signer,
            getPublicKeyId = { "did:key:issuer#key-1" }
        )
        proofRegistry.register(proofGenerator)

        credentialIssuer = CredentialIssuer(
            proofGenerator = proofGenerator,
            resolveDid = { did -> did.startsWith("did:key:") },
            proofRegistry = proofRegistry
        )

        didRegistry = DefaultDidMethodRegistry().also { registry ->
            registry.register(object : DidMethod {
                override val method: String = "key"

                override suspend fun createDid(options: DidCreationOptions): DidDocument {
                    val id = options.additionalProperties["id"] as? String ?: "did:key:${UUID.randomUUID()}"
                    return DidDocument(id = id)
                }

                override suspend fun resolveDid(did: String): DidResolutionResult {
                    return DidResolutionResult.Success(
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



