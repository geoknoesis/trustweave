package org.trustweave.did

import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import org.trustweave.did.resolver.DidResolutionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

/**
 * Comprehensive tests for DidDocumentMetadata with Instant fields.
 */
class DidDocumentMetadataComprehensiveTest {

    @Test
    fun `test DidDocumentMetadata with all fields populated`() {
        val now = Clock.System.now()
        val later = now.plus(kotlin.time.Duration.parse("PT1H"))
        val future = now.plus(kotlin.time.Duration.parse("P1D"))

        val metadata = DidDocumentMetadata(
            created = now,
            updated = later,
            versionId = "v1.0.0",
            nextUpdate = future,
            canonicalId = Did("did:key:canonical"),
            equivalentId = listOf(Did("did:key:equivalent1"), Did("did:key:equivalent2"))
        )

        assertEquals(now, metadata.created)
        assertEquals(later, metadata.updated)
        assertEquals("v1.0.0", metadata.versionId)
        assertEquals(future, metadata.nextUpdate)
        assertEquals("did:key:canonical", metadata.canonicalId?.value)
        assertEquals(2, metadata.equivalentId.size)
    }

    @Test
    fun `test DidDocumentMetadata with partial fields`() {
        val now = Clock.System.now()

        val metadata = DidDocumentMetadata(
            created = now,
            versionId = "v1"
            // Other fields are null/default
        )

        assertEquals(now, metadata.created)
        assertEquals("v1", metadata.versionId)
        assertNull(metadata.updated)
        assertNull(metadata.nextUpdate)
        assertNull(metadata.canonicalId)
        assertTrue(metadata.equivalentId.isEmpty())
    }

    @Test
    fun `test DidDocumentMetadata default values`() {
        val metadata = DidDocumentMetadata()

        assertNull(metadata.created)
        assertNull(metadata.updated)
        assertNull(metadata.versionId)
        assertNull(metadata.nextUpdate)
        assertNull(metadata.canonicalId)
        assertTrue(metadata.equivalentId.isEmpty())
    }

    @Test
    fun `test DidDocumentMetadata with equivalent IDs`() {
        val metadata = DidDocumentMetadata(
            equivalentId = listOf(
                Did("did:key:equiv1"),
                Did("did:key:equiv2"),
                Did("did:key:equiv3")
            )
        )

        assertEquals(3, metadata.equivalentId.size)
        assertTrue(metadata.equivalentId.any { it.value == "did:key:equiv1" })
        assertTrue(metadata.equivalentId.any { it.value == "did:key:equiv2" })
        assertTrue(metadata.equivalentId.any { it.value == "did:key:equiv3" })
    }

    @Test
    fun `test DidDocumentMetadata equality`() {
        val now = Clock.System.now()

        val metadata1 = DidDocumentMetadata(
            created = now,
            versionId = "v1"
        )

        val metadata2 = DidDocumentMetadata(
            created = now,
            versionId = "v1"
        )

        assertEquals(metadata1, metadata2)
    }

    @Test
    fun `test DidDocumentMetadata inequality`() {
        val now = Clock.System.now()

        val metadata1 = DidDocumentMetadata(
            created = now,
            versionId = "v1"
        )

        val metadata2 = DidDocumentMetadata(
            created = now,
            versionId = "v2"
        )

        assertFalse(metadata1 == metadata2)
    }

    @Test
    fun `test DidResolutionResult with DidDocumentMetadata`() {
        val doc = DidDocument(id = Did("did:key:test"))
        val metadata = DidDocumentMetadata(
            created = Clock.System.now(),
            updated = Clock.System.now(),
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

    @Test
    fun `test DidResolutionResult with null document`() {
        val result = DidResolutionResult.Failure.NotFound(
            did = Did("did:key:test"),
            reason = "notFound",
            resolutionMetadata = mapOf("error" to "notFound")
        )

        assertTrue(result is DidResolutionResult.Failure.NotFound)
        assertEquals("notFound", result.resolutionMetadata["error"])
    }
}

