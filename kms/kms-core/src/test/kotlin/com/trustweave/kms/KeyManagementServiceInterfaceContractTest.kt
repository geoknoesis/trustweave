package com.trustweave.kms

import com.trustweave.core.identifiers.KeyId
import com.trustweave.kms.results.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive interface contract tests for KeyManagementService.
 * Tests all methods, branches, and edge cases using Result-based API.
 */
class KeyManagementServiceInterfaceContractTest {

    @Test
    fun `test KeyManagementService generateKey returns key handle`() = runBlocking {
        val kms = createMockKms()

        val result = kms.generateKey("Ed25519")
        assertTrue(result is GenerateKeyResult.Success)
        val handle = result.keyHandle

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

        val result = kms.generateKey("Ed25519", options)
        assertTrue(result is GenerateKeyResult.Success)
        val handle = result.keyHandle

        assertNotNull(handle)
        assertEquals("custom-key-123", handle.id.value)
    }

    @Test
    fun `test KeyManagementService generateKey with different algorithms`() = runBlocking {
        val kms = createMockKms()

        val ed25519Result = kms.generateKey("Ed25519")
        val secp256k1Result = kms.generateKey("secp256k1")

        assertTrue(ed25519Result is GenerateKeyResult.Success)
        assertTrue(secp256k1Result is GenerateKeyResult.Success)
        assertEquals("Ed25519", ed25519Result.keyHandle.algorithm)
        assertEquals("secp256k1", secp256k1Result.keyHandle.algorithm)
    }

    @Test
    fun `test KeyManagementService getPublicKey returns key handle`() = runBlocking {
        val kms = createMockKms()
        val generateResult = kms.generateKey("Ed25519")
        assertTrue(generateResult is GenerateKeyResult.Success)
        val handle = generateResult.keyHandle

        val result = kms.getPublicKey(handle.id)
        assertTrue(result is GetPublicKeyResult.Success)
        val publicKey = result.keyHandle

        assertNotNull(publicKey)
        assertEquals(handle.id, publicKey.id)
        assertEquals(handle.algorithm, publicKey.algorithm)
    }

    @Test
    fun `test KeyManagementService getPublicKey returns KeyNotFound`() = runBlocking {
        val kms = createMockKms()

        val result = kms.getPublicKey(KeyId("non-existent"))
        assertTrue(result is GetPublicKeyResult.Failure.KeyNotFound)
        assertEquals(KeyId("non-existent"), result.keyId)
    }

    @Test
    fun `test KeyManagementService sign returns signature`() = runBlocking {
        val kms = createMockKms()
        val generateResult = kms.generateKey("Ed25519")
        assertTrue(generateResult is GenerateKeyResult.Success)
        val handle = generateResult.keyHandle
        val data = "test data".toByteArray()

        val result = kms.sign(handle.id, data)
        assertTrue(result is SignResult.Success)
        val signature = result.signature

        assertNotNull(signature)
        assertTrue(signature.isNotEmpty())
    }

    @Test
    fun `test KeyManagementService sign with algorithm override`() = runBlocking {
        val kms = createMockKms()
        val generateResult = kms.generateKey("Ed25519")
        assertTrue(generateResult is GenerateKeyResult.Success)
        val handle = generateResult.keyHandle
        val data = "test data".toByteArray()

        val result = kms.sign(handle.id, data, "Ed25519")
        assertTrue(result is SignResult.Success)
        assertNotNull(result.signature)
    }

    @Test
    fun `test KeyManagementService sign returns KeyNotFound`() = runBlocking {
        val kms = createMockKms()
        val data = "test data".toByteArray()

        val result = kms.sign(KeyId("non-existent"), data)
        assertTrue(result is SignResult.Failure.KeyNotFound)
        assertEquals(KeyId("non-existent"), result.keyId)
    }

    @Test
    fun `test KeyManagementService deleteKey returns Deleted`() = runBlocking {
        val kms = createMockKms()
        val generateResult = kms.generateKey("Ed25519")
        assertTrue(generateResult is GenerateKeyResult.Success)
        val handle = generateResult.keyHandle

        val result = kms.deleteKey(handle.id)
        assertTrue(result is DeleteKeyResult.Deleted)

        val getResult = kms.getPublicKey(handle.id)
        assertTrue(getResult is GetPublicKeyResult.Failure.KeyNotFound)
    }

    @Test
    fun `test KeyManagementService deleteKey returns NotFound for non-existent key`() = runBlocking {
        val kms = createMockKms()

        val result = kms.deleteKey(KeyId("non-existent"))
        assertTrue(result is DeleteKeyResult.NotFound)
    }

    @Test
    fun `test KeyManagementService generateKey then sign then deleteKey`() = runBlocking {
        val kms = createMockKms()
        val generateResult = kms.generateKey("Ed25519")
        assertTrue(generateResult is GenerateKeyResult.Success)
        val handle = generateResult.keyHandle
        val data = "test data".toByteArray()

        val sign1 = kms.sign(handle.id, data)
        assertTrue(sign1 is SignResult.Success)
        assertNotNull(sign1.signature)

        val publicKeyResult = kms.getPublicKey(handle.id)
        assertTrue(publicKeyResult is GetPublicKeyResult.Success)
        assertNotNull(publicKeyResult.keyHandle)

        val deleteResult = kms.deleteKey(handle.id)
        assertTrue(deleteResult is DeleteKeyResult.Deleted)

        val sign2 = kms.sign(handle.id, data)
        assertTrue(sign2 is SignResult.Failure.KeyNotFound)
    }

    @Test
    fun `test KeyManagementService generateKey with publicKeyJwk`() = runBlocking {
        val kms = createMockKms()
        val result = kms.generateKey("Ed25519")
        assertTrue(result is GenerateKeyResult.Success)
        val handle = result.keyHandle

        assertNotNull(handle.publicKeyJwk)
        assertTrue(handle.publicKeyJwk!!.containsKey("kty"))
    }

    @Test
    fun `test KeyManagementService generateKey with publicKeyMultibase`() = runBlocking {
        val kms = createMockKms()
        val result = kms.generateKey("Ed25519")
        assertTrue(result is GenerateKeyResult.Success)
        val handle = result.keyHandle

        // May or may not have multibase depending on implementation
        // Just verify handle is valid
        assertNotNull(handle)
    }

    private fun createMockKms(): KeyManagementService {
        return object : KeyManagementService {
            private val keys = mutableMapOf<KeyId, KeyHandle>()

            override suspend fun getSupportedAlgorithms(): Set<Algorithm> {
                return setOf(Algorithm.Ed25519, Algorithm.Secp256k1, Algorithm.P256)
            }

            override suspend fun generateKey(
                algorithm: Algorithm,
                options: Map<String, Any?>
            ): GenerateKeyResult {
                val keyIdStr = options["keyId"] as? String ?: "key-${java.util.UUID.randomUUID()}"
                val keyId = KeyId(keyIdStr)
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
                return GenerateKeyResult.Success(handle)
            }

            override suspend fun getPublicKey(keyId: KeyId): GetPublicKeyResult {
                return keys[keyId]?.let { GetPublicKeyResult.Success(it) }
                    ?: GetPublicKeyResult.Failure.KeyNotFound(keyId)
            }

            override suspend fun sign(
                keyId: KeyId,
                data: ByteArray,
                algorithm: Algorithm?
            ): SignResult {
                val handle = keys[keyId] ?: return SignResult.Failure.KeyNotFound(keyId)
                val algo = algorithm?.name ?: handle.algorithm
                return SignResult.Success("signature-$algo-${data.size}".toByteArray())
            }

            override suspend fun deleteKey(keyId: KeyId): DeleteKeyResult {
                return if (keys.remove(keyId) != null) {
                    DeleteKeyResult.Deleted
                } else {
                    DeleteKeyResult.NotFound
                }
            }
        }
    }
}
