package com.trustweave.trust

import com.trustweave.testkit.trust.InMemoryTrustRegistry
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.time.Instant

/**
 * Edge case and comprehensive tests for Trust Registry.
 */
class TrustRegistryEdgeCasesTest {

    @Test
    fun `test trust registry with empty credential types list`() = runBlocking {
        val registry = InMemoryTrustRegistry()

        registry.addTrustAnchor(
            anchorDid = "did:key:issuer",
            metadata = TrustAnchorMetadata(
                credentialTypes = emptyList()
            )
        )

        // Empty list means no specific types trusted (only when credentialType is null)
        assertFalse(registry.isTrustedIssuer("did:key:issuer", "AnyCredential"))
        // But should be trusted when credentialType is null (all types)
        assertTrue(registry.isTrustedIssuer("did:key:issuer", null))
    }

    @Test
    fun `test trust registry with null credential types`() = runBlocking {
        val registry = InMemoryTrustRegistry()

        registry.addTrustAnchor(
            anchorDid = "did:key:issuer",
            metadata = TrustAnchorMetadata(
                credentialTypes = null
            )
        )

        // null means all types trusted
        assertTrue(registry.isTrustedIssuer("did:key:issuer", "AnyCredential"))
        assertTrue(registry.isTrustedIssuer("did:key:issuer", null))
    }

    @Test
    fun `test trust path with circular references`() = runBlocking {
        val registry = InMemoryTrustRegistry()

        registry.addTrustAnchor("did:key:anchor1", TrustAnchorMetadata())
        registry.addTrustAnchor("did:key:anchor2", TrustAnchorMetadata())
        registry.addTrustRelationship("did:key:anchor1", "did:key:anchor2")
        registry.addTrustRelationship("did:key:anchor2", "did:key:anchor1")

        val path = registry.findTrustPath(
            com.trustweave.trust.types.VerifierIdentity(com.trustweave.trust.types.Did("did:key:anchor1")),
            com.trustweave.trust.types.IssuerIdentity.from("did:key:anchor2", "key-1")
        )

        assertTrue(path is com.trustweave.trust.types.TrustPath.Verified)
        // Should handle circular references gracefully
    }

    @Test
    fun `test trust path with disconnected nodes`() = runBlocking {
        val registry = InMemoryTrustRegistry()

        registry.addTrustAnchor("did:key:anchor1", TrustAnchorMetadata())
        registry.addTrustAnchor("did:key:anchor2", TrustAnchorMetadata())
        // No relationship between them

        val path = registry.findTrustPath(
            com.trustweave.trust.types.VerifierIdentity(com.trustweave.trust.types.Did("did:key:anchor1")),
            com.trustweave.trust.types.IssuerIdentity.from("did:key:anchor2", "key-1")
        )

        assertTrue(path is com.trustweave.trust.types.TrustPath.NotFound)
    }

    @Test
    fun `test trust score calculation for various path lengths`() = runBlocking {
        val registry = InMemoryTrustRegistry()

        registry.addTrustAnchor("did:key:a1", TrustAnchorMetadata())
        registry.addTrustAnchor("did:key:a2", TrustAnchorMetadata())
        registry.addTrustAnchor("did:key:a3", TrustAnchorMetadata())
        registry.addTrustAnchor("did:key:a4", TrustAnchorMetadata())

        registry.addTrustRelationship("did:key:a1", "did:key:a2")
        registry.addTrustRelationship("did:key:a2", "did:key:a3")
        registry.addTrustRelationship("did:key:a3", "did:key:a4")

        val path1 = registry.findTrustPath(
            com.trustweave.trust.types.VerifierIdentity(com.trustweave.trust.types.Did("did:key:a1")),
            com.trustweave.trust.types.IssuerIdentity.from("did:key:a1", "key-1")
        )
        assertTrue(path1 is com.trustweave.trust.types.TrustPath.Verified)
        val verified1 = path1 as com.trustweave.trust.types.TrustPath.Verified
        assertEquals(1.0, verified1.trustScore)

        val path2 = registry.findTrustPath(
            com.trustweave.trust.types.VerifierIdentity(com.trustweave.trust.types.Did("did:key:a1")),
            com.trustweave.trust.types.IssuerIdentity.from("did:key:a2", "key-1")
        )
        assertTrue(path2 is com.trustweave.trust.types.TrustPath.Verified)
        val verified2 = path2 as com.trustweave.trust.types.TrustPath.Verified
        assertTrue(verified2.trustScore in 0.0..1.0)

        val path3 = registry.findTrustPath(
            com.trustweave.trust.types.VerifierIdentity(com.trustweave.trust.types.Did("did:key:a1")),
            com.trustweave.trust.types.IssuerIdentity.from("did:key:a4", "key-1")
        )
        assertTrue(path3 is com.trustweave.trust.types.TrustPath.Verified)
        val verified3 = path3 as com.trustweave.trust.types.TrustPath.Verified
        assertTrue(verified3.trustScore in 0.0..1.0)
        assertTrue(verified3.trustScore < verified2.trustScore) // Longer path = lower score
    }

    @Test
    fun `test remove non-existent trust anchor`() = runBlocking {
        val registry = InMemoryTrustRegistry()

        val removed = registry.removeTrustAnchor("did:key:nonexistent")
        assertFalse(removed)
    }

    @Test
    fun `test get trusted issuers with empty registry`() = runBlocking {
        val registry = InMemoryTrustRegistry()

        val issuers: List<String> = registry.getTrustedIssuers(null as String?)
        assertTrue(issuers.isEmpty())
    }

    @Test
    fun `test trust anchor metadata with all fields`() = runBlocking {
        val now = Instant.now()
        val metadata = TrustAnchorMetadata(
            credentialTypes = listOf("Type1", "Type2"),
            description = "Test anchor",
            addedAt = now
        )

        assertEquals(2, metadata.credentialTypes?.size)
        assertEquals("Test anchor", metadata.description)
        assertEquals(now, metadata.addedAt)
    }

    @Test
    fun `test trust path result validation`() {
        // Valid trust path
        val validPath = TrustPathResult(
            path = listOf("did:key:a1", "did:key:a2"),
            trustScore = 0.8,
            valid = true
        )
        assertTrue(validPath.valid)
        assertEquals(0.8, validPath.trustScore)

        // Invalid trust path
        val invalidPath = TrustPathResult(
            path = emptyList(),
            trustScore = 0.0,
            valid = false
        )
        assertFalse(invalidPath.valid)
    }

    @Test
    fun `test trust path result with invalid trust score`() {
        try {
            TrustPathResult(
                path = listOf("did:key:a1"),
                trustScore = 1.5, // Invalid: > 1.0
                valid = true
            )
            assertFalse(true, "Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Trust score") == true)
        }

        try {
            TrustPathResult(
                path = listOf("did:key:a1"),
                trustScore = -0.1, // Invalid: < 0.0
                valid = true
            )
            assertFalse(true, "Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Trust score") == true)
        }
    }
}

