package org.trustweave.did.resolution

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Test
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import org.trustweave.did.resolver.DidErrorType
import org.trustweave.did.resolver.DidResolutionResult
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DidResolutionResultJsonTest {

    private val did = Did("did:example:123456789abcdefghi")

    @Test
    fun `media type is application-did-resolution`() {
        assertEquals("application/did-resolution", DidResolutionResultJson.MEDIA_TYPE)
    }

    @Test
    fun `a success carries all three members`() {
        val json = DidResolutionResultJson.toJson(
            DidResolutionResult.Success(DidDocument(id = did))
        )
        assertTrue(json.containsKey("didDocument"))
        assertTrue(json.containsKey("didResolutionMetadata"))
        assertTrue(json.containsKey("didDocumentMetadata"))
        assertEquals(JsonPrimitive(did.value), json["didDocument"]!!.jsonObject["id"])
    }

    @Test
    fun `a failure has a null document and an empty document metadata`() {
        val json = DidResolutionResultJson.toJson(DidResolutionResult.Failure.NotFound(did))
        assertEquals(JsonNull, json["didDocument"])
        assertTrue(json["didDocumentMetadata"]!!.jsonObject.isEmpty())
        assertEquals(
            JsonPrimitive(DidErrorType.NOT_FOUND),
            json["didResolutionMetadata"]!!.jsonObject["error"]!!.jsonObject["type"]
        )
    }

    @Test
    fun `a deactivated result has a null document and deactivated metadata`() {
        val json = DidResolutionResultJson.toJson(
            DidResolutionResult.Deactivated(did, DidDocumentMetadata(deactivated = true))
        )
        assertEquals(JsonNull, json["didDocument"])
        assertEquals(JsonPrimitive(true), json["didDocumentMetadata"]!!.jsonObject["deactivated"])
        assertTrue(json["didResolutionMetadata"]!!.jsonObject["error"] == null)
    }
}
