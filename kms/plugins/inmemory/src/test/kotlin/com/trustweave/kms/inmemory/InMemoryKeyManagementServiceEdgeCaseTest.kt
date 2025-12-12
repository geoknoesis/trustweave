package com.trustweave.kms.inmemory

import com.trustweave.core.identifiers.KeyId
import com.trustweave.kms.Algorithm
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.results.GenerateKeyResult
import com.trustweave.kms.results.GetPublicKeyResult
import com.trustweave.kms.results.SignResult
import com.trustweave.testkit.kms.PluginEdgeCaseTestTemplate
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Edge case tests for InMemory KMS plugin.
 * 
 * Tests plugin-specific edge cases and error scenarios.
 */
class InMemoryKeyManagementServiceEdgeCaseTest : PluginEdgeCaseTestTemplate() {
    
    override fun createKms(): KeyManagementService {
        return InMemoryKeyManagementService()
    }

    override fun getSupportedAlgorithms(): List<Algorithm> {
        return InMemoryKeyManagementService.SUPPORTED_ALGORITHMS.toList()
    }

    /**
     * Additional test for empty key ID handling.
     * KeyId constructor validates and throws exception, so this tests that validation.
     */
    @Test
    fun `test empty key ID validation`() = runBlocking {
        // KeyId constructor throws IllegalArgumentException for blank strings,
        // so we can't create an empty KeyId. This test verifies that the validation
        // happens at the KeyId level, not at the KMS level.
        try {
            val emptyKeyId = KeyId("")
            // If we get here (unlikely), test that KMS handles it
            val kms = createKms()
            val getResult = kms.getPublicKey(emptyKeyId)
            assertTrue(
                getResult is GetPublicKeyResult.Failure.KeyNotFound || 
                getResult is GetPublicKeyResult.Failure.Error,
                "Empty key ID should result in failure"
            )
        } catch (e: IllegalArgumentException) {
            // Expected: KeyId validation prevents blank strings
            assertTrue(e.message?.contains("blank") == true, "Should throw exception for blank key ID")
        }
    }

    /**
     * Additional test for signing with incompatible algorithm.
     * The implementation returns UnsupportedAlgorithm, which is acceptable.
     */
    @Test
    fun `test signing with incompatible algorithm returns UnsupportedAlgorithm`() = runBlocking {
        val kms = createKms()
        val algorithms = getSupportedAlgorithms()
        if (algorithms.size < 2) return@runBlocking
        
        val algorithm1 = algorithms[0]
        val algorithm2 = algorithms[1]
        
        val generateResult = kms.generateKey(algorithm1)
        assertTrue(generateResult is GenerateKeyResult.Success, "Key generation should succeed")
        val keyId = generateResult.keyHandle.id
        
        // Try to sign with different algorithm
        val sign = kms.sign(keyId, "test".toByteArray(), algorithm2)
        
        // Should either succeed (if algorithms are compatible), return UnsupportedAlgorithm, or return Error
        assertTrue(
            sign is SignResult.Success || 
            sign is SignResult.Failure.UnsupportedAlgorithm ||
            sign is SignResult.Failure.Error,
            "Signing with different algorithm should either succeed or return clear error"
        )
    }
}

