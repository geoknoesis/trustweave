package io.geoknoesis.vericore.testkit.kms

import io.geoknoesis.vericore.kms.KeyNotFoundException
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class InMemoryKeyManagementServiceTest {

    private lateinit var kms: InMemoryKeyManagementService

    @BeforeTest
    fun setup() {
        kms = InMemoryKeyManagementService()
    }

    @AfterTest
    fun cleanup() {
        kms.clear()
    }

    @Test
    fun `test generate Ed25519 key`() = runBlocking {
        val handle = kms.generateKey("Ed25519", emptyMap())

        assertNotNull(handle)
        assertEquals("Ed25519", handle.algorithm)
        assertNotNull(handle.id)
        val jwk = handle.publicKeyJwk
        assertNotNull(jwk)
        assertEquals("OKP", jwk["kty"])
        assertEquals("Ed25519", jwk["crv"])
        assertNotNull(jwk["x"])
    }

    @Test
    fun `test generate Secp256k1 key`() = runBlocking {
        try {
            val handle = kms.generateKey("SECP256K1", emptyMap())

            assertNotNull(handle)
            assertEquals("SECP256K1", handle.algorithm)
            val jwk = handle.publicKeyJwk
            assertNotNull(jwk)
            assertEquals("EC", jwk["kty"])
            assertEquals("secp256k1", jwk["crv"])
            assertNotNull(jwk["x"])
            assertNotNull(jwk["y"])
        } catch (e: UnsupportedOperationException) {
            // Skip test if secp256k1 is not available in this JVM
            println("Skipping test: secp256k1 not available: ${e.message}")
        }
    }

    @Test
    fun `test generate key with custom key ID`() = runBlocking {
        val handle = kms.generateKey("Ed25519", mapOf("keyId" to "custom-key-123"))

        assertEquals("custom-key-123", handle.id)
    }

    @Test
    fun `test generate key with unsupported algorithm throws exception`() = runBlocking {
        assertFailsWith<IllegalArgumentException> {
            kms.generateKey("RSA", emptyMap())
        }
    }

    @Test
    fun `test get public key`() = runBlocking {
        val handle = kms.generateKey("Ed25519", mapOf("keyId" to "key-1"))

        val retrieved = kms.getPublicKey("key-1")

        assertEquals(handle.id, retrieved.id)
        assertEquals(handle.algorithm, retrieved.algorithm)
        assertEquals(handle.publicKeyJwk, retrieved.publicKeyJwk)
    }

    @Test
    fun `test get non-existent key throws exception`() = runBlocking {
        assertFailsWith<KeyNotFoundException> {
            kms.getPublicKey("non-existent-key")
        }
    }

    @Test
    fun `test sign data`() = runBlocking {
        val handle = kms.generateKey("Ed25519", mapOf("keyId" to "signing-key"))
        val data = "test data".toByteArray()

        val signature = kms.sign("signing-key", data)

        assertNotNull(signature)
        assertTrue(signature.isNotEmpty())
    }

    @Test
    fun `test sign with explicit algorithm`() = runBlocking {
        val handle = kms.generateKey("Ed25519", mapOf("keyId" to "signing-key"))
        val data = "test data".toByteArray()

        val signature = kms.sign("signing-key", data, "Ed25519")

        assertNotNull(signature)
        assertTrue(signature.isNotEmpty())
    }

    @Test
    fun `test sign with Secp256k1 algorithm`() = runBlocking {
        try {
            val handle = kms.generateKey("SECP256K1", mapOf("keyId" to "ecdsa-key"))
            val data = "test data".toByteArray()

            val signature = kms.sign("ecdsa-key", data, "SHA256withECDSA")

            assertNotNull(signature)
            assertTrue(signature.isNotEmpty())
        } catch (e: UnsupportedOperationException) {
            // Skip test if secp256k1 is not available in this JVM
            println("Skipping test: secp256k1 not available: ${e.message}")
        }
    }

    @Test
    fun `test sign with non-existent key throws exception`() = runBlocking {
        val data = "test data".toByteArray()

        assertFailsWith<KeyNotFoundException> {
            kms.sign("non-existent-key", data)
        }
    }

    @Test
    fun `test delete key`() = runBlocking {
        val handle = kms.generateKey("Ed25519", mapOf("keyId" to "key-to-delete"))

        val deleted = kms.deleteKey("key-to-delete")

        assertTrue(deleted)
        assertFailsWith<KeyNotFoundException> {
            kms.getPublicKey("key-to-delete")
        }
    }

    @Test
    fun `test delete non-existent key returns false`() = runBlocking {
        val deleted = kms.deleteKey("non-existent-key")

        assertFalse(deleted)
    }

    @Test
    fun `test clear all keys`() = runBlocking {
        kms.generateKey("Ed25519", mapOf("keyId" to "key-1"))
        kms.generateKey("Ed25519", mapOf("keyId" to "key-2"))

        kms.clear()

        assertFailsWith<KeyNotFoundException> {
            kms.getPublicKey("key-1")
        }
        assertFailsWith<KeyNotFoundException> {
            kms.getPublicKey("key-2")
        }
    }

    @Test
    fun `test multiple keys can coexist`() = runBlocking {
        try {
            val key1 = kms.generateKey("Ed25519", mapOf("keyId" to "key-1"))
            val key2 = kms.generateKey("SECP256K1", mapOf("keyId" to "key-2"))

            val retrieved1 = kms.getPublicKey("key-1")
            val retrieved2 = kms.getPublicKey("key-2")

            assertEquals("Ed25519", retrieved1.algorithm)
            assertEquals("SECP256K1", retrieved2.algorithm)
            assertNotEquals(key1.id, key2.id)
        } catch (e: UnsupportedOperationException) {
            // Skip test if algorithms are not available in this JVM
            println("Skipping test: algorithms not available: ${e.message}")
        }
    }

    @Test
    fun `test sign same data produces consistent signature`() = runBlocking {
        val handle = kms.generateKey("Ed25519", mapOf("keyId" to "consistent-key"))
        val data = "same data".toByteArray()

        val sig1 = kms.sign("consistent-key", data)
        val sig2 = kms.sign("consistent-key", data)

        // Signatures should be consistent (same key, same data)
        assertTrue(sig1.contentEquals(sig2))
    }

    @Test
    fun `test sign different data produces different signatures`() = runBlocking {
        val handle = kms.generateKey("Ed25519", mapOf("keyId" to "different-key"))
        val data1 = "data 1".toByteArray()
        val data2 = "data 2".toByteArray()

        val sig1 = kms.sign("different-key", data1)
        val sig2 = kms.sign("different-key", data2)

        // Signatures should be different for different data
        assertFalse(sig1.contentEquals(sig2))
    }
}
