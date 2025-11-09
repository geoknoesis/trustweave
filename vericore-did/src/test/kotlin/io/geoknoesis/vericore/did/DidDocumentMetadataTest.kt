package io.geoknoesis.vericore.did

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.time.Instant

/**
 * Tests for DidDocumentMetadata with Instant fields.
 */
class DidDocumentMetadataTest {
    
    @Test
    fun `test DidDocumentMetadata with all fields`() {
        val now = Instant.now()
        val metadata = DidDocumentMetadata(
            created = now,
            updated = now.plusSeconds(3600),
            versionId = "v1",
            nextUpdate = now.plusSeconds(86400),
            canonicalId = "did:key:canonical",
            equivalentId = listOf("did:key:equivalent1", "did:key:equivalent2")
        )
        
        assertNotNull(metadata.created)
        assertNotNull(metadata.updated)
        assertEquals("v1", metadata.versionId)
        assertNotNull(metadata.nextUpdate)
        assertEquals("did:key:canonical", metadata.canonicalId)
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
        val now = Instant.now()
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
        val doc = DidDocument(id = "did:key:test")
        val metadata = DidDocumentMetadata(
            created = Instant.now(),
            versionId = "v1"
        )
        
        val result = DidResolutionResult(
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

