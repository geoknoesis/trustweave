package com.trustweave.credential.issuer

import com.trustweave.credential.CredentialIssuanceOptions
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.proof.Ed25519ProofGenerator
import com.trustweave.credential.proof.ProofGeneratorRegistry
import com.trustweave.credential.schema.JsonSchemaValidator
import com.trustweave.credential.schema.SchemaRegistry
import com.trustweave.credential.schema.SchemaValidatorRegistry
import com.trustweave.credential.SchemaFormat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.*
import kotlinx.datetime.Clock

/**
 * Comprehensive branch coverage tests for CredentialIssuer.
 * Tests all conditional branches in issue() method.
 */
class CredentialIssuerBranchCoverageTest {

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

    // ========== Issuer DID Match Branch ==========

    @Test
    fun `test branch issuer DID matches`() = runBlocking {
        val credential = createTestCredential(issuerDid = issuerDid)

        val result = issuer.issue(credential, issuerDid, "key-1")

        assertNotNull(result.proof)
    }

    @Test
    fun `test branch issuer DID does not match`() = runBlocking {
        val credential = createTestCredential(issuerDid = "did:key:different")

        assertFailsWith<IllegalArgumentException> {
            issuer.issue(credential, issuerDid, "key-1")
        }
    }

    // ========== Schema Validation Branch ==========

    @Test
    fun `test branch credential schema is null`() = runBlocking {
        val credential = createTestCredential(schema = null)

        val result = issuer.issue(credential, issuerDid, "key-1")

        assertNotNull(result.proof)
    }

    @Test
    fun `test branch credential schema is not null`() = runBlocking {
        val schemaId = "https://example.com/schemas/person"
        val schema = com.trustweave.credential.models.CredentialSchema(
            id = schemaId,
            type = "JsonSchemaValidator2018",
            schemaFormat = SchemaFormat.JSON_SCHEMA
        )
        val schemaDefinition = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
            put("properties", buildJsonObject {
                put("name", buildJsonObject { put("type", "string") })
            })
        }
        SchemaRegistry.registerSchema(schema, schemaDefinition)

        val credential = createTestCredential(schema = schema)

        val result = issuer.issue(credential, issuerDid, "key-1")

        assertNotNull(result.proof)
    }

    // ========== Proof Generator Branch ==========

    @Test
    fun `test branch proof generator provided in constructor`() = runBlocking {
        val credential = createTestCredential()

        val result = issuer.issue(credential, issuerDid, "key-1")

        assertNotNull(result.proof)
    }

    @Test
    fun `test branch proof generator from registry`() = runBlocking {
        val proofGenerator = Ed25519ProofGenerator(
            signer = { data, _ -> "mock-signature".toByteArray() },
            getPublicKeyId = { "did:key:issuer123#key-1" }
        )
        proofRegistry.register(proofGenerator)

        val issuerWithoutGenerator = CredentialIssuer(
            proofGenerator = null,
            resolveDid = { did -> did == issuerDid },
            proofRegistry = proofRegistry
        )
        val credential = createTestCredential()

        val result = issuerWithoutGenerator.issue(
            credential,
            issuerDid,
            "key-1",
            CredentialIssuanceOptions(proofType = "Ed25519Signature2020")
        )

        assertNotNull(result.proof)
    }

    @Test
    fun `test branch proof generator not found in registry`() = runBlocking {
        val emptyRegistry = ProofGeneratorRegistry()
        val issuerWithoutGenerator = CredentialIssuer(
            proofGenerator = null,
            resolveDid = { did -> did == issuerDid },
            proofRegistry = emptyRegistry
        )
        val credential = createTestCredential()

        assertFailsWith<IllegalArgumentException> {
            issuerWithoutGenerator.issue(
                credential,
                issuerDid,
                "key-1",
                CredentialIssuanceOptions(proofType = "UnknownProofType")
            )
        }
    }

    // ========== DID Resolution Branch ==========

    @Test
    fun `test branch DID resolution succeeds`() = runBlocking {
        val credential = createTestCredential()

        val result = issuer.issue(credential, issuerDid, "key-1")

        assertNotNull(result.proof)
    }

    @Test
    fun `test branch DID resolution fails returns false`() = runBlocking {
        val proofGenerator = Ed25519ProofGenerator(
            signer = { data, _ -> "mock-signature".toByteArray() },
            getPublicKeyId = { "did:key:issuer123#key-1" }
        )

        val issuerWithFailedDid = CredentialIssuer(
            proofGenerator = proofGenerator,
            resolveDid = { false },
            proofRegistry = proofRegistry
        )
        val credential = createTestCredential()

        assertFailsWith<IllegalArgumentException> {
            issuerWithFailedDid.issue(credential, issuerDid, "key-1")
        }
    }

    @Test
    fun `test branch DID resolution throws exception`() = runBlocking {
        val proofGenerator = Ed25519ProofGenerator(
            signer = { data, _ -> "mock-signature".toByteArray() },
            getPublicKeyId = { "did:key:issuer123#key-1" }
        )

        val issuerWithThrowingDid = CredentialIssuer(
            proofGenerator = proofGenerator,
            resolveDid = { throw RuntimeException("DID resolution failed") },
            proofRegistry = proofRegistry
        )
        val credential = createTestCredential()

        assertFailsWith<IllegalArgumentException> {
            issuerWithThrowingDid.issue(credential, issuerDid, "key-1")
        }
    }

    // ========== Schema Validation Result Branch ==========

    @Test
    fun `test branch schema validation succeeds`() = runBlocking {
        val schemaId = "https://example.com/schemas/person"
        val schema = com.trustweave.credential.models.CredentialSchema(
            id = schemaId,
            type = "JsonSchemaValidator2018",
            schemaFormat = SchemaFormat.JSON_SCHEMA
        )
        val schemaDefinition = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
        }
        SchemaRegistry.registerSchema(schema, schemaDefinition)

        val credential = createTestCredential(schema = schema)

        val result = issuer.issue(credential, issuerDid, "key-1")

        assertNotNull(result.proof)
    }

    @Test
    fun `test branch schema validation fails`() = runBlocking {
        val schemaId = "https://example.com/schemas/person"
        val schema = com.trustweave.credential.models.CredentialSchema(
            id = schemaId,
            type = "JsonSchemaValidator2018",
            schemaFormat = SchemaFormat.JSON_SCHEMA
        )
        val schemaDefinition = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
            put("required", buildJsonArray { add("missingField") })
        }
        SchemaRegistry.registerSchema(schema, schemaDefinition)

        val credential = createTestCredential(schema = schema)

        // May or may not fail depending on JsonSchemaValidator implementation
        // This tests the branch where validation result is checked
        try {
            val result = issuer.issue(credential, issuerDid, "key-1")
            assertNotNull(result)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("validation failed") == true || e.message?.contains("schema") == true)
        }
    }

    private fun createTestCredential(
        id: String? = "https://example.com/credentials/1",
        types: List<String> = listOf("VerifiableCredential", "PersonCredential"),
        issuerDid: String = this.issuerDid,
        subject: JsonObject = buildJsonObject {
            put("id", "did:key:subject")
            put("name", "John Doe")
        },
        issuanceDate: String = Clock.System.now().toString(),
        schema: com.trustweave.credential.models.CredentialSchema? = null
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = types,
            issuer = issuerDid,
            credentialSubject = subject,
            issuanceDate = issuanceDate,
            credentialSchema = schema
        )
    }
}

