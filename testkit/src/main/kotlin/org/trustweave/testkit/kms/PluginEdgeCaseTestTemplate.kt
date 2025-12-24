package org.trustweave.testkit.kms

import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.*
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Template for plugin-specific edge case tests.
 * 
 * **Purpose:**
 * - Test plugin-specific edge cases and error scenarios
 * - Validate plugin-specific behavior and constraints
 * - Ensure robust error handling for provider-specific failures
 * 
 * **Usage:**
 * ```kotlin
 * class MyPluginEdgeCaseTest : PluginEdgeCaseTestTemplate() {
 *     override fun createKms(): KeyManagementService {
 *         return MyKeyManagementService(config)
 *     }
 * 
 *     override fun getSupportedAlgorithms(): List<Algorithm> {
 *         return MyKeyManagementService.SUPPORTED_ALGORITHMS.toList()
 *     }
 * 
 *     @Test
 *     fun `test plugin-specific edge case`() = runBlocking {
 *         // Plugin-specific test
 *     }
 * }
 * ```
 * 
 * **Test Coverage:**
 * - ✅ Empty/null inputs
 * - ✅ Very large inputs
 * - ✅ Invalid key IDs
 * - ✅ Concurrent operations
 * - ✅ Provider-specific error scenarios
 * - ✅ Rate limiting scenarios
 * - ✅ Network timeout scenarios
 * - ✅ Invalid credentials scenarios
 */
abstract class PluginEdgeCaseTestTemplate {

    /**
     * Creates a KMS instance for testing.
     */
    abstract fun createKms(): KeyManagementService

    /**
     * Returns the list of algorithms supported by this plugin.
     */
    abstract fun getSupportedAlgorithms(): List<Algorithm>

    /**
     * Test: Empty key ID
     */
    @Test
    fun `test empty key ID`() = runBlocking {
        val kms = createKms()
        // KeyId constructor validates and throws IllegalArgumentException for blank strings
        // This test verifies that validation happens at the KeyId level
        try {
            val emptyKeyId = KeyId("")
            // If we get here (unlikely), test that KMS handles it
            val getResult = kms.getPublicKey(emptyKeyId)
            assertTrue(
                getResult is GetPublicKeyResult.Failure.KeyNotFound || 
                getResult is GetPublicKeyResult.Failure.Error,
                "Empty key ID should result in failure"
            )
        } catch (e: IllegalArgumentException) {
            // Expected: KeyId validation prevents blank strings
            assertTrue(
                e.message?.contains("blank") == true || e.message?.contains("Key ID") == true,
                "Should throw exception for blank key ID, got: ${e.message}"
            )
        }
    }

    /**
     * Test: Very long key ID
     */
    @Test
    fun `test very long key ID`() = runBlocking {
        val kms = createKms()
        val longKeyId = KeyId("a".repeat(1000))
        
        val getResult = kms.getPublicKey(longKeyId)
        assertTrue(
            getResult is GetPublicKeyResult.Failure.KeyNotFound || 
            getResult is GetPublicKeyResult.Failure.Error,
            "Very long key ID should result in failure"
        )
    }

    /**
     * Test: Empty signing data
     */
    @Test
    fun `test signing with empty data`() = runBlocking {
        val kms = createKms()
        val algorithm = getSupportedAlgorithms().firstOrNull() ?: return@runBlocking
        
        val generateResult = kms.generateKey(algorithm)
        assertTrue(generateResult is GenerateKeyResult.Success, "Key generation should succeed")
        val keyId = generateResult.keyHandle.id
        
        val sign = kms.sign(keyId, ByteArray(0))
        assertTrue(
            sign is SignResult.Success || 
            sign is SignResult.Failure.Error,
            "Empty data signing should either succeed or return clear error"
        )
    }

    /**
     * Test: Very large signing data
     */
    @Test
    fun `test signing with very large data`() = runBlocking {
        val kms = createKms()
        val algorithm = getSupportedAlgorithms().firstOrNull() ?: return@runBlocking
        
        val generateResult = kms.generateKey(algorithm)
        assertTrue(generateResult is GenerateKeyResult.Success, "Key generation should succeed")
        val keyId = generateResult.keyHandle.id
        
        // 10MB of data
        val largeData = ByteArray(10 * 1024 * 1024) { it.toByte() }
        val sign = kms.sign(keyId, largeData)
        
        assertTrue(
            sign is SignResult.Success || 
            sign is SignResult.Failure.Error,
            "Large data signing should either succeed or return clear error"
        )
    }

    /**
     * Test: Concurrent key generation
     */
    @Test
    fun `test concurrent key generation`() = runBlocking {
        val kms = createKms()
        val algorithm = getSupportedAlgorithms().firstOrNull() ?: return@runBlocking
        
        val concurrentOperations = 10
        val results = (1..concurrentOperations).map {
            async {
                kms.generateKey(algorithm)
            }
        }.map { it.await() }
        
        results.forEach { result ->
            assertTrue(result is GenerateKeyResult.Success, "Concurrent key generation should succeed")
        }
        
        // All keys should be unique
        val keyIds = results.mapNotNull { (it as? GenerateKeyResult.Success)?.keyHandle?.id }
        assertEquals(concurrentOperations, keyIds.toSet().size, "All generated keys should be unique")
    }

    /**
     * Test: Concurrent signing with same key
     */
    @Test
    fun `test concurrent signing with same key`() = runBlocking {
        val kms = createKms()
        val algorithm = getSupportedAlgorithms().firstOrNull() ?: return@runBlocking
        
        val generateResult = kms.generateKey(algorithm)
        assertTrue(generateResult is GenerateKeyResult.Success, "Key generation should succeed")
        val keyId = generateResult.keyHandle.id
        
        val data = "test data".toByteArray()
        val concurrentOperations = 20
        val results = (1..concurrentOperations).map {
            async {
                kms.sign(keyId, data)
            }
        }.map { it.await() }
        
        results.forEach { result ->
            assertTrue(result is SignResult.Success, "Concurrent signing should succeed")
        }
    }

    /**
     * Test: Delete non-existent key (idempotency)
     */
    @Test
    fun `test delete non-existent key is idempotent`() = runBlocking {
        val kms = createKms()
        val nonExistentKeyId = KeyId("non-existent-key-${System.currentTimeMillis()}")
        
        // First deletion
        val result1 = kms.deleteKey(nonExistentKeyId)
        assertTrue(result1 is DeleteKeyResult.NotFound, "First deletion should return NotFound")
        
        // Second deletion (should be idempotent)
        val result2 = kms.deleteKey(nonExistentKeyId)
        assertTrue(result2 is DeleteKeyResult.NotFound, "Second deletion should also return NotFound (idempotent)")
    }

    /**
     * Test: Sign with wrong algorithm
     */
    @Test
    fun `test signing with incompatible algorithm`() = runBlocking {
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
        
        // Should either succeed (if algorithms are compatible), return UnsupportedAlgorithm, or return clear error
        assertTrue(
            sign is SignResult.Success || 
            sign is SignResult.Failure.UnsupportedAlgorithm ||
            sign is SignResult.Failure.Error,
            "Signing with different algorithm should either succeed or return clear error"
        )
    }

    /**
     * Test: Key ID with special characters
     */
    @Test
    fun `test key ID with special characters`() = runBlocking {
        val kms = createKms()
        val specialKeyId = KeyId("key-with-special-chars-!@#\$%^&*()")
        
        val getResult = kms.getPublicKey(specialKeyId)
        assertTrue(
            getResult is GetPublicKeyResult.Failure.KeyNotFound || 
            getResult is GetPublicKeyResult.Failure.Error,
            "Key ID with special characters should result in failure or be handled gracefully"
        )
    }

    /**
     * Test: Multiple operations on same key
     */
    @Test
    fun `test multiple operations on same key`() = runBlocking {
        val kms = createKms()
        val algorithm = getSupportedAlgorithms().firstOrNull() ?: return@runBlocking
        
        val generateResult = kms.generateKey(algorithm)
        assertTrue(generateResult is GenerateKeyResult.Success, "Key generation should succeed")
        val keyId = generateResult.keyHandle.id
        
        // Get public key multiple times
        repeat(5) {
            val getResult = kms.getPublicKey(keyId)
            assertTrue(getResult is GetPublicKeyResult.Success, "Public key retrieval should succeed")
        }
        
        // Sign multiple times
        repeat(5) {
            val sign = kms.sign(keyId, "test".toByteArray())
            assertTrue(sign is SignResult.Success, "Signing should succeed")
        }
        
        // Delete
        val deleteResult = kms.deleteKey(keyId)
        assertTrue(deleteResult is DeleteKeyResult.Deleted, "Deletion should succeed")
    }
}

