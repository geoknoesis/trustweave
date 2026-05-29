package org.trustweave.core.json

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * DSL marker for [JsonDataBuilder]. Prevents accidental access to an outer
 * builder's receiver from inside a nested block.
 */
@DslMarker
annotation class JsonDataDsl

/**
 * Typed JSON object builder. Callers stay in plain Kotlin types — no
 * [JsonElement] / `buildJsonObject` / `put` in user code:
 *
 * ```kotlin
 * jsonData {
 *     "floodDepthCm" to 75.0
 *     "stationId" to "STA-42"
 *     "location" {
 *         "lat" to 40.7128
 *         "lon" to -74.0060
 *     }
 *     "tags" to listOf("flood", "urgent")
 * }
 * ```
 *
 * Used across the framework anywhere a `JsonObject` payload is needed (contract
 * trigger data, contract draft data, VC subject claims, audit records, …).
 */
@JsonDataDsl
class JsonDataBuilder {
    @PublishedApi
    internal val properties = mutableMapOf<String, JsonElement>()

    infix fun String.to(value: String) {
        properties[this] = JsonPrimitive(value)
    }

    infix fun String.to(value: Number) {
        properties[this] = JsonPrimitive(value)
    }

    infix fun String.to(value: Boolean) {
        properties[this] = JsonPrimitive(value)
    }

    infix fun String.to(value: Nothing?) {
        properties[this] = JsonNull
    }

    infix fun String.to(value: List<*>) {
        properties[this] = JsonArray(value.map { it.toJsonValue() })
    }

    /** Nested object using `"key" { ... }` syntax. */
    operator fun String.invoke(block: JsonDataBuilder.() -> Unit) {
        val nested = JsonDataBuilder().apply(block)
        properties[this] = nested.build()
    }

    /** Nested object using `"key" to { ... }` syntax. */
    infix fun String.to(block: JsonDataBuilder.() -> Unit) {
        val nested = JsonDataBuilder().apply(block)
        properties[this] = nested.build()
    }

    /**
     * Escape hatch for callers that already hold a raw [JsonElement] (e.g.
     * extracted from a credential subject). Prefer the typed forms above.
     */
    infix fun String.to(value: JsonElement) {
        properties[this] = value
    }

    /**
     * Catch-all overload that accepts nullable primitives (`String?`, `Int?`,
     * etc.) and arbitrary `Any?` values. Resolves only when the typed
     * overloads above don't match. `null` becomes [JsonNull].
     */
    @JvmName("toAny")
    infix fun String.to(value: Any?) {
        properties[this] = value.toJsonValue()
    }

    /**
     * Internal: assign an arbitrary value, converting Kotlin types to JSON.
     * Used by factories that accept `vararg Pair<String, Any?>`.
     */
    @PublishedApi
    internal fun putAny(key: String, value: Any?) {
        properties[key] = value.toJsonValue()
    }

    fun build(): JsonObject = JsonObject(properties.toMap())
}

/**
 * Build a [JsonObject] from a [JsonDataBuilder] block. Use this instead of
 * `buildJsonObject { put(...) }` in user code — it stays in typed Kotlin and
 * keeps `kotlinx.serialization` out of the public surface.
 */
fun jsonData(block: JsonDataBuilder.() -> Unit): JsonObject =
    JsonDataBuilder().apply(block).build()

internal fun Any?.toJsonValue(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(
        entries.associate { (k, v) ->
            requireNotNull(k) { "JSON object keys must be non-null strings" }.toString() to v.toJsonValue()
        }
    )
    is Iterable<*> -> JsonArray(map { it.toJsonValue() })
    is Array<*> -> JsonArray(map { it.toJsonValue() })
    else -> error("Unsupported value type for JSON conversion: ${this::class}")
}
