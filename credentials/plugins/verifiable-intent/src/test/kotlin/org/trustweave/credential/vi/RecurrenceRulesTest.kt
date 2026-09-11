package org.trustweave.credential.vi

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.trustweave.credential.vi.verification.RecurrenceRules
import java.time.Instant

class RecurrenceRulesTest {
    private fun obj(raw: String) = Json.parseToJsonElement(raw) as JsonObject

    private val agent =
        obj(
            """{"type":"mandate.payment.agent_recurrence","frequency":"WEEK","start_date":"2026-03-01","end_date":"2026-03-31","max_occurrences":3}""",
        )

    @Test
    fun `agent UTC dates inclusive frequencies advisory and malformed bounds rejected`() {
        for (time in listOf("2026-03-01T00:00:00Z", "2026-03-02T12:00:00Z", "2026-03-31T23:59:59Z")) {
            assertEquals(3, RecurrenceRules.agent(agent, Instant.parse(time).epochSecond))
        }
        for (time in listOf("2026-02-28T23:59:59Z", "2026-04-01T00:00:00Z")) {
            assertThrows(IllegalArgumentException::class.java) { RecurrenceRules.agent(agent, Instant.parse(time).epochSecond) }
        }
        val changes =
            listOf(
                "start_date" to JsonPrimitive("2026-02-30"),
                "end_date" to JsonPrimitive("2026-02-01"),
                "frequency" to JsonPrimitive("HOURLY"),
                "max_occurrences" to JsonPrimitive(0),
                "max_occurrences" to JsonPrimitive("3"),
                "max_occurrences" to JsonPrimitive(-1),
                "unknown" to JsonPrimitive(true),
            )
        for ((field, value) in changes) {
            assertThrows(IllegalArgumentException::class.java) {
                RecurrenceRules.agent(JsonObject(agent + (field to value)), Instant.parse("2026-03-02T00:00:00Z").epochSecond)
            }
        }
        assertEquals(
            Long.MAX_VALUE,
            RecurrenceRules.agent(JsonObject(agent - "max_occurrences"), Instant.parse("2026-03-02T00:00:00Z").epochSecond),
        )
    }

    @Test
    fun `merchant subscription metadata must match bounded signed terms`() {
        val terms = obj("""{"frequency":"MNTH","start_date":"2026-03-01","end_date":"2027-03-01","number":12}""")
        RecurrenceRules.merchant(terms, terms)
        for ((field, value) in listOf(
            "frequency" to JsonPrimitive("WEEK"),
            "start_date" to JsonPrimitive("2026-03-02"),
            "end_date" to JsonPrimitive("2027-03-02"),
            "number" to JsonPrimitive(13),
            "number" to JsonPrimitive("12"),
        )) {
            assertThrows(IllegalArgumentException::class.java) { RecurrenceRules.merchant(terms, JsonObject(terms + (field to value))) }
        }
        for (field in listOf("frequency", "start_date", "end_date", "number")) {
            assertThrows(IllegalArgumentException::class.java) { RecurrenceRules.merchant(terms, JsonObject(terms - field)) }
        }
    }
}
