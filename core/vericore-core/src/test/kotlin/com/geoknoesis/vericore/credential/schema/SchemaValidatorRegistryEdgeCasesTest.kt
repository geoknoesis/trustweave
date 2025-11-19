package com.geoknoesis.vericore.credential.schema

import com.geoknoesis.vericore.credential.models.VerifiableCredential
import com.geoknoesis.vericore.spi.SchemaFormat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Additional edge case tests for SchemaValidatorRegistry, especially detectSchemaFormat.
 */
class SchemaValidatorRegistryEdgeCasesTest {

    @BeforeEach
    fun setup() {
        SchemaValidatorRegistry.clear()
    }

    @Test
    fun `test detectSchemaFormat with JSON Schema indicators`() {
        val schema = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
            put("properties", buildJsonObject {
                put("name", buildJsonObject { put("type", "string") })
            })
        }
        
        val format = SchemaValidatorRegistry.detectSchemaFormat(schema)
        
        assertEquals(SchemaFormat.JSON_SCHEMA, format)
    }

    @Test
    fun `test detectSchemaFormat with SHACL indicators`() {
        val schema = buildJsonObject {
            put("@context", "http://www.w3.org/ns/shacl")
            put("sh:targetClass", "Person")
        }
        
        val format = SchemaValidatorRegistry.detectSchemaFormat(schema)
        
        assertEquals(SchemaFormat.SHACL, format)
    }

    @Test
    fun `test detectSchemaFormat with SHACL context string`() {
        val schema = buildJsonObject {
            put("@context", buildJsonObject {
                put("sh", "http://www.w3.org/ns/shacl#")
            })
        }
        
        val format = SchemaValidatorRegistry.detectSchemaFormat(schema)
        
        assertEquals(SchemaFormat.SHACL, format)
    }

    @Test
    fun `test detectSchemaFormat defaults to JSON_SCHEMA`() {
        val schema = buildJsonObject {
            put("unknown", "value")
        }
        
        val format = SchemaValidatorRegistry.detectSchemaFormat(schema)
        
        assertEquals(SchemaFormat.JSON_SCHEMA, format)
    }

    @Test
    fun `test validate throws when no validator registered`() = runBlocking {
        val credential = VerifiableCredential(
            type = listOf("VerifiableCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject { put("id", "did:key:subject") },
            issuanceDate = "2024-01-01T00:00:00Z"
        )
        val schema = buildJsonObject { put("type", "object") }
        
        assertFailsWith<IllegalArgumentException> {
            SchemaValidatorRegistry.validate(credential, schema)
        }
    }

    @Test
    fun `test validateCredentialSubject throws when no validator registered`() = runBlocking {
        val subject = buildJsonObject { put("id", "did:key:subject") }
        val schema = buildJsonObject { put("type", "object") }
        
        assertFailsWith<IllegalArgumentException> {
            SchemaValidatorRegistry.validateCredentialSubject(subject, schema)
        }
    }

    @Test
    fun `test hasValidator`() {
        assertFalse(SchemaValidatorRegistry.hasValidator(SchemaFormat.JSON_SCHEMA))
        
        SchemaValidatorRegistry.register(JsonSchemaValidator())
        
        assertTrue(SchemaValidatorRegistry.hasValidator(SchemaFormat.JSON_SCHEMA))
        assertFalse(SchemaValidatorRegistry.hasValidator(SchemaFormat.SHACL))
    }

    @Test
    fun `test getRegisteredFormats`() {
        assertTrue(SchemaValidatorRegistry.getRegisteredFormats().isEmpty())
        
        SchemaValidatorRegistry.register(JsonSchemaValidator())
        
        val formats = SchemaValidatorRegistry.getRegisteredFormats()
        assertEquals(1, formats.size)
        assertTrue(formats.contains(SchemaFormat.JSON_SCHEMA))
    }

    @Test
    fun `test unregister validator`() {
        SchemaValidatorRegistry.register(JsonSchemaValidator())
        assertTrue(SchemaValidatorRegistry.hasValidator(SchemaFormat.JSON_SCHEMA))
        
        SchemaValidatorRegistry.unregister(SchemaFormat.JSON_SCHEMA)
        
        assertFalse(SchemaValidatorRegistry.hasValidator(SchemaFormat.JSON_SCHEMA))
    }
}



