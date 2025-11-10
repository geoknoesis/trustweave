package io.geoknoesis.vericore.trust

import io.geoknoesis.vericore.testkit.trust.InMemoryTrustRegistry
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.time.Instant

/**
 * Tests for Trust Registry operations.
 */
class TrustRegistryTest {
    
    @Test
    fun `test add trust anchor`() = runBlocking {
        val registry = InMemoryTrustRegistry()
        
        val added = registry.addTrustAnchor(
            anchorDid = "did:key:university",
            metadata = TrustAnchorMetadata(
                credentialTypes = listOf("EducationCredential"),
                description = "Trusted university"
            )
        )
        
        assertTrue(added)
        assertTrue(registry.isTrustedIssuer("did:key:university", "EducationCredential"))
    }
    
    @Test
    fun `test add duplicate trust anchor returns false`() = runBlocking {
        val registry = InMemoryTrustRegistry()
        
        registry.addTrustAnchor("did:key:university", TrustAnchorMetadata())
        val addedAgain = registry.addTrustAnchor("did:key:university", TrustAnchorMetadata())
        
        assertFalse(addedAgain)
    }
    
    @Test
    fun `test remove trust anchor`() = runBlocking {
        val registry = InMemoryTrustRegistry()
        
        registry.addTrustAnchor("did:key:university", TrustAnchorMetadata())
        assertTrue(registry.isTrustedIssuer("did:key:university", null))
        
        val removed = registry.removeTrustAnchor("did:key:university")
        assertTrue(removed)
        assertFalse(registry.isTrustedIssuer("did:key:university", null))
    }
    
    @Test
    fun `test isTrustedIssuer with credential type filter`() = runBlocking {
        val registry = InMemoryTrustRegistry()
        
        registry.addTrustAnchor(
            anchorDid = "did:key:university",
            metadata = TrustAnchorMetadata(
                credentialTypes = listOf("EducationCredential", "DegreeCredential")
            )
        )
        
        assertTrue(registry.isTrustedIssuer("did:key:university", "EducationCredential"))
        assertTrue(registry.isTrustedIssuer("did:key:university", "DegreeCredential"))
        assertFalse(registry.isTrustedIssuer("did:key:university", "EmploymentCredential"))
    }
    
    @Test
    fun `test isTrustedIssuer with null credential type`() = runBlocking {
        val registry = InMemoryTrustRegistry()
        
        registry.addTrustAnchor(
            anchorDid = "did:key:university",
            metadata = TrustAnchorMetadata(
                credentialTypes = listOf("EducationCredential")
            )
        )
        
        // null credential type should check if anchor exists
        assertTrue(registry.isTrustedIssuer("did:key:university", null))
    }
    
    @Test
    fun `test getTrustPath direct trust`() = runBlocking {
        val registry = InMemoryTrustRegistry()
        
        registry.addTrustAnchor("did:key:anchor1", TrustAnchorMetadata())
        registry.addTrustAnchor("did:key:anchor2", TrustAnchorMetadata())
        
        val path = registry.getTrustPath("did:key:anchor1", "did:key:anchor1")
        
        assertNotNull(path)
        assertTrue(path.valid)
        assertEquals(1, path.path.size)
        assertEquals("did:key:anchor1", path.path[0])
    }
    
    @Test
    fun `test getTrustPath between anchors`() = runBlocking {
        val registry = InMemoryTrustRegistry()
        
        registry.addTrustAnchor("did:key:anchor1", TrustAnchorMetadata())
        registry.addTrustAnchor("did:key:anchor2", TrustAnchorMetadata())
        registry.addTrustRelationship("did:key:anchor1", "did:key:anchor2")
        
        val path = registry.getTrustPath("did:key:anchor1", "did:key:anchor2")
        
        assertNotNull(path)
        assertTrue(path.valid)
        assertEquals(2, path.path.size)
        assertTrue(path.trustScore > 0.0)
    }
    
    @Test
    fun `test getTrustPath no path returns null`() = runBlocking {
        val registry = InMemoryTrustRegistry()
        
        registry.addTrustAnchor("did:key:anchor1", TrustAnchorMetadata())
        // anchor2 not added, so no path
        
        val path = registry.getTrustPath("did:key:anchor1", "did:key:anchor2")
        
        assertNull(path)
    }
    
    @Test
    fun `test getTrustedIssuers with credential type filter`() = runBlocking {
        val registry = InMemoryTrustRegistry()
        
        registry.addTrustAnchor(
            anchorDid = "did:key:university",
            metadata = TrustAnchorMetadata(credentialTypes = listOf("EducationCredential"))
        )
        registry.addTrustAnchor(
            anchorDid = "did:key:company",
            metadata = TrustAnchorMetadata(credentialTypes = listOf("EmploymentCredential"))
        )
        
        val educationIssuers = registry.getTrustedIssuers("EducationCredential")
        assertEquals(1, educationIssuers.size)
        assertTrue(educationIssuers.contains("did:key:university"))
        
        val employmentIssuers = registry.getTrustedIssuers("EmploymentCredential")
        assertEquals(1, employmentIssuers.size)
        assertTrue(employmentIssuers.contains("did:key:company"))
    }
    
    @Test
    fun `test getTrustedIssuers with null credential type`() = runBlocking {
        val registry = InMemoryTrustRegistry()
        
        registry.addTrustAnchor("did:key:anchor1", TrustAnchorMetadata())
        registry.addTrustAnchor("did:key:anchor2", TrustAnchorMetadata())
        
        val allIssuers = registry.getTrustedIssuers(null)
        assertEquals(2, allIssuers.size)
    }
    
    @Test
    fun `test trust score calculation`() = runBlocking {
        val registry = InMemoryTrustRegistry()
        
        registry.addTrustAnchor("did:key:anchor1", TrustAnchorMetadata())
        registry.addTrustAnchor("did:key:anchor2", TrustAnchorMetadata())
        registry.addTrustRelationship("did:key:anchor1", "did:key:anchor2")
        
        val path = registry.getTrustPath("did:key:anchor1", "did:key:anchor2")
        
        assertNotNull(path)
        assertTrue(path.trustScore in 0.0..1.0)
        assertTrue(path.trustScore > 0.0)
    }
    
    @Test
    fun `test clear trust registry`() = runBlocking {
        val registry = InMemoryTrustRegistry()
        
        registry.addTrustAnchor("did:key:anchor1", TrustAnchorMetadata())
        assertTrue(registry.isTrustedIssuer("did:key:anchor1", null))
        
        registry.clear()
        assertFalse(registry.isTrustedIssuer("did:key:anchor1", null))
    }
}

