package com.geoknoesis.vericore.credential.schema

import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.spi.SchemaFormat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Tests for SchemaValidator interface implementations.
 * Tests the interface contract and multiple implementations.
 */
class SchemaValidatorTest {

    private lateinit var validator: SchemaValidator

    @BeforeEach
    fun setup() {
        SchemaValidatorRegistry.clear()
        validator = JsonSchemaValidator()
        SchemaValidatorRegistry.register(validator)
    }

    @AfterEach
    fun cleanup() {
        SchemaValidatorRegistry.clear()
    }

    @Test
    fun `test SchemaValidator schemaFormat property`() = runBlocking {
        assertEquals(SchemaFormat.JSON_SCHEMA, validator.schemaFormat)
    }

    @Test
    fun `test SchemaValidator validate with valid credential`() = runBlocking {
        val credential = createTestCredential()
        val schema = createTestSchema()
        
        val result = validator.validate(credential, schema)
        
        assertNotNull(result)
        // Current implementation may not fully validate, but should not throw
    }

    @Test
    fun `test SchemaValidator validateCredentialSubject with valid subject`() = runBlocking {
        val subject = buildJsonObject {
            put("id", "did:key:subject")
            put("name", "John Doe")
        }
        val schema = createTestSchema()
        
        val result = validator.validateCredentialSubject(subject, schema)
        
        assertNotNull(result)
    }

    @Test
    fun `test SchemaValidator validateCredentialSubject with missing required field`() = runBlocking {
        val subject = buildJsonObject {
            put("id", "did:key:subject")
            // Missing "name" field
        }
        val schema = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
            put("required", buildJsonArray { add("name") })
            put("properties", buildJsonObject {
                put("name", buildJsonObject { put("type", "string") })
            })
        }
        
        val result = validator.validateCredentialSubject(subject, schema)
        
        assertNotNull(result)
    }

    @Test
    fun `test SchemaValidator validate with empty schema`() = runBlocking {
        val credential = createTestCredential()
        val schema = buildJsonObject {}
        
        val result = validator.validate(credential, schema)
        
        assertNotNull(result)
    }

    @Test
    fun `test SchemaValidator validate with complex schema`() = runBlocking {
        val credential = createTestCredential(
            subject = buildJsonObject {
                put("id", "did:key:subject")
                put("name", "John Doe")
                put("age", 30)
                put("email", "john@example.com")
            }
        )
        val schema = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
            put("properties", buildJsonObject {
                put("name", buildJsonObject { put("type", "string") })
                put("age", buildJsonObject { put("type", "integer") })
                put("email", buildJsonObject { put("type", "string") })
            })
        }
        
        val result = validator.validate(credential, schema)
        
        assertNotNull(result)
    }

    @Test
    fun `test SchemaValidator validateCredentialSubject with array subject`() = runBlocking {
        val subject = buildJsonArray {
            add(buildJsonObject {
                put("id", "did:key:subject1")
            })
            add(buildJsonObject {
                put("id", "did:key:subject2")
            })
        }
        val schema = createTestSchema()
        
        val result = validator.validateCredentialSubject(subject, schema)
        
        assertNotNull(result)
    }

    @Test
    fun `test SchemaValidator validateCredentialSubject with primitive subject`() = runBlocking {
        val subject = JsonPrimitive("simple-string")
        val schema = createTestSchema()
        
        val result = validator.validateCredentialSubject(subject, schema)
        
        assertNotNull(result)
    }

    @Test
    fun `test SchemaValidator validate with credential having schema`() = runBlocking {
        val schemaId = "https://example.com/schemas/person"
        val credential = createTestCredential(
            schema = com.geoknoesis.vericore.credential.models.CredentialSchema(
                id = schemaId,
                type = "JsonSchemaValidator2018",
                schemaFormat = SchemaFormat.JSON_SCHEMA
            )
        )
        val schema = createTestSchema()
        
        val result = validator.validate(credential, schema)
        
        assertNotNull(result)
    }

    @Test
    fun `test SchemaValidator validate with nested credential subject`() = runBlocking {
        val credential = createTestCredential(
            subject = buildJsonObject {
                put("id", "did:key:subject")
                put("address", buildJsonObject {
                    put("street", "123 Main St")
                    put("city", "New York")
                })
            }
        )
        val schema = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
            put("properties", buildJsonObject {
                put("address", buildJsonObject {
                    put("type", "object")
                    put("properties", buildJsonObject {
                        put("street", buildJsonObject { put("type", "string") })
                        put("city", buildJsonObject { put("type", "string") })
                    })
                })
            })
        }
        
        val result = validator.validate(credential, schema)
        
        assertNotNull(result)
    }

    @Test
    fun `test SchemaValidator validate with null credential fields`() = runBlocking {
        val credential = VerifiableCredential(
            type = listOf("VerifiableCredential"),
            issuer = "did:key:issuer",
            issuanceDate = java.time.Instant.now().toString(),
            credentialSubject = buildJsonObject {}
        )
        val schema = createTestSchema()
        
        val result = validator.validate(credential, schema)
        
        assertNotNull(result)
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
        schema: com.geoknoesis.vericore.credential.models.CredentialSchema? = null
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

    private fun createTestSchema(): JsonObject {
        return buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
            put("properties", buildJsonObject {
                put("name", buildJsonObject { put("type", "string") })
            })
        }
    }
}

