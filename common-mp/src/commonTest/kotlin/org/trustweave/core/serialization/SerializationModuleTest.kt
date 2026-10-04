package org.trustweave.core.serialization

import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SerializationModuleTest {
    @Serializable
    private data class Holder(
        @Contextual val at: Instant,
        @Contextual val maybe: Instant? = null,
    )

    private val json = Json { serializersModule = SerializationModule.default }

    @Test
    fun contextualInstantsRoundTripAsIsoStrings() {
        val holder = Holder(Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2025-06-30T12:30:45Z"))

        val encoded = json.encodeToString(Holder.serializer(), holder)

        assertEquals("""{"at":"2024-01-01T00:00:00Z","maybe":"2025-06-30T12:30:45Z"}""", encoded)
        assertEquals(holder, json.decodeFromString(Holder.serializer(), encoded))
    }

    @Test
    fun nullableInstantAcceptsNull() {
        val decoded = json.decodeFromString(Holder.serializer(), """{"at":"2024-01-01T00:00:00Z","maybe":null}""")
        assertNull(decoded.maybe)
    }
}
