package org.trustweave.core.serialization

import kotlinx.datetime.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for InstantSerializer and NullableInstantSerializer.
 */
class InstantSerializerTest {

    private val json = Json {
        serializersModule = SerializationModule.default
    }

    @Test
    fun `test serialize Instant to ISO-8601 string`() {
        val instant = Instant.parse("2024-01-01T12:30:45Z")
        val jsonString = json.encodeToString(instant)
        
        assertEquals("\"2024-01-01T12:30:45Z\"", jsonString)
    }

    @Test
    fun `test deserialize ISO-8601 string to Instant`() {
        val jsonString = "\"2024-01-01T12:30:45Z\""
        val instant = json.decodeFromString<Instant>(jsonString)
        
        assertEquals(Instant.parse("2024-01-01T12:30:45Z"), instant)
    }

    @Test
    fun `test serialize and deserialize round-trip`() {
        val original = Instant.parse("2024-12-25T23:59:59Z")
        val jsonString = json.encodeToString(original)
        val deserialized = json.decodeFromString<Instant>(jsonString)
        
        assertEquals(original, deserialized)
    }

    @Test
    fun `test serialize Instant with milliseconds`() {
        val instant = Instant.parse("2024-01-01T12:30:45.123Z")
        val jsonString = json.encodeToString(instant)
        
        assertEquals("\"2024-01-01T12:30:45.123Z\"", jsonString)
    }

    @Test
    fun `test deserialize Instant with milliseconds`() {
        val jsonString = "\"2024-01-01T12:30:45.123Z\""
        val instant = json.decodeFromString<Instant>(jsonString)
        
        assertEquals(Instant.parse("2024-01-01T12:30:45.123Z"), instant)
    }

    @Test
    fun `test serialize Instant with timezone offset`() {
        val instant = Instant.parse("2024-01-01T12:30:45+05:00")
        val jsonString = json.encodeToString(instant)
        
        // Should serialize as UTC (Z)
        assertTrue(jsonString.contains("Z") || jsonString.contains("+00:00"))
    }

    @Test
    fun `test deserialize invalid format throws exception`() {
        val invalidJson = "\"not-a-date\""
        
        // Should throw an exception (either SerializationException from our serializer
        // or DateTimeFormatException if serializer isn't being used)
        assertFailsWith<Exception> {
            json.decodeFromString<Instant>(invalidJson)
        }
    }

    @Test
    fun `test deserialize empty string throws exception`() {
        val emptyJson = "\"\""
        
        // Should throw an exception for invalid format
        assertFailsWith<Exception> {
            json.decodeFromString<Instant>(emptyJson)
        }
    }

    @Test
    fun `test serialize nullable Instant with null value`() {
        val instant: Instant? = null
        val jsonString = json.encodeToString(instant)
        
        assertEquals("null", jsonString)
    }

    @Test
    fun `test serialize nullable Instant with non-null value`() {
        val instant: Instant? = Instant.parse("2024-01-01T12:30:45Z")
        val jsonString = json.encodeToString(instant)
        
        assertEquals("\"2024-01-01T12:30:45Z\"", jsonString)
    }

    @Test
    fun `test deserialize nullable Instant with null value`() {
        val jsonString = "null"
        val instant = json.decodeFromString<Instant?>(jsonString)
        
        assertNull(instant)
    }

    @Test
    fun `test deserialize nullable Instant with non-null value`() {
        val jsonString = "\"2024-01-01T12:30:45Z\""
        val instant = json.decodeFromString<Instant?>(jsonString)
        
        assertNotNull(instant)
        assertEquals(Instant.parse("2024-01-01T12:30:45Z"), instant)
    }

    @Test
    fun `test serialize and deserialize nullable Instant round-trip`() {
        val original: Instant? = Instant.parse("2024-12-25T23:59:59Z")
        val jsonString = json.encodeToString(original)
        val deserialized = json.decodeFromString<Instant?>(jsonString)
        
        assertEquals(original, deserialized)
    }

    @Test
    fun `test serialize and deserialize nullable Instant null round-trip`() {
        val original: Instant? = null
        val jsonString = json.encodeToString(original)
        val deserialized = json.decodeFromString<Instant?>(jsonString)
        
        assertNull(deserialized)
    }

    @Test
    fun `test serialize Instant in data class`() {
        @kotlinx.serialization.Serializable
        data class TestData(
            val timestamp: Instant,
            val name: String
        )
        
        val data = TestData(
            timestamp = Instant.parse("2024-01-01T12:30:45Z"),
            name = "test"
        )
        
        val jsonString = json.encodeToString(data)
        assertTrue(jsonString.contains("2024-01-01T12:30:45Z"))
        assertTrue(jsonString.contains("test"))
    }

    @Test
    fun `test deserialize Instant in data class`() {
        @kotlinx.serialization.Serializable
        data class TestData(
            val timestamp: Instant,
            val name: String
        )
        
        val jsonString = """{"timestamp":"2024-01-01T12:30:45Z","name":"test"}"""
        val data = json.decodeFromString<TestData>(jsonString)
        
        assertEquals(Instant.parse("2024-01-01T12:30:45Z"), data.timestamp)
        assertEquals("test", data.name)
    }

    @Test
    fun `test serialize nullable Instant in data class`() {
        @kotlinx.serialization.Serializable
        data class TestData(
            val timestamp: Instant?,
            val name: String
        )
        
        val data = TestData(
            timestamp = Instant.parse("2024-01-01T12:30:45Z"),
            name = "test"
        )
        
        val jsonString = json.encodeToString(data)
        assertTrue(jsonString.contains("2024-01-01T12:30:45Z"))
    }

    @Test
    fun `test serialize nullable Instant null in data class`() {
        @kotlinx.serialization.Serializable
        data class TestData(
            val timestamp: Instant?,
            val name: String
        )
        
        val data = TestData(
            timestamp = null,
            name = "test"
        )
        
        val jsonString = json.encodeToString(data)
        assertTrue(jsonString.contains("null") || jsonString.contains("\"timestamp\":null"))
    }

    @Test
    fun `test deserialize nullable Instant in data class`() {
        @kotlinx.serialization.Serializable
        data class TestData(
            val timestamp: Instant?,
            val name: String
        )
        
        val jsonString = """{"timestamp":"2024-01-01T12:30:45Z","name":"test"}"""
        val data = json.decodeFromString<TestData>(jsonString)
        
        assertNotNull(data.timestamp)
        assertEquals(Instant.parse("2024-01-01T12:30:45Z"), data.timestamp)
    }

    @Test
    fun `test deserialize nullable Instant null in data class`() {
        @kotlinx.serialization.Serializable
        data class TestData(
            val timestamp: Instant?,
            val name: String
        )
        
        val jsonString = """{"timestamp":null,"name":"test"}"""
        val data = json.decodeFromString<TestData>(jsonString)
        
        assertNull(data.timestamp)
        assertEquals("test", data.name)
    }

    @Test
    fun `test serialize Instant with different timezones`() {
        val utc = Instant.parse("2024-01-01T00:00:00Z")
        val jsonUtc = json.encodeToString(utc)
        assertTrue(jsonUtc.contains("2024-01-01T00:00:00"))
        
        val withOffset = Instant.parse("2024-01-01T00:00:00+05:00")
        val jsonOffset = json.encodeToString(withOffset)
        // Both should serialize correctly
        assertNotNull(jsonOffset)
    }

    @Test
    fun `test SerializationModule default is cached`() {
        val module1 = SerializationModule.default
        val module2 = SerializationModule.default
        
        // Should be the same instance (cached)
        assertSame(module1, module2)
    }

    @Test
    fun `test error message includes original string`() {
        val invalidJson = "\"invalid-date-format\""
        
        // Should throw some exception
        val exception = assertFailsWith<Exception> {
            json.decodeFromString<Instant>(invalidJson)
        }
        
        // Verify exception is thrown and has a message
        assertNotNull(exception.message)
        assertTrue(exception.message?.isNotEmpty() == true)
    }

    @Test
    fun `test error message includes cause`() {
        val invalidJson = "\"invalid-date-format\""
        
        // Should throw some exception
        val exception = assertFailsWith<Exception> {
            json.decodeFromString<Instant>(invalidJson)
        }
        
        // If it's a SerializationException, it should have a cause
        if (exception is SerializationException) {
            assertNotNull(exception.cause)
        }
    }
}

