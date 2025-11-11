package com.geoknoesis.vericore.credential.issuer

import com.geoknoesis.vericore.credential.CredentialIssuanceOptions
import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.credential.proof.Ed25519ProofGenerator
import com.geoknoesis.vericore.credential.proof.ProofGeneratorRegistry
import com.geoknoesis.vericore.credential.schema.JsonSchemaValidator
import com.geoknoesis.vericore.credential.schema.SchemaRegistry
import com.geoknoesis.vericore.credential.schema.SchemaValidatorRegistry
import com.geoknoesis.vericore.spi.SchemaFormat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.*

/**
 * Additional comprehensive branch coverage tests for CredentialIssuer.
 * Covers additional branches not covered in CredentialIssuerBranchCoverageTest.
 */
class CredentialIssuerAdditionalBranchesTest {

    private lateinit var issuer: CredentialIssuer
    private val issuerDid = "did:key:issuer123"
    private lateinit var proofRegistry: ProofGeneratorRegistry

    @BeforeEach
    fun setup() {
        proofRegistry = ProofGeneratorRegistry()
        SchemaRegistry.clear()
        SchemaValidatorRegistry.clear()
        SchemaValidatorRegistry.register(JsonSchemaValidator())
        
        val signer: suspend (ByteArray, String) -> ByteArray = { data, _ ->
            "mock-signature-${UUID.randomUUID()}".toByteArray()
        }
        
        val proofGenerator = Ed25519ProofGenerator(
            signer = signer,
            getPublicKeyId = { "did:key:issuer123#key-1" }
        )
        proofRegistry.register(proofGenerator)
        
        issuer = CredentialIssuer(
            proofGenerator = proofGenerator,
            resolveDid = { did -> did == issuerDid },
            proofRegistry = proofRegistry
        )
    }

    @AfterEach
    fun cleanup() {
        proofRegistry.clear()
        SchemaRegistry.clear()
        SchemaValidatorRegistry.clear()
    }

    // ========== Options Branches ==========

    @Test
    fun `test issue with options containing proofType`() = runBlocking {
        val credential = createTestCredential()
        val options = CredentialIssuanceOptions(proofType = "Ed25519Signature2020")
        
        val result = issuer.issue(credential, issuerDid, "key-1", options)
        
        assertNotNull(result.proof)
        assertEquals("Ed25519Signature2020", result.proof?.type)
    }

    @Test
    fun `test issue with options containing challenge`() = runBlocking {
        val credential = createTestCredential()
        val options = CredentialIssuanceOptions(challenge = "challenge-123")
        
        val result = issuer.issue(credential, issuerDid, "key-1", options)
        
        assertEquals("challenge-123", result.proof?.challenge)
    }

    @Test
    fun `test issue with options containing domain`() = runBlocking {
        val credential = createTestCredential()
        val options = CredentialIssuanceOptions(domain = "example.com")
        
        val result = issuer.issue(credential, issuerDid, "key-1", options)
        
        assertEquals("example.com", result.proof?.domain)
    }

    @Test
    fun `test issue with options containing all fields`() = runBlocking {
        val credential = createTestCredential()
        val options = CredentialIssuanceOptions(
            proofType = "Ed25519Signature2020",
            challenge = "challenge-123",
            domain = "example.com"
        )
        
        val result = issuer.issue(credential, issuerDid, "key-1", options)
        
        assertNotNull(result.proof)
        assertEquals("Ed25519Signature2020", result.proof?.type)
        assertEquals("challenge-123", result.proof?.challenge)
        assertEquals("example.com", result.proof?.domain)
    }

    // ========== Credential Field Branches ==========

    @Test
    fun `test issue with credential having expirationDate`() = runBlocking {
        val credential = createTestCredential(
            expirationDate = Instant.now().plusSeconds(86400).toString()
        )
        
        val result = issuer.issue(credential, issuerDid, "key-1")
        
        assertNotNull(result.proof)
        assertEquals(credential.expirationDate, result.expirationDate)
    }

    @Test
    fun `test issue with credential having credentialStatus`() = runBlocking {
        val credential = createTestCredential(
            credentialStatus = com.geoknoesis.vericore.credential.models.CredentialStatus(
                id = "https://example.com/status/1",
                type = "StatusList2021Entry",
                statusListIndex = "1",
                statusListCredential = "https://example.com/status/1"
            )
        )
        
        val result = issuer.issue(credential, issuerDid, "key-1")
        
        assertNotNull(result.proof)
        assertNotNull(result.credentialStatus)
    }

    @Test
    fun `test issue with credential having evidence`() = runBlocking {
        val credential = createTestCredential(
            evidence = listOf(
                com.geoknoesis.vericore.credential.models.Evidence(
                    id = "evidence-1",
                    type = listOf("DocumentVerification"),
                    evidenceDocument = kotlinx.serialization.json.JsonPrimitive("https://example.com/evidence/1")
                )
            )
        )
        
        val result = issuer.issue(credential, issuerDid, "key-1")
        
        assertNotNull(result.proof)
        assertNotNull(result.evidence)
        assertEquals(1, result.evidence?.size)
    }

    @Test
    fun `test issue with credential having termsOfUse`() = runBlocking {
        val termsOfUse = com.geoknoesis.vericore.credential.models.TermsOfUse(
            id = "terms-1",
            type = "IssuerPolicy",
            termsOfUse = kotlinx.serialization.json.buildJsonObject {
                put("profile", "https://example.com/terms")
            }
        )
        val credential = createTestCredential(
            termsOfUse = termsOfUse
        )
        
        val result = issuer.issue(credential, issuerDid, "key-1")
        
        assertNotNull(result.proof)
        assertNotNull(result.termsOfUse)
    }

    @Test
    fun `test issue with credential having refreshService`() = runBlocking {
        val credential = createTestCredential(
            refreshService = com.geoknoesis.vericore.credential.models.RefreshService(
                id = "refresh-1",
                type = "CredentialRefreshService2020",
                serviceEndpoint = "https://example.com/refresh"
            )
        )
        
        val result = issuer.issue(credential, issuerDid, "key-1")
        
        assertNotNull(result.proof)
        assertNotNull(result.refreshService)
    }

    // ========== DID Resolution Branches ==========

    @Test
    fun `test issue with resolveDid returning false`() = runBlocking {
        val issuerWithFailedDidResolution = CredentialIssuer(
            proofGenerator = proofRegistry.get("Ed25519Signature2020")!!,
            resolveDid = { false },
            proofRegistry = proofRegistry
        )
        
        val credential = createTestCredential()
        
        // Should still issue, but DID resolution might affect validation
        val result = issuerWithFailedDidResolution.issue(credential, issuerDid, "key-1")
        
        assertNotNull(result.proof)
    }

    @Test
    fun `test issue with resolveDid throwing exception`() = runBlocking {
        val issuerWithThrowingDidResolution = CredentialIssuer(
            proofGenerator = proofRegistry.get("Ed25519Signature2020")!!,
            resolveDid = { throw RuntimeException("DID resolution failed") },
            proofRegistry = proofRegistry
        )
        
        val credential = createTestCredential()
        
        assertFailsWith<RuntimeException> {
            issuerWithThrowingDidResolution.issue(credential, issuerDid, "key-1")
        }
    }

    // ========== Proof Generator Branches ==========

    @Test
    fun `test issue with proofGenerator from registry`() = runBlocking {
        val issuerWithoutDirectGenerator = CredentialIssuer(
            proofGenerator = null,
            resolveDid = { did -> did == issuerDid },
            proofRegistry = proofRegistry
        )
        
        val credential = createTestCredential()
        
        // Should use registry
        val result = issuerWithoutDirectGenerator.issue(credential, issuerDid, "key-1")
        
        assertNotNull(result.proof)
    }

    @Test
    fun `test issue with proofGenerator not in registry`() = runBlocking {
        val emptyRegistry = ProofGeneratorRegistry()
        
        val issuerWithoutGenerator = CredentialIssuer(
            proofGenerator = null,
            resolveDid = { did -> did == issuerDid },
            proofRegistry = emptyRegistry
        )
        
        val credential = createTestCredential()
        
        assertFailsWith<IllegalArgumentException> {
            issuerWithoutGenerator.issue(credential, issuerDid, "key-1")
        }
    }

    // ========== Schema Validation Branches ==========

    @Test
    fun `test issue with schema validation returning warnings`() = runBlocking {
        val schemaId = "https://example.com/schemas/person"
        val schema = com.geoknoesis.vericore.credential.models.CredentialSchema(
            id = schemaId,
            type = "JsonSchemaValidator2018",
            schemaFormat = SchemaFormat.JSON_SCHEMA
        )
        val schemaDefinition = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
            put("properties", buildJsonObject {
                put("name", buildJsonObject {
                    put("type", "string")
                })
            })
        }
        
        runBlocking {
            SchemaRegistry.registerSchema(schema, schemaDefinition)
        }
        
        val credential = createTestCredential(
            schema = schema
        )
        
        val result = issuer.issue(credential, issuerDid, "key-1")
        
        assertNotNull(result.proof)
    }

    // ========== Helper Functions ==========

    private fun createTestCredential(
        id: String = "credential-${UUID.randomUUID()}",
        issuerDid: String = this.issuerDid,
        types: List<String> = listOf("VerifiableCredential", "PersonCredential"),
        credentialSubject: JsonObject = buildJsonObject {
            put("id", "did:key:subject123")
            put("name", "John Doe")
        },
        expirationDate: String? = null,
        credentialStatus: com.geoknoesis.vericore.credential.models.CredentialStatus? = null,
        evidence: List<com.geoknoesis.vericore.credential.models.Evidence>? = null,
        termsOfUse: com.geoknoesis.vericore.credential.models.TermsOfUse? = null,
        refreshService: com.geoknoesis.vericore.credential.models.RefreshService? = null,
        schema: com.geoknoesis.vericore.credential.models.CredentialSchema? = null
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = types,
            issuer = issuerDid,
            issuanceDate = Instant.now().toString(),
            expirationDate = expirationDate,
            credentialSubject = credentialSubject,
            credentialStatus = credentialStatus,
            evidence = evidence,
            termsOfUse = termsOfUse,
            refreshService = refreshService,
            credentialSchema = schema,
            proof = null
        )
    }
}

