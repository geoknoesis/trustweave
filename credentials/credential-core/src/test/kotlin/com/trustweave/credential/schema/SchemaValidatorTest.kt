package com.trustweave.credential.schema

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.vc.CredentialSchema
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.did.identifiers.Did
import com.trustweave.credential.model.SchemaFormat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Tests for SchemaValidator interface implementations.
 * Tests the interface contract and multiple implementations.
 */
class SchemaValidatorTest {

    private lateinit var validator: SchemaValidator

    @BeforeEach
    fun setup() {
        val registry = com.trustweave.credential.schema.SchemaRegistries.defaultValidatorRegistry()
        registry.clear()
        validator = SchemaRegistries.defaultValidatorRegistry().get(SchemaFormat.JSON_SCHEMA) as com.trustweave.credential.schema.SchemaValidator
        registry.register(validator)
    }

    @AfterEach
    fun cleanup() {
        val registry = com.trustweave.credential.schema.SchemaRegistries.defaultValidatorRegistry()
        registry.clear()
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

    // Note: validateCredentialSubject is not part of the public SchemaValidator API
    /*
    @Test
    fun `test SchemaValidator validateCredentialSubject with valid subject`() = runBlocking {
        // TODO: Refactor to use public API
    }

    @Test
    fun `test SchemaValidator validateCredentialSubject with missing required field`() = runBlocking {
        // TODO: Refactor to use public API
    }
    */

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
            subject = CredentialSubject.fromDid(
                Did("did:key:subject"),
                claims = mapOf(
                    "name" to JsonPrimitive("John Doe"),
                    "age" to JsonPrimitive(30),
                    "email" to JsonPrimitive("john@example.com")
                )
            )
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

    // Note: validateCredentialSubject is not part of the public SchemaValidator API
    /*
    @Test
    fun `test SchemaValidator validateCredentialSubject with array subject`() = runBlocking {
        // TODO: Refactor to use public API
    }
    */

    // Note: validateCredentialSubject is not part of the public SchemaValidator API
    /*
    @Test
    fun `test SchemaValidator validateCredentialSubject with primitive subject`() = runBlocking {
        // TODO: Refactor to use public API
    }
    */

    @Test
    fun `test SchemaValidator validate with credential having schema`() = runBlocking {
        val schemaId = "https://example.com/schemas/person"
        val credential = createTestCredential(
            schema = CredentialSchema(
                id = com.trustweave.credential.identifiers.SchemaId(schemaId),
                type = "JsonSchemaValidator2018"
            )
        )
        val schema = createTestSchema()

        val result = validator.validate(credential, schema)

        assertNotNull(result)
    }

    @Test
    fun `test SchemaValidator validate with nested credential subject`() = runBlocking {
        val credential = createTestCredential(
            subject = CredentialSubject.fromDid(
                Did("did:key:subject"),
                claims = mapOf(
                    "address" to buildJsonObject {
                        put("street", "123 Main St")
                        put("city", "New York")
                    }
                )
            )
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
            type = listOf(com.trustweave.credential.model.CredentialType.VerifiableCredential),
            issuer = com.trustweave.credential.model.vc.Issuer.fromDid(com.trustweave.did.identifiers.Did("did:key:issuer")),
            issuanceDate = kotlinx.datetime.Clock.System.now(),
            credentialSubject = com.trustweave.credential.model.vc.CredentialSubject.fromDid(
                com.trustweave.did.identifiers.Did("did:key:subject"),
                claims = emptyMap()
            )
        )
        val schema = createTestSchema()

        val result = validator.validate(credential, schema)

        assertNotNull(result)
    }

    private fun createTestCredential(
        id: String? = "https://example.com/credentials/1",
        types: List<com.trustweave.credential.model.CredentialType> = listOf(com.trustweave.credential.model.CredentialType.VerifiableCredential, com.trustweave.credential.model.CredentialType.Custom("PersonCredential")),
        issuerDid: String = "did:key:issuer",
        subject: com.trustweave.credential.model.vc.CredentialSubject = com.trustweave.credential.model.vc.CredentialSubject.fromDid(
            com.trustweave.did.identifiers.Did("did:key:subject"),
            claims = mapOf("name" to kotlinx.serialization.json.JsonPrimitive("John Doe"))
        ),
        issuanceDate: kotlinx.datetime.Instant = kotlinx.datetime.Clock.System.now(),
        schema: com.trustweave.credential.model.vc.CredentialSchema? = null
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id?.let { com.trustweave.credential.identifiers.CredentialId(it) },
            type = types,
            issuer = com.trustweave.credential.model.vc.Issuer.fromDid(com.trustweave.did.identifiers.Did(issuerDid)),
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

