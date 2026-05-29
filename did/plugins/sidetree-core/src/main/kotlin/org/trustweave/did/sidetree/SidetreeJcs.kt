package org.trustweave.did.sidetree

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * JCS (JSON Canonicalization Scheme, RFC 8785) helpers used throughout the
 * Sidetree protocol: object keys are sorted lexicographically; arrays preserve
 * order; no whitespace; UTF-8 encoding.
 *
 * This is a deliberately small implementation — Sidetree only ever canonicalizes
 * objects with string keys, simple values, and nested objects/arrays, so we
 * don't need to handle the full RFC 8785 number-formatting edge cases.
 */
object SidetreeJcs {

    fun canonicalize(obj: JsonObject): ByteArray =
        build(obj).toByteArray(StandardCharsets.UTF_8)

    fun canonicalize(map: Map<String, Any?>): ByteArray =
        build(mapToJsonObject(map)).toByteArray(StandardCharsets.UTF_8)

    fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    /**
     * Multihash-encoded SHA-256 digest: a `[0x12, 0x20]` prefix (SHA-256 algorithm
     * code 0x12 followed by the digest length 0x20 = 32) concatenated with the raw
     * SHA-256 output.
     *
     * Sidetree v1.0.0 §5.4 requires every hash that appears on the wire
     * (`revealValue`, `deltaHash`, commitments, the DID suffix, …) to be
     * multihash-framed so future protocol versions can rotate the hash algorithm
     * without breaking existing payloads.
     */
    fun multihashSha256(bytes: ByteArray): ByteArray {
        val digest = sha256(bytes)
        val out = ByteArray(digest.size + 2)
        out[0] = MULTIHASH_SHA256_CODE
        out[1] = MULTIHASH_SHA256_LEN
        System.arraycopy(digest, 0, out, 2, digest.size)
        return out
    }

    /** SHA-256 multihash algorithm code (0x12 = 18 in the multicodec table). */
    private const val MULTIHASH_SHA256_CODE: Byte = 0x12

    /** SHA-256 digest length in bytes (0x20 = 32). */
    private const val MULTIHASH_SHA256_LEN: Byte = 0x20

    private fun build(element: JsonElement): String = when (element) {
        is JsonObject -> element.entries
            .sortedBy { it.key }
            .joinToString(",", "{", "}") { (k, v) -> "\"$k\":${build(v)}" }
        is JsonArray -> element.joinToString(",", "[", "]") { build(it) }
        is JsonPrimitive -> element.toString()
        else -> element.toString()
    }

    /**
     * Best-effort conversion from a generic `Map<String, Any?>` (the form
     * Sidetree uses for JWK fragments) into a [JsonObject]. Lists and nested
     * maps are recursed; everything else falls through to `toString()`.
     */
    fun mapToJsonObject(map: Map<String, Any?>): JsonObject = buildJsonObject {
        map.forEach { (key, value) -> put(key, valueToJson(value)) }
    }

    private fun valueToJson(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> {
            @Suppress("UNCHECKED_CAST")
            mapToJsonObject(value as Map<String, Any?>)
        }
        is List<*> -> JsonArray(value.map { valueToJson(it) })
        is JsonElement -> value
        else -> JsonPrimitive(value.toString())
    }
}
