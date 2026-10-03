package org.trustweave.core.serialization

import kotlinx.datetime.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstantSerializerTest {
    private val json = Json

    @Test
    fun roundTripsAsIso8601String() {
        val instant = Instant.parse("2024-01-01T00:00:00Z")
        val text = json.encodeToString(InstantSerializer, instant)
        assertEquals("\"2024-01-01T00:00:00Z\"", text)
        assertEquals(instant, json.decodeFromString(InstantSerializer, text))
    }

    @Test
    fun keepsSubSecondPrecision() {
        val instant = Instant.parse("2024-06-30T12:34:56.789Z")
        assertEquals(instant, json.decodeFromString(InstantSerializer, json.encodeToString(InstantSerializer, instant)))
    }

    @Test
    fun rejectsMalformedInputWithADescriptiveSerializationException() {
        val e = assertFailsWith<SerializationException> { json.decodeFromString(InstantSerializer, "\"not-a-date\"") }
        assertTrue(e.message!!.contains("not-a-date") && e.message!!.contains("ISO-8601"))
        assertFailsWith<SerializationException> { json.decodeFromString(InstantSerializer, "\"2024-01-01\"") }
    }

    @Test
    fun nullableSerializerHandlesNullAndValues() {
        assertEquals("null", json.encodeToString(NullableInstantSerializer, null))
        assertNull(json.decodeFromString(NullableInstantSerializer, "null"))
        val instant = Instant.parse("2025-02-03T04:05:06Z")
        assertEquals(instant, json.decodeFromString(NullableInstantSerializer, json.encodeToString(NullableInstantSerializer, instant)))
    }

    @Test
    fun nonStringTokenIsRejected() {
        assertFailsWith<SerializationException> { json.decodeFromString(InstantSerializer, "123") }
        assertEquals("x", json.decodeFromString(String.serializer(), "\"x\""))
    }
}
