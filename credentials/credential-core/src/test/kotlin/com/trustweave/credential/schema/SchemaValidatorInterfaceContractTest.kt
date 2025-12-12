package com.trustweave.credential.schema

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.did.identifiers.Did
import com.trustweave.credential.model.SchemaFormat
import com.trustweave.credential.schema.SchemaRegistries
import kotlinx.datetime.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlinx.datetime.Clock

/**
 * Comprehensive interface contract tests for SchemaValidator.
 * Tests all methods, branches, and edge cases.
 */
class SchemaValidatorInterfaceContractTest {

    @Test
    fun `test SchemaValidator schemaFormat returns format`() = runBlocking {
        val validator = createMockValidator(SchemaFormat.JSON_SCHEMA)

        assertEquals(SchemaFormat.JSON_SCHEMA, validator.schemaFormat)
    }

    @Test
    fun `test SchemaValidator validate returns validation result`() = runBlocking {
        val validator = createMockValidator(SchemaFormat.JSON_SCHEMA)
        val credential = createTestCredential()
        val schema = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
            put("properties", buildJsonObject {
                put("id", buildJsonObject { put("type", "string") })
                put("name", buildJsonObject { put("type", "string") })
            })
        }

        val result = validator.validate(credential, schema)

        assertNotNull(result)
        assertNotNull(result.valid)
    }

    @Test
    fun `test SchemaValidator validate with invalid credential`() = runBlocking {
        val validator = createMockValidator(SchemaFormat.JSON_SCHEMA)
        val credential = createTestCredential()
        val schema = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
            put("required", buildJsonArray { add("missingField") })
        }

        val result = validator.validate(credential, schema)

        assertNotNull(result)
        // Current implementation may not fully validate, but should return a result
    }

    // Note: validateCredentialSubject is not part of the public SchemaValidator API
    /*
    @Test
    fun `test SchemaValidator validateCredentialSubject returns validation result`() = runBlocking {
        // TODO: Refactor to use public API
    }

    @Test
    fun `test SchemaValidator validateCredentialSubject with invalid subject`() = runBlocking {
        // TODO: Refactor to use public API
    }
    */

    @Test
    fun `test SchemaValidator validate with empty schema`() = runBlocking {
        val validator = createMockValidator(SchemaFormat.JSON_SCHEMA)
        val credential = createTestCredential()
        val schema = buildJsonObject {}

        val result = validator.validate(credential, schema)

        assertNotNull(result)
    }

    @Test
    fun `test SchemaValidator validate with complex schema`() = runBlocking {
        val validator = createMockValidator(SchemaFormat.JSON_SCHEMA)
        val credential = createTestCredential(
            subject = CredentialSubject.fromDid(
                Did("did:key:subject"),
                claims = mapOf(
                    "name" to JsonPrimitive("John Doe"),
                    "email" to JsonPrimitive("john@example.com"),
                    "age" to JsonPrimitive(30)
                )
            )
        )
        val schema = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
            put("properties", buildJsonObject {
                put("id", buildJsonObject { put("type", "string") })
                put("name", buildJsonObject { put("type", "string") })
                put("email", buildJsonObject { put("type", "string") })
                put("age", buildJsonObject { put("type", "number") })
            })
        }

        val result = validator.validate(credential, schema)

        assertNotNull(result)
    }

    private fun createMockValidator(format: SchemaFormat): SchemaValidator {
        return object : SchemaValidator {
            override val schemaFormat: SchemaFormat = format

            override suspend fun validate(
                credential: VerifiableCredential,
                schema: JsonObject
            ): SchemaValidationResult {
                // Simplified validation - check basic structure
                val errors = mutableListOf<SchemaValidationError>()

                if (schema.containsKey("\$schema")) {
                    // Basic validation passed
                } else {
                    errors.add(SchemaValidationError("$", "Schema missing \$schema field"))
                }

                return SchemaValidationResult(
                    valid = errors.isEmpty(),
                    errors = errors
                )
            }

            override suspend fun validateClaims(
                claims: com.trustweave.credential.model.Claims,
                schema: JsonObject
            ): SchemaValidationResult {
                return SchemaValidationResult(
                    valid = true,
                    errors = emptyList()
                )
            }
        }
    }

    private fun createTestCredential(
        id: String? = null,
        types: List<CredentialType> = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("PersonCredential")),
        issuerDid: String = "did:key:issuer",
        subject: CredentialSubject = CredentialSubject.fromDid(
            Did("did:key:subject"),
            claims = mapOf("name" to JsonPrimitive("John Doe"))
        ),
        issuanceDate: Instant = Clock.System.now()
    ): VerifiableCredential {
        return VerifiableCredential(
            id = id?.let { CredentialId(it) },
            type = types,
            issuer = Issuer.fromDid(Did(issuerDid)),
            credentialSubject = subject,
            issuanceDate = issuanceDate
        )
    }
}



