package org.trustweave.kms

import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.results.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for KeyManagementService models and Result-based operations.
 */
class KeyManagementServiceTest {

    @Test
    fun `test KeyHandle with all fields`() {
        val handle = KeyHandle(
            id = KeyId("key-1"),
            algorithm = "Ed25519",
            publicKeyJwk = mapOf("kty" to "OKP", "crv" to "Ed25519"),
            publicKeyMultibase = "z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
        )

        assertEquals("key-1", handle.id.value)
        assertEquals("Ed25519", handle.algorithm)
        assertNotNull(handle.publicKeyJwk)
        assertNotNull(handle.publicKeyMultibase)
    }

    @Test
    fun `test KeyHandle with defaults`() {
        val handle = KeyHandle(
            id = KeyId("key-1"),
            algorithm = "Ed25519"
        )

        assertNull(handle.publicKeyJwk)
        assertNull(handle.publicKeyMultibase)
    }

    @Test
    fun `test KeyManagementService interface methods`() = runBlocking {
        val kms = object : KeyManagementService {
            private val keys = mutableMapOf<KeyId, KeyHandle>()

            override suspend fun getSupportedAlgorithms(): Set<Algorithm> {
                return setOf(Algorithm.Ed25519, Algorithm.Secp256k1, Algorithm.P256)
            }

            override suspend fun generateKey(algorithm: Algorithm, options: Map<String, Any?>): GenerateKeyResult {
                val keyId = KeyId("key-${keys.size + 1}")
                val handle = KeyHandle(
                    id = keyId,
                    algorithm = algorithm.name
                )
                keys[handle.id] = handle
                return GenerateKeyResult.Success(handle)
            }

            override suspend fun getPublicKey(keyId: KeyId): GetPublicKeyResult {
                return keys[keyId]?.let { GetPublicKeyResult.Success(it) }
                    ?: GetPublicKeyResult.Failure.KeyNotFound(keyId)
            }

            override suspend fun sign(keyId: KeyId, data: ByteArray, algorithm: Algorithm?): SignResult {
                return keys[keyId]?.let { SignResult.Success(ByteArray(64)) }
                    ?: SignResult.Failure.KeyNotFound(keyId)
            }

            override suspend fun deleteKey(keyId: KeyId): DeleteKeyResult {
                return if (keys.remove(keyId) != null) {
                    DeleteKeyResult.Deleted
                } else {
                    DeleteKeyResult.NotFound
                }
            }
        }

        val result = kms.generateKey("Ed25519")
        assertTrue(result is GenerateKeyResult.Success)
        val handle = result.keyHandle
        assertEquals("key-1", handle.id.value)

        val publicKeyResult = kms.getPublicKey(handle.id)
        assertTrue(publicKeyResult is GetPublicKeyResult.Success)
        assertEquals(handle, publicKeyResult.keyHandle)

        val sign = kms.sign(handle.id, "test".toByteArray())
        assertTrue(sign is SignResult.Success)
        assertEquals(64, sign.signature.size)

        val deleteResult = kms.deleteKey(handle.id)
        assertTrue(deleteResult is DeleteKeyResult.Deleted)
        
        val deleteResult2 = kms.deleteKey(handle.id)
        assertTrue(deleteResult2 is DeleteKeyResult.NotFound)
    }

    @Test
    fun `test KeyManagementService getPublicKey returns KeyNotFound`() = runBlocking {
        val kms = object : KeyManagementService {
            private val keys = mutableMapOf<KeyId, KeyHandle>()

            override suspend fun getSupportedAlgorithms(): Set<Algorithm> {
                return setOf(Algorithm.Ed25519)
            }

            override suspend fun generateKey(algorithm: Algorithm, options: Map<String, Any?>): GenerateKeyResult {
                val keyId = KeyId("key-${keys.size + 1}")
                val handle = KeyHandle(id = keyId, algorithm = algorithm.name)
                keys[keyId] = handle
                return GenerateKeyResult.Success(handle)
            }

            override suspend fun getPublicKey(keyId: KeyId) = GetPublicKeyResult.Failure.KeyNotFound(keyId)

            override suspend fun sign(keyId: KeyId, data: ByteArray, algorithm: Algorithm?): SignResult {
                return SignResult.Failure.KeyNotFound(keyId)
            }

            override suspend fun deleteKey(keyId: KeyId): DeleteKeyResult {
                return DeleteKeyResult.NotFound
            }
        }

        val result = kms.getPublicKey(KeyId("nonexistent"))
        assertTrue(result is GetPublicKeyResult.Failure.KeyNotFound)
        assertEquals(KeyId("nonexistent"), result.keyId)
    }
}
