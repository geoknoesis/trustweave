package com.trustweave.credential.schema

import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.Issuer
import com.trustweave.credential.model.vc.CredentialSubject
import com.trustweave.credential.model.CredentialType
import com.trustweave.credential.model.SchemaFormat
import com.trustweave.credential.model.Claims
import com.trustweave.credential.identifiers.CredentialId
import com.trustweave.did.identifiers.Did
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Comprehensive branch coverage tests for SchemaValidatorRegistry.
 * Tests all conditional branches and code paths.
 */
class SchemaValidatorRegistryBranchCoverageTest {

    private lateinit var registry: SchemaValidatorRegistry

    @BeforeEach
    fun setup() {
        registry = SchemaRegistries.defaultValidatorRegistry()
        registry.clear()
    }

    private fun createMockValidator(format: SchemaFormat = SchemaFormat.JSON_SCHEMA): SchemaValidator {
        return object : SchemaValidator {
            override val schemaFormat: SchemaFormat = format

            override suspend fun validate(
                credential: VerifiableCredential,
                schema: JsonObject
            ): SchemaValidationResult {
                return SchemaValidationResult(valid = true)
            }

            override suspend fun validateClaims(
                claims: Claims,
                schema: JsonObject
            ): SchemaValidationResult {
                return SchemaValidationResult(valid = true)
            }
        }
    }

    @Test
    fun `test branch register stores validator`() {
        val validator = createMockValidator()

        registry.register(validator)

        assertEquals(validator, registry.get(SchemaFormat.JSON_SCHEMA))
    }

    @Test
    fun `test branch unregister removes validator`() {
        val validator = createMockValidator()
        registry.register(validator)

        registry.unregister(SchemaFormat.JSON_SCHEMA)

        assertNull(registry.get(SchemaFormat.JSON_SCHEMA))
    }

    @Test
    fun `test branch get returns registered validator`() {
        val validator = createMockValidator()
        registry.register(validator)

        val retrieved = registry.get(SchemaFormat.JSON_SCHEMA)

        assertEquals(validator, retrieved)
    }

    @Test
    fun `test branch get returns null`() {
        val retrieved = registry.get(SchemaFormat.JSON_SCHEMA)

        assertNull(retrieved)
    }

    // Note: validate, validateCredentialSubject, and detectSchemaFormat methods
    // are no longer available on SchemaValidatorRegistry in the new API.
    // These tests are commented out as the functionality has been moved to SchemaRegistry.

    @Test
    fun `test branch hasValidator returns true`() {
        val validator = createMockValidator()
        registry.register(validator)

        assertTrue(registry.hasValidator(SchemaFormat.JSON_SCHEMA))
    }

    @Test
    fun `test branch hasValidator returns false`() {
        assertFalse(registry.hasValidator(SchemaFormat.JSON_SCHEMA))
    }

    @Test
    fun `test branch getRegisteredFormats returns formats`() {
        val validator = createMockValidator()
        registry.register(validator)

        val formats = registry.getRegisteredFormats()

        assertTrue(formats.contains(SchemaFormat.JSON_SCHEMA))
    }

    @Test
    fun `test branch clear removes all validators`() {
        val validator = createMockValidator()
        registry.register(validator)

        registry.clear()

        assertTrue(registry.getRegisteredFormats().isEmpty())
    }

    private fun createTestCredential(): VerifiableCredential {
        val subjectId = "did:key:subject"
        val subjectClaims = buildJsonObject {
            put("name", "John Doe")
        }
        return VerifiableCredential(
            type = listOf(CredentialType.VerifiableCredential, CredentialType.Custom("PersonCredential")),
            issuer = Issuer.fromDid(Did("did:key:issuer")),
            credentialSubject = CredentialSubject.fromDid(Did(subjectId), claims = subjectClaims),
            issuanceDate = Clock.System.now()
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
