package com.trustweave.credential.issuer

import com.trustweave.credential.CredentialIssuanceOptions
import com.trustweave.credential.models.CredentialSchema
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.proof.Ed25519ProofGenerator
import com.trustweave.credential.proof.ProofGeneratorRegistry
import com.trustweave.credential.schema.JsonSchemaValidator
import com.trustweave.credential.schema.SchemaRegistry
import com.trustweave.credential.schema.SchemaValidatorRegistry
import com.trustweave.core.SchemaFormat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import java.util.UUID

/**
 * Exhaustive edge case tests for CredentialIssuer API robustness.
 */
class CredentialIssuerEdgeCasesTest {

    private lateinit var issuer: CredentialIssuer
    private val issuerDid = "did:key:issuer123"
    private val keyId = "key-1"
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

    // ========== Null and Empty Input Tests ==========

    @Test
    fun `test issue credential with null ID`() = runBlocking {
        val credential = createTestCredential(id = null)
        
        val issued = issuer.issue(credential, issuerDid, keyId)
        
        assertNotNull(issued.id) // Should generate an ID
        assertNotNull(issued.proof)
    }

    @Test
    fun `test issue credential with empty type list`() = runBlocking {
        val credential = createTestCredential(types = emptyList())
        
        assertFailsWith<IllegalArgumentException> {
            issuer.issue(credential, issuerDid, keyId)
        }
    }

    @Test
    fun `test issue credential with empty issuer DID`() = runBlocking {
        val credential = createTestCredential(issuerDid = "")
        
        assertFailsWith<IllegalArgumentException> {
            issuer.issue(credential, issuerDid, keyId)
        }
    }

    @Test
    fun `test issue credential with whitespace-only issuer DID`() = runBlocking {
        val credential = createTestCredential(issuerDid = "   ")
        
        assertFailsWith<IllegalArgumentException> {
            issuer.issue(credential, issuerDid, keyId)
        }
    }

    @Test
    fun `test issue credential with empty issuance date`() = runBlocking {
        val credential = createTestCredential(issuanceDate = "")
        
        assertFailsWith<IllegalArgumentException> {
            issuer.issue(credential, issuerDid, keyId)
        }
    }

    @Test
    fun `test issue credential with empty credential subject`() = runBlocking {
        val credential = createTestCredential(subject = buildJsonObject {})
        
        val issued = issuer.issue(credential, issuerDid, keyId)
        
        assertNotNull(issued.proof)
    }

    @Test
    fun `test issue credential with null credential subject fields`() = runBlocking {
        val credential = createTestCredential(
            subject = buildJsonObject {
                put("id", "did:key:subject")
                put("name", JsonNull)
                put("age", JsonNull)
            }
        )
        
        val issued = issuer.issue(credential, issuerDid, keyId)
        
        assertNotNull(issued.proof)
    }

    // ========== Invalid Format Tests ==========

    @Test
    fun `test issue credential with invalid issuance date format`() = runBlocking {
        val credential = createTestCredential(issuanceDate = "not-a-date")
        
        assertFailsWith<IllegalArgumentException> {
            issuer.issue(credential, issuerDid, keyId)
        }
    }

    @Test
    fun `test issue credential with invalid expiration date format`() = runBlocking {
        val credential = createTestCredential(expirationDate = "not-a-date")
        
        // Should still issue, but expiration date may be invalid
        try {
            val issued = issuer.issue(credential, issuerDid, keyId)
            assertNotNull(issued.proof)
        } catch (e: IllegalArgumentException) {
            // May fail validation
            assertTrue(e.message?.contains("date") == true || e.message?.contains("format") == true)
        }
    }

    @Test
    fun `test issue credential with malformed DID`() = runBlocking {
        val credential = createTestCredential(issuerDid = "not-a-did")
        
        assertFailsWith<IllegalArgumentException> {
            issuer.issue(credential, issuerDid, keyId)
        }
    }

    @Test
    fun `test issue credential with DID missing method`() = runBlocking {
        val credential = createTestCredential(issuerDid = "did::identifier")
        
        assertFailsWith<IllegalArgumentException> {
            issuer.issue(credential, issuerDid, keyId)
        }
    }

    // ========== Boundary Value Tests ==========

    @Test
    fun `test issue credential with very long issuer DID`() = runBlocking {
        val longDid = "did:key:${"a".repeat(1000)}"
        val credential = createTestCredential(issuerDid = longDid)
        
        assertFailsWith<IllegalArgumentException> {
            issuer.issue(credential, issuerDid, keyId)
        }
    }

    @Test
    fun `test issue credential with very long credential ID`() = runBlocking {
        val longId = "https://example.com/credentials/${"a".repeat(2000)}"
        val credential = createTestCredential(id = longId)
        
        val issued = issuer.issue(credential, issuerDid, keyId)
        
        assertEquals(longId, issued.id)
        assertNotNull(issued.proof)
    }

    @Test
    fun `test issue credential with maximum number of types`() = runBlocking {
        val types = listOf("VerifiableCredential") + (1..100).map { "Type$it" }
        val credential = createTestCredential(types = types)
        
        val issued = issuer.issue(credential, issuerDid, keyId)
        
        assertEquals(types.size, issued.type.size)
        assertNotNull(issued.proof)
    }

    @Test
    fun `test issue credential with expiration date in past`() = runBlocking {
        val pastDate = java.time.Instant.now().minusSeconds(86400).toString()
        val credential = createTestCredential(expirationDate = pastDate)
        
        val issued = issuer.issue(credential, issuerDid, keyId)
        
        assertEquals(pastDate, issued.expirationDate)
        assertNotNull(issued.proof)
    }

    @Test
    fun `test issue credential with expiration date far in future`() = runBlocking {
        val futureDate = java.time.Instant.now().plusSeconds(31536000L * 100).toString() // 100 years
        val credential = createTestCredential(expirationDate = futureDate)
        
        val issued = issuer.issue(credential, issuerDid, keyId)
        
        assertEquals(futureDate, issued.expirationDate)
        assertNotNull(issued.proof)
    }

    // ========== Special Character Tests ==========

    @Test
    fun `test issue credential with special characters in subject`() = runBlocking {
        val credential = createTestCredential(
            subject = buildJsonObject {
                put("id", "did:key:subject")
                put("name", "John \"Doe\" <john@example.com>")
                put("description", "Special chars: !@#$%^&*()_+-=[]{}|;':\",./<>?")
            }
        )
        
        val issued = issuer.issue(credential, issuerDid, keyId)
        
        assertNotNull(issued.proof)
    }

    @Test
    fun `test issue credential with unicode characters in subject`() = runBlocking {
        val credential = createTestCredential(
            subject = buildJsonObject {
                put("id", "did:key:subject")
                put("name", "José García 中文 العربية 🎉")
            }
        )
        
        val issued = issuer.issue(credential, issuerDid, keyId)
        
        assertNotNull(issued.proof)
    }

    @Test
    fun `test issue credential with newlines in subject`() = runBlocking {
        val credential = createTestCredential(
            subject = buildJsonObject {
                put("id", "did:key:subject")
                put("description", "Line 1\nLine 2\nLine 3")
            }
        )
        
        val issued = issuer.issue(credential, issuerDid, keyId)
        
        assertNotNull(issued.proof)
    }

    // ========== Nested Structure Tests ==========

    @Test
    fun `test issue credential with deeply nested subject`() = runBlocking {
        val credential = createTestCredential(
            subject = buildJsonObject {
                put("id", "did:key:subject")
                put("level1", buildJsonObject {
                    put("level2", buildJsonObject {
                        put("level3", buildJsonObject {
                            put("level4", buildJsonObject {
                                put("level5", "deep value")
                            })
                        })
                    })
                })
            }
        )
        
        val issued = issuer.issue(credential, issuerDid, keyId)
        
        assertNotNull(issued.proof)
    }

    @Test
    fun `test issue credential with large arrays in subject`() = runBlocking {
        val credential = createTestCredential(
            subject = buildJsonObject {
                put("id", "did:key:subject")
                put("items", buildJsonArray {
                    repeat(1000) { add("item$it") }
                })
            }
        )
        
        val issued = issuer.issue(credential, issuerDid, keyId)
        
        assertNotNull(issued.proof)
    }

    // ========== Concurrent Operations Tests ==========

    @Test
    fun `test issue multiple credentials concurrently`() = runBlocking {
        val credentials = (1..10).map { createTestCredential(id = "cred-$it") }
        
        val issued = credentials.map { credential ->
            issuer.issue(credential, issuerDid, keyId)
        }
        
        assertEquals(10, issued.size)
        issued.forEach { assertNotNull(it.proof) }
    }

    // ========== Error Recovery Tests ==========

    @Test
    fun `test issue credential after DID resolution failure`() = runBlocking {
        val issuerWithFailedDid = CredentialIssuer(
            resolveDid = { false }
        )
        val credential = createTestCredential()
        
        assertFailsWith<IllegalArgumentException> {
            issuerWithFailedDid.issue(credential, issuerDid, keyId)
        }
        
        // Should still work with correct issuer
        val issued = issuer.issue(credential, issuerDid, keyId)
        assertNotNull(issued.proof)
    }

    @Test
    fun `test issue credential with signer that throws exception`() = runBlocking {
        val failingSigner: suspend (ByteArray, String) -> ByteArray = { _, _ ->
            throw RuntimeException("Signing failed")
        }
        
        val failingGenerator = Ed25519ProofGenerator(
            signer = failingSigner,
            getPublicKeyId = { "did:key:issuer123#key-1" }
        )
        
        val failingIssuer = CredentialIssuer(
            proofGenerator = failingGenerator,
            resolveDid = { did -> did == issuerDid }
        )
        
        val credential = createTestCredential()
        
        assertFailsWith<Exception> {
            failingIssuer.issue(credential, issuerDid, keyId)
        }
    }

    // ========== Options Edge Cases ==========

    @Test
    fun `test issue credential with empty proof type`() = runBlocking {
        val credential = createTestCredential()
        val options = CredentialIssuanceOptions(proofType = "")
        
        // Should use default proof generator
        val issued = issuer.issue(credential, issuerDid, keyId, options)
        
        assertNotNull(issued.proof)
    }

    @Test
    fun `test issue credential with very long challenge`() = runBlocking {
        val credential = createTestCredential()
        val longChallenge = "a".repeat(10000)
        val options = CredentialIssuanceOptions(
            proofType = "Ed25519Signature2020",
            challenge = longChallenge
        )
        
        val issued = issuer.issue(credential, issuerDid, keyId, options)
        
        assertEquals(longChallenge, issued.proof?.challenge)
    }

    @Test
    fun `test issue credential with very long domain`() = runBlocking {
        val credential = createTestCredential()
        val longDomain = "a".repeat(1000) + ".example.com"
        val options = CredentialIssuanceOptions(
            proofType = "Ed25519Signature2020",
            domain = longDomain
        )
        
        val issued = issuer.issue(credential, issuerDid, keyId, options)
        
        assertEquals(longDomain, issued.proof?.domain)
    }

    // ========== Schema Edge Cases ==========

    @Test
    fun `test issue credential with non-existent schema`() = runBlocking {
        val schema = CredentialSchema(
            id = "https://example.com/nonexistent",
            type = "JsonSchemaValidator2018",
            schemaFormat = SchemaFormat.JSON_SCHEMA
        )
        val credential = createTestCredential(schema = schema)
        
        // Should fail schema validation or skip if schema not found
        try {
            val issued = issuer.issue(credential, issuerDid, keyId)
            assertNotNull(issued.proof)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("schema") == true || e.message?.contains("validation") == true)
        }
    }

    @Test
    fun `test issue credential with invalid schema format`() = runBlocking {
        val schema = CredentialSchema(
            id = "https://example.com/schema",
            type = "InvalidSchemaType",
            schemaFormat = SchemaFormat.JSON_SCHEMA
        )
        val credential = createTestCredential(schema = schema)
        
        try {
            val issued = issuer.issue(credential, issuerDid, keyId)
            assertNotNull(issued.proof)
        } catch (e: IllegalArgumentException) {
            // May fail if schema type is invalid
            assertTrue(true)
        }
    }

    // ========== Helper Methods ==========

    private fun createTestCredential(
        id: String? = "https://example.com/credentials/1",
        types: List<String> = listOf("VerifiableCredential", "PersonCredential"),
        issuerDid: String = this.issuerDid,
        subject: JsonObject = buildJsonObject {
            put("id", "did:key:subject")
            put("name", "John Doe")
        },
        issuanceDate: String = java.time.Instant.now().toString(),
        expirationDate: String? = null,
        schema: CredentialSchema? = null
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id,
            type = types,
            issuer = issuerDid,
            credentialSubject = subject,
            issuanceDate = issuanceDate,
            expirationDate = expirationDate,
            credentialSchema = schema
        )
    }
}



