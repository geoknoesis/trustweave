package com.geoknoesis.vericore.kms

import kotlinx.coroutines.runBlocking
import kotlin.test.*

/**
 * Comprehensive edge case tests for KeyManagementService and KeyHandle.
 */
class KeyManagementServiceEdgeCasesTest {

    @Test
    fun `test KeyHandle with null publicKeyJwk and publicKeyMultibase`() {
        val handle = KeyHandle(
            id = "key-1",
            algorithm = "Ed25519",
            publicKeyJwk = null,
            publicKeyMultibase = null
        )
        
        assertNull(handle.publicKeyJwk)
        assertNull(handle.publicKeyMultibase)
    }

    @Test
    fun `test KeyHandle with only publicKeyJwk`() {
        val handle = KeyHandle(
            id = "key-1",
            algorithm = "Ed25519",
            publicKeyJwk = mapOf("kty" to "OKP", "crv" to "Ed25519"),
            publicKeyMultibase = null
        )
        
        assertNotNull(handle.publicKeyJwk)
        assertNull(handle.publicKeyMultibase)
    }

    @Test
    fun `test KeyHandle with only publicKeyMultibase`() {
        val handle = KeyHandle(
            id = "key-1",
            algorithm = "Ed25519",
            publicKeyJwk = null,
            publicKeyMultibase = "z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK"
        )
        
        assertNull(handle.publicKeyJwk)
        assertNotNull(handle.publicKeyMultibase)
    }

    @Test
    fun `test KeyHandle with empty publicKeyJwk`() {
        val handle = KeyHandle(
            id = "key-1",
            algorithm = "Ed25519",
            publicKeyJwk = emptyMap()
        )
        
        assertNotNull(handle.publicKeyJwk)
        assertTrue(handle.publicKeyJwk!!.isEmpty())
    }

    @Test
    fun `test KeyHandle with complex publicKeyJwk`() {
        val jwk = mapOf(
            "kty" to "OKP",
            "crv" to "Ed25519",
            "x" to "base64url-encoded-public-key",
            "kid" to "key-1",
            "use" to "sig",
            "alg" to "EdDSA"
        )
        val handle = KeyHandle(
            id = "key-1",
            algorithm = "Ed25519",
            publicKeyJwk = jwk
        )
        
        assertEquals(6, handle.publicKeyJwk?.size)
    }

    @Test
    fun `test KeyHandle equality`() {
        val handle1 = KeyHandle(
            id = "key-1",
            algorithm = "Ed25519",
            publicKeyJwk = mapOf("kty" to "OKP")
        )
        val handle2 = KeyHandle(
            id = "key-1",
            algorithm = "Ed25519",
            publicKeyJwk = mapOf("kty" to "OKP")
        )
        
        assertEquals(handle1, handle2)
    }

    @Test
    fun `test KeyHandle toString`() {
        val handle = KeyHandle(
            id = "key-1",
            algorithm = "Ed25519"
        )
        
        val str = handle.toString()
        assertTrue(str.contains("key-1"))
        assertTrue(str.contains("Ed25519"))
    }

    @Test
    fun `test KeyNotFoundException with null cause`() {
        val exception = KeyNotFoundException("Key not found")
        
        assertEquals("Key not found", exception.message)
        assertNull(exception.cause)
    }

    @Test
    fun `test KeyNotFoundException with cause`() {
        val cause = RuntimeException("Underlying error")
        val exception = KeyNotFoundException("Key not found", cause)
        
        assertEquals("Key not found", exception.message)
        assertEquals(cause, exception.cause)
    }

    @Test
    fun `test KeyManagementService generateKey with different algorithms`() = runBlocking {
        val kms = createMockKMS()
        
        val ed25519 = kms.generateKey("Ed25519")
        val secp256k1 = kms.generateKey("secp256k1")
        val rsa = kms.generateKey("RSA")
        
        assertEquals("Ed25519", ed25519.algorithm)
        assertEquals("secp256k1", secp256k1.algorithm)
        assertEquals("RSA", rsa.algorithm)
    }

    @Test
    fun `test KeyManagementService generateKey with options`() = runBlocking {
        val kms = createMockKMS()
        
        val handle = kms.generateKey(
            "Ed25519",
            mapOf("keyId" to "custom-key-id", "metadata" to mapOf("purpose" to "signing"))
        )
        
        assertNotNull(handle)
        assertEquals("Ed25519", handle.algorithm)
    }

    @Test
    fun `test KeyManagementService sign with different algorithms`() = runBlocking {
        val kms = createMockKMS()
        val handle = kms.generateKey("Ed25519")
        
        val signature1 = kms.sign(handle.id, "test".toByteArray(), "Ed25519")
        val signature2 = kms.sign(handle.id, "test".toByteArray(), "EdDSA")
        
        assertNotNull(signature1)
        assertNotNull(signature2)
    }

    @Test
    fun `test KeyManagementService sign with null algorithm`() = runBlocking {
        val kms = createMockKMS()
        val handle = kms.generateKey("Ed25519")
        
        val signature = kms.sign(handle.id, "test".toByteArray(), null as com.geoknoesis.vericore.kms.Algorithm?)
        
        assertNotNull(signature)
    }

    @Test
    fun `test KeyManagementService sign with empty data`() = runBlocking {
        val kms = createMockKMS()
        val handle = kms.generateKey("Ed25519")
        
        val signature = kms.sign(handle.id, ByteArray(0))
        
        assertNotNull(signature)
    }

    @Test
    fun `test KeyManagementService sign with large data`() = runBlocking {
        val kms = createMockKMS()
        val handle = kms.generateKey("Ed25519")
        val largeData = ByteArray(10000) { it.toByte() }
        
        val signature = kms.sign(handle.id, largeData)
        
        assertNotNull(signature)
    }

    @Test
    fun `test KeyManagementService deleteKey returns false for nonexistent key`() = runBlocking {
        val kms = createMockKMS()
        
        assertFalse(kms.deleteKey("nonexistent"))
    }

    @Test
    fun `test KeyManagementService deleteKey after getPublicKey fails`() = runBlocking {
        val kms = createMockKMS()
        val handle = kms.generateKey("Ed25519")
        
        assertTrue(kms.deleteKey(handle.id))
        
        assertFailsWith<KeyNotFoundException> {
            kms.getPublicKey(handle.id)
        }
    }

    @Test
    fun `test KeyManagementService multiple keys coexist`() = runBlocking {
        val kms = createMockKMS()
        
        val key1 = kms.generateKey("Ed25519")
        val key2 = kms.generateKey("secp256k1")
        val key3 = kms.generateKey("RSA")
        
        assertEquals(key1, kms.getPublicKey(key1.id))
        assertEquals(key2, kms.getPublicKey(key2.id))
        assertEquals(key3, kms.getPublicKey(key3.id))
    }

    @Test
    fun `test KeyManagementService getPublicKey throws KeyNotFoundException`() = runBlocking {
        val kms = createMockKMS()
        
        assertFailsWith<KeyNotFoundException> {
            kms.getPublicKey("nonexistent-key")
        }
    }

    @Test
    fun `test KeyManagementService sign throws KeyNotFoundException`() = runBlocking {
        val kms = createMockKMS()
        
        assertFailsWith<KeyNotFoundException> {
            kms.sign("nonexistent-key", "test".toByteArray())
        }
    }

    private fun createMockKMS(): KeyManagementService {
        return object : KeyManagementService {
            private val keys = mutableMapOf<String, KeyHandle>()
            
            override suspend fun getSupportedAlgorithms(): Set<Algorithm> {
                return setOf(
                    Algorithm.Ed25519, 
                    Algorithm.Secp256k1, 
                    Algorithm.P256,
                    Algorithm.P384,
                    Algorithm.P521,
                    Algorithm.RSA.RSA_2048,
                    Algorithm.RSA.RSA_3072,
                    Algorithm.RSA.RSA_4096,
                    Algorithm.Custom("RSA") // Support "RSA" as a custom algorithm
                )
            }
            
            override suspend fun generateKey(algorithm: Algorithm, options: Map<String, Any?>): KeyHandle {
                // Support string algorithm names for backward compatibility
                val algorithmName = when (algorithm) {
                    is Algorithm.Ed25519 -> "Ed25519"
                    is Algorithm.Secp256k1 -> "secp256k1"
                    is Algorithm.P256 -> "P-256"
                    is Algorithm.P384 -> "P-384"
                    is Algorithm.P521 -> "P-521"
                    is Algorithm.RSA -> "RSA" // Just "RSA" for the test expectation
                    is Algorithm.Custom -> algorithm.name // For "RSA" as Custom, return "RSA"
                    else -> algorithm.name
                }
                
                val handle = KeyHandle(
                    id = options["keyId"] as? String ?: "key-${keys.size + 1}",
                    algorithm = algorithmName
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
    }
}



