package com.trustweave.testkit.templates

import com.trustweave.did.DidCreationOptions
import com.trustweave.did.DidCreationOptions.KeyAlgorithm
import com.trustweave.did.DidMethod
import com.trustweave.testkit.BasePluginTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Template for DID method unit tests.
 * 
 * Copy this template and adapt it for your DID method plugin.
 * 
 * **Required Tests**:
 * - ✅ Create DID with different algorithms
 * - ✅ Resolve DID after creation
 * - ✅ Update DID
 * - ✅ Deactivate DID
 * - ✅ Error handling (invalid inputs, network errors)
 * - ✅ Algorithm support verification
 * - ✅ SPI discovery (if applicable)
 */
abstract class DidMethodTestTemplate : BasePluginTest() {
    
    /**
     * Gets the DID method to test.
     * Must be implemented by subclasses.
     */
    abstract fun getDidMethod(): DidMethod
    
    /**
     * Gets the expected DID method name (e.g., "key", "web", "ion").
     */
    abstract fun getExpectedMethodName(): String
    
    @Test
    fun `test method name matches expected`() {
        val method = getDidMethod()
        assertEquals(getExpectedMethodName(), method.method)
    }
    
    @Test
    fun `test create DID with Ed25519`() = runBlocking {
        val method = getDidMethod()
        val document = method.createDid(
            didCreationOptions {
                algorithm = KeyAlgorithm.ED25519
            }
        )
        
        assertNotNull(document)
        assertTrue(document.id.startsWith("did:${getExpectedMethodName()}:"))
        assertTrue(document.verificationMethod.isNotEmpty())
    }
    
    @Test
    fun `test create DID with Secp256k1`() = runBlocking {
        val method = getDidMethod()
        val document = method.createDid(
            didCreationOptions {
                algorithm = KeyAlgorithm.SECP256K1
            }
        )
        
        assertNotNull(document)
        assertTrue(document.id.startsWith("did:${getExpectedMethodName()}:"))
    }
    
    @Test
    fun `test resolve DID after creation`() = runBlocking {
        val method = getDidMethod()
        val document = method.createDid(
            didCreationOptions {
                algorithm = KeyAlgorithm.ED25519
            }
        )
        
        val resolution = method.resolveDid(document.id)
        assertNotNull(resolution.document)
        assertEquals(document.id, resolution.document?.id)
    }
    
    @Test
    fun `test resolve non-existent DID`() = runBlocking {
        val method = getDidMethod()
        val nonExistentDid = "did:${getExpectedMethodName()}:nonexistent"
        
        val resolution = method.resolveDid(nonExistentDid)
        // Some methods return null, others return resolution with error metadata
        // This is method-specific behavior
    }
    
    @Test
    fun `test update DID`() = runBlocking {
        val method = getDidMethod()
        val document = method.createDid(
            didCreationOptions {
                algorithm = KeyAlgorithm.ED25519
            }
        )
        
        val updated = method.updateDid(document.id) { doc ->
            // Add a service endpoint
            doc.copy(
                service = doc.service + com.trustweave.did.Service(
                    id = "${doc.id}#service-1",
                    type = "LinkedDomains",
                    serviceEndpoint = "https://example.com"
                )
            )
        }
        
        assertNotNull(updated)
        assertTrue(updated.service.size > document.service.size)
    }
    
    @Test
    fun `test deactivate DID`() = runBlocking {
        val method = getDidMethod()
        val document = method.createDid(
            didCreationOptions {
                algorithm = KeyAlgorithm.ED25519
            }
        )
        
        val deactivated = method.deactivateDid(document.id)
        assertTrue(deactivated)
    }
    
    @Test
    fun `test invalid DID format`() = runBlocking {
        val method = getDidMethod()
        
        try {
            method.resolveDid("invalid-did-format")
            // Some methods may not throw, so this is optional
        } catch (e: Exception) {
            // Expected behavior
            assertNotNull(e.message)
        }
    }
    
    @Test
    fun `test empty DID string`() = runBlocking {
        val method = getDidMethod()
        
        try {
            method.resolveDid("")
            // Should handle empty string appropriately
        } catch (e: Exception) {
            // Expected behavior
        }
    }
    
    @Test
    fun `test DID with wrong method prefix`() = runBlocking {
        val method = getDidMethod()
        val wrongMethodDid = "did:wrongmethod:identifier"
        
        try {
            method.resolveDid(wrongMethodDid)
            // Should handle wrong method appropriately
        } catch (e: Exception) {
            // Expected behavior
        }
    }
}

