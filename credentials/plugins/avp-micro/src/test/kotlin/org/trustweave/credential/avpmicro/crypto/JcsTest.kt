package org.trustweave.credential.avpmicro.crypto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class JcsTest {
    private fun canon(s: String): String =
        Jcs.canonicalize(Json.parseToJsonElement(s)).toString(Charsets.UTF_8)

    @Test
    fun `sorts object keys and strips whitespace`() {
        assertEquals("""{"a":"1","b":"2"}""", canon(""" { "b":"2", "a":"1" } """))
    }

    @Test
    fun `preserves array order and nests`() {
        assertEquals("""{"x":["2","1",{"k":"v"}]}""", canon("""{"x":["2","1",{"k":"v"}]}"""))
    }

    @Test
    fun `escapes control characters and quotes minimally`() {
        assertEquals(""""a\tb\"c\\d"""", canon(""""a\tb\"c\\d""""))
    }
}
