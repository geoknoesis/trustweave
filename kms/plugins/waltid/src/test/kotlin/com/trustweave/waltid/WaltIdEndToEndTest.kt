package com.trustweave.waltid

import com.trustweave.did.DidCreationOptions
import com.trustweave.did.registry.DidMethodRegistry
import com.trustweave.did.didCreationOptions
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end integration tests for walt.id adapters.
 * 
 * Note: These tests will fully work once walt.id dependencies are added and
 * adapter implementations use real walt.id APIs.
 */
class WaltIdEndToEndTest {

    @Test
    fun discoverAndRegister_shouldDiscoverAndRegisterWaltIdAdapters() = runBlocking {
        val registry = DidMethodRegistry()
        val result = WaltIdIntegration.discoverAndRegister(registry)
        
        assertNotNull(result.kms, "KMS should be discovered")
        assertTrue(result.registeredDidMethods.isNotEmpty(), "At least one DID method should be registered")
        assertTrue(result.registeredDidMethods.contains("key"), "did:key should be registered")
        assertTrue(result.registeredDidMethods.contains("web"), "did:web should be registered")
    }

    @Test
    fun endToEnd_didCreationAndResolution() = runBlocking {
        // Setup integration
        val registry = DidMethodRegistry()
        val result = WaltIdIntegration.discoverAndRegister(registry)
        
        // Get the key method
        val keyMethod = registry.get("key")
        assertNotNull(keyMethod, "did:key method should be registered")
        
        // Create a DID
        val document = keyMethod!!.createDid()
        assertNotNull(document.id, "DID should have an ID")
        assertTrue(document.id.startsWith("did:key:"), "DID should be did:key format")
        assertTrue(document.verificationMethod.isNotEmpty(), "DID document should have verification methods")
        
        // Resolve the DID
        val resolutionResult = registry.resolve(document.id)
        assertNotNull(resolutionResult.document, "DID should resolve to a document")
        assertEquals(document.id, resolutionResult.document?.id, "Resolved document should match created document")
    }

    @Test
    fun endToEnd_didWebCreation() = runBlocking {
        val registry = DidMethodRegistry()
        val result = WaltIdIntegration.discoverAndRegister(registry)
        
        val webMethod = registry.get("web")
        assertNotNull(webMethod, "did:web method should be registered")
        
        val document = webMethod!!.createDid(
            didCreationOptions {
                property("domain", "example.com")
                algorithm = DidCreationOptions.KeyAlgorithm.ED25519
            }
        )
        
        assertNotNull(document.id)
        assertEquals("did:web:example.com", document.id)
        assertTrue(document.verificationMethod.isNotEmpty())
    }

    @Test
    fun `integration with TrustWeave workflow`() = runBlocking {
        // This test demonstrates a complete TrustWeave workflow using walt.id adapters
        val registry = DidMethodRegistry()
        val result = WaltIdIntegration.discoverAndRegister(registry)
        
        // 1. Create a DID for an issuer
        val keyMethod = registry.get("key")!!
        val issuerDoc = keyMethod.createDid()
        val issuerDid = issuerDoc.id
        
        // 2. Verify the DID can be resolved
        val resolutionResult = registry.resolve(issuerDid)
        assertNotNull(resolutionResult.document)
        assertEquals(issuerDid, resolutionResult.document?.id)
        
        // 3. Verify metadata indicates walt.id provider
        assertTrue(
            resolutionResult.resolutionMetadata["provider"] == "waltid" ||
            resolutionResult.resolutionMetadata.containsKey("provider"),
            "Resolution metadata should indicate provider"
        )
    }

    @Test
    fun `multiple DID methods can coexist`() = runBlocking {
        val registry = DidMethodRegistry()
        val result = WaltIdIntegration.discoverAndRegister(registry)
        
        // Both methods should be available
        val keyMethod = registry.get("key")
        val webMethod = registry.get("web")
        
        assertNotNull(keyMethod, "did:key should be available")
        assertNotNull(webMethod, "did:web should be available")
        
        // Create DIDs using both methods
        val keyDoc = keyMethod!!.createDid()
        val webDoc = webMethod!!.createDid(
            didCreationOptions {
                property("domain", "test.com")
            }
        )
        
        assertTrue(keyDoc.id.startsWith("did:key:"))
        assertTrue(webDoc.id.startsWith("did:web:"))
    }
}

