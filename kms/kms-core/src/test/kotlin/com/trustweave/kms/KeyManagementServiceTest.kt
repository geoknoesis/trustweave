package com.trustweave.kms

import com.trustweave.core.types.KeyId
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for KeyManagementService models and KeyNotFoundException.
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
    fun `test KeyNotFoundException`() {
        val exception = KeyNotFoundException(KeyId("key-123").value)

        assertEquals("Key not found: key-123", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun `test KeyNotFoundException constructor`() {
        val exception = KeyNotFoundException(KeyId("key-123").value)

        assertEquals("Key not found: key-123", exception.message)
    }

    @Test
    fun `test KeyManagementService interface methods`() = runBlocking {
        val kms = object : KeyManagementService {
            private val keys = mutableMapOf<KeyId, KeyHandle>()

            override suspend fun getSupportedAlgorithms(): Set<Algorithm> {
                return setOf(Algorithm.Ed25519, Algorithm.Secp256k1, Algorithm.P256)
            }

            override suspend fun generateKey(algorithm: Algorithm, options: Map<String, Any?>): KeyHandle {
                val keyId = KeyId("key-${keys.size + 1}")
                val handle = KeyHandle(
                    id = keyId,
                    algorithm = algorithm.name
                )
                keys[handle.id] = handle
                return handle
            }

            override suspend fun getPublicKey(keyId: KeyId): KeyHandle {
                return keys[keyId] ?: throw KeyNotFoundException(keyId.value)
            }

            override suspend fun sign(keyId: KeyId, data: ByteArray, algorithm: Algorithm?): ByteArray {
                keys[keyId] ?: throw KeyNotFoundException(keyId.value)
                return ByteArray(64) // Mock signature
            }

            override suspend fun deleteKey(keyId: KeyId): Boolean {
                return keys.remove(keyId) != null
            }
        }

        val handle = kms.generateKey("Ed25519")
        assertEquals("key-1", handle.id.value)

        val publicKey = kms.getPublicKey(handle.id)
        assertEquals(handle, publicKey)

        val signature = kms.sign(handle.id, "test".toByteArray())
        assertEquals(64, signature.size)

        assertTrue(kms.deleteKey(handle.id))
        assertFalse(kms.deleteKey(handle.id))
    }

    @Test
    fun `test KeyManagementService getPublicKey throws KeyNotFoundException`() = runBlocking {
        val kms = object : KeyManagementService {
            override suspend fun getSupportedAlgorithms(): Set<Algorithm> {
                return setOf(Algorithm.Ed25519)
            }

            override suspend fun generateKey(algorithm: Algorithm, options: Map<String, Any?>) = TODO()
            override suspend fun getPublicKey(keyId: KeyId) = throw KeyNotFoundException(keyId.value)
            override suspend fun sign(keyId: KeyId, data: ByteArray, algorithm: Algorithm?) = TODO()
            override suspend fun deleteKey(keyId: KeyId) = TODO()
        }

        assertFailsWith<KeyNotFoundException> {
            kms.getPublicKey(KeyId("nonexistent"))
        }
    }
}
