package org.trustweave.kms

import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.results.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/**
 * Comprehensive edge case tests for KeyManagementService and KeyHandle.
 */
class KeyManagementServiceEdgeCasesTest {

    @Test
    fun `test KeyHandle with null publicKeyJwk and publicKeyMultibase`() {
        val handle = KeyHandle(
            id = KeyId("key-1"),
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
            id = KeyId("key-1"),
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
            id = KeyId("key-1"),
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
            id = KeyId("key-1"),
            algorithm = "Ed25519",
            publicKeyJwk = emptyMap()
        )

        assertNotNull(handle.publicKeyJwk)
        assertTrue(handle.publicKeyJwk?.isEmpty() == true)
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
            id = KeyId("key-1"),
            algorithm = "Ed25519",
            publicKeyJwk = jwk
        )

        assertEquals(6, handle.publicKeyJwk?.size)
    }

    @Test
    fun `test KeyHandle equality`() {
        val handle1 = KeyHandle(
            id = KeyId("key-1"),
            algorithm = "Ed25519",
            publicKeyJwk = mapOf("kty" to "OKP")
        )
        val handle2 = KeyHandle(
            id = KeyId("key-1"),
            algorithm = "Ed25519",
            publicKeyJwk = mapOf("kty" to "OKP")
        )

        assertEquals(handle1, handle2)
    }

    @Test
    fun `test KeyHandle toString`() {
        val handle = KeyHandle(
            id = KeyId("key-1"),
            algorithm = "Ed25519"
        )

        val str = handle.toString()
        assertTrue(str.contains("key-1"))
        assertTrue(str.contains("Ed25519"))
    }

    @Test
    fun `test KeyManagementService generateKey with different algorithms`() = runBlocking {
        val kms = createMockKMS()

        val ed25519Result = kms.generateKey("Ed25519")
        val secp256k1Result = kms.generateKey("secp256k1")
        val rsaResult = kms.generateKey("RSA-2048")

        assertTrue(ed25519Result is GenerateKeyResult.Success)
        assertTrue(secp256k1Result is GenerateKeyResult.Success)
        assertTrue(rsaResult is GenerateKeyResult.Success)
        assertEquals("Ed25519", ed25519Result.keyHandle.algorithm)
        assertEquals("secp256k1", secp256k1Result.keyHandle.algorithm)
        assertEquals("RSA-2048", rsaResult.keyHandle.algorithm)
    }

    @Test
    fun `test KeyManagementService generateKey with options`() = runBlocking {
        val kms = createMockKMS()

        val result = kms.generateKey(
            "Ed25519",
            mapOf("keyId" to "custom-key-id", "metadata" to mapOf("purpose" to "signing"))
        )

        assertTrue(result is GenerateKeyResult.Success)
        val handle = result.keyHandle
        assertNotNull(handle)
        assertEquals("Ed25519", handle.algorithm)
    }

    @Test
    fun `test KeyManagementService sign with different algorithms`() = runBlocking {
        val kms = createMockKMS()
        val generateResult = kms.generateKey("Ed25519")
        assertTrue(generateResult is GenerateKeyResult.Success)
        val handle = generateResult.keyHandle

        val signature1 = kms.sign(handle.id, "test".toByteArray(), "Ed25519")
        val signature2 = kms.sign(handle.id, "test".toByteArray(), "EdDSA")

        assertTrue(signature1 is SignResult.Success)
        assertTrue(signature2 is SignResult.Success)
        assertNotNull(signature1.signature)
        assertNotNull(signature2.signature)
    }

    @Test
    fun `test KeyManagementService sign with null algorithm`() = runBlocking {
        val kms = createMockKMS()
        val generateResult = kms.generateKey("Ed25519")
        assertTrue(generateResult is GenerateKeyResult.Success)
        val handle = generateResult.keyHandle

        val result = kms.sign(handle.id, "test".toByteArray(), null as Algorithm?)

        assertTrue(result is SignResult.Success)
        assertNotNull(result.signature)
    }

    @Test
    fun `test KeyManagementService sign with empty data`() = runBlocking {
        val kms = createMockKMS()
        val generateResult = kms.generateKey("Ed25519")
        assertTrue(generateResult is GenerateKeyResult.Success)
        val handle = generateResult.keyHandle

        val result = kms.sign(handle.id, ByteArray(0))

        assertTrue(result is SignResult.Success)
        assertNotNull(result.signature)
    }

    @Test
    fun `test KeyManagementService sign with large data`() = runBlocking {
        val kms = createMockKMS()
        val generateResult = kms.generateKey("Ed25519")
        assertTrue(generateResult is GenerateKeyResult.Success)
        val handle = generateResult.keyHandle
        val largeData = ByteArray(10000) { it.toByte() }

        val result = kms.sign(handle.id, largeData)

        assertTrue(result is SignResult.Success)
        assertNotNull(result.signature)
    }

    @Test
    fun `test KeyManagementService deleteKey returns NotFound for nonexistent key`() = runBlocking {
        val kms = createMockKMS()

        val result = kms.deleteKey(KeyId("nonexistent"))
        assertTrue(result is DeleteKeyResult.NotFound)
    }

    @Test
    fun `test KeyManagementService deleteKey after getPublicKey fails`() = runBlocking {
        val kms = createMockKMS()
        val generateResult = kms.generateKey("Ed25519")
        assertTrue(generateResult is GenerateKeyResult.Success)
        val handle = generateResult.keyHandle

        val deleteResult = kms.deleteKey(handle.id)
        assertTrue(deleteResult is DeleteKeyResult.Deleted)

        val getResult = kms.getPublicKey(handle.id)
        assertTrue(getResult is GetPublicKeyResult.Failure.KeyNotFound)
    }

    @Test
    fun `test KeyManagementService multiple keys coexist`() = runBlocking {
        val kms = createMockKMS()

        val key1Result = kms.generateKey("Ed25519")
        val key2Result = kms.generateKey("secp256k1")
        val key3Result = kms.generateKey("RSA-2048")

        assertTrue(key1Result is GenerateKeyResult.Success)
        assertTrue(key2Result is GenerateKeyResult.Success)
        assertTrue(key3Result is GenerateKeyResult.Success)

        val key1 = key1Result.keyHandle
        val key2 = key2Result.keyHandle
        val key3 = key3Result.keyHandle

        val get1 = kms.getPublicKey(key1.id)
        val get2 = kms.getPublicKey(key2.id)
        val get3 = kms.getPublicKey(key3.id)

        assertTrue(get1 is GetPublicKeyResult.Success)
        assertTrue(get2 is GetPublicKeyResult.Success)
        assertTrue(get3 is GetPublicKeyResult.Success)
        assertEquals(key1, get1.keyHandle)
        assertEquals(key2, get2.keyHandle)
        assertEquals(key3, get3.keyHandle)
    }

    @Test
    fun `test KeyManagementService getPublicKey returns KeyNotFound`() = runBlocking {
        val kms = createMockKMS()

        val result = kms.getPublicKey(KeyId("nonexistent-key"))
        assertTrue(result is GetPublicKeyResult.Failure.KeyNotFound)
        assertEquals(KeyId("nonexistent-key"), result.keyId)
    }

    @Test
    fun `test KeyManagementService sign returns KeyNotFound`() = runBlocking {
        val kms = createMockKMS()

        val result = kms.sign(KeyId("nonexistent-key"), "test".toByteArray())
        assertTrue(result is SignResult.Failure.KeyNotFound)
        assertEquals(KeyId("nonexistent-key"), result.keyId)
    }

    private fun createMockKMS(): KeyManagementService {
        return object : KeyManagementService {
            private val keys = mutableMapOf<KeyId, KeyHandle>()

            override suspend fun getSupportedAlgorithms(): Set<Algorithm> {
                return setOf(
                    Algorithm.Ed25519,
                    Algorithm.Secp256k1,
                    Algorithm.P256,
                    Algorithm.P384,
                    Algorithm.P521,
                    Algorithm.RSA.RSA_2048,
                    Algorithm.RSA.RSA_3072,
                    Algorithm.RSA.RSA_4096
                )
            }

            override suspend fun generateKey(algorithm: Algorithm, options: Map<String, Any?>): GenerateKeyResult {
                // Support string algorithm names for backward compatibility
                val algorithmName = when (algorithm) {
                    is Algorithm.Ed25519 -> "Ed25519"
                    is Algorithm.Secp256k1 -> "secp256k1"
                    is Algorithm.P256 -> "P-256"
                    is Algorithm.P384 -> "P-384"
                    is Algorithm.P521 -> "P-521"
                    is Algorithm.RSA -> algorithm.name // "RSA-2048", "RSA-3072", or "RSA-4096"
                    is Algorithm.Custom -> algorithm.name
                    else -> algorithm.name
                }

                val keyIdStr = options["keyId"] as? String ?: "key-${keys.size + 1}"
                val keyId = KeyId(keyIdStr)
                val handle = KeyHandle(
                    id = keyId,
                    algorithm = algorithmName
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
    }
}
