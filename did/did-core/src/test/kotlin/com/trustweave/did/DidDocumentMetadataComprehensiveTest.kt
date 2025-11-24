package com.trustweave.did

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.time.Instant
import com.trustweave.did.resolver.DidResolutionResult

/**
 * Comprehensive tests for DidDocumentMetadata with Instant fields.
 */
class DidDocumentMetadataComprehensiveTest {
    
    @Test
    fun `test DidDocumentMetadata with all fields populated`() {
        val now = Instant.now()
        val later = now.plusSeconds(3600)
        val future = now.plusSeconds(86400)
        
        val metadata = DidDocumentMetadata(
            created = now,
            updated = later,
            versionId = "v1.0.0",
            nextUpdate = future,
            canonicalId = "did:key:canonical",
            equivalentId = listOf("did:key:equivalent1", "did:key:equivalent2")
        )
        
        assertEquals(now, metadata.created)
        assertEquals(later, metadata.updated)
        assertEquals("v1.0.0", metadata.versionId)
        assertEquals(future, metadata.nextUpdate)
        assertEquals("did:key:canonical", metadata.canonicalId)
        assertEquals(2, metadata.equivalentId.size)
    }
    
    @Test
    fun `test DidDocumentMetadata with partial fields`() {
        val now = Instant.now()
        
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
                "did:key:equiv1",
                "did:key:equiv2",
                "did:key:equiv3"
            )
        )
        
        assertEquals(3, metadata.equivalentId.size)
        assertTrue(metadata.equivalentId.contains("did:key:equiv1"))
        assertTrue(metadata.equivalentId.contains("did:key:equiv2"))
        assertTrue(metadata.equivalentId.contains("did:key:equiv3"))
    }
    
    @Test
    fun `test DidDocumentMetadata equality`() {
        val now = Instant.now()
        
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
        val now = Instant.now()
        
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
        val doc = DidDocument(id = "did:key:test")
        val metadata = DidDocumentMetadata(
            created = Instant.now(),
            updated = Instant.now(),
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
    
    @Test
    fun `test DidResolutionResult with null document`() {
        val metadata = DidDocumentMetadata()
        
        val result = DidResolutionResult(
            document = null,
            documentMetadata = metadata,
            resolutionMetadata = mapOf("error" to "notFound")
        )
        
        assertNull(result.document)
        assertNotNull(result.documentMetadata)
        assertEquals("notFound", result.resolutionMetadata["error"])
    }
}

