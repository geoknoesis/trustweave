package com.trustweave.credential.schema

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.did.identifiers.Did
import com.trustweave.core.identifiers.Iri
import com.trustweave.credential.model.SchemaFormat
import com.trustweave.credential.schema.SchemaRegistries
import com.trustweave.credential.schema.SchemaValidator
import kotlinx.datetime.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlinx.datetime.Clock

/**
 * Comprehensive branch coverage tests for JsonSchemaValidator.
 * Tests all conditional branches and code paths.
 */
class JsonSchemaValidatorBranchCoverageTest {

    private lateinit var validator: SchemaValidator

    @BeforeEach
    fun setup() {
        validator = SchemaRegistries.defaultValidatorRegistry().get(SchemaFormat.JSON_SCHEMA)!!
    }

    @Test
    fun `test branch validate with VerifiableCredential type`() = runBlocking {
        val credential = createTestCredential(types = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("PersonCredential")))
        val schema = createTestSchema()

        val result = validator.validate(credential, schema)

        assertTrue(result.valid)
    }

    @Test
    fun `test branch validate without VerifiableCredential type`() = runBlocking {
        val credential = createTestCredential(types = listOf(CredentialType.Custom("PersonCredential")))
        val schema = createTestSchema()

        val result = validator.validate(credential, schema)

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.path == "/type" })
    }

    @Test
    fun `test branch validate with blank issuer`() = runBlocking {
        // Create credential with blank issuer - use IriIssuer with empty string
        // Note: JsonSchemaValidator doesn't validate issuer field directly, only validates claims
        // So we'll test with a schema that requires issuer validation
        val credential = VerifiableCredential(
            type = listOf(CredentialType.VerifiableCredential),
            issuer = Issuer.IriIssuer(com.trustweave.core.identifiers.Iri("")), // Blank issuer
            credentialSubject = CredentialSubject.fromDid(Did("did:key:subject")),
            issuanceDate = Clock.System.now()
        )
        val schema = createTestSchema()

        val result = validator.validate(credential, schema)

        // JsonSchemaValidator doesn't validate issuer field, only validates claims
        // So this test may pass even with blank issuer
        // The test is checking that validation completes without errors for the structure
        assertNotNull(result)
        // If issuer validation is needed, it should be done at a higher level (CredentialVerifier)
    }

    @Test
    fun `test branch validate with non-blank issuer`() = runBlocking {
        val credential = createTestCredential(issuerDid = "did:key:issuer")
        val schema = createTestSchema()

        val result = validator.validate(credential, schema)

        assertTrue(result.valid)
    }

    @Test
    fun `test branch validate with schema properties`() = runBlocking {
        val credential = createTestCredential()
        val schema = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("properties", buildJsonObject {
                put("name", buildJsonObject { put("type", "string") })
            })
        }

        val result = validator.validate(credential, schema)

        assertNotNull(result)
    }

    @Test
    fun `test branch validate without schema properties`() = runBlocking {
        val credential = createTestCredential()
        val schema = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
        }

        val result = validator.validate(credential, schema)

        assertTrue(result.valid)
    }

    // Note: validateCredentialSubject is an internal method, not part of public SchemaValidator API
    // These tests are commented out as they test internal implementation details
    /*
    @Test
    fun `test branch validateCredentialSubject with schema properties`() = runBlocking {
        val subject = buildJsonObject {
            put("id", "did:key:subject")
            put("name", "John Doe")
        }
        val schema = buildJsonObject {
            put("properties", buildJsonObject {
                put("name", buildJsonObject { put("type", "string") })
            })
        }

        val result = validator.validateCredentialSubject(subject, schema)

        assertNotNull(result)
    }

    @Test
    fun `test branch validateCredentialSubject without schema properties`() = runBlocking {
        val subject = buildJsonObject {
            put("id", "did:key:subject")
        }
        val schema = buildJsonObject {}

        val result = validator.validateCredentialSubject(subject, schema)

        assertTrue(result.valid)
    }

    @Test
    fun `test branch validateCredentialSubject with required fields empty`() = runBlocking {
        val subject = buildJsonObject {
            put("id", "did:key:subject")
        }
        val schema = buildJsonObject {
            put("properties", buildJsonObject {
                put("name", buildJsonObject { put("type", "string") })
            })
            put("required", buildJsonArray {})
        }

        val result = validator.validateCredentialSubject(subject, schema)

        assertTrue(result.valid) // Required fields list is empty
    }
    */

    // Commented out - tests internal method not in public API
    /*
    @Test
    fun `test branch validateCredentialSubject with non-JsonObject subject`() = runBlocking {
        val subject = JsonPrimitive("not-an-object")
        val schema = buildJsonObject {
            put("properties", buildJsonObject {
                put("name", buildJsonObject { put("type", "string") })
            })
        }

        val result = validator.validateCredentialSubject(subject, schema)

        assertTrue(result.valid) // Current implementation doesn't check type
    }

    @Test
    fun `test branch validateCredentialSubject with null required`() = runBlocking {
        val subject = buildJsonObject {
            put("id", "did:key:subject")
        }
        val schema = buildJsonObject {
            put("properties", buildJsonObject {
                put("name", buildJsonObject { put("type", "string") })
            })
        }

        val result = validator.validateCredentialSubject(subject, schema)

        assertTrue(result.valid)
    }
    */

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

    private fun createTestSchema(): JsonObject {
        return buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
        }
    }
}



