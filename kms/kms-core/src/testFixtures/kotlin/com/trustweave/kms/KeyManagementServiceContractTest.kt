package com.trustweave.kms

import com.trustweave.core.identifiers.KeyId
import com.trustweave.kms.results.DeleteKeyResult
import com.trustweave.kms.results.GenerateKeyResult
import com.trustweave.kms.results.GetPublicKeyResult
import com.trustweave.kms.results.SignResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Abstract contract test for KeyManagementService implementations.
 *
 * This test verifies that all implementations correctly follow the KeyManagementService
 * interface contract. Each plugin should extend this class and provide a concrete
 * implementation.
 *
 * **Purpose:**
 * - Verify interface contract compliance
 * - Ensure consistent behavior across all implementations
 * - Fast, lightweight tests (no testkit dependency)
 * - Test Result-based API correctness
 *
 * **Usage:**
 * ```kotlin
 * class MyPluginContractTest : KeyManagementServiceContractTest() {
 *     override fun createKms(): KeyManagementService {
 *         return MyKeyManagementService(config)
 *     }
 *
 *     override fun getSupportedAlgorithms(): List<Algorithm> {
 *         return MyKeyManagementService.SUPPORTED_ALGORITHMS.toList()
 *     }
 * }
 * ```
 *
 * **Test Coverage:**
 * - ✅ Key generation with all supported algorithms
 * - ✅ Public key retrieval
 * - ✅ Signing operations
 * - ✅ Key deletion
 * - ✅ Full key lifecycle
 * - ✅ Error handling (unsupported algorithms, missing keys)
 * - ✅ Input validation
 * - ✅ Result type correctness
 */
abstract class KeyManagementServiceContractTest {

    /**
     * Creates a KeyManagementService instance to test.
     * Must be implemented by subclasses.
     */
    abstract fun createKms(): KeyManagementService

    /**
     * Returns the list of algorithms supported by this KMS.
     * Must be implemented by subclasses.
     */
    abstract fun getSupportedAlgorithms(): List<Algorithm>

    /**
     * Returns an algorithm that is NOT supported by this KMS.
     * Defaults to BLS12_381. Override if needed.
     */
    open fun getUnsupportedAlgorithm(): Algorithm {
        return Algorithm.BLS12_381
    }

    @Test
    fun `test getSupportedAlgorithms returns correct set`() = runBlocking {
        val kms = createKms()
        val expected = getSupportedAlgorithms().toSet()
        val actual = kms.getSupportedAlgorithms()

        assertEquals(expected.size, actual.size, "Supported algorithms count mismatch")
        expected.forEach { algorithm ->
            assertTrue(
                actual.contains(algorithm),
                "Algorithm ${algorithm.name} should be in supported set"
            )
        }
    }

    @Test
    fun `test supportsAlgorithm returns true for supported algorithms`() = runBlocking {
        val kms = createKms()
        getSupportedAlgorithms().forEach { algorithm ->
            assertTrue(
                kms.supportsAlgorithm(algorithm),
                "KMS should support algorithm: ${algorithm.name}"
            )
        }
    }

    @Test
    fun `test supportsAlgorithm returns false for unsupported algorithms`() = runBlocking {
        val kms = createKms()
        val unsupported = getUnsupportedAlgorithm()

        if (!getSupportedAlgorithms().contains(unsupported)) {
            assertFalse(
                kms.supportsAlgorithm(unsupported),
                "KMS should not support algorithm: ${unsupported.name}"
            )
        }
    }

    @Test
    fun `test generateKey with all supported algorithms`() = runBlocking {
        val kms = createKms()
        val supported = getSupportedAlgorithms()

        supported.forEach { algorithm ->
            val result = kms.generateKey(algorithm)
            assertTrue(
                result is GenerateKeyResult.Success,
                "Failed to generate key for ${algorithm.name}: $result"
            )

            val handle = result.keyHandle
            assertNotNull(handle, "KeyHandle should not be null")
            assertNotNull(handle.id, "Key ID should not be null")
            assertEquals(algorithm.name, handle.algorithm, "Algorithm name should match")
            assertNotNull(handle.publicKeyJwk, "Public key JWK should not be null")
        }
    }

    @Test
    fun `test generateKey with unsupported algorithm returns UnsupportedAlgorithm`() = runBlocking {
        val kms = createKms()
        val unsupported = getUnsupportedAlgorithm()

        if (!getSupportedAlgorithms().contains(unsupported)) {
            val result = kms.generateKey(unsupported)
            assertTrue(
                result is GenerateKeyResult.Failure.UnsupportedAlgorithm,
                "Should return UnsupportedAlgorithm for ${unsupported.name}, got: $result"
            )
            assertEquals(unsupported, result.algorithm)
            assertNotNull(result.supportedAlgorithms)
        }
    }

    @Test
    fun `test generateKey with custom key ID`() = runBlocking {
        val kms = createKms()
        val algorithm = getSupportedAlgorithms().first()
        val customKeyId = "custom-key-${System.currentTimeMillis()}"

        val result = kms.generateKey(
            algorithm,
            mapOf(KmsOptionKeys.KEY_ID to customKeyId)
        )

        assertTrue(result is GenerateKeyResult.Success)
        assertEquals(customKeyId, result.keyHandle.id.value)
    }

    @Test
    fun `test getPublicKey retrieves existing key`() = runBlocking {
        val kms = createKms()
        val algorithm = getSupportedAlgorithms().first()

        val generateResult = kms.generateKey(algorithm)
        assertTrue(generateResult is GenerateKeyResult.Success)
        val keyId = generateResult.keyHandle.id

        val getResult = kms.getPublicKey(keyId)
        assertTrue(getResult is GetPublicKeyResult.Success)
        assertEquals(keyId, getResult.keyHandle.id)
        assertEquals(algorithm.name, getResult.keyHandle.algorithm)
        assertNotNull(getResult.keyHandle.publicKeyJwk)
    }

    @Test
    fun `test getPublicKey returns KeyNotFound for non-existent key`() = runBlocking {
        val kms = createKms()
        val nonExistentKeyId = KeyId("non-existent-key-${System.currentTimeMillis()}")

        val result = kms.getPublicKey(nonExistentKeyId)
        assertTrue(
            result is GetPublicKeyResult.Failure.KeyNotFound,
            "Should return KeyNotFound, got: $result"
        )
        assertEquals(nonExistentKeyId, result.keyId)
    }

    @Test
    fun `test sign signs data successfully`() = runBlocking {
        val kms = createKms()
        val algorithm = getSupportedAlgorithms().first()
        val data = "test message".toByteArray()

        val generateResult = kms.generateKey(algorithm)
        assertTrue(generateResult is GenerateKeyResult.Success)
        val keyId = generateResult.keyHandle.id

        val sign = kms.sign(keyId, data)
        assertTrue(sign is SignResult.Success)
        assertNotNull(sign.signature)
        assertTrue(sign.signature.isNotEmpty(), "Signature should not be empty")
    }

    @Test
    fun `test sign with algorithm override`() = runBlocking {
        val kms = createKms()
        val algorithm = getSupportedAlgorithms().first()
        val data = "test data".toByteArray()

        val generateResult = kms.generateKey(algorithm)
        assertTrue(generateResult is GenerateKeyResult.Success)
        val keyId = generateResult.keyHandle.id

        // Sign with explicit algorithm (should use key's algorithm if compatible)
        val sign = kms.sign(keyId, data, algorithm)
        assertTrue(sign is SignResult.Success)
        assertNotNull(sign.signature)
    }

    @Test
    fun `test sign returns KeyNotFound for non-existent key`() = runBlocking {
        val kms = createKms()
        val nonExistentKeyId = KeyId("non-existent-key-${System.currentTimeMillis()}")
        val data = "test data".toByteArray()

        val result = kms.sign(nonExistentKeyId, data)
        assertTrue(
            result is SignResult.Failure.KeyNotFound,
            "Should return KeyNotFound, got: $result"
        )
        assertEquals(nonExistentKeyId, result.keyId)
    }

    @Test
    fun `test deleteKey deletes existing key`() = runBlocking {
        val kms = createKms()
        val algorithm = getSupportedAlgorithms().first()

        val generateResult = kms.generateKey(algorithm)
        assertTrue(generateResult is GenerateKeyResult.Success)
        val keyId = generateResult.keyHandle.id

        val deleteResult = kms.deleteKey(keyId)
        assertTrue(
            deleteResult is DeleteKeyResult.Deleted,
            "Should return Deleted, got: $deleteResult"
        )

        // Verify key is actually deleted
        val getResult = kms.getPublicKey(keyId)
        assertTrue(
            getResult is GetPublicKeyResult.Failure.KeyNotFound,
            "Key should be deleted, got: $getResult"
        )
    }

    @Test
    fun `test deleteKey returns NotFound for non-existent key`() = runBlocking {
        val kms = createKms()
        val nonExistentKeyId = KeyId("non-existent-key-${System.currentTimeMillis()}")

        val result = kms.deleteKey(nonExistentKeyId)
        assertTrue(
            result is DeleteKeyResult.NotFound,
            "Should return NotFound, got: $result"
        )
    }

    @Test
    fun `test deleteKey is idempotent`() = runBlocking {
        val kms = createKms()
        val algorithm = getSupportedAlgorithms().first()

        val generateResult = kms.generateKey(algorithm)
        assertTrue(generateResult is GenerateKeyResult.Success)
        val keyId = generateResult.keyHandle.id

        val firstDelete = kms.deleteKey(keyId)
        assertTrue(firstDelete is DeleteKeyResult.Deleted)

        val secondDelete = kms.deleteKey(keyId)
        assertTrue(
            secondDelete is DeleteKeyResult.NotFound,
            "Second delete should return NotFound (idempotent), got: $secondDelete"
        )
    }

    @Test
    fun `test full key lifecycle`() = runBlocking {
        val kms = createKms()
        val algorithm = getSupportedAlgorithms().first()
        val data = "test data".toByteArray()

        // 1. Generate key
        val generateResult = kms.generateKey(algorithm)
        assertTrue(generateResult is GenerateKeyResult.Success)
        val keyId = generateResult.keyHandle.id

        // 2. Get public key
        val getResult = kms.getPublicKey(keyId)
        assertTrue(getResult is GetPublicKeyResult.Success)
        assertEquals(keyId, getResult.keyHandle.id)
        assertEquals(algorithm.name, getResult.keyHandle.algorithm)

        // 3. Sign data
        val sign = kms.sign(keyId, data)
        assertTrue(sign is SignResult.Success)
        assertTrue(sign.signature.isNotEmpty())

        // 4. Delete key
        val deleteResult = kms.deleteKey(keyId)
        assertTrue(deleteResult is DeleteKeyResult.Deleted)

        // 5. Verify key is deleted
        val getAfterDelete = kms.getPublicKey(keyId)
        assertTrue(getAfterDelete is GetPublicKeyResult.Failure.KeyNotFound)

        // 6. Verify signing fails after deletion
        val signAfterDelete = kms.sign(keyId, data)
        assertTrue(signAfterDelete is SignResult.Failure.KeyNotFound)
    }

    @Test
    fun `test generateKey with multiple keys`() = runBlocking {
        val kms = createKms()
        val algorithm = getSupportedAlgorithms().first()

        val keyHandles = (1..5).map {
            val result = kms.generateKey(algorithm)
            assertTrue(result is GenerateKeyResult.Success)
            result.keyHandle
        }

        assertEquals(5, keyHandles.size)
        keyHandles.forEach { handle ->
            assertNotNull(handle.id)
            assertEquals(algorithm.name, handle.algorithm)
        }

        // Verify all keys are unique
        val keyIds = keyHandles.map { it.id }.toSet()
        assertEquals(5, keyIds.size, "All generated keys should be unique")
    }

    @Test
    fun `test sign with different data sizes`() = runBlocking {
        val kms = createKms()
        val algorithm = getSupportedAlgorithms().first()

        val generateResult = kms.generateKey(algorithm)
        assertTrue(generateResult is GenerateKeyResult.Success)
        val keyId = generateResult.keyHandle.id

        val dataSizes = listOf(1, 10, 100, 1000, 10000)
        dataSizes.forEach { size ->
            val data = ByteArray(size) { it.toByte() }
            val sign = kms.sign(keyId, data)
            assertTrue(
                sign is SignResult.Success,
                "Should sign data of size $size, got: $sign"
            )
            assertTrue(sign.signature.isNotEmpty())
        }
    }

    @Test
    fun `test generateKey with duplicate key ID returns InvalidOptions`() = runBlocking {
        val kms = createKms()
        val algorithm = getSupportedAlgorithms().first()
        val keyId = "duplicate-key-${System.currentTimeMillis()}"

        val firstResult = kms.generateKey(
            algorithm,
            mapOf(KmsOptionKeys.KEY_ID to keyId)
        )
        assertTrue(firstResult is GenerateKeyResult.Success)

        val secondResult = kms.generateKey(
            algorithm,
            mapOf(KmsOptionKeys.KEY_ID to keyId)
        )
        assertTrue(
            secondResult is GenerateKeyResult.Failure.InvalidOptions,
            "Should reject duplicate key ID, got: $secondResult"
        )
    }

    @Test
    fun `test publicKeyJwk format is valid`() = runBlocking {
        val kms = createKms()
        val algorithm = getSupportedAlgorithms().first()

        val generateResult = kms.generateKey(algorithm)
        assertTrue(generateResult is GenerateKeyResult.Success)
        val jwk = generateResult.keyHandle.publicKeyJwk

        assertNotNull(jwk, "Public key JWK should not be null")
        assertTrue(jwk.containsKey(JwkKeys.KTY), "JWK should contain 'kty' field")
        assertNotNull(jwk[JwkKeys.KTY], "JWK 'kty' should not be null")
    }
}


