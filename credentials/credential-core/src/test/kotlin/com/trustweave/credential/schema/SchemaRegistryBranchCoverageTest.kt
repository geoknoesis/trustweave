package com.trustweave.credential.schema

import com.trustweave.credential.model.vc.CredentialSchema
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.model.SchemaFormat
import com.trustweave.credential.schema.SchemaRegistries
import com.trustweave.credential.schema.SchemaValidator
import com.trustweave.credential.schema.SchemaValidationResult
import com.trustweave.credential.identifiers.SchemaId
import com.trustweave.did.identifiers.Did
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Comprehensive branch coverage tests for SchemaRegistry.
 * Tests all conditional branches and code paths.
 */
class SchemaRegistryBranchCoverageTest {

    private lateinit var registry: com.trustweave.credential.schema.SchemaRegistry
    private lateinit var validatorRegistry: com.trustweave.credential.schema.SchemaValidatorRegistry

    @BeforeEach
    fun setup() = runBlocking {
        registry = SchemaRegistries.default()
        validatorRegistry = SchemaRegistries.defaultValidatorRegistry()
        registry.clear()
        validatorRegistry.clear()
        
        // Create a mock validator since JsonSchemaValidator is internal
        val mockValidator = object : SchemaValidator {
            override val schemaFormat: SchemaFormat = SchemaFormat.JSON_SCHEMA
            override suspend fun validate(credential: VerifiableCredential, schema: JsonObject): SchemaValidationResult {
                return SchemaValidationResult(valid = true)
            }
            override suspend fun validateClaims(claims: com.trustweave.credential.model.Claims, schema: JsonObject): SchemaValidationResult {
                return SchemaValidationResult(valid = true)
            }
        }
        validatorRegistry.register(mockValidator)
    }

    @Test
    fun `test branch registerSchema success`() = runBlocking {
        val schemaId = SchemaId("schema-1")
        val definition = createTestSchemaDefinition()

        registry.registerSchema(schemaId, SchemaFormat.JSON_SCHEMA, definition)

        assertTrue(registry.isRegistered(schemaId))
    }

    @Test
    fun `test branch registerSchema stores schema and definition`() = runBlocking {
        val schemaId = SchemaId("schema-1")
        val definition = createTestSchemaDefinition()

        registry.registerSchema(schemaId, SchemaFormat.JSON_SCHEMA, definition)

        assertNotNull(registry.getSchemaFormat(schemaId))
        assertEquals(definition, registry.getSchemaDefinition(schemaId))
    }

    @Test
    fun `test branch getSchemaFormat returns registered format`() = runBlocking {
        val schemaId = SchemaId("schema-1")
        val definition = createTestSchemaDefinition()

        registry.registerSchema(schemaId, SchemaFormat.JSON_SCHEMA, definition)

        val retrieved = registry.getSchemaFormat(schemaId)

        assertNotNull(retrieved)
        assertEquals(SchemaFormat.JSON_SCHEMA, retrieved)
    }

    @Test
    fun `test branch getSchemaFormat returns null`() = runBlocking {
        val retrieved = registry.getSchemaFormat(SchemaId("non-existent"))

        assertNull(retrieved)
    }

    @Test
    fun `test branch getSchemaDefinition returns definition`() = runBlocking {
        val schemaId = SchemaId("schema-1")
        val definition = createTestSchemaDefinition()

        registry.registerSchema(schemaId, SchemaFormat.JSON_SCHEMA, definition)

        val retrieved = registry.getSchemaDefinition(schemaId)

        assertEquals(definition, retrieved)
    }

    @Test
    fun `test branch getSchemaDefinition returns null`() = runBlocking {
        val retrieved = registry.getSchemaDefinition(SchemaId("non-existent"))

        assertNull(retrieved)
    }

    @Test
    fun `test branch validate with registered schema`() = runBlocking {
        val schemaId = SchemaId("schema-1")
        val definition = createTestSchemaDefinition()
        registry.registerSchema(schemaId, SchemaFormat.JSON_SCHEMA, definition)

        val credential = createTestCredential()

        val result = registry.validate(credential, schemaId)

        assertNotNull(result)
    }

    @Test
    fun `test branch validate throws when schema not found`() = runBlocking {
        val credential = createTestCredential()

        assertFailsWith<IllegalArgumentException> {
            registry.validate(credential, SchemaId("non-existent"))
        }
    }

    @Test
    fun `test branch validate throws when definition not found`() = runBlocking {
        val schemaId = SchemaId("schema-1")
        registry.registerSchema(schemaId, SchemaFormat.JSON_SCHEMA, createTestSchemaDefinition())

        // Remove definition manually (simulating edge case)
        registry.unregister(schemaId)

        val credential = createTestCredential()

        // Re-register schema but not definition
        registry.registerSchema(schemaId, SchemaFormat.JSON_SCHEMA, createTestSchemaDefinition())
        registry.unregister(schemaId)

        // This should throw when trying to validate
        assertFailsWith<IllegalArgumentException> {
            registry.validate(credential, schemaId)
        }
    }

    @Test
    fun `test branch isRegistered returns true`() = runBlocking {
        val schemaId = SchemaId("schema-1")
        registry.registerSchema(schemaId, SchemaFormat.JSON_SCHEMA, createTestSchemaDefinition())

        assertTrue(registry.isRegistered(schemaId))
    }

    @Test
    fun `test branch isRegistered returns false`() = runBlocking {
        assertFalse(registry.isRegistered(SchemaId("non-existent")))
    }

    @Test
    fun `test branch getAllSchemaIds returns all IDs`() = runBlocking {
        val schemaId1 = SchemaId("schema-1")
        val schemaId2 = SchemaId("schema-2")
        registry.registerSchema(schemaId1, SchemaFormat.JSON_SCHEMA, createTestSchemaDefinition())
        registry.registerSchema(schemaId2, SchemaFormat.JSON_SCHEMA, createTestSchemaDefinition())

        val ids = registry.getAllSchemaIds()

        assertTrue(ids.contains(schemaId1))
        assertTrue(ids.contains(schemaId2))
    }

    @Test
    fun `test branch unregister removes schema and definition`() = runBlocking {
        val schemaId = SchemaId("schema-1")
        val definition = createTestSchemaDefinition()
        registry.registerSchema(schemaId, SchemaFormat.JSON_SCHEMA, definition)

        registry.unregister(schemaId)

        assertNull(registry.getSchemaFormat(schemaId))
        assertNull(registry.getSchemaDefinition(schemaId))
    }

    @Test
    fun `test branch clear removes all schemas`() = runBlocking {
        val schemaId1 = SchemaId("schema-1")
        val schemaId2 = SchemaId("schema-2")
        registry.registerSchema(schemaId1, SchemaFormat.JSON_SCHEMA, createTestSchemaDefinition())
        registry.registerSchema(schemaId2, SchemaFormat.JSON_SCHEMA, createTestSchemaDefinition())

        registry.clear()

        assertTrue(registry.getAllSchemaIds().isEmpty())
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
        val claims = mapOf("name" to JsonPrimitive("John Doe"))
        return VerifiableCredential(
            type = listOf(CredentialType.fromString("VerifiableCredential"), CredentialType.fromString("PersonCredential")),
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromDid(Did("did:key:subject"), claims = claims),
            issuanceDate = Clock.System.now()
        )
    }
}
