package org.trustweave.did.util

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class XmlDateTimeTest {

    @Serializable
    private data class Holder(
        @Serializable(with = XmlDateTimeSerializer::class) val at: Instant
    )

    @Test
    fun `sub-second precision is truncated`() {
        val instant = Instant.parse("2020-12-20T19:17:47.123456789Z")
        assertEquals("2020-12-20T19:17:47Z", instant.toXmlDateTime())
    }

    @Test
    fun `whole-second instants are unchanged`() {
        val instant = Instant.parse("2020-12-20T19:17:47Z")
        assertEquals("2020-12-20T19:17:47Z", instant.toXmlDateTime())
    }

    @Test
    fun `non-UTC offsets are normalized to Z`() {
        val instant = Instant.parse("2020-12-20T20:17:47+01:00")
        assertEquals("2020-12-20T19:17:47Z", instant.toXmlDateTime())
    }

    @Test
    fun `serializer emits truncated UTC form`() {
        val json = Json.encodeToString(Holder.serializer(), Holder(Instant.parse("2024-06-01T19:07:24.5Z")))
        assertEquals("""{"at":"2024-06-01T19:07:24Z"}""", json)
    }

    @Test
    fun `serializer round-trips a truncated value`() {
        val decoded = Json.decodeFromString(Holder.serializer(), """{"at":"2024-06-01T19:07:24Z"}""")
        assertEquals(Instant.parse("2024-06-01T19:07:24Z"), decoded.at)
    }
}
