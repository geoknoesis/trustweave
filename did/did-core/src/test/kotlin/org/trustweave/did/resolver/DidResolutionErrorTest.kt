package org.trustweave.did.resolver

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DidResolutionErrorTest {

    @Test
    fun `error type constants use the spec URI prefix`() {
        assertEquals("https://www.w3.org/ns/did#INVALID_DID", DidErrorType.INVALID_DID)
        assertEquals("https://www.w3.org/ns/did#INVALID_DID_DOCUMENT", DidErrorType.INVALID_DID_DOCUMENT)
        assertEquals("https://www.w3.org/ns/did#NOT_FOUND", DidErrorType.NOT_FOUND)
        assertEquals(
            "https://www.w3.org/ns/did#REPRESENTATION_NOT_SUPPORTED",
            DidErrorType.REPRESENTATION_NOT_SUPPORTED
        )
        assertEquals("https://www.w3.org/ns/did#INVALID_DID_URL", DidErrorType.INVALID_DID_URL)
        assertEquals("https://www.w3.org/ns/did#METHOD_NOT_SUPPORTED", DidErrorType.METHOD_NOT_SUPPORTED)
        assertEquals("https://www.w3.org/ns/did#INVALID_OPTIONS", DidErrorType.INVALID_OPTIONS)
        assertEquals("https://www.w3.org/ns/did#INTERNAL_ERROR", DidErrorType.INTERNAL_ERROR)
        assertEquals("https://www.w3.org/ns/did#FEATURE_NOT_SUPPORTED", DidErrorType.FEATURE_NOT_SUPPORTED)
    }

    @Test
    fun `http status mapping follows the section 12-1 table`() {
        assertEquals(400, DidErrorType.httpStatus(DidErrorType.INVALID_DID))
        assertEquals(400, DidErrorType.httpStatus(DidErrorType.INVALID_DID_URL))
        assertEquals(400, DidErrorType.httpStatus(DidErrorType.INVALID_OPTIONS))
        assertEquals(404, DidErrorType.httpStatus(DidErrorType.NOT_FOUND))
        assertEquals(406, DidErrorType.httpStatus(DidErrorType.REPRESENTATION_NOT_SUPPORTED))
        assertEquals(500, DidErrorType.httpStatus(DidErrorType.INVALID_DID_DOCUMENT))
        assertEquals(501, DidErrorType.httpStatus(DidErrorType.METHOD_NOT_SUPPORTED))
        assertEquals(501, DidErrorType.httpStatus(DidErrorType.FEATURE_NOT_SUPPORTED))
        assertEquals(500, DidErrorType.httpStatus(DidErrorType.INTERNAL_ERROR))
        assertEquals(500, DidErrorType.httpStatus("https://example.com/some-other-error"))
    }

    @Test
    fun `legacy v0-3 codes map to spec URIs`() {
        assertEquals(DidErrorType.NOT_FOUND, DidErrorType.fromLegacyCode("notFound"))
        assertEquals(DidErrorType.INVALID_DID, DidErrorType.fromLegacyCode("invalidDid"))
        assertEquals(DidErrorType.METHOD_NOT_SUPPORTED, DidErrorType.fromLegacyCode("methodNotSupported"))
        assertEquals(DidErrorType.INTERNAL_ERROR, DidErrorType.fromLegacyCode("resolutionError"))
        assertEquals(DidErrorType.INTERNAL_ERROR, DidErrorType.fromLegacyCode("internalError"))
    }

    @Test
    fun `unknown legacy code is prefixed with the spec base URL`() {
        assertEquals("https://www.w3.org/ns/did#somethingElse", DidErrorType.fromLegacyCode("somethingElse"))
    }

    @Test
    fun `an already absolute error URI passes through unchanged`() {
        assertEquals(
            "https://example.com/errors/custom",
            DidErrorType.fromLegacyCode("https://example.com/errors/custom")
        )
    }

    @Test
    fun `factory populates type title and detail`() {
        val error = DidResolutionError.notFound("did:example:123 does not exist")
        assertEquals(DidErrorType.NOT_FOUND, error.type)
        assertEquals("Not found", error.title)
        assertEquals("did:example:123 does not exist", error.detail)
        assertEquals(404, error.httpStatus)
    }

    @Test
    fun `a relative error type is rejected`() {
        assertFailsWith<IllegalArgumentException> { DidResolutionError("NOT_FOUND") }
    }

    @Test
    fun `toJson emits only populated members`() {
        val json = DidResolutionError(DidErrorType.INTERNAL_ERROR).toJson()
        assertEquals(1, json.size)
        assertEquals(JsonPrimitive(DidErrorType.INTERNAL_ERROR), json["type"])
    }

    @Test
    fun `fromJson parses an RFC 9457 object`() {
        val json = buildJsonObject {
            put("type", DidErrorType.INVALID_OPTIONS)
            put("title", "Invalid options")
            put("detail", "versionId and versionTime are mutually exclusive")
        }
        val error = DidResolutionError.fromJson(json)
        assertEquals(DidErrorType.INVALID_OPTIONS, error?.type)
        assertEquals("versionId and versionTime are mutually exclusive", error?.detail)
    }

    @Test
    fun `fromJson upgrades a legacy string error from an upstream resolver`() {
        val error = DidResolutionError.fromJson(JsonPrimitive("notFound"))
        assertEquals(DidErrorType.NOT_FOUND, error?.type)
        assertEquals("notFound", error?.detail)
    }

    @Test
    fun `fromJson returns null for null and JsonNull`() {
        assertNull(DidResolutionError.fromJson(null))
        assertNull(DidResolutionError.fromJson(JsonNull))
    }

    @Test
    fun `fromJson returns null for a non-primitive type instead of throwing`() {
        val json = buildJsonObject {
            put("type", buildJsonObject { put("nested", "object") })
        }
        assertNull(DidResolutionError.fromJson(json))
    }

    @Test
    fun `fromJson omits title and detail when they are non-primitive but type is valid`() {
        val json = buildJsonObject {
            put("type", DidErrorType.NOT_FOUND)
            put("title", buildJsonObject { put("nested", "object") })
            put("detail", buildJsonObject { put("nested", "object") })
        }
        val error = DidResolutionError.fromJson(json)
        assertEquals(DidErrorType.NOT_FOUND, error?.type)
        assertEquals(DidErrorType.title(DidErrorType.NOT_FOUND), error?.title)
        assertNull(error?.detail)
    }
}
