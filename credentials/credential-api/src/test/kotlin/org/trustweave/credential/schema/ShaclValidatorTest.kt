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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The SHACL validator's own documentation calls it a basic implementation, and it had no tests.
 *
 * That combination is the risk: a caller reading `SchemaFormat.SHACL` reasonably expects SHACL,
 * and the only way to know what this actually enforces is to state it. Each case below is written
 * as what a shape does or does not constrain, including the places where the answer is "less than
 * the keyword suggests".
 */
class ShaclValidatorTest {
    private val validator = SchemaRegistries.defaultValidatorRegistry().get(SchemaFormat.SHACL)!!

    private fun shape(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject

    private fun claims(json: String): Map<String, JsonElement> = (Json.parseToJsonElement(json) as JsonObject).toMap()

    private fun credential(
        claimsJson: String,
        types: List<String> = listOf("VerifiableCredential"),
        issuer: String = "did:example:issuer",
    ) = VerifiableCredential(
        type = types.map { CredentialType.fromString(it) },
        issuer = Issuer.IriIssuer(Iri(issuer)),
        issuanceDate = Clock.System.now(),
        credentialSubject = CredentialSubject(id = Iri("did:example:subject"), claims = claims(claimsJson)),
    )

    private fun validateClaims(
        shapeJson: String,
        claimsJson: String,
    ) = runBlocking { validator.validateClaims(claims(claimsJson), shape(shapeJson)) }

    private fun assertClaimsValid(
        shapeJson: String,
        claimsJson: String,
    ) {
        val result = validateClaims(shapeJson, claimsJson)
        assertTrue(result.valid, "expected valid, got ${result.errors}")
    }

    private fun assertClaimsRejected(
        shapeJson: String,
        claimsJson: String,
        code: String,
    ) {
        val result = validateClaims(shapeJson, claimsJson)
        assertFalse(result.valid, "expected a rejection")
        assertTrue(code in result.errors.map { it.code }, "expected '$code', got ${result.errors.map { it.code }}")
    }

    private fun property(body: String) = """{"sh:property":[{$body}]}"""

    // ---------------------------------------------------------------- credential structure

    @Test
    fun `a credential missing the VerifiableCredential type is rejected`() =
        runBlocking<Unit> {
            val result = validator.validate(credential("""{}""", types = listOf("DegreeCredential")), shape("{}"))
            assertFalse(result.valid)
            assertEquals("missing_type", result.errors.single().code)
        }

    @Test
    fun `a blank issuer cannot reach the validator at all`() {
        // The validator carries a missing_issuer branch, but Iri refuses to hold a blank value,
        // so the model makes that branch unreachable. Recorded so the dead check is a known
        // belt-and-braces rather than something a reader assumes is load-bearing.
        assertFailsWith<IllegalArgumentException> { credential("""{}""", issuer = "") }
    }

    @Test
    fun `a well-formed credential against an empty shape passes`() =
        runBlocking<Unit> {
            assertTrue(validator.validate(credential("""{"degree":"BSc"}"""), shape("{}")).valid)
        }

    // ---------------------------------------------------------------- target class

    @Test
    fun `targetClass must match the credential's non-base type`() =
        runBlocking<Unit> {
            val s = shape("""{"sh:targetClass":"DegreeCredential"}""")
            val matching = credential("""{}""", types = listOf("VerifiableCredential", "DegreeCredential"))
            assertTrue(validator.validate(matching, s).valid)

            val other = credential("""{}""", types = listOf("VerifiableCredential", "MembershipCredential"))
            val result = validator.validate(other, s)
            assertFalse(result.valid)
            assertEquals("type_mismatch", result.errors.single().code)
            assertTrue("DegreeCredential" in result.errors.single().message)
        }

    @Test
    fun `a credential carrying only the base type fails a targetClass shape`() =
        runBlocking<Unit> {
            val result = validator.validate(credential("""{}"""), shape("""{"sh:targetClass":"DegreeCredential"}"""))
            assertFalse(result.valid, "there is no non-base type to match the target class")
        }

    // ---------------------------------------------------------------- minCount

    @Test
    fun `minCount makes a claim required`() {
        val s = property(""""sh:path":"credentialSubject.degree","sh:minCount":1""")
        assertClaimsValid(s, """{"degree":"BSc"}""")
        assertClaimsRejected(s, """{"other":"x"}""", "min_count_violation")
    }

    @Test
    fun `minCount zero leaves the claim optional`() {
        assertClaimsValid(property(""""sh:path":"degree","sh:minCount":0"""), """{}""")
    }

    @Test
    fun `a missing required claim on a credential names the credentialSubject path`() =
        runBlocking<Unit> {
            val s =
                shape(
                    """{"sh:property":[{"sh:path":"credentialSubject.degree","sh:minCount":1}]}""",
                )
            val result = validator.validate(credential("""{"other":"x"}"""), s)
            assertFalse(result.valid)
            assertEquals("/credentialSubject/degree", result.errors.single().path)
        }

    @Test
    fun `a property shape with no path constrains nothing`() {
        assertClaimsValid(property(""""sh:minCount":1"""), """{}""")
    }

    // ---------------------------------------------------------------- datatypes

    @Test
    fun `the xsd string datatype is enforced in both spellings`() {
        for (datatype in listOf("xsd:string", "http://www.w3.org/2001/XMLSchema#string")) {
            val s = property(""""sh:path":"v","sh:datatype":"$datatype"""")
            assertClaimsValid(s, """{"v":"text"}""")
            assertClaimsRejected(s, """{"v":7}""", "datatype_mismatch")
        }
    }

    @Test
    fun `the numeric datatypes check parseability rather than JSON type`() {
        val integer = property(""""sh:path":"v","sh:datatype":"xsd:integer"""")
        assertClaimsValid(integer, """{"v":42}""")
        // Same leniency as boolean: longOrNull reads the content, so a quoted number passes.
        assertClaimsValid(integer, """{"v":"42"}""")
        assertClaimsRejected(integer, """{"v":"forty-two"}""", "datatype_mismatch")
        assertClaimsRejected(integer, """{"v":4.2}""", "datatype_mismatch")

        val decimal = property(""""sh:path":"v","sh:datatype":"xsd:decimal"""")
        assertClaimsValid(decimal, """{"v":4.2}""")
        assertClaimsValid(decimal, """{"v":42}""")
        assertClaimsRejected(decimal, """{"v":"four point two"}""", "datatype_mismatch")

        val double = property(""""sh:path":"v","sh:datatype":"http://www.w3.org/2001/XMLSchema#double"""")
        assertClaimsValid(double, """{"v":1.5}""")
    }

    @Test
    fun `the boolean datatype accepts a quoted boolean, which is worth knowing`() {
        val s = property(""""sh:path":"v","sh:datatype":"xsd:boolean"""")
        assertClaimsValid(s, """{"v":true}""")
        // booleanOrNull parses the primitive's content whether or not it was quoted, so the
        // string "true" satisfies an xsd:boolean shape. Lenient rather than wrong, but a shape
        // author expecting JSON type enforcement here would be surprised.
        assertClaimsValid(s, """{"v":"true"}""")
        assertClaimsRejected(s, """{"v":"yes"}""", "datatype_mismatch")
    }

    @Test
    fun `the dateTime datatype requires a parseable instant`() {
        val s = property(""""sh:path":"v","sh:datatype":"xsd:dateTime"""")
        assertClaimsValid(s, """{"v":"2026-09-13T10:00:00Z"}""")
        assertClaimsRejected(s, """{"v":"13 September"}""", "datatype_mismatch")
        assertClaimsRejected(s, """{"v":7}""", "datatype_mismatch")
    }

    @Test
    fun `the anyURI datatype accepts the schemes this validator knows`() {
        val s = property(""""sh:path":"v","sh:datatype":"xsd:anyURI"""")
        for (uri in listOf("https://example.com", "http://example.com", "did:example:1", "urn:uuid:1")) {
            assertClaimsValid(s, """{"v":"$uri"}""")
        }
        assertClaimsRejected(s, """{"v":"example.com"}""", "datatype_mismatch")
        assertClaimsRejected(s, """{"v":5}""", "datatype_mismatch")
    }

    @Test
    fun `an unknown datatype is not enforced`() {
        // Stating it rather than skirting it: a shape naming a datatype this validator does not
        // implement constrains nothing, and a caller must not read that silence as a pass.
        assertClaimsValid(property(""""sh:path":"v","sh:datatype":"xsd:hexBinary""""), """{"v":"zzz"}""")
    }

    @Test
    fun `a datatype is only checked when the claim is present`() {
        assertClaimsValid(property(""""sh:path":"v","sh:datatype":"xsd:integer""""), """{}""")
    }

    // ---------------------------------------------------------------- strings

    @Test
    fun `length bounds are enforced on string claims`() {
        val s = property(""""sh:path":"v","sh:minLength":3,"sh:maxLength":5""")
        assertClaimsValid(s, """{"v":"abcd"}""")
        assertClaimsRejected(s, """{"v":"ab"}""", "min_length_violation")
        assertClaimsRejected(s, """{"v":"abcdef"}""", "max_length_violation")
    }

    @Test
    fun `pattern is anchored, unlike the JSON Schema validator`() {
        // SHACL here uses Matcher.matches(), which requires the whole value to match. The JSON
        // Schema validator in the same package uses containsMatchIn. The difference is real and
        // a shape author needs to know which one they are writing for.
        val s = property(""""sh:path":"v","sh:pattern":"[A-Z]{3}"""")
        assertClaimsValid(s, """{"v":"ABC"}""")
        assertClaimsRejected(s, """{"v":"xxABCxx"}""", "pattern_violation")
    }

    @Test
    fun `an unparseable pattern is reported rather than thrown`() {
        val unclosed = "{\"sh:property\":[{\"sh:path\":\"v\",\"sh:pattern\":\"[unclosed\"}]}"
        assertClaimsRejected(unclosed, """{"v":"x"}""", "invalid_pattern")
    }

    @Test
    fun `sh in restricts a string claim to the listed values`() {
        val s = property(""""sh:path":"v","sh:in":["gold","silver"]""")
        assertClaimsValid(s, """{"v":"gold"}""")
        val result = validateClaims(s, """{"v":"bronze"}""")
        assertFalse(result.valid)
        assertTrue("bronze" in result.errors.single().message, result.errors.single().message)
    }

    // ---------------------------------------------------------------- counts and shapes

    @Test
    fun `maxCount one rejects an array of more than one value`() {
        val s = property(""""sh:path":"v","sh:maxCount":1""")
        assertClaimsValid(s, """{"v":["only"]}""")
        assertClaimsRejected(s, """{"v":["a","b"]}""", "max_count_violation")
    }

    @Test
    fun `a single property shape may be given as an object rather than an array`() {
        assertClaimsRejected(
            """{"sh:property":{"sh:path":"v","sh:minCount":1}}""",
            """{}""",
            "min_count_violation",
        )
    }

    @Test
    fun `every constraint agrees about what a path names`() {
        // This is the case the validator used to get wrong: the minCount check parsed the path
        // differently from the datatype and length checks, so a dotted path — the form this
        // class's own KDoc example uses — reported a present claim as missing.
        for (path in listOf("credentialSubject.degree", "ex:degree", "http://example.com/degree", "degree")) {
            assertClaimsValid(property(""""sh:path":"$path","sh:minCount":1"""), """{"degree":"BSc"}""")
            assertClaimsRejected(property(""""sh:path":"$path","sh:minCount":1"""), """{"other":"x"}""", "min_count_violation")
        }
    }

    @Test
    fun `a shape with no properties constrains nothing`() {
        assertClaimsValid("{}", """{"anything":"at all"}""")
    }

    @Test
    fun `several violations are all reported`() {
        val s =
            """
            {"sh:property":[
              {"sh:path":"a","sh:minCount":1},
              {"sh:path":"b","sh:datatype":"xsd:integer"},
              {"sh:path":"c","sh:minLength":5}
            ]}
            """.trimIndent()
        val result = validateClaims(s, """{"b":"not an integer","c":"ab"}""")
        assertFalse(result.valid)
        assertEquals(
            listOf("datatype_mismatch", "min_count_violation", "min_length_violation", "missing_required_property"),
            result.errors.mapNotNull { it.code }.sorted(),
            "got ${result.errors}",
        )
    }
}
