package org.trustweave.did

import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import org.trustweave.did.resolver.DidResolutionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

/**
 * Tests for DidDocumentMetadata with Instant fields.
 */
class DidDocumentMetadataTest {

    @Test
    fun `test DidDocumentMetadata with all fields`() {
        val now = Clock.System.now()
        val metadata = DidDocumentMetadata(
            created = now,
            updated = now.plus(kotlin.time.Duration.parse("PT1H")),
            versionId = "v1",
            nextUpdate = now.plus(kotlin.time.Duration.parse("P1D")),
            canonicalId = Did("did:key:canonical"),
            equivalentId = listOf(Did("did:key:equivalent1"), Did("did:key:equivalent2"))
        )

        assertNotNull(metadata.created)
        assertNotNull(metadata.updated)
        assertEquals("v1", metadata.versionId)
        assertNotNull(metadata.nextUpdate)
        assertEquals("did:key:canonical", metadata.canonicalId?.value)
        assertEquals(2, metadata.equivalentId.size)
    }

    @Test
    fun `test DidDocumentMetadata with null fields`() {
        val metadata = DidDocumentMetadata()

        assertNull(metadata.created)
        assertNull(metadata.updated)
        assertNull(metadata.versionId)
        assertNull(metadata.nextUpdate)
        assertNull(metadata.canonicalId)
        assertTrue(metadata.equivalentId.isEmpty())
    }

    @Test
    fun `test DidDocumentMetadata with Instant serialization`() {
        val now = Clock.System.now()
        val metadata = DidDocumentMetadata(
            created = now,
            updated = now
        )

        // Verify Instant fields are preserved
        assertEquals(now, metadata.created)
        assertEquals(now, metadata.updated)
    }

    @Test
    fun `test DidResolutionResult with DidDocumentMetadata`() {
        val doc = DidDocument(id = Did("did:key:test"))
        val metadata = DidDocumentMetadata(
            created = Clock.System.now(),
            versionId = "v1"
        )

        val result = DidResolutionResult.Success(
            document = doc,
            documentMetadata = metadata,
            resolutionMetadata = mapOf("provider" to "test")
        )

        assertNotNull(result.document)
        assertNotNull(result.documentMetadata)
        assertEquals("v1", result.documentMetadata.versionId)
        assertEquals("test", result.resolutionMetadata["provider"])
    }
}

