package com.trustweave.credential.schema

import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.core.SchemaFormat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive branch coverage tests for SchemaValidatorRegistry.
 * Tests all conditional branches and code paths.
 */
class SchemaValidatorRegistryBranchCoverageTest {

    @BeforeEach
    fun setup() {
        SchemaValidatorRegistry.clear()
    }

    @Test
    fun `test branch register stores validator`() {
        val validator = JsonSchemaValidator()
        
        SchemaValidatorRegistry.register(validator)
        
        assertEquals(validator, SchemaValidatorRegistry.get(SchemaFormat.JSON_SCHEMA))
    }

    @Test
    fun `test branch unregister removes validator`() {
        val validator = JsonSchemaValidator()
        SchemaValidatorRegistry.register(validator)
        
        SchemaValidatorRegistry.unregister(SchemaFormat.JSON_SCHEMA)
        
        assertNull(SchemaValidatorRegistry.get(SchemaFormat.JSON_SCHEMA))
    }

    @Test
    fun `test branch get returns registered validator`() {
        val validator = JsonSchemaValidator()
        SchemaValidatorRegistry.register(validator)
        
        val retrieved = SchemaValidatorRegistry.get(SchemaFormat.JSON_SCHEMA)
        
        assertEquals(validator, retrieved)
    }

    @Test
    fun `test branch get returns null`() {
        val retrieved = SchemaValidatorRegistry.get(SchemaFormat.JSON_SCHEMA)
        
        assertNull(retrieved)
    }

    @Test
    fun `test branch validate with explicit format`() = runBlocking {
        val validator = JsonSchemaValidator()
        SchemaValidatorRegistry.register(validator)
        
        val credential = createTestCredential()
        val schema = createTestSchema()
        
        val result = SchemaValidatorRegistry.validate(credential, schema, SchemaFormat.JSON_SCHEMA)
        
        assertNotNull(result)
    }

    @Test
    fun `test branch validate with auto-detection`() = runBlocking {
        val validator = JsonSchemaValidator()
        SchemaValidatorRegistry.register(validator)
        
        val credential = createTestCredential()
        val schema = createTestSchema()
        
        val result = SchemaValidatorRegistry.validate(credential, schema)
        
        assertNotNull(result)
    }

    @Test
    fun `test branch validate throws when no validator`() = runBlocking {
        val credential = createTestCredential()
        val schema = createTestSchema()
        
        assertFailsWith<IllegalArgumentException> {
            SchemaValidatorRegistry.validate(credential, schema, SchemaFormat.JSON_SCHEMA)
        }
    }

    @Test
    fun `test branch validateCredentialSubject with explicit format`() = runBlocking {
        val validator = JsonSchemaValidator()
        SchemaValidatorRegistry.register(validator)
        
        val subject = buildJsonObject {
            put("id", "did:key:subject")
            put("name", "John Doe")
        }
        val schema = createTestSchema()
        
        val result = SchemaValidatorRegistry.validateCredentialSubject(subject, schema, SchemaFormat.JSON_SCHEMA)
        
        assertNotNull(result)
    }

    @Test
    fun `test branch validateCredentialSubject with auto-detection`() = runBlocking {
        val validator = JsonSchemaValidator()
        SchemaValidatorRegistry.register(validator)
        
        val subject = buildJsonObject {
            put("id", "did:key:subject")
            put("name", "John Doe")
        }
        val schema = createTestSchema()
        
        val result = SchemaValidatorRegistry.validateCredentialSubject(subject, schema)
        
        assertNotNull(result)
    }

    @Test
    fun `test branch detectSchemaFormat with SHACL context`() {
        val schema = buildJsonObject {
            put("@context", "http://www.w3.org/ns/shacl")
        }
        
        val format = SchemaValidatorRegistry.detectSchemaFormat(schema)
        
        assertEquals(SchemaFormat.SHACL, format)
    }

    @Test
    fun `test branch detectSchemaFormat with shacl in context`() {
        val schema = buildJsonObject {
            put("@context", buildJsonObject {
                put("sh", "http://www.w3.org/ns/shacl#")
            })
        }
        
        val format = SchemaValidatorRegistry.detectSchemaFormat(schema)
        
        assertEquals(SchemaFormat.SHACL, format)
    }

    @Test
    fun `test branch detectSchemaFormat with sh targetClass`() {
        val schema = buildJsonObject {
            put("sh:targetClass", "Person")
        }
        
        val format = SchemaValidatorRegistry.detectSchemaFormat(schema)
        
        assertEquals(SchemaFormat.SHACL, format)
    }

    @Test
    fun `test branch detectSchemaFormat with sh property`() {
        val schema = buildJsonObject {
            put("sh:property", buildJsonArray {})
        }
        
        val format = SchemaValidatorRegistry.detectSchemaFormat(schema)
        
        assertEquals(SchemaFormat.SHACL, format)
    }

    @Test
    fun `test branch detectSchemaFormat with sh node`() {
        val schema = buildJsonObject {
            put("sh:node", buildJsonObject {})
        }
        
        val format = SchemaValidatorRegistry.detectSchemaFormat(schema)
        
        assertEquals(SchemaFormat.SHACL, format)
    }

    @Test
    fun `test branch detectSchemaFormat with dollar schema`() {
        val schema = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
        }
        
        val format = SchemaValidatorRegistry.detectSchemaFormat(schema)
        
        assertEquals(SchemaFormat.JSON_SCHEMA, format)
    }

    @Test
    fun `test branch detectSchemaFormat with type`() {
        val schema = buildJsonObject {
            put("type", "object")
        }
        
        val format = SchemaValidatorRegistry.detectSchemaFormat(schema)
        
        assertEquals(SchemaFormat.JSON_SCHEMA, format)
    }

    @Test
    fun `test branch detectSchemaFormat with properties`() {
        val schema = buildJsonObject {
            put("properties", buildJsonObject {})
        }
        
        val format = SchemaValidatorRegistry.detectSchemaFormat(schema)
        
        assertEquals(SchemaFormat.JSON_SCHEMA, format)
    }

    @Test
    fun `test branch detectSchemaFormat defaults to JSON_SCHEMA`() {
        val schema = buildJsonObject {}
        
        val format = SchemaValidatorRegistry.detectSchemaFormat(schema)
        
        assertEquals(SchemaFormat.JSON_SCHEMA, format)
    }

    @Test
    fun `test branch hasValidator returns true`() {
        val validator = JsonSchemaValidator()
        SchemaValidatorRegistry.register(validator)
        
        assertTrue(SchemaValidatorRegistry.hasValidator(SchemaFormat.JSON_SCHEMA))
    }

    @Test
    fun `test branch hasValidator returns false`() {
        assertFalse(SchemaValidatorRegistry.hasValidator(SchemaFormat.JSON_SCHEMA))
    }

    @Test
    fun `test branch getRegisteredFormats returns formats`() {
        val validator = JsonSchemaValidator()
        SchemaValidatorRegistry.register(validator)
        
        val formats = SchemaValidatorRegistry.getRegisteredFormats()
        
        assertTrue(formats.contains(SchemaFormat.JSON_SCHEMA))
    }

    @Test
    fun `test branch clear removes all validators`() {
        val validator = JsonSchemaValidator()
        SchemaValidatorRegistry.register(validator)
        
        SchemaValidatorRegistry.clear()
        
        assertTrue(SchemaValidatorRegistry.getRegisteredFormats().isEmpty())
    }

    private fun createTestCredential(): VerifiableCredential {
        return VerifiableCredential(
            type = listOf("VerifiableCredential", "PersonCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject {
                put("id", "did:key:subject")
                put("name", "John Doe")
            },
            issuanceDate = java.time.Instant.now().toString()
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
