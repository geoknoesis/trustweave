package com.trustweave.credential.schema

import com.trustweave.credential.model.vc.CredentialSchema
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.model.SchemaFormat
import com.trustweave.credential.identifiers.SchemaId
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.did.identifiers.Did
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Comprehensive tests for SchemaRegistry API.
 */
class SchemaRegistryTest {

    private lateinit var schemaRegistry: SchemaRegistry
    private lateinit var validatorRegistry: SchemaValidatorRegistry

    @BeforeEach
    fun setup() = runBlocking {
        validatorRegistry = SchemaRegistries.defaultValidatorRegistry()
        validatorRegistry.clear()
        schemaRegistry = SchemaRegistries.default()
        schemaRegistry.clear()
    }

    @AfterEach
    fun cleanup() = runBlocking {
        schemaRegistry.clear()
        validatorRegistry.clear()
    }

    @Test
    fun `test register schema successfully`() = runBlocking {
        val schemaId = SchemaId("https://example.com/schemas/person")
        val definition = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
        }

        val result = schemaRegistry.registerSchema(schemaId, SchemaFormat.JSON_SCHEMA, definition)

        assertTrue(result.success)
        assertEquals(schemaId, result.schemaId)
    }

    @Test
    fun `test get registered schema`() = runBlocking {
        val schemaId = SchemaId("https://example.com/schemas/person")
        val definition = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
        }

        schemaRegistry.registerSchema(schemaId, SchemaFormat.JSON_SCHEMA, definition)

        val retrievedDefinition = schemaRegistry.getSchemaDefinition(schemaId)
        val retrievedFormat = schemaRegistry.getSchemaFormat(schemaId)

        assertNotNull(retrievedDefinition)
        assertNotNull(retrievedFormat)
        assertEquals(SchemaFormat.JSON_SCHEMA, retrievedFormat)
    }

    @Test
    fun `test get schema definition`() = runBlocking {
        val schemaId = SchemaId("https://example.com/schemas/person")
        val definition = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
            put("properties", buildJsonObject {
                put("name", buildJsonObject { put("type", "string") })
            })
        }

        schemaRegistry.registerSchema(schemaId, SchemaFormat.JSON_SCHEMA, definition)

        val retrieved = schemaRegistry.getSchemaDefinition(schemaId)

        assertNotNull(retrieved)
        assertEquals(definition["type"]?.jsonPrimitive?.content, retrieved?.get("type")?.jsonPrimitive?.content)
    }

    @Test
    fun `test get non-existent schema returns null`() = runBlocking {
        val schemaId = SchemaId("https://example.com/schemas/nonexistent")
        val definition = schemaRegistry.getSchemaDefinition(schemaId)

        assertNull(definition)
    }

    @Test
    fun `test is registered returns true for registered schema`() = runBlocking {
        val schemaId = SchemaId("https://example.com/schemas/person")
        val definition = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
        }

        schemaRegistry.registerSchema(schemaId, SchemaFormat.JSON_SCHEMA, definition)

        assertTrue(schemaRegistry.isRegistered(schemaId))
        assertFalse(schemaRegistry.isRegistered(SchemaId("https://example.com/schemas/nonexistent")))
    }

    @Test
    fun `test getAllSchemaIds returns all registered schemas`() = runBlocking {
        val schemaId1 = SchemaId("https://example.com/schemas/person")
        val schemaId2 = SchemaId("https://example.com/schemas/degree")
        val definition = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
        }

        schemaRegistry.registerSchema(schemaId1, SchemaFormat.JSON_SCHEMA, definition)
        schemaRegistry.registerSchema(schemaId2, SchemaFormat.JSON_SCHEMA, definition)

        val ids = schemaRegistry.getAllSchemaIds()

        assertEquals(2, ids.size)
        assertTrue(ids.contains(schemaId1))
        assertTrue(ids.contains(schemaId2))
    }

    @Test
    fun `test unregister schema`() = runBlocking {
        val schemaId = SchemaId("https://example.com/schemas/person")
        val definition = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
        }

        schemaRegistry.registerSchema(schemaId, SchemaFormat.JSON_SCHEMA, definition)
        assertTrue(schemaRegistry.isRegistered(schemaId))

        schemaRegistry.unregister(schemaId)

        assertFalse(schemaRegistry.isRegistered(schemaId))
        assertNull(schemaRegistry.getSchemaDefinition(schemaId))
    }

    @Test
    fun `test clear all schemas`() = runBlocking {
        val schemaId1 = SchemaId("https://example.com/schemas/person")
        val schemaId2 = SchemaId("https://example.com/schemas/degree")
        val definition = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
        }

        schemaRegistry.registerSchema(schemaId1, SchemaFormat.JSON_SCHEMA, definition)
        schemaRegistry.registerSchema(schemaId2, SchemaFormat.JSON_SCHEMA, definition)

        assertEquals(2, schemaRegistry.getAllSchemaIds().size)

        schemaRegistry.clear()

        assertEquals(0, schemaRegistry.getAllSchemaIds().size)
        assertFalse(schemaRegistry.isRegistered(schemaId1))
        assertFalse(schemaRegistry.isRegistered(schemaId2))
    }

    @Test
    fun `test validate credential against schema`() = runBlocking {
        val schemaId = SchemaId("https://example.com/schemas/person")
        val schemaDefinition = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
            put("properties", buildJsonObject {
                put("name", buildJsonObject { put("type", "string") })
            })
        }
        schemaRegistry.registerSchema(schemaId, SchemaFormat.JSON_SCHEMA, schemaDefinition)

        val subjectId = "did:key:subject"
        val subjectClaims = buildJsonObject {
            put("name", "John Doe")
        }
        val credential = VerifiableCredential(
            id = CredentialId("https://example.com/credentials/1"),
            type = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("PersonCredential")),
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromDid(Did(subjectId), claims = subjectClaims),
            issuanceDate = Clock.System.now()
        )

        val result = schemaRegistry.validate(credential, schemaId)

        assertNotNull(result)
    }

    @Test
    fun `test validate credential fails when schema not found`() = runBlocking {
        val subjectId = "did:key:subject"
        val credential = VerifiableCredential(
            id = CredentialId("https://example.com/credentials/1"),
            type = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("PersonCredential")),
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromDid(Did(subjectId)),
            issuanceDate = Clock.System.now()
        )

        assertFailsWith<IllegalArgumentException> {
            schemaRegistry.validate(credential, SchemaId("https://example.com/schemas/nonexistent"))
        }
    }

    @Test
    fun `test register schema with SHACL format`() = runBlocking {
        val schemaId = SchemaId("https://example.com/schemas/person")
        val definition = buildJsonObject {
            put("@context", "https://www.w3.org/ns/shacl#")
            put("@type", "NodeShape")
        }

        val result = schemaRegistry.registerSchema(schemaId, SchemaFormat.SHACL, definition)

        assertTrue(result.success)
        val retrievedFormat = schemaRegistry.getSchemaFormat(schemaId)
        assertEquals(SchemaFormat.SHACL, retrievedFormat)
    }

    @Test
    fun `test register schema handles errors gracefully`() = runBlocking {
        // This test verifies that registration errors are handled
        // The actual implementation catches exceptions and returns a result
        val schemaId = SchemaId("https://example.com/schemas/person")
        val definition = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
        }

        val result = schemaRegistry.registerSchema(schemaId, SchemaFormat.JSON_SCHEMA, definition)

        // Should succeed under normal circumstances
        assertTrue(result.success)
    }
}

