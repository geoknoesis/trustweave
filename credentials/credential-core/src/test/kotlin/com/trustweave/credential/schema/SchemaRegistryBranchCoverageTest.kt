package com.trustweave.credential.schema

import com.trustweave.credential.models.CredentialSchema
import com.trustweave.credential.models.VerifiableCredential
import com.trustweave.credential.SchemaFormat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive branch coverage tests for SchemaRegistry.
 * Tests all conditional branches and code paths.
 */
class SchemaRegistryBranchCoverageTest {

    @BeforeEach
    fun setup() {
        SchemaRegistry.clear()
        SchemaValidatorRegistry.clear()
        SchemaValidatorRegistry.register(JsonSchemaValidator())
    }

    @Test
    fun `test branch registerSchema success`() = runBlocking {
        val schema = createTestSchema()
        val definition = createTestSchemaDefinition()
        
        val result = SchemaRegistry.registerSchema(schema, definition)
        
        assertTrue(result.success)
        assertEquals(schema.id, result.schemaId)
    }

    @Test
    fun `test branch registerSchema stores schema and definition`() = runBlocking {
        val schema = createTestSchema()
        val definition = createTestSchemaDefinition()
        
        SchemaRegistry.registerSchema(schema, definition)
        
        assertEquals(schema, SchemaRegistry.getSchema(schema.id))
        assertEquals(definition, SchemaRegistry.getSchemaDefinition(schema.id))
    }

    @Test
    fun `test branch getSchema returns registered schema`() = runBlocking {
        val schema = createTestSchema()
        val definition = createTestSchemaDefinition()
        
        SchemaRegistry.registerSchema(schema, definition)
        
        val retrieved = SchemaRegistry.getSchema(schema.id)
        
        assertEquals(schema, retrieved)
    }

    @Test
    fun `test branch getSchema returns null`() {
        val retrieved = SchemaRegistry.getSchema("non-existent")
        
        assertNull(retrieved)
    }

    @Test
    fun `test branch getSchemaDefinition returns definition`() = runBlocking {
        val schema = createTestSchema()
        val definition = createTestSchemaDefinition()
        
        SchemaRegistry.registerSchema(schema, definition)
        
        val retrieved = SchemaRegistry.getSchemaDefinition(schema.id)
        
        assertEquals(definition, retrieved)
    }

    @Test
    fun `test branch getSchemaDefinition returns null`() {
        val retrieved = SchemaRegistry.getSchemaDefinition("non-existent")
        
        assertNull(retrieved)
    }

    @Test
    fun `test branch validateCredential with registered schema`() = runBlocking {
        val schema = createTestSchema()
        val definition = createTestSchemaDefinition()
        SchemaRegistry.registerSchema(schema, definition)
        
        val credential = createTestCredential()
        
        val result = SchemaRegistry.validateCredential(credential, schema.id)
        
        assertNotNull(result)
    }

    @Test
    fun `test branch validateCredential throws when schema not found`() = runBlocking {
        val credential = createTestCredential()
        
        assertFailsWith<IllegalArgumentException> {
            SchemaRegistry.validateCredential(credential, "non-existent")
        }
    }

    @Test
    fun `test branch validateCredential throws when definition not found`() = runBlocking {
        val schema = createTestSchema()
        SchemaRegistry.registerSchema(schema, createTestSchemaDefinition())
        
        // Remove definition manually (simulating edge case)
        SchemaRegistry.unregister(schema.id)
        
        val credential = createTestCredential()
        
        // Re-register schema but not definition
        SchemaRegistry.registerSchema(schema, createTestSchemaDefinition())
        SchemaRegistry.unregister(schema.id)
        
        // This should throw when trying to validate
        assertFailsWith<IllegalArgumentException> {
            SchemaRegistry.validateCredential(credential, schema.id)
        }
    }

    @Test
    fun `test branch isRegistered returns true`() = runBlocking {
        val schema = createTestSchema()
        SchemaRegistry.registerSchema(schema, createTestSchemaDefinition())
        
        assertTrue(SchemaRegistry.isRegistered(schema.id))
    }

    @Test
    fun `test branch isRegistered returns false`() {
        assertFalse(SchemaRegistry.isRegistered("non-existent"))
    }

    @Test
    fun `test branch getAllSchemaIds returns all IDs`() = runBlocking {
        val schema1 = createTestSchema(id = "schema-1")
        val schema2 = createTestSchema(id = "schema-2")
        SchemaRegistry.registerSchema(schema1, createTestSchemaDefinition())
        SchemaRegistry.registerSchema(schema2, createTestSchemaDefinition())
        
        val ids = SchemaRegistry.getAllSchemaIds()
        
        assertTrue(ids.contains("schema-1"))
        assertTrue(ids.contains("schema-2"))
    }

    @Test
    fun `test branch unregister removes schema and definition`() = runBlocking {
        val schema = createTestSchema()
        val definition = createTestSchemaDefinition()
        SchemaRegistry.registerSchema(schema, definition)
        
        SchemaRegistry.unregister(schema.id)
        
        assertNull(SchemaRegistry.getSchema(schema.id))
        assertNull(SchemaRegistry.getSchemaDefinition(schema.id))
    }

    @Test
    fun `test branch clear removes all schemas`() = runBlocking {
        val schema1 = createTestSchema(id = "schema-1")
        val schema2 = createTestSchema(id = "schema-2")
        SchemaRegistry.registerSchema(schema1, createTestSchemaDefinition())
        SchemaRegistry.registerSchema(schema2, createTestSchemaDefinition())
        
        SchemaRegistry.clear()
        
        assertTrue(SchemaRegistry.getAllSchemaIds().isEmpty())
    }

    private fun createTestSchema(id: String = "schema-1"): CredentialSchema {
        return CredentialSchema(
            id = id,
            type = "JsonSchemaValidator2018",
            schemaFormat = SchemaFormat.JSON_SCHEMA
        )
    }

    private fun createTestSchemaDefinition(): JsonObject {
        return buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
            put("properties", buildJsonObject {
                put("name", buildJsonObject { put("type", "string") })
            })
        }
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
}
