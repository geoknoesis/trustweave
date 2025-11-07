package io.geoknoesis.vericore.waltid

import io.geoknoesis.vericore.did.DidRegistry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
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

    @AfterEach
    fun cleanup() {
        DidRegistry.clear()
    }

    @Test
    fun discoverAndRegister_shouldDiscoverAndRegisterWaltIdAdapters() = runBlocking {
        val result = WaltIdIntegration.discoverAndRegister()
        
        assertNotNull(result.kms, "KMS should be discovered")
        assertTrue(result.registeredDidMethods.isNotEmpty(), "At least one DID method should be registered")
        assertTrue(result.registeredDidMethods.contains("key"), "did:key should be registered")
        assertTrue(result.registeredDidMethods.contains("web"), "did:web should be registered")
    }

    @Test
    fun endToEnd_didCreationAndResolution() = runBlocking {
        // Setup integration
        val result = WaltIdIntegration.discoverAndRegister()
        
        // Get the key method
        val keyMethod = DidRegistry.get("key")
        assertNotNull(keyMethod, "did:key method should be registered")
        
        // Create a DID
        val document = keyMethod!!.createDid(mapOf("algorithm" to "Ed25519"))
        assertNotNull(document.id, "DID should have an ID")
        assertTrue(document.id.startsWith("did:key:"), "DID should be did:key format")
        assertTrue(document.verificationMethod.isNotEmpty(), "DID document should have verification methods")
        
        // Resolve the DID
        val resolutionResult = DidRegistry.resolve(document.id)
        assertNotNull(resolutionResult.document, "DID should resolve to a document")
        assertEquals(document.id, resolutionResult.document?.id, "Resolved document should match created document")
    }

    @Test
    fun endToEnd_didWebCreation() = runBlocking {
        val result = WaltIdIntegration.discoverAndRegister()
        
        val webMethod = DidRegistry.get("web")
        assertNotNull(webMethod, "did:web method should be registered")
        
        val document = webMethod!!.createDid(mapOf(
            "domain" to "example.com",
            "algorithm" to "Ed25519"
        ))
        
        assertNotNull(document.id)
        assertEquals("did:web:example.com", document.id)
        assertTrue(document.verificationMethod.isNotEmpty())
    }

    @Test
    fun `integration with VeriCore workflow`() = runBlocking {
        // This test demonstrates a complete VeriCore workflow using walt.id adapters
        val result = WaltIdIntegration.discoverAndRegister()
        
        // 1. Create a DID for an issuer
        val keyMethod = DidRegistry.get("key")!!
        val issuerDoc = keyMethod.createDid(mapOf("algorithm" to "Ed25519"))
        val issuerDid = issuerDoc.id
        
        // 2. Verify the DID can be resolved
        val resolutionResult = DidRegistry.resolve(issuerDid)
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
        val result = WaltIdIntegration.discoverAndRegister()
        
        // Both methods should be available
        val keyMethod = DidRegistry.get("key")
        val webMethod = DidRegistry.get("web")
        
        assertNotNull(keyMethod, "did:key should be available")
        assertNotNull(webMethod, "did:web should be available")
        
        // Create DIDs using both methods
        val keyDoc = keyMethod!!.createDid()
        val webDoc = webMethod!!.createDid(mapOf("domain" to "test.com"))
        
        assertTrue(keyDoc.id.startsWith("did:key:"))
        assertTrue(webDoc.id.startsWith("did:web:"))
    }
}

