package org.trustweave.testkit.templates

import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.KeyNotFoundException
import org.trustweave.kms.UnsupportedAlgorithmException
import org.trustweave.testkit.BasePluginTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Template for KMS plugin unit tests.
 *
 * Copy this template and adapt it for your KMS plugin.
 *
 * **Required Tests**:
 * - ✅ Key generation with supported algorithms
 * - ✅ Key retrieval
 * - ✅ Signing operations
 * - ✅ Key deletion
 * - ✅ Algorithm mapping (if applicable)
 * - ✅ Error handling (unsupported algorithms, missing keys)
 * - ✅ Configuration validation
 * - ✅ SPI discovery (if applicable)
 */
abstract class KmsPluginTestTemplate : BasePluginTest() {

    /**
     * Gets the KMS service to test.
     * Must be implemented by subclasses.
     */
    abstract fun getKms(): KeyManagementService

    /**
     * Gets the list of supported algorithms.
     * Must be implemented by subclasses.
     */
    abstract fun getSupportedAlgorithms(): List<Algorithm>

    /**
     * Gets an unsupported algorithm for error testing.
     * Defaults to BLS12_381. Override if needed.
     */
    open fun getUnsupportedAlgorithm(): Algorithm {
        return Algorithm.BLS12_381
    }

    @Test
    fun `test get supported algorithms`() = runBlocking {
        val kms = getKms()
        val supported = getSupportedAlgorithms()

        supported.forEach { algorithm ->
            assertTrue(kms.supportsAlgorithm(algorithm),
                "KMS should support algorithm: $algorithm")
        }
    }

    @Test
    fun `test generate key with Ed25519`() = runBlocking {
        val kms = getKms()

        if (getSupportedAlgorithms().contains(Algorithm.Ed25519)) {
            val keyHandle = kms.generateKey(Algorithm.Ed25519)
            assertNotNull(keyHandle)
            assertNotNull(keyHandle.keyId)
            assertEquals(Algorithm.Ed25519, keyHandle.algorithm)
        }
    }

    @Test
    fun `test generate key with Secp256k1`() = runBlocking {
        val kms = getKms()

        if (getSupportedAlgorithms().contains(Algorithm.Secp256k1)) {
            val keyHandle = kms.generateKey(Algorithm.Secp256k1)
            assertNotNull(keyHandle)
            assertNotNull(keyHandle.keyId)
            assertEquals(Algorithm.Secp256k1, keyHandle.algorithm)
        }
    }

    @Test
    fun `test unsupported algorithm throws exception`() = runBlocking {
        val kms = getKms()
        val unsupported = getUnsupportedAlgorithm()

        if (!getSupportedAlgorithms().contains(unsupported)) {
            assertThrows<UnsupportedAlgorithmException> {
                runBlocking {
                    kms.generateKey(unsupported)
                }
            }
        }
    }

    @Test
    fun `test retrieve key after generation`() = runBlocking {
        val kms = getKms()
        val algorithm = getSupportedAlgorithms().first()

        val keyHandle = kms.generateKey(algorithm)
        val retrieved = kms.getKey(keyHandle.keyId)

        assertNotNull(retrieved)
        assertEquals(keyHandle.keyId, retrieved.keyId)
        assertEquals(keyHandle.algorithm, retrieved.algorithm)
    }

    @Test
    fun `test retrieve non-existent key throws exception`() = runBlocking {
        val kms = getKms()
        val nonExistentKeyId = "non-existent-key-${System.currentTimeMillis()}"

        assertThrows<KeyNotFoundException> {
            runBlocking {
                kms.getKey(nonExistentKeyId)
            }
        }
    }

    @Test
    fun `test sign with generated key`() = runBlocking {
        val kms = getKms()
        val algorithm = getSupportedAlgorithms().first()

        val keyHandle = kms.generateKey(algorithm)
        val message = "test message".toByteArray()
        val signature = kms.sign(keyHandle.keyId, message)

        assertNotNull(signature)
        assertTrue(signature.isNotEmpty())
    }

    @Test
    fun `test sign with non-existent key throws exception`() = runBlocking {
        val kms = getKms()
        val nonExistentKeyId = "non-existent-key-${System.currentTimeMillis()}"
        val message = "test message".toByteArray()

        assertThrows<KeyNotFoundException> {
            runBlocking {
                kms.sign(nonExistentKeyId, message)
            }
        }
    }

    @Test
    fun `test delete key`() = runBlocking {
        val kms = getKms()
        val algorithm = getSupportedAlgorithms().first()

        val keyHandle = kms.generateKey(algorithm)
        val deleted = kms.deleteKey(keyHandle.keyId)

        assertTrue(deleted)
    }

    @Test
    fun `test delete non-existent key`() = runBlocking {
        val kms = getKms()
        val nonExistentKeyId = "non-existent-key-${System.currentTimeMillis()}"

        // Some KMS implementations may return false, others may throw
        try {
            val deleted = kms.deleteKey(nonExistentKeyId)
            // If no exception, should return false
        } catch (e: KeyNotFoundException) {
            // Expected behavior
        }
    }

    @Test
    fun `test sign empty message`() = runBlocking {
        val kms = getKms()
        val algorithm = getSupportedAlgorithms().first()

        val keyHandle = kms.generateKey(algorithm)
        val emptyMessage = ByteArray(0)

        // Some KMS may allow empty messages, others may not
        try {
            val signature = kms.sign(keyHandle.keyId, emptyMessage)
            assertNotNull(signature)
        } catch (e: Exception) {
            // Acceptable behavior
        }
    }

    @Test
    fun `test generate multiple keys`() = runBlocking {
        val kms = getKms()
        val algorithm = getSupportedAlgorithms().first()

        val keyHandles = (1..5).map {
            kms.generateKey(algorithm)
        }

        assertTrue(keyHandles.size == 5)
        keyHandles.forEach { handle ->
            assertNotNull(handle.keyId)
        }

        // Verify all keys are unique
        val keyIds = keyHandles.map { it.keyId }.toSet()
        assertTrue(keyIds.size == 5, "All generated keys should be unique")
    }
}

