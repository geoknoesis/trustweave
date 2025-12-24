package org.trustweave.testkit.kms

import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.UnsupportedAlgorithmException
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
        val result = kms.generateKey("Ed25519", emptyMap())
        val handle = when (result) {
            is org.trustweave.kms.results.GenerateKeyResult.Success -> result.keyHandle
            else -> throw IllegalStateException("Failed to generate key: $result")
        }

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
            val result = kms.generateKey("SECP256K1", emptyMap())
            val handle = when (result) {
                is org.trustweave.kms.results.GenerateKeyResult.Success -> result.keyHandle
                else -> throw IllegalStateException("Failed to generate key: $result")
            }

            assertNotNull(handle)
            assertEquals("secp256k1", handle.algorithm)
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
        val result = kms.generateKey("Ed25519", mapOf("keyId" to "custom-key-123"))
        val handle = when (result) {
            is org.trustweave.kms.results.GenerateKeyResult.Success -> result.keyHandle
            else -> throw IllegalStateException("Failed to generate key: $result")
        }

        assertEquals("custom-key-123", handle.id.value)
    }

    @Test
    fun `test generate key with unsupported algorithm throws exception`() = runBlocking {
        val result = kms.generateKey("RSA", emptyMap())
        assertTrue(result is org.trustweave.kms.results.GenerateKeyResult.Failure.UnsupportedAlgorithm)
    }

    @Test
    fun `test get supported algorithms`() = runBlocking {
        val supported = kms.getSupportedAlgorithms()

        assertTrue(supported.contains(Algorithm.Ed25519))
        assertTrue(supported.contains(Algorithm.Secp256k1))
        assertEquals(2, supported.size)
    }

    @Test
    fun `test supports algorithm`() = runBlocking {
        assertTrue(kms.supportsAlgorithm(Algorithm.Ed25519))
        assertTrue(kms.supportsAlgorithm(Algorithm.Secp256k1))
        assertFalse(kms.supportsAlgorithm(Algorithm.P256))
        assertFalse(kms.supportsAlgorithm(Algorithm.RSA.RSA_2048))
    }

    @Test
    fun `test supports algorithm by name`() = runBlocking {
        assertTrue(kms.supportsAlgorithm("Ed25519"))
        assertTrue(kms.supportsAlgorithm("ed25519")) // case insensitive
        assertTrue(kms.supportsAlgorithm("secp256k1"))
        assertFalse(kms.supportsAlgorithm("P-256"))
        assertFalse(kms.supportsAlgorithm("invalid"))
    }

    @Test
    fun `test generate key with Algorithm type`() = runBlocking {
        val result = kms.generateKey(Algorithm.Ed25519, emptyMap())
        val handle = when (result) {
            is org.trustweave.kms.results.GenerateKeyResult.Success -> result.keyHandle
            else -> throw IllegalStateException("Failed to generate key: $result")
        }

        assertNotNull(handle)
        assertEquals("Ed25519", handle.algorithm)
    }

    @Test
    fun `test generate key with unsupported Algorithm type throws exception`() = runBlocking {
        val result = kms.generateKey(Algorithm.P256, emptyMap())
        assertTrue(result is org.trustweave.kms.results.GenerateKeyResult.Failure.UnsupportedAlgorithm)
    }

    @Test
    fun `test get public key`() = runBlocking {
        val generateResult = kms.generateKey("Ed25519", mapOf("keyId" to "key-1"))
        val handle = when (generateResult) {
            is org.trustweave.kms.results.GenerateKeyResult.Success -> generateResult.keyHandle
            else -> throw IllegalStateException("Failed to generate key: $generateResult")
        }

        val getResult = kms.getPublicKey(KeyId("key-1"))
        val retrieved = when (getResult) {
            is org.trustweave.kms.results.GetPublicKeyResult.Success -> getResult.keyHandle
            else -> throw IllegalStateException("Failed to get public key: $getResult")
        }

        assertEquals(handle.id, retrieved.id)
        assertEquals(handle.algorithm, retrieved.algorithm)
        assertEquals(handle.publicKeyJwk, retrieved.publicKeyJwk)
    }

    @Test
    fun `test get non-existent key throws exception`() = runBlocking {
        val getResult = kms.getPublicKey(KeyId("non-existent-key"))
        assertTrue(getResult is org.trustweave.kms.results.GetPublicKeyResult.Failure.KeyNotFound)
    }

    @Test
    fun `test sign data`() = runBlocking {
        val generateResult = kms.generateKey("Ed25519", mapOf("keyId" to "signing-key"))
        when (generateResult) {
            is org.trustweave.kms.results.GenerateKeyResult.Success -> { /* OK */ }
            else -> throw IllegalStateException("Failed to generate key: $generateResult")
        }
        val data = "test data".toByteArray()

        val signResult = kms.sign(KeyId("signing-key"), data)
        val signature = when (signResult) {
            is org.trustweave.kms.results.SignResult.Success -> signResult.signature
            else -> throw IllegalStateException("Failed to sign: $signResult")
        }

        assertNotNull(signature)
        assertTrue(signature.isNotEmpty())
    }

    @Test
    fun `test sign with explicit algorithm`() = runBlocking {
        val generateResult = kms.generateKey("Ed25519", mapOf("keyId" to "signing-key"))
        when (generateResult) {
            is org.trustweave.kms.results.GenerateKeyResult.Success -> { /* OK */ }
            else -> throw IllegalStateException("Failed to generate key: $generateResult")
        }
        val data = "test data".toByteArray()

        val signResult = kms.sign(KeyId("signing-key"), data, Algorithm.Ed25519)
        val signature = when (signResult) {
            is org.trustweave.kms.results.SignResult.Success -> signResult.signature
            else -> throw IllegalStateException("Failed to sign: $signResult")
        }

        assertNotNull(signature)
        assertTrue(signature.isNotEmpty())
    }

    @Test
    fun `test sign with Secp256k1 algorithm`() = runBlocking {
        try {
            val generateResult = kms.generateKey("SECP256K1", mapOf("keyId" to "ecdsa-key"))
            when (generateResult) {
                is org.trustweave.kms.results.GenerateKeyResult.Success -> { /* OK */ }
                else -> throw IllegalStateException("Failed to generate key: $generateResult")
            }
            val data = "test data".toByteArray()

            // Sign with the key's algorithm (secp256k1) - pass Algorithm? explicitly to avoid ambiguity
            val signResult = kms.sign(KeyId("ecdsa-key"), data, null as Algorithm?)
            val signature = when (signResult) {
                is org.trustweave.kms.results.SignResult.Success -> signResult.signature
                else -> throw IllegalStateException("Failed to sign: $signResult")
            }

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

        val signResult = kms.sign(KeyId("non-existent-key"), data)
        assertTrue(signResult is org.trustweave.kms.results.SignResult.Failure.KeyNotFound)
    }

    @Test
    fun `test delete key`() = runBlocking {
        val generateResult = kms.generateKey("Ed25519", mapOf("keyId" to "key-to-delete"))
        when (generateResult) {
            is org.trustweave.kms.results.GenerateKeyResult.Success -> { /* OK */ }
            else -> throw IllegalStateException("Failed to generate key: $generateResult")
        }

        val deleteResult = kms.deleteKey(KeyId("key-to-delete"))
        assertTrue(
            deleteResult is org.trustweave.kms.results.DeleteKeyResult.Deleted,
            "Expected Deleted, got: $deleteResult"
        )
        val getResult = kms.getPublicKey(KeyId("key-to-delete"))
        assertTrue(getResult is org.trustweave.kms.results.GetPublicKeyResult.Failure.KeyNotFound)
    }

    @Test
    fun `test delete non-existent key returns false`() = runBlocking {
        val deleteResult = kms.deleteKey(KeyId("non-existent-key"))
        val deleted = when (deleteResult) {
            is org.trustweave.kms.results.DeleteKeyResult.Deleted -> true
            is org.trustweave.kms.results.DeleteKeyResult.NotFound -> false
            is org.trustweave.kms.results.DeleteKeyResult.Failure -> throw IllegalStateException("Failed to delete key: $deleteResult")
        }

        assertFalse(deleted)
    }

    @Test
    fun `test clear all keys`() = runBlocking {
        val result1 = kms.generateKey("Ed25519", mapOf("keyId" to "key-1"))
        when (result1) {
            is org.trustweave.kms.results.GenerateKeyResult.Success -> { /* OK */ }
            else -> throw IllegalStateException("Failed to generate key: $result1")
        }
        val result2 = kms.generateKey("Ed25519", mapOf("keyId" to "key-2"))
        when (result2) {
            is org.trustweave.kms.results.GenerateKeyResult.Success -> { /* OK */ }
            else -> throw IllegalStateException("Failed to generate key: $result2")
        }

        kms.clear()

        val getResult1 = kms.getPublicKey(KeyId("key-1"))
        assertTrue(getResult1 is org.trustweave.kms.results.GetPublicKeyResult.Failure.KeyNotFound)
        val getResult2 = kms.getPublicKey(KeyId("key-2"))
        assertTrue(getResult2 is org.trustweave.kms.results.GetPublicKeyResult.Failure.KeyNotFound)
    }

    @Test
    fun `test multiple keys can coexist`() = runBlocking {
        try {
            val result1 = kms.generateKey("Ed25519", mapOf("keyId" to "key-1"))
            val key1 = when (result1) {
                is org.trustweave.kms.results.GenerateKeyResult.Success -> result1.keyHandle
                else -> throw IllegalStateException("Failed to generate key: $result1")
            }
            val result2 = kms.generateKey("SECP256K1", mapOf("keyId" to "key-2"))
            val key2 = when (result2) {
                is org.trustweave.kms.results.GenerateKeyResult.Success -> result2.keyHandle
                else -> throw IllegalStateException("Failed to generate key: $result2")
            }

            val getResult1 = kms.getPublicKey(KeyId("key-1"))
            val retrieved1 = when (getResult1) {
                is org.trustweave.kms.results.GetPublicKeyResult.Success -> getResult1.keyHandle
                else -> throw IllegalStateException("Failed to get public key: $getResult1")
            }
            val getResult2 = kms.getPublicKey(KeyId("key-2"))
            val retrieved2 = when (getResult2) {
                is org.trustweave.kms.results.GetPublicKeyResult.Success -> getResult2.keyHandle
                else -> throw IllegalStateException("Failed to get public key: $getResult2")
            }

            assertEquals("Ed25519", retrieved1.algorithm)
            assertEquals("secp256k1", retrieved2.algorithm)
            assertNotEquals(key1.id, key2.id)
        } catch (e: UnsupportedOperationException) {
            // Skip test if algorithms are not available in this JVM
            println("Skipping test: algorithms not available: ${e.message}")
        }
    }

    @Test
    fun `test sign same data produces consistent signature`() = runBlocking {
        val generateResult = kms.generateKey("Ed25519", mapOf("keyId" to "consistent-key"))
        when (generateResult) {
            is org.trustweave.kms.results.GenerateKeyResult.Success -> { /* OK */ }
            else -> throw IllegalStateException("Failed to generate key: $generateResult")
        }
        val data = "same data".toByteArray()

        val signResult1 = kms.sign(KeyId("consistent-key"), data)
        val sig1 = when (signResult1) {
            is org.trustweave.kms.results.SignResult.Success -> signResult1.signature
            else -> throw IllegalStateException("Failed to sign: $signResult1")
        }
        val signResult2 = kms.sign(KeyId("consistent-key"), data)
        val sig2 = when (signResult2) {
            is org.trustweave.kms.results.SignResult.Success -> signResult2.signature
            else -> throw IllegalStateException("Failed to sign: $signResult2")
        }

        // Signatures should be consistent (same key, same data)
        assertTrue(sig1.contentEquals(sig2))
    }

    @Test
    fun `test sign different data produces different signatures`() = runBlocking {
        val generateResult = kms.generateKey("Ed25519", mapOf("keyId" to "different-key"))
        when (generateResult) {
            is org.trustweave.kms.results.GenerateKeyResult.Success -> { /* OK */ }
            else -> throw IllegalStateException("Failed to generate key: $generateResult")
        }
        val data1 = "data 1".toByteArray()
        val data2 = "data 2".toByteArray()

        val signResult1 = kms.sign(KeyId("different-key"), data1)
        val sig1 = when (signResult1) {
            is org.trustweave.kms.results.SignResult.Success -> signResult1.signature
            else -> throw IllegalStateException("Failed to sign: $signResult1")
        }
        val signResult2 = kms.sign(KeyId("different-key"), data2)
        val sig2 = when (signResult2) {
            is org.trustweave.kms.results.SignResult.Success -> signResult2.signature
            else -> throw IllegalStateException("Failed to sign: $signResult2")
        }

        // Signatures should be different for different data
        assertFalse(sig1.contentEquals(sig2))
    }
}
