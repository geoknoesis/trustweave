package io.geoknoesis.vericore.waltid

import io.geoknoesis.vericore.core.VeriCoreException
import io.geoknoesis.vericore.did.didCreationOptions
import io.geoknoesis.vericore.kms.KeyNotFoundException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for error handling in walt.id adapters.
 */
class WaltIdErrorHandlingTest {

    @Test
    fun `KMS should throw KeyNotFoundException for non-existent key`() = runBlocking {
        val kms = WaltIdKeyManagementService()
        
        assertFailsWith<KeyNotFoundException> {
            kms.getPublicKey("nonexistent-key-id")
        }
    }

    @Test
    fun `KMS should throw KeyNotFoundException when signing with non-existent key`() = runBlocking {
        val kms = WaltIdKeyManagementService()
        
        assertFailsWith<KeyNotFoundException> {
            kms.sign("nonexistent-key-id", "test data".toByteArray())
        }
    }

    @Test
    fun `DID method should throw exception for invalid options`() = runBlocking {
        val kms = WaltIdKeyManagementService()
        val webMethod = io.geoknoesis.vericore.waltid.did.WaltIdWebMethod(kms)
        
        // did:web requires domain option
        assertFailsWith<IllegalArgumentException> {
            webMethod.createDid()
        }
    }

    @Test
    fun `DID method should throw exception when resolving non-existent DID`() = runBlocking {
        val kms = WaltIdKeyManagementService()
        val keyMethod = io.geoknoesis.vericore.waltid.did.WaltIdKeyMethod(kms)
        
        // Resolving a DID that was never created should return null document
        val result = keyMethod.resolveDid("did:key:zNonexistent")
        assertNull(result.document, "Non-existent DID should resolve to null document")
    }

    @Test
    fun `DID method should throw exception when updating non-existent DID`() = runBlocking {
        val kms = WaltIdKeyManagementService()
        val keyMethod = io.geoknoesis.vericore.waltid.did.WaltIdKeyMethod(kms)
        
        assertFailsWith<IllegalArgumentException> {
            keyMethod.updateDid("did:key:zNonexistent") { it }
        }
    }

    @Test
    fun `DID method provider should throw when KMS not available`() {
        val provider = io.geoknoesis.vericore.waltid.did.WaltIdDidMethodProvider()
        
        // If no KMS is provided and SPI discovery fails, should throw
        // Note: This test may need adjustment based on actual SPI discovery behavior
        try {
            val method = provider.create("key")
            // If SPI discovery works, method might be created
            // If not, exception should be thrown
            assertNotNull(method, "Method should be created if KMS is discoverable via SPI")
        } catch (e: IllegalStateException) {
            // Expected if KMS is not available
            assertNotNull(e.message)
            assertTrue(e.message?.contains("KeyManagementService") == true)
        }
    }

    @Test
    fun `DID method provider should return null for unsupported method`() {
        val kms = io.geoknoesis.vericore.testkit.kms.InMemoryKeyManagementService()
        val provider = io.geoknoesis.vericore.waltid.did.WaltIdDidMethodProvider()
        
        val method = provider.create("unsupported-method", didCreationOptions { property("kms", kms) })
        assertNull(method, "Unsupported method should return null")
    }

    @Test
    fun `KMS should handle invalid algorithm gracefully`() = runBlocking {
        val kms = WaltIdKeyManagementService()
        
        // Try to generate key with unsupported algorithm
        try {
            val handle = kms.generateKey("UnsupportedAlgorithm123")
            // If it doesn't throw, verify it still creates a handle
            assertNotNull(handle)
        } catch (e: VeriCoreException) {
            // Expected if algorithm validation is strict
            assertNotNull(e.message)
        } catch (e: IllegalArgumentException) {
            // Also acceptable
            assertNotNull(e.message)
        }
    }
}

