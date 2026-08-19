package org.trustweave.did.resolver

import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DidResolutionMetadataTest {

    @Test
    fun `default content type is application-did`() {
        assertEquals("application/did", DidResolutionMetadata().contentType)
    }

    @Test
    fun `error serializes as an RFC 9457 object`() {
        val metadata = DidResolutionMetadata(error = DidResolutionError.notFound("no such DID"))
        val json = metadata.toJson()
        assertEquals(
            JsonPrimitive("https://www.w3.org/ns/did#NOT_FOUND"),
            json["error"]!!.jsonObject["type"]
        )
        assertEquals(JsonPrimitive("no such DID"), json["error"]!!.jsonObject["detail"])
    }

    @Test
    fun `retrieved timestamp is truncated to whole seconds`() {
        val metadata = DidResolutionMetadata(retrieved = Instant.parse("2024-06-01T19:07:24.987Z"))
        assertEquals("2024-06-01T19:07:24Z", metadata.toMap()["retrieved"])
    }

    @Test
    fun `fromMap upgrades a legacy string error code`() {
        val metadata = DidResolutionMetadata.fromMap(mapOf("error" to "notFound"))
        assertEquals(DidErrorType.NOT_FOUND, metadata.error?.type)
    }

    @Test
    fun `fromJson parses a CR error object`() {
        val json = buildJsonObject {
            put("contentType", "application/did")
            put(
                "error",
                buildJsonObject {
                    put("type", "https://www.w3.org/ns/did#METHOD_NOT_SUPPORTED")
                    put("detail", "did:nope is unknown")
                }
            )
        }
        val metadata = DidResolutionMetadata.fromJson(json)
        assertEquals(DidErrorType.METHOD_NOT_SUPPORTED, metadata.error?.type)
        assertEquals("did:nope is unknown", metadata.error?.detail)
    }

    @Test
    fun `unknown members are preserved in properties`() {
        val metadata = DidResolutionMetadata.fromMap(mapOf("blockNumber" to 42))
        assertEquals("42", metadata.properties["blockNumber"])
    }

    @Test
    fun `toMap omits absent members`() {
        val map = DidResolutionMetadata().toMap()
        assertEquals(setOf("contentType"), map.keys)
        assertNull(map["error"])
    }

    @Test
    fun `deprecated errorMessage reads the error detail`() {
        @Suppress("DEPRECATION")
        val message = DidResolutionMetadata(error = DidResolutionError.internalError("boom")).errorMessage
        assertEquals("boom", message)
    }

    @Test
    fun `proof entries round-trip through toJson`() {
        val proof = buildJsonObject { put("type", "DataIntegrityProof") }
        val json = DidResolutionMetadata(proof = listOf(proof)).toJson()
        assertTrue(json.containsKey("proof"))
    }

    // ─── Regression: legacy errorMessage must survive alongside a legacy error code ───

    @Test
    fun `fromMap preserves the sibling errorMessage sentence for a legacy string error`() {
        val metadata = DidResolutionMetadata.fromMap(
            mapOf(
                "error" to "notFound",
                "errorMessage" to "DID did:x:y does not exist"
            )
        )
        assertEquals(DidErrorType.NOT_FOUND, metadata.error?.type)
        assertEquals("DID did:x:y does not exist", metadata.error?.detail)
    }

    @Test
    fun `fromMap upgrades a standalone errorMessage with no error member to an internal error`() {
        val metadata = DidResolutionMetadata.fromMap(mapOf("errorMessage" to "driver exploded"))
        assertEquals(DidErrorType.INTERNAL_ERROR, metadata.error?.type)
        assertEquals("driver exploded", metadata.error?.detail)
    }

    // ─── Round-trip ───

    @Test
    fun `toMap then fromMap round-trip preserves error type, contentType and properties`() {
        val original = DidResolutionMetadata(
            contentType = "application/did+json",
            error = DidResolutionError.invalidDid("malformed identifier"),
            properties = mapOf("blockNumber" to "42")
        )
        val roundTripped = DidResolutionMetadata.fromMap(original.toMap())

        assertEquals(original.contentType, roundTripped.contentType)
        assertEquals(original.error?.type, roundTripped.error?.type)
        assertEquals(original.properties, roundTripped.properties)
    }

    // ─── D4: both parsers accept the legacy string error form ───

    @Test
    fun `fromJson accepts a legacy string error`() {
        val json = buildJsonObject { put("error", "notFound") }
        val metadata = DidResolutionMetadata.fromJson(json)
        assertEquals(DidErrorType.NOT_FOUND, metadata.error?.type)
    }

    // ─── Malformed input degrades instead of throwing ───

    @Test
    fun `fromJson degrades an object-valued contentType instead of throwing`() {
        val json = buildJsonObject { put("contentType", buildJsonObject { put("nested", "value") }) }
        val metadata = DidResolutionMetadata.fromJson(json)
        assertEquals("application/did", metadata.contentType)
    }

    @Test
    fun `fromJson degrades a malformed retrieved timestamp instead of throwing`() {
        val json = buildJsonObject { put("retrieved", "yesterday") }
        val metadata = DidResolutionMetadata.fromJson(json)
        assertNull(metadata.retrieved)
    }

    @Test
    fun `fromMap degrades a malformed retrieved timestamp instead of throwing`() {
        val metadata = DidResolutionMetadata.fromMap(mapOf("retrieved" to "yesterday"))
        assertNull(metadata.retrieved)
    }
}
