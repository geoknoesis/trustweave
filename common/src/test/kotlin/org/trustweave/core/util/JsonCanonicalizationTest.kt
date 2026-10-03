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
            "9007199254740993" to "9007199254740992",
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
}
