package org.trustweave.credential.schema

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.identifiers.SchemaId
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.SchemaFormat
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The registry is what turns a schema id on a credential into a validation decision.
 *
 * The cases that matter are the ones where it cannot reach a decision — an unregistered id, a
 * format with no validator — because silently returning "valid" there would make the schema
 * reference decorative.
 */
class SchemaRegistryTest {
    private val schemaId = SchemaId("https://example.com/schemas/person")

    private val definition: JsonObject =
        Json.parseToJsonElement(
            """{"type":"object","required":["name"],"properties":{"name":{"type":"string","minLength":2}}}""",
        ) as JsonObject

    private fun claims(json: String): Map<String, JsonElement> = (Json.parseToJsonElement(json) as JsonObject).toMap()

    private fun credential(claimsJson: String) =
        VerifiableCredential(
            type = listOf(CredentialType.fromString("VerifiableCredential")),
            issuer = Issuer.IriIssuer(Iri("did:example:issuer")),
            issuanceDate = Clock.System.now(),
            credentialSubject = CredentialSubject(id = Iri("did:example:subject"), claims = claims(claimsJson)),
        )

    @Test
    fun `a registered schema validates a credential and its claims`() =
        runBlocking<Unit> {
            val registry = SchemaRegistries.default()
            assertTrue(registry.registerSchema(schemaId, SchemaFormat.JSON_SCHEMA, definition).success)

            assertTrue(registry.validate(credential("""{"name":"Ada"}"""), schemaId).valid)
            assertFalse(registry.validate(credential("""{"name":"A"}"""), schemaId).valid)
            assertTrue(registry.validateClaims(claims("""{"name":"Ada"}"""), schemaId).valid)
            assertFalse(registry.validateClaims(claims("""{}"""), schemaId).valid)
        }

    @Test
    fun `registration records the definition and its format`() =
        runBlocking<Unit> {
            val registry = SchemaRegistries.default()
            val result = registry.registerSchema(schemaId, SchemaFormat.JSON_SCHEMA, definition)
            assertEquals(schemaId, result.schemaId)
            assertNull(result.error)

            assertTrue(registry.isRegistered(schemaId))
            assertEquals(definition, registry.getSchemaDefinition(schemaId))
            assertEquals(SchemaFormat.JSON_SCHEMA, registry.getSchemaFormat(schemaId))
            assertEquals(listOf(schemaId), registry.getAllSchemaIds())
        }

    @Test
    fun `an unregistered schema is not silently treated as valid`() =
        runBlocking<Unit> {
            val registry = SchemaRegistries.default()
            val unknown = SchemaId("https://example.com/schemas/never-registered")

            assertFalse(registry.isRegistered(unknown))
            assertNull(registry.getSchemaDefinition(unknown))
            assertNull(registry.getSchemaFormat(unknown))

            // Refusing loudly is the point: a credential naming a schema nobody registered must
            // not pass validation just because the registry had nothing to check it against.
            assertFailsWith<IllegalArgumentException> { registry.validate(credential("""{"name":"Ada"}"""), unknown) }
            assertFailsWith<IllegalArgumentException> { registry.validateClaims(claims("""{"name":"Ada"}"""), unknown) }
        }

    @Test
    fun `a format with no registered validator is refused rather than skipped`() =
        runBlocking<Unit> {
            // An empty validator registry: the schema is registered, but nothing can check it.
            val registry = SchemaRegistries.withValidatorRegistry(EmptyValidatorRegistry())
            registry.registerSchema(schemaId, SchemaFormat.JSON_SCHEMA, definition)

            val failure =
                assertFailsWith<IllegalArgumentException> {
                    registry.validate(credential("""{"name":"Ada"}"""), schemaId)
                }
            assertTrue("validator" in (failure.message ?: "").lowercase(), failure.message ?: "")
        }

    @Test
    fun `re-registering replaces the definition`() =
        runBlocking<Unit> {
            val registry = SchemaRegistries.default()
            registry.registerSchema(schemaId, SchemaFormat.JSON_SCHEMA, definition)
            assertFalse(registry.validateClaims(claims("""{}"""), schemaId).valid)

            val permissive = Json.parseToJsonElement("""{"type":"object"}""") as JsonObject
            registry.registerSchema(schemaId, SchemaFormat.JSON_SCHEMA, permissive)
            assertTrue(registry.validateClaims(claims("""{}"""), schemaId).valid)
            assertEquals(1, registry.getAllSchemaIds().size, "re-registering must not create a second entry")
        }

    @Test
    fun `unregistering removes the schema and its format`() =
        runBlocking<Unit> {
            val registry = SchemaRegistries.default()
            registry.registerSchema(schemaId, SchemaFormat.JSON_SCHEMA, definition)

            assertTrue(registry.unregister(schemaId))
            assertFalse(registry.unregister(schemaId), "a second unregister reports nothing was removed")
            assertFalse(registry.isRegistered(schemaId))
            assertNull(registry.getSchemaFormat(schemaId))
            assertFailsWith<IllegalArgumentException> { registry.validateClaims(claims("""{"name":"Ada"}"""), schemaId) }
        }

    @Test
    fun `clear empties the registry`() =
        runBlocking<Unit> {
            val registry = SchemaRegistries.default()
            registry.registerSchema(schemaId, SchemaFormat.JSON_SCHEMA, definition)
            registry.registerSchema(SchemaId("https://example.com/schemas/other"), SchemaFormat.JSON_SCHEMA, definition)
            assertEquals(2, registry.getAllSchemaIds().size)

            registry.clear()
            assertEquals(emptyList(), registry.getAllSchemaIds())
            assertFalse(registry.isRegistered(schemaId))
        }

    @Test
    fun `the default validator registry carries the built-in formats`() {
        val validators = SchemaRegistries.defaultValidatorRegistry()
        assertEquals(SchemaFormat.JSON_SCHEMA, validators.get(SchemaFormat.JSON_SCHEMA)?.schemaFormat)
        assertEquals(SchemaFormat.SHACL, validators.get(SchemaFormat.SHACL)?.schemaFormat)
    }

    /** A registry that knows no validators, so "no validator for this format" is reachable. */
    private class EmptyValidatorRegistry : SchemaValidatorRegistry {
        override fun register(validator: SchemaValidator) = Unit

        override fun unregister(format: SchemaFormat): Boolean = false

        override fun get(format: SchemaFormat): SchemaValidator? = null

        override fun hasValidator(format: SchemaFormat): Boolean = false

        override fun getRegisteredFormats(): List<SchemaFormat> = emptyList()

        override fun clear() = Unit
    }
}
