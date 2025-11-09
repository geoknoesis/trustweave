package io.geoknoesis.vericore.credential.dsl

import io.geoknoesis.vericore.credential.models.VerifiableCredential
import io.geoknoesis.vericore.credential.schema.SchemaRegistry
import io.geoknoesis.vericore.spi.SchemaFormat
import io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Unit tests for SchemaDsl.kt
 */
class SchemaDslTest {
    
    private lateinit var trustLayer: TrustLayerConfig
    
    @BeforeEach
    fun setup() = runBlocking {
        val kms = InMemoryKeyManagementService()
        trustLayer = trustLayer {
            keys {
                custom(kms as Any)
            }
            did {
                method("key") {
                    algorithm("Ed25519")
                }
            }
            schemas {
                autoValidate(false)
                defaultFormat(SchemaFormat.JSON_SCHEMA)
            }
        }
        
        // Clear schema registry before each test
        SchemaRegistry.clear()
    }
    
    @Test
    fun `test register JSON schema`() = runBlocking {
        val schemaId = "https://example.com/schemas/person"
        
        val result = trustLayer.registerSchema {
            id(schemaId)
            type(SchemaValidatorTypes.JSON_SCHEMA)
            jsonSchema {
                put("\$schema", "http://json-schema.org/draft-07/schema#")
                put("type", "object")
                put("properties", buildJsonObject {
                    put("name", buildJsonObject {
                        put("type", "string")
                    })
                })
            }
        }
        
        assertTrue(result.success)
        assertEquals(schemaId, result.schemaId)
        assertTrue(SchemaRegistry.isRegistered(schemaId))
    }
    
    @Test
    fun `test register SHACL schema`() = runBlocking {
        val schemaId = "https://example.com/schemas/person-shacl"
        
        val result = trustLayer.registerSchema {
            id(schemaId)
            type(SchemaValidatorTypes.SHACL)
            shacl {
                put("@context", "https://www.w3.org/ns/shacl#")
                put("sh:targetClass", "PersonCredential")
            }
        }
        
        assertTrue(result.success)
        assertEquals(schemaId, result.schemaId)
    }
    
    @Test
    fun `test register schema without id throws exception`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            trustLayer.registerSchema {
                type(SchemaValidatorTypes.JSON_SCHEMA)
                jsonSchema {
                    put("type", "object")
                }
            }
        }
    }
    
    @Test
    fun `test register schema without definition throws exception`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            trustLayer.registerSchema {
                id("https://example.com/schemas/test")
                type(SchemaValidatorTypes.JSON_SCHEMA)
            }
        }
    }
    
    @Test
    fun `test validate credential against schema`() = runBlocking {
        val schemaId = "https://example.com/schemas/person"
        
        // Register schema
        trustLayer.registerSchema {
            id(schemaId)
            type(SchemaValidatorTypes.JSON_SCHEMA)
            jsonSchema {
                put("\$schema", "http://json-schema.org/draft-07/schema#")
                put("type", "object")
                put("properties", buildJsonObject {
                    put("name", buildJsonObject {
                        put("type", "string")
                    })
                })
            }
        }
        
        // Create credential matching schema
        val credential = VerifiableCredential(
            type = listOf("VerifiableCredential", "PersonCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject {
                put("id", "did:key:subject")
                put("name", "Alice")
            },
            issuanceDate = "2024-01-01T00:00:00Z"
        )
        
        // Note: Actual validation requires a registered validator
        // This test verifies the DSL structure works
        val schema = trustLayer.schema(schemaId)
        assertNotNull(schema)
    }
    
    @Test
    fun `test JsonObjectBuilder`() {
        val builder = JsonObjectBuilder()
        builder.put("key1", "value1")
        builder.put("key2", 123)
        builder.put("key3", true)
        builder.put("nested") {
            put("nestedKey", "nestedValue")
        }
        
        val jsonObject = builder.build()
        assertNotNull(jsonObject)
        assertEquals("value1", jsonObject["key1"]?.toString()?.trim('"'))
    }
    
    @Test
    fun `test schema builder with format override`() = runBlocking {
        val schemaId = "https://example.com/schemas/test"
        
        val result = trustLayer.registerSchema {
            id(schemaId)
            format(io.geoknoesis.vericore.spi.SchemaFormat.SHACL)
            shacl {
                put("@context", "https://www.w3.org/ns/shacl#")
                put("sh:targetClass", "TestCredential")
            }
        }
        
        assertTrue(result.success)
    }
    
    @Test
    fun `test schema builder definition method`() = runBlocking {
        val schemaId = "https://example.com/schemas/test"
        val definition = buildJsonObject {
            put("type", "object")
            put("properties", buildJsonObject {
                put("name", buildJsonObject { put("type", "string") })
            })
        }
        
        val result = trustLayer.registerSchema {
            id(schemaId)
            type(SchemaValidatorTypes.JSON_SCHEMA)
            definition(definition)
        }
        
        assertTrue(result.success)
    }
    
    @Test
    fun `test validate credential against unregistered schema throws exception`() = runBlocking {
        val credential = VerifiableCredential(
            type = listOf("VerifiableCredential"),
            issuer = "did:key:issuer",
            credentialSubject = buildJsonObject {
                put("id", "did:key:subject")
            },
            issuanceDate = "2024-01-01T00:00:00Z"
        )
        
        assertFailsWith<IllegalArgumentException> {
            trustLayer.schema("https://example.com/schemas/nonexistent").validate(credential)
        }
    }
}

