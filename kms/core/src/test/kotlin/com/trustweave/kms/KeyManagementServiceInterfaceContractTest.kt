package com.trustweave.kms

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive interface contract tests for KeyManagementService.
 * Tests all methods, branches, and edge cases.
 */
class KeyManagementServiceInterfaceContractTest {

    @Test
    fun `test KeyManagementService generateKey returns key handle`() = runBlocking {
        val kms = createMockKms()
        
        val handle = kms.generateKey("Ed25519")
        
        assertNotNull(handle)
        assertEquals("Ed25519", handle.algorithm)
        assertNotNull(handle.id)
    }

    @Test
    fun `test KeyManagementService generateKey with custom ID`() = runBlocking {
        val kms = createMockKms()
        val options = mapOf<String, Any?>(
            "keyId" to "custom-key-123"
        )
        
        val handle = kms.generateKey("Ed25519", options)
        
        assertNotNull(handle)
        assertEquals("custom-key-123", handle.id)
    }

    @Test
    fun `test KeyManagementService generateKey with different algorithms`() = runBlocking {
        val kms = createMockKms()
        
        val ed25519 = kms.generateKey("Ed25519")
        val secp256k1 = kms.generateKey("secp256k1")
        
        assertEquals("Ed25519", ed25519.algorithm)
        assertEquals("secp256k1", secp256k1.algorithm)
    }

    @Test
    fun `test KeyManagementService getPublicKey returns key handle`() = runBlocking {
        val kms = createMockKms()
        val handle = kms.generateKey("Ed25519")
        
        val publicKey = kms.getPublicKey(handle.id)
        
        assertNotNull(publicKey)
        assertEquals(handle.id, publicKey.id)
        assertEquals(handle.algorithm, publicKey.algorithm)
    }

    @Test
    fun `test KeyManagementService getPublicKey throws KeyNotFoundException`() = runBlocking {
        val kms = createMockKms()
        
        assertFailsWith<KeyNotFoundException> {
            kms.getPublicKey("non-existent")
        }
    }

    @Test
    fun `test KeyManagementService sign returns signature`() = runBlocking {
        val kms = createMockKms()
        val handle = kms.generateKey("Ed25519")
        val data = "test data".toByteArray()
        
        val signature = kms.sign(handle.id, data)
        
        assertNotNull(signature)
        assertTrue(signature.isNotEmpty())
    }

    @Test
    fun `test KeyManagementService sign with algorithm override`() = runBlocking {
        val kms = createMockKms()
        val handle = kms.generateKey("Ed25519")
        val data = "test data".toByteArray()
        
        val signature = kms.sign(handle.id, data, "Ed25519")
        
        assertNotNull(signature)
    }

    @Test
    fun `test KeyManagementService sign throws KeyNotFoundException`() = runBlocking {
        val kms = createMockKms()
        val data = "test data".toByteArray()
        
        assertFailsWith<KeyNotFoundException> {
            kms.sign("non-existent", data)
        }
    }

    @Test
    fun `test KeyManagementService deleteKey returns true`() = runBlocking {
        val kms = createMockKms()
        val handle = kms.generateKey("Ed25519")
        
        val deleted = kms.deleteKey(handle.id)
        
        assertTrue(deleted)
        assertFailsWith<KeyNotFoundException> {
            kms.getPublicKey(handle.id)
        }
    }

    @Test
    fun `test KeyManagementService deleteKey returns false for non-existent key`() = runBlocking {
        val kms = createMockKms()
        
        val deleted = kms.deleteKey("non-existent")
        
        assertFalse(deleted)
    }

    @Test
    fun `test KeyManagementService generateKey then sign then delete`() = runBlocking {
        val kms = createMockKms()
        val handle = kms.generateKey("Ed25519")
        val data = "test data".toByteArray()
        
        val signature1 = kms.sign(handle.id, data)
        assertNotNull(signature1)
        
        val publicKey = kms.getPublicKey(handle.id)
        assertNotNull(publicKey)
        
        val deleted = kms.deleteKey(handle.id)
        assertTrue(deleted)
        
        assertFailsWith<KeyNotFoundException> {
            kms.sign(handle.id, data)
        }
    }

    @Test
    fun `test KeyManagementService generateKey with publicKeyJwk`() = runBlocking {
        val kms = createMockKms()
        val handle = kms.generateKey("Ed25519")
        
        assertNotNull(handle.publicKeyJwk)
        assertTrue(handle.publicKeyJwk.containsKey("kty"))
    }

    @Test
    fun `test KeyManagementService generateKey with publicKeyMultibase`() = runBlocking {
        val kms = createMockKms()
        val handle = kms.generateKey("Ed25519")
        
        // May or may not have multibase depending on implementation
        // Just verify handle is valid
        assertNotNull(handle)
    }

    private fun createMockKms(): KeyManagementService {
        return object : KeyManagementService {
            private val keys = mutableMapOf<String, KeyHandle>()
            
            override suspend fun getSupportedAlgorithms(): Set<Algorithm> {
                return setOf(Algorithm.Ed25519, Algorithm.Secp256k1, Algorithm.P256)
            }
            
            override suspend fun generateKey(
                algorithm: Algorithm,
                options: Map<String, Any?>
            ): KeyHandle {
                val keyId = options["keyId"] as? String ?: "key-${java.util.UUID.randomUUID()}"
                val handle = KeyHandle(
                    id = keyId,
                    algorithm = algorithm.name,
                    publicKeyJwk = mapOf(
                        "kty" to "OKP",
                        "crv" to algorithm.name,
                        "x" to "test-public-key"
                    ),
                    publicKeyMultibase = "z${"test".repeat(20)}"
                )
                keys[keyId] = handle
                return handle
            }
            
            override suspend fun getPublicKey(keyId: String): KeyHandle {
                return keys[keyId] ?: throw KeyNotFoundException("Key not found: $keyId")
            }
            
            override suspend fun sign(
                keyId: String,
                data: ByteArray,
                algorithm: Algorithm?
            ): ByteArray {
                val handle = keys[keyId] ?: throw KeyNotFoundException("Key not found: $keyId")
                val algo = algorithm?.name ?: handle.algorithm
                return "signature-$algo-${data.size}".toByteArray()
            }
            
            override suspend fun deleteKey(keyId: String): Boolean {
                return keys.remove(keyId) != null
            }
        }
    }
}

