package com.trustweave.kms

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
            id = "key-1",
            algorithm = "Ed25519",
            publicKeyJwk = mapOf("kty" to "OKP", "crv" to "Ed25519"),
            publicKeyMultibase = "z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
        )
        
        assertEquals("key-1", handle.id)
        assertEquals("Ed25519", handle.algorithm)
        assertNotNull(handle.publicKeyJwk)
        assertNotNull(handle.publicKeyMultibase)
    }

    @Test
    fun `test KeyHandle with defaults`() {
        val handle = KeyHandle(
            id = "key-1",
            algorithm = "Ed25519"
        )
        
        assertNull(handle.publicKeyJwk)
        assertNull(handle.publicKeyMultibase)
    }

    @Test
    fun `test KeyNotFoundException`() {
        val exception = KeyNotFoundException("Key not found: key-123")
        
        assertEquals("Key not found: key-123", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun `test KeyNotFoundException with cause`() {
        val cause = RuntimeException("Underlying error")
        val exception = KeyNotFoundException("Key not found", cause)
        
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `test KeyManagementService interface methods`() = runBlocking {
        val kms = object : KeyManagementService {
            private val keys = mutableMapOf<String, KeyHandle>()
            
            override suspend fun getSupportedAlgorithms(): Set<Algorithm> {
                return setOf(Algorithm.Ed25519, Algorithm.Secp256k1, Algorithm.P256)
            }
            
            override suspend fun generateKey(algorithm: Algorithm, options: Map<String, Any?>): KeyHandle {
                val handle = KeyHandle(
                    id = "key-${keys.size + 1}",
                    algorithm = algorithm.name
                )
                keys[handle.id] = handle
                return handle
            }
            
            override suspend fun getPublicKey(keyId: String): KeyHandle {
                return keys[keyId] ?: throw KeyNotFoundException("Key not found: $keyId")
            }
            
            override suspend fun sign(keyId: String, data: ByteArray, algorithm: Algorithm?): ByteArray {
                keys[keyId] ?: throw KeyNotFoundException("Key not found: $keyId")
                return ByteArray(64) // Mock signature
            }
            
            override suspend fun deleteKey(keyId: String): Boolean {
                return keys.remove(keyId) != null
            }
        }
        
        val handle = kms.generateKey("Ed25519")
        assertEquals("key-1", handle.id)
        
        val publicKey = kms.getPublicKey("key-1")
        assertEquals(handle, publicKey)
        
        val signature = kms.sign("key-1", "test".toByteArray())
        assertEquals(64, signature.size)
        
        assertTrue(kms.deleteKey("key-1"))
        assertFalse(kms.deleteKey("key-1"))
    }

    @Test
    fun `test KeyManagementService getPublicKey throws KeyNotFoundException`() = runBlocking {
        val kms = object : KeyManagementService {
            override suspend fun getSupportedAlgorithms(): Set<Algorithm> {
                return setOf(Algorithm.Ed25519)
            }
            
            override suspend fun generateKey(algorithm: Algorithm, options: Map<String, Any?>) = TODO()
            override suspend fun getPublicKey(keyId: String) = throw KeyNotFoundException("Key not found: $keyId")
            override suspend fun sign(keyId: String, data: ByteArray, algorithm: Algorithm?) = TODO()
            override suspend fun deleteKey(keyId: String) = TODO()
        }
        
        assertFailsWith<KeyNotFoundException> {
            kms.getPublicKey("nonexistent")
        }
    }
}
