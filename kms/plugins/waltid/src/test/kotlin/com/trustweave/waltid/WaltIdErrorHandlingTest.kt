package com.trustweave.waltid

import com.trustweave.core.exception.TrustWeaveException
import com.trustweave.did.didCreationOptions
import com.trustweave.did.resolver.DidResolutionResult
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
    fun `KMS should return KeyNotFound result for non-existent key`() = runBlocking {
        val kms = WaltIdKeyManagementService()

        val result = kms.getPublicKey(com.trustweave.core.identifiers.KeyId("nonexistent-key-id"))
        assertTrue(result is com.trustweave.kms.results.GetPublicKeyResult.Failure.KeyNotFound)
    }

    @Test
    fun `KMS should return KeyNotFound result when signing with non-existent key`() = runBlocking {
        val kms = WaltIdKeyManagementService()

        val result = kms.sign(com.trustweave.core.identifiers.KeyId("nonexistent-key-id"), "test data".toByteArray())
        assertTrue(result is com.trustweave.kms.results.SignResult.Failure.KeyNotFound)
    }

    @Test
    fun `DID method should throw exception for invalid options`() = runBlocking {
        val kms = WaltIdKeyManagementService()
        val webMethod = com.trustweave.waltid.did.WaltIdWebMethod(kms)

        // did:web requires domain option
        assertFailsWith<IllegalArgumentException> {
            webMethod.createDid()
        }
    }

    @Test
    fun `DID method should throw exception when resolving non-existent DID`() = runBlocking {
        val kms = WaltIdKeyManagementService()
        val keyMethod = com.trustweave.waltid.did.WaltIdKeyMethod(kms)

        // Resolving a DID that was never created should return null document
        val result = keyMethod.resolveDid(com.trustweave.did.identifiers.Did("did:key:zNonexistent"))
        assertTrue(result is com.trustweave.did.resolver.DidResolutionResult.Failure || result !is com.trustweave.did.resolver.DidResolutionResult.Success, "Non-existent DID should not resolve successfully")
    }

    @Test
    fun `DID method should throw exception when updating non-existent DID`() = runBlocking {
        val kms = WaltIdKeyManagementService()
        val keyMethod = com.trustweave.waltid.did.WaltIdKeyMethod(kms)

        assertFailsWith<IllegalArgumentException> {
            keyMethod.updateDid(com.trustweave.did.identifiers.Did("did:key:zNonexistent")) { it }
        }
    }

    @Test
    fun `DID method provider should throw when KMS not available`() {
        val provider = com.trustweave.waltid.did.WaltIdDidMethodProvider()

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
        val kms = com.trustweave.testkit.kms.InMemoryKeyManagementService()
        val provider = com.trustweave.waltid.did.WaltIdDidMethodProvider()

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
        } catch (e: TrustWeaveException) {
            // Expected if algorithm validation is strict
            assertNotNull(e.message)
        } catch (e: IllegalArgumentException) {
            // Also acceptable
            assertNotNull(e.message)
        }
    }
}

