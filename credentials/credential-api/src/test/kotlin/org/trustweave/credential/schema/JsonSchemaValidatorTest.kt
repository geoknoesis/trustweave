package org.trustweave.credential.schema

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.trustweave.core.identifiers.Iri
import org.trustweave.credential.model.CredentialType
import org.trustweave.credential.model.SchemaFormat
import org.trustweave.credential.model.vc.CredentialSubject
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Schema validation gates issuance, and it had no tests at all.
 *
 * Each case is written as a claim about what the validator accepts or rejects, because that is
 * what a caller depends on. Where the implementation supports less of JSON Schema than the
 * keyword name suggests, the test says so rather than skirting the case.
 */
class JsonSchemaValidatorTest {
    private val validator = SchemaRegistries.defaultValidatorRegistry().get(SchemaFormat.JSON_SCHEMA)!!

    private fun schema(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    private fun claims(json: String): Map<String, JsonElement> = (Json.parseToJsonElement(json) as JsonObject).toMap()

    private fun validate(
        schemaJson: String,
        claimsJson: String,
    ) = runBlocking { validator.validateClaims(claims(claimsJson), schema(schemaJson)) }

    private fun assertValid(
        schemaJson: String,
        claimsJson: String,
    ) {
        val result = validate(schemaJson, claimsJson)
        assertTrue(result.valid, "expected valid, got ${result.errors}")
    }

    private fun assertRejects(
        schemaJson: String,
        claimsJson: String,
        code: String,
    ) {
        val result = validate(schemaJson, claimsJson)
        assertFalse(result.valid, "expected a rejection")
        assertTrue(code in result.errors.map { it.code }, "expected code '$code', got ${result.errors.map { it.code }}")
    }

    // ---------------------------------------------------------------- required and properties

    @Test
    fun `a missing required field is rejected and its path names the field`() {
        val s = """{"type":"object","required":["name"],"properties":{"name":{"type":"string"}}}"""
        val result = validate(s, """{"other":"x"}""")
        assertFalse(result.valid)
        val error = result.errors.single { it.code == "required" }
        assertEquals("//name", error.path)
        assertTrue("name" in error.message)
    }

    @Test
    fun `a present required field is accepted`() {
        assertValid(
            """{"type":"object","required":["name"],"properties":{"name":{"type":"string"}}}""",
            """{"name":"Ada"}""",
        )
    }

    @Test
    fun `additionalProperties false rejects an unknown property`() {
        assertRejects(
            """{"type":"object","additionalProperties":false,"properties":{"name":{"type":"string"}}}""",
            """{"name":"Ada","sneaky":"x"}""",
            "additional_properties",
        )
    }

    @Test
    fun `additionalProperties false with no declared properties forbids every property`() {
        // JSON Schema applies additionalProperties to everything "properties" did not match, so
        // with no "properties" at all it matches nothing and forbids everything. This check used
        // to be nested inside the properties loop, which made such a schema constrain nothing.
        assertRejects("""{"type":"object","additionalProperties":false}""", """{"anything":"at all"}""", "additional_properties")
        assertValid("""{"type":"object","additionalProperties":false}""", """{}""")
    }

    @Test
    fun `additionalProperties true or absent permits extras`() {
        assertValid("""{"type":"object","additionalProperties":true}""", """{"extra":1}""")
        assertValid("""{"type":"object"}""", """{"extra":1}""")
    }

    @Test
    fun `minProperties and maxProperties bound the object`() {
        assertRejects("""{"type":"object","minProperties":2}""", """{"a":1}""", "min_properties")
        assertRejects("""{"type":"object","maxProperties":1}""", """{"a":1,"b":2}""", "max_properties")
        assertValid("""{"type":"object","minProperties":1,"maxProperties":2}""", """{"a":1}""")
    }

    // ---------------------------------------------------------------- types

    @Test
    fun `a type mismatch is rejected`() {
        assertRejects(
            """{"type":"object","properties":{"age":{"type":"integer"}}}""",
            """{"age":"not a number"}""",
            "type_mismatch",
        )
    }

    @Test
    fun `an integer satisfies a number schema but not the reverse`() {
        assertValid("""{"type":"object","properties":{"v":{"type":"number"}}}""", """{"v":3}""")
        assertRejects(
            """{"type":"object","properties":{"v":{"type":"integer"}}}""",
            """{"v":3.5}""",
            "type_mismatch",
        )
    }

    @Test
    fun `a type mismatch stops further checks on that value`() {
        // The validator returns early on a type mismatch because anything it said afterwards
        // would be about a value of the wrong shape. One error, not a cascade.
        val result =
            validate(
                """{"type":"object","properties":{"v":{"type":"string","minLength":10,"pattern":"^z"}}}""",
                """{"v":7}""",
            )
        assertEquals(listOf("type_mismatch"), result.errors.map { it.code })
    }

    @Test
    fun `booleans, nulls and arrays are recognised as their own types`() {
        assertValid("""{"type":"object","properties":{"v":{"type":"boolean"}}}""", """{"v":true}""")
        assertValid("""{"type":"object","properties":{"v":{"type":"null"}}}""", """{"v":null}""")
        assertValid("""{"type":"object","properties":{"v":{"type":"array"}}}""", """{"v":[]}""")
        assertRejects("""{"type":"object","properties":{"v":{"type":"boolean"}}}""", """{"v":"true"}""", "type_mismatch")
    }

    // ---------------------------------------------------------------- const and enum

    @Test
    fun `const requires the exact value`() {
        val s = """{"type":"object","properties":{"v":{"const":"fixed"}}}"""
        assertValid(s, """{"v":"fixed"}""")
        assertRejects(s, """{"v":"other"}""", "const_mismatch")
    }

    @Test
    fun `enum restricts to its members`() {
        val s = """{"type":"object","properties":{"v":{"enum":["a","b"]}}}"""
        assertValid(s, """{"v":"b"}""")
        assertRejects(s, """{"v":"c"}""", "enum_mismatch")
    }

    // ---------------------------------------------------------------- strings

    @Test
    fun `string length bounds are enforced`() {
        val s = """{"type":"object","properties":{"v":{"type":"string","minLength":2,"maxLength":4}}}"""
        assertValid(s, """{"v":"abc"}""")
        assertRejects(s, """{"v":"a"}""", "min_length")
        assertRejects(s, """{"v":"abcde"}""", "max_length")
    }

    @Test
    fun `pattern is an unanchored search, as JSON Schema specifies`() {
        val s = """{"type":"object","properties":{"v":{"type":"string","pattern":"b+c"}}}"""
        assertValid(s, """{"v":"xxbbcyy"}""")
        assertRejects(s, """{"v":"xxx"}""", "pattern")
    }

    @Test
    fun `the supported formats accept well-formed values and reject malformed ones`() {
        fun check(
            format: String,
            good: String,
            bad: String,
            code: String,
        ) {
            val s = """{"type":"object","properties":{"v":{"type":"string","format":"$format"}}}"""
            assertValid(s, """{"v":"$good"}""")
            assertRejects(s, """{"v":"$bad"}""", code)
        }
        check("email", "ada@example.com", "ada-at-example", "format_email")
        check("uri", "https://example.com/x", "not a uri", "format_uri")
        check("iri", "did:example:123", "nope", "format_uri")
        check("date", "2026-09-13", "13-09-2026", "format_date")
        check("date-time", "2026-09-13T10:00:00Z", "2026-09-13", "format_date_time")
        check("uuid", "123e4567-e89b-12d3-a456-426614174000", "123e4567", "format_uuid")
    }

    @Test
    fun `an unrecognised format is not an error`() {
        assertValid(
            """{"type":"object","properties":{"v":{"type":"string","format":"hostname"}}}""",
            """{"v":"anything"}""",
        )
    }

    // ---------------------------------------------------------------- numbers

    @Test
    fun `numeric bounds are enforced, inclusive and exclusive`() {
        val s =
            """{"type":"object","properties":{"v":{"type":"number","minimum":1,"maximum":10}}}"""
        assertValid(s, """{"v":1}""")
        assertValid(s, """{"v":10}""")
        assertRejects(s, """{"v":0}""", "minimum")
        assertRejects(s, """{"v":11}""", "maximum")

        val exclusive =
            """{"type":"object","properties":{"v":{"type":"number","exclusiveMinimum":1,"exclusiveMaximum":10}}}"""
        assertValid(exclusive, """{"v":5}""")
        assertRejects(exclusive, """{"v":1}""", "exclusive_minimum")
        assertRejects(exclusive, """{"v":10}""", "exclusive_maximum")
    }

    @Test
    fun `multipleOf is enforced and a zero divisor does not divide by zero`() {
        assertValid("""{"type":"object","properties":{"v":{"type":"number","multipleOf":5}}}""", """{"v":15}""")
        assertRejects("""{"type":"object","properties":{"v":{"type":"number","multipleOf":5}}}""", """{"v":7}""", "multiple_of")
        assertValid("""{"type":"object","properties":{"v":{"type":"number","multipleOf":0}}}""", """{"v":7}""")
    }

    // ---------------------------------------------------------------- arrays

    @Test
    fun `array bounds and item schemas are enforced`() {
        val s =
            """{"type":"object","properties":{"v":{"type":"array","minItems":1,"maxItems":2,"items":{"type":"string"}}}}"""
        assertValid(s, """{"v":["a"]}""")
        assertRejects(s, """{"v":[]}""", "min_items")
        assertRejects(s, """{"v":["a","b","c"]}""", "max_items")
        assertRejects(s, """{"v":[1]}""", "type_mismatch")
    }

    @Test
    fun `an item error carries the index in its path`() {
        val result =
            validate(
                """{"type":"object","properties":{"v":{"type":"array","items":{"type":"string"}}}}""",
                """{"v":["ok",2]}""",
            )
        assertEquals("//v/1", result.errors.single().path)
    }

    @Test
    fun `uniqueItems rejects a repeat`() {
        val s = """{"type":"object","properties":{"v":{"type":"array","uniqueItems":true}}}"""
        assertValid(s, """{"v":["a","b"]}""")
        assertRejects(s, """{"v":["a","a"]}""", "unique_items")
    }

    // ---------------------------------------------------------------- composition

    @Test
    fun `allOf requires every branch`() {
        val s =
            """{"type":"object","properties":{"v":{"allOf":[{"type":"string"},{"minLength":3}]}}}"""
        assertValid(s, """{"v":"abc"}""")
        assertRejects(s, """{"v":"ab"}""", "min_length")
    }

    @Test
    fun `anyOf requires at least one branch`() {
        val s =
            """{"type":"object","properties":{"v":{"anyOf":[{"type":"string"},{"type":"integer"}]}}}"""
        assertValid(s, """{"v":"a"}""")
        assertValid(s, """{"v":3}""")
        assertRejects(s, """{"v":true}""", "any_of")
    }

    @Test
    fun `oneOf requires exactly one branch`() {
        val exactlyOne =
            """{"type":"object","properties":{"v":{"oneOf":[{"type":"string"},{"type":"integer"}]}}}"""
        assertValid(exactlyOne, """{"v":"a"}""")
        assertRejects(exactlyOne, """{"v":true}""", "one_of")

        // Two branches both match, which oneOf forbids.
        val ambiguous =
            """{"type":"object","properties":{"v":{"oneOf":[{"type":"string"},{"minLength":1}]}}}"""
        assertRejects(ambiguous, """{"v":"a"}""", "one_of")
    }

    @Test
    fun `not inverts its branch`() {
        val s = """{"type":"object","properties":{"v":{"not":{"type":"string"}}}}"""
        assertValid(s, """{"v":3}""")
        assertRejects(s, """{"v":"a"}""", "not")
    }

    // ---------------------------------------------------------------- nesting

    @Test
    fun `nested objects are validated recursively and the path shows the whole route`() {
        val s =
            """
            {"type":"object","properties":{
              "address":{"type":"object","required":["city"],"properties":{
                "city":{"type":"string","minLength":2}}}}}
            """.trimIndent()
        assertValid(s, """{"address":{"city":"Oslo"}}""")

        val short = validate(s, """{"address":{"city":"X"}}""")
        assertEquals("//address/city", short.errors.single().path)

        val missing = validate(s, """{"address":{}}""")
        assertEquals("//address/city", missing.errors.single().path)
    }

    @Test
    fun `an empty schema accepts anything`() {
        assertValid("{}", """{"anything":[1,"two",{"three":null}]}""")
    }

    @Test
    fun `every violation is reported, not just the first`() {
        val s =
            """
            {"type":"object","required":["a","b"],"properties":{
              "c":{"type":"string","minLength":5,"pattern":"^z"}}}
            """.trimIndent()
        val result = validate(s, """{"c":"ab"}""")
        assertEquals(
            listOf("min_length", "pattern", "required", "required").sorted(),
            result.errors.mapNotNull { it.code }.sorted(),
            "got ${result.errors}",
        )
    }

    // ---------------------------------------------------------------- whole credentials

    @Test
    fun `validating a credential also checks its type`() =
        runBlocking<Unit> {
            val s = schema("""{"type":"object","required":["name"],"properties":{"name":{"type":"string"}}}""")
            val wrongType =
                VerifiableCredential(
                    type = listOf(CredentialType.fromString("SomethingElse")),
                    issuer = Issuer.IriIssuer(Iri("did:example:issuer")),
                    issuanceDate = Clock.System.now(),
                    credentialSubject =
                        CredentialSubject(
                            id = Iri("did:example:subject"),
                            claims = claims("""{"name":"Ada"}"""),
                        ),
                )
            val result = validator.validate(wrongType, s)
            assertFalse(result.valid)
            assertEquals("missing_type", result.errors.single().code)
            assertEquals("/type", result.errors.single().path)
        }

    @Test
    fun `a well-formed credential passes`() =
        runBlocking<Unit> {
            val s = schema("""{"type":"object","required":["name"],"properties":{"name":{"type":"string"}}}""")
            val credential =
                VerifiableCredential(
                    type = listOf(CredentialType.fromString("VerifiableCredential")),
                    issuer = Issuer.IriIssuer(Iri("did:example:issuer")),
                    issuanceDate = Clock.System.now(),
                    credentialSubject =
                        CredentialSubject(
                            id = Iri("did:example:subject"),
                            claims = claims("""{"name":"Ada"}"""),
                        ),
                )
            assertTrue(validator.validate(credential, s).valid)
        }

    @Test
    fun `a credential whose claims break the schema is rejected alongside its type`() =
        runBlocking<Unit> {
            val s = schema("""{"type":"object","required":["name"],"properties":{"name":{"type":"string"}}}""")
            val credential =
                VerifiableCredential(
                    type = listOf(CredentialType.fromString("SomethingElse")),
                    issuer = Issuer.IriIssuer(Iri("did:example:issuer")),
                    issuanceDate = Clock.System.now(),
                    credentialSubject = CredentialSubject(id = Iri("did:example:subject"), claims = emptyMap()),
                )
            val result = validator.validate(credential, s)
            assertEquals(listOf("missing_type", "required"), result.errors.mapNotNull { it.code }.sorted())
        }
}
