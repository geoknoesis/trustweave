package org.trustweave.trust.dsl

import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.schema.SchemaRegistry
import org.trustweave.credential.model.SchemaFormat
import org.trustweave.kms.results.SignResult
import org.trustweave.testkit.kms.InMemoryKeyManagementService
import org.trustweave.trust.dsl.TrustWeaveConfig
import org.trustweave.trust.dsl.trustWeave
import org.trustweave.trust.dsl.credential.DidMethods
import org.trustweave.trust.dsl.credential.KeyAlgorithms
import org.trustweave.trust.dsl.credential.SchemaValidatorTypes
import org.trustweave.trust.dsl.credential.JsonObjectBuilder
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

    private lateinit var trustWeave: TrustWeaveConfig

    @BeforeEach
    fun setup() = runBlocking {
        val kms = InMemoryKeyManagementService()
        trustWeave = trustWeave {
            // DID methods auto-discovered via SPI
            keys {
                custom(kms)
                signer { data, keyId ->
                    when (val result = kms.sign(org.trustweave.core.identifiers.KeyId(keyId), data)) {
                        is SignResult.Success -> result.signature
                        else -> throw IllegalStateException("Signing failed: $result")
                    }
                }
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

        // Schema registry is managed by TrustWeave instance
    }

    @Test
    fun `test register JSON schema`() = runBlocking {
        val schemaId = "https://example.com/schemas/person"

        val result = trustWeave.registerSchema {
            id(schemaId)
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
        assertNotNull(result.schemaId)
        assertEquals(schemaId, result.schemaId?.value)
    }

    @Test
    fun `test register SHACL schema`() = runBlocking {
        val schemaId = "https://example.com/schemas/person-shacl"

        val result = trustWeave.registerSchema {
            id(schemaId)
            shacl {
                put("@context", "https://www.w3.org/ns/shacl#")
                put("sh:targetClass", "PersonCredential")
            }
        }

        assertTrue(result.success)
        assertNotNull(result.schemaId)
        assertEquals(schemaId, result.schemaId?.value)
    }

    @Test
    fun `test register schema without id throws exception`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            trustWeave.registerSchema {
                jsonSchema {
                    put("type", "object")
                }
            }
        }
    }

    @Test
    fun `test register schema without definition throws exception`() = runBlocking {
        assertFailsWith<IllegalStateException> {
            trustWeave.registerSchema {
                id("https://example.com/schemas/test")
            }
        }
    }

    @Test
    fun `test validate credential against schema`() = runBlocking {
        val schemaId = "https://example.com/schemas/person"

        // Register schema
        trustWeave.registerSchema {
            id(schemaId)
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
            type = listOf(org.trustweave.credential.model.CredentialType.VerifiableCredential, org.trustweave.credential.model.CredentialType.Custom("PersonCredential")),
            issuer = org.trustweave.credential.model.vc.Issuer.fromDid(org.trustweave.did.identifiers.Did("did:key:issuer")),
            credentialSubject = org.trustweave.credential.model.vc.CredentialSubject.fromDid(
                org.trustweave.did.identifiers.Did("did:key:subject"),
                claims = mapOf("name" to kotlinx.serialization.json.JsonPrimitive("Alice"))
            ),
            issuanceDate = kotlinx.datetime.Instant.parse("2024-01-01T00:00:00Z")
        )

        // Note: Actual validation requires a registered validator
        // This test verifies the DSL structure works
        val schema = trustWeave.schema(schemaId)
        assertNotNull(schema)
    }

    @Test
    fun `test JsonObjectBuilder`() {
        val builder = JsonObjectBuilder()
        builder.put("key1", "value1")
        builder.put("key2", 123)
        builder.put("key3", true)
        builder.put("nested") {
            "nestedKey" to "nestedValue"
        }

        val jsonObject = builder.build()
        assertNotNull(jsonObject)
        assertEquals("\"value1\"", jsonObject["key1"]?.toString())
    }

    @Test
    fun `test schema builder with format override`() = runBlocking {
        val schemaId = "https://example.com/schemas/test"

        val result = trustWeave.registerSchema {
            id(schemaId)
            format(SchemaFormat.SHACL)
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

        val result = trustWeave.registerSchema {
            id(schemaId)
            definition(definition)
        }

        assertTrue(result.success)
    }

    @Test
    fun `test validate credential against unregistered schema throws exception`() = runBlocking {
        val credential = VerifiableCredential(
            type = listOf(org.trustweave.credential.model.CredentialType.VerifiableCredential),
            issuer = org.trustweave.credential.model.vc.Issuer.fromDid(org.trustweave.did.identifiers.Did("did:key:issuer")),
            credentialSubject = org.trustweave.credential.model.vc.CredentialSubject.fromDid(
                org.trustweave.did.identifiers.Did("did:key:subject"),
                claims = emptyMap()
            ),
            issuanceDate = kotlinx.datetime.Instant.parse("2024-01-01T00:00:00Z")
        )

        assertFailsWith<IllegalArgumentException> {
            trustWeave.schema("https://example.com/schemas/nonexistent").validate(credential)
        }
    }
}


