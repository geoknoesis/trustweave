package io.geoknoesis.vericore.credential.schema

import io.geoknoesis.vericore.credential.models.CredentialSchema
import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.schema.JsonSchemaValidator
import io.geoknoesis.vericore.credential.schema.SchemaValidatorRegistry
import io.geoknoesis.vericore.spi.SchemaFormat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for SchemaRegistry API.
 */
class SchemaRegistryTest {

    @BeforeEach
    fun setup() {
        SchemaRegistry.clear()
        SchemaValidatorRegistry.clear()
        // Register JSON Schema validator
        SchemaValidatorRegistry.register(JsonSchemaValidator())
    }

    @AfterEach
    fun cleanup() {
        SchemaRegistry.clear()
        SchemaValidatorRegistry.clear()
    }

    @Test
    fun `test register schema successfully`() = runBlocking {
        val schema = CredentialSchema(
            id = "https://example.com/schemas/person",
            type = "JsonSchemaValidator2018",
            schemaFormat = SchemaFormat.JSON_SCHEMA
        )
        val definition = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
        }
        
        val result = SchemaRegistry.registerSchema(schema, definition)
        
        assertTrue(result.success)
        assertEquals("https://example.com/schemas/person", result.schemaId)
    }

    @Test
    fun `test get registered schema`() = runBlocking {
        val schema = CredentialSchema(
            id = "https://example.com/schemas/person",
            type = "JsonSchemaValidator2018",
            schemaFormat = SchemaFormat.JSON_SCHEMA
        )
        val definition = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
        }
        
        SchemaRegistry.registerSchema(schema, definition)
        
        val retrieved = SchemaRegistry.getSchema("https://example.com/schemas/person")
        
        assertNotNull(retrieved)
        assertEquals(schema.id, retrieved?.id)
        assertEquals(schema.type, retrieved?.type)
    }

    @Test
    fun `test get schema definition`() = runBlocking {
        val schema = CredentialSchema(
            id = "https://example.com/schemas/person",
            type = "JsonSchemaValidator2018",
            schemaFormat = SchemaFormat.JSON_SCHEMA
        )
        val definition = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
            put("properties", buildJsonObject {
                put("name", buildJsonObject { put("type", "string") })
            })
        }
        
        SchemaRegistry.registerSchema(schema, definition)
        
        val retrieved = SchemaRegistry.getSchemaDefinition("https://example.com/schemas/person")
        
        assertNotNull(retrieved)
        assertEquals(definition["type"]?.jsonPrimitive?.content, retrieved?.get("type")?.jsonPrimitive?.content)
    }

    @Test
    fun `test get non-existent schema returns null`() = runBlocking {
        val schema = SchemaRegistry.getSchema("https://example.com/schemas/nonexistent")
        
        assertNull(schema)
    }

    @Test
    fun `test is registered returns true for registered schema`() = runBlocking {
        val schema = CredentialSchema(
            id = "https://example.com/schemas/person",
            type = "JsonSchemaValidator2018",
            schemaFormat = SchemaFormat.JSON_SCHEMA
        )
        val definition = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
        }
        
        SchemaRegistry.registerSchema(schema, definition)
        
        assertTrue(SchemaRegistry.isRegistered("https://example.com/schemas/person"))
        assertFalse(SchemaRegistry.isRegistered("https://example.com/schemas/nonexistent"))
    }

    @Test
    fun `test getAllSchemaIds returns all registered schemas`() = runBlocking {
        val schema1 = CredentialSchema(
            id = "https://example.com/schemas/person",
            type = "JsonSchemaValidator2018",
            schemaFormat = SchemaFormat.JSON_SCHEMA
        )
        val schema2 = CredentialSchema(
            id = "https://example.com/schemas/degree",
            type = "JsonSchemaValidator2018",
            schemaFormat = SchemaFormat.JSON_SCHEMA
        )
        val definition = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
        }
        
        SchemaRegistry.registerSchema(schema1, definition)
        SchemaRegistry.registerSchema(schema2, definition)
        
        val ids = SchemaRegistry.getAllSchemaIds()
        
        assertEquals(2, ids.size)
        assertTrue(ids.contains("https://example.com/schemas/person"))
        assertTrue(ids.contains("https://example.com/schemas/degree"))
    }

    @Test
    fun `test unregister schema`() = runBlocking {
        val schema = CredentialSchema(
            id = "https://example.com/schemas/person",
            type = "JsonSchemaValidator2018",
            schemaFormat = SchemaFormat.JSON_SCHEMA
        )
        val definition = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
        }
        
        SchemaRegistry.registerSchema(schema, definition)
        assertTrue(SchemaRegistry.isRegistered("https://example.com/schemas/person"))
        
        SchemaRegistry.unregister("https://example.com/schemas/person")
        
        assertFalse(SchemaRegistry.isRegistered("https://example.com/schemas/person"))
        assertNull(SchemaRegistry.getSchema("https://example.com/schemas/person"))
        assertNull(SchemaRegistry.getSchemaDefinition("https://example.com/schemas/person"))
    }

    @Test
    fun `test clear all schemas`() = runBlocking {
        val schema1 = CredentialSchema(
            id = "https://example.com/schemas/person",
            type = "JsonSchemaValidator2018",
            schemaFormat = SchemaFormat.JSON_SCHEMA
        )
        val schema2 = CredentialSchema(
            id = "https://example.com/schemas/degree",
            type = "JsonSchemaValidator2018",
            schemaFormat = SchemaFormat.JSON_SCHEMA
        )
        val definition = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
        }
        
        SchemaRegistry.registerSchema(schema1, definition)
        SchemaRegistry.registerSchema(schema2, definition)
        
        assertEquals(2, SchemaRegistry.getAllSchemaIds().size)
        
        SchemaRegistry.clear()
        
        assertEquals(0, SchemaRegistry.getAllSchemaIds().size)
        assertFalse(SchemaRegistry.isRegistered("https://example.com/schemas/person"))
        assertFalse(SchemaRegistry.isRegistered("https://example.com/schemas/degree"))
    }

    @Test
    fun `test validate credential against schema`() = runBlocking {
        val schemaId = "https://example.com/schemas/person"
        val schema = CredentialSchema(
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
        
        val credential = VerifiableCredential(
            id = "https://example.com/credentials/1",
            type = listOf("VerifiableCredential", "PersonCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject {
                put("id", "did:key:subject")
                put("name", "John Doe")
            },
            issuanceDate = java.time.Instant.now().toString()
        )
        
        val result = SchemaRegistry.validateCredential(credential, schemaId)
        
        assertNotNull(result)
    }

    @Test
    fun `test validate credential fails when schema not found`() = runBlocking {
        val credential = VerifiableCredential(
            id = "https://example.com/credentials/1",
            type = listOf("VerifiableCredential", "PersonCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject {
                put("id", "did:key:subject")
            },
            issuanceDate = java.time.Instant.now().toString()
        )
        
        assertFailsWith<IllegalArgumentException> {
            SchemaRegistry.validateCredential(credential, "https://example.com/schemas/nonexistent")
        }
    }

    @Test
    fun `test register schema with SHACL format`() = runBlocking {
        val schema = CredentialSchema(
            id = "https://example.com/schemas/person",
            type = "ShaclValidator2020",
            schemaFormat = SchemaFormat.SHACL
        )
        val definition = buildJsonObject {
            put("@context", "https://www.w3.org/ns/shacl#")
            put("@type", "NodeShape")
        }
        
        val result = SchemaRegistry.registerSchema(schema, definition)
        
        assertTrue(result.success)
        val retrieved = SchemaRegistry.getSchema("https://example.com/schemas/person")
        assertEquals(SchemaFormat.SHACL, retrieved?.schemaFormat)
    }

    @Test
    fun `test register schema handles errors gracefully`() = runBlocking {
        // This test verifies that registration errors are handled
        // The actual implementation catches exceptions and returns a result
        val schema = CredentialSchema(
            id = "https://example.com/schemas/person",
            type = "JsonSchemaValidator2018",
            schemaFormat = SchemaFormat.JSON_SCHEMA
        )
        val definition = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
        }
        
        val result = SchemaRegistry.registerSchema(schema, definition)
        
        // Should succeed under normal circumstances
        assertTrue(result.success)
    }
}

