package org.trustweave.core.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JsonCanonicalizationTest {
    private fun jcs(json: String) = JsonCanonicalization.canonicalize(Json.parseToJsonElement(json))

    @Test
    fun `RFC 8785 section 3_2_2 example`() {
        val input =
            """
            {
              "numbers": [333333333.33333329, 1E30, 4.50, 2e-3, 0.000000000000000000000000001],
              "string": "\u20ac${'$'}\u000F\u000aA'\u0042\u0022\u005c\\\"\/",
              "literals": [null, true, false]
            }
            """.trimIndent()

        assertEquals(
            """{"literals":[null,true,false],"numbers":[333333333.3333333,1e+30,4.5,0.002,1e-27],""" +
                """"string":"€${'$'}\u000f\nA'B\"\\\\\"/"}""",
            jcs(input),
        )
    }

    @Test
    fun `RFC 8785 section 3_2_3 sorts by UTF-16 code units`() {
        val input =
            """
            {
              "\u20ac": "Euro Sign",
              "\r": "Carriage Return",
              "\ufb33": "Hebrew Letter Dalet With Dagesh",
              "1": "One",
              "\ud83d\ude00": "Emoji: Grinning Face",
              "\u0080": "Control",
              "\u00f6": "Latin Small Letter O With Diaeresis"
            }
            """.trimIndent()

        val keys = (Json.parseToJsonElement(jcs(input)) as JsonObject).keys.toList()
        // Order mandated by RFC 8785 §3.2.3 (emoji surrogates sort before U+FB33).
        assertEquals(listOf("\r", "1", "\u0080", "\u00f6", "\u20ac", "\ud83d\ude00", "\ufb33"), keys)
    }

    @Test
    fun `key order and whitespace do not change the canonical form`() {
        val expected = """{"a":"z","b":[1,{"x":1,"y":2}]}"""
        assertEquals(expected, jcs("""{ "b" : [1, {"y":2,"x":1}], "a": "z" }"""))
        assertEquals(expected, jcs(expected))
    }

    @Test
    fun `numbers follow ECMAScript Number toString`() {
        mapOf(
            "0" to "0",
            "-0" to "0",
            "1.0" to "1",
            "-1.5" to "-1.5",
            "100" to "100",
            "1e21" to "1e+21",
            "1e20" to "100000000000000000000",
            "0.000001" to "0.000001",
            "0.0000001" to "1e-7",
            "123.456e-10" to "1.23456e-8",
            "9007199254740992" to "9007199254740992",
            "-9007199254740992" to "-9007199254740992",
            "9007199254740993e0" to "9007199254740992", // exponent form: an explicit double, per RFC 8785
            "5e-324" to "5e-324",
            "1.7976931348623157e308" to "1.7976931348623157e+308",
        ).forEach { (literal, expected) ->
            assertEquals(expected, JsonCanonicalization.serializeNumber(literal), literal)
        }
    }

    @Test
    fun `non-finite numbers are rejected`() {
        assertFailsWith<IllegalArgumentException> { JsonCanonicalization.canonicalize(JsonPrimitive(Double.NaN)) }
        assertFailsWith<IllegalArgumentException> { JsonCanonicalization.serializeNumber("1e400") }
    }

    @Test
    fun `literals that are not valid JSON numbers are rejected`() {
        listOf(
            "1d",
            "1f",
            "1D",
            "0x10",
            "0x1p3",
            "NaN",
            "Infinity",
            "-Infinity",
            "+1",
            "01",
            "1.",
            ".5",
            "1e",
            "",
            " 1",
            "1 ",
            "1_0",
            "١",
        ).forEach {
            assertFailsWith<IllegalArgumentException>(it) { JsonCanonicalization.serializeNumber(it) }
        }
        // Through the element API too, as an unquoted literal that kotlinx would happily carry.
        assertFailsWith<IllegalArgumentException> {
            JsonCanonicalization.canonicalize(kotlinx.serialization.json.JsonUnquotedLiteral("1d"))
        }
    }

    @Test
    fun `lone surrogates are rejected instead of becoming question marks`() {
        val high = "a\uD800b"
        val low = "a\uDC00b"
        assertFailsWith<IllegalArgumentException> { JsonCanonicalization.canonicalize(JsonPrimitive(high)) }
        assertFailsWith<IllegalArgumentException> { JsonCanonicalization.canonicalize(JsonPrimitive(low)) }
        assertFailsWith<IllegalArgumentException> { JsonCanonicalization.canonicalize(JsonPrimitive("trailing\uD83D")) }
        assertFailsWith<IllegalArgumentException> {
            JsonCanonicalization.canonicalize(kotlinx.serialization.json.JsonObject(mapOf(high to JsonPrimitive(1))))
        }
        // A well-formed surrogate pair (U+1F600) is fine.
        assertEquals("\"\uD83D\uDE00\"", JsonCanonicalization.canonicalize(JsonPrimitive("\uD83D\uDE00")))
    }

    @Test
    fun `integers beyond 2 to the 53 are rejected rather than silently rounded`() {
        assertFailsWith<IllegalArgumentException> { JsonCanonicalization.serializeNumber("9007199254740993") }
        assertFailsWith<IllegalArgumentException> { JsonCanonicalization.serializeNumber("-9007199254740993") }
        assertFailsWith<IllegalArgumentException> { JsonCanonicalization.serializeNumber("123456789012345678901234567890") }
        // Two different big integers must never silently share canonical bytes.
        assertFailsWith<IllegalArgumentException> { JsonCanonicalization.canonicalize(JsonPrimitive(9007199254740993L)) }
    }
}
