package org.trustweave.credential.vi.verification

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Draft VI agent frequency is advisory; date and occurrence bounds are mandatory. */
internal object RecurrenceRules {
    private val frequencies = setOf("INDA", "DAIL", "WEEK", "TOWK", "TWMN", "MNTH", "TOMN", "QUTR", "FOMN", "SEMI", "YEAR", "TYEA")

    fun agent(
        raw: JsonObject,
        now: Long,
    ): Long {
        require(raw.keys.all { it in setOf("type", "frequency", "start_date", "end_date", "max_occurrences") }) {
            "Unsupported agent recurrence fields"
        }
        require(text(raw, "frequency") in frequencies + "ON_DEMAND") { "Unsupported agent recurrence frequency" }
        val start = date(raw, "start_date")
        val end = date(raw, "end_date")
        require(!end.isBefore(start)) { "Invalid recurrence date range" }
        val current = Instant.ofEpochSecond(now).atOffset(ZoneOffset.UTC).toLocalDate()
        require(current >= start && current <= end) { "Agent recurrence outside authorized dates" }
        return if ("max_occurrences" in raw) positive(raw, "max_occurrences") else Long.MAX_VALUE
    }

    /** Narrow checkout-JWT metadata profile: require all bounded merchant terms, never skip absent metadata. */
    fun merchant(
        constraint: JsonObject,
        metadata: JsonObject,
    ) {
        val fields = setOf("frequency", "start_date", "end_date", "number")
        require(constraint.keys.all { it in fields + "type" } && metadata.keys == fields) { "Unsupported merchant recurrence fields" }
        require(text(constraint, "frequency") in frequencies) { "Unsupported merchant recurrence frequency" }
        require(text(metadata, "frequency") == text(constraint, "frequency")) { "Merchant recurrence frequency mismatch" }
        val start = date(constraint, "start_date")
        val end = date(constraint, "end_date")
        require(end >= start && date(metadata, "start_date") == start) { "Merchant recurrence start mismatch" }
        val actualEnd = date(metadata, "end_date")
        require(actualEnd >= start && actualEnd <= end) { "Merchant recurrence exceeds end date" }
        require(positive(metadata, "number") <= positive(constraint, "number")) { "Merchant recurrence exceeds count" }
    }

    private fun text(
        raw: JsonObject,
        name: String,
    ): String = (raw[name] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw IllegalArgumentException("Invalid recurrence $name")

    private fun date(
        raw: JsonObject,
        name: String,
    ): LocalDate {
        val value = text(raw, name)
        require(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(value)) { "Invalid recurrence date" }
        return try {
            LocalDate.parse(value)
        } catch (_: java.time.DateTimeException) {
            throw IllegalArgumentException("Invalid recurrence date")
        }
    }

    private fun positive(
        raw: JsonObject,
        name: String,
    ): Long =
        (raw[name] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull?.takeIf { it > 0 }
            ?: throw IllegalArgumentException("Recurrence $name must be a positive integer")
}
