package org.trustweave.did.model

import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DidDocumentMetadataConformanceTest {
    @Test
    fun `nextVersionId is a document metadata property`() {
        val metadata = DidDocumentMetadata(versionId = "3", nextVersionId = "4")
        assertEquals("4", metadata.nextVersionId)
    }

    @Test
    fun `timestamps serialize without sub-second precision`() {
        val metadata =
            DidDocumentMetadata(
                created = Instant.parse("2019-03-23T06:35:22.512Z"),
                updated = Instant.parse("2023-08-10T13:40:06.001Z"),
            )
        val json = metadata.toJson()
        assertEquals(JsonPrimitive("2019-03-23T06:35:22Z"), json["created"])
        assertEquals(JsonPrimitive("2023-08-10T13:40:06Z"), json["updated"])
    }

    @Test
    fun `deactivated is omitted when false and emitted when true`() {
        assertFalse(DidDocumentMetadata().toJson().containsKey("deactivated"))
        assertEquals(JsonPrimitive(true), DidDocumentMetadata(deactivated = true).toJson()["deactivated"])
    }

    @Test
    fun `proof entries are emitted as an array`() {
        val proof = buildJsonObject { put("type", "DataIntegrityProof") }
        assertTrue(DidDocumentMetadata(proof = listOf(proof)).toJson().containsKey("proof"))
    }

    @Test
    fun `an empty metadata structure serializes to an empty object`() {
        assertTrue(DidDocumentMetadata().toJson().isEmpty())
    }
}
