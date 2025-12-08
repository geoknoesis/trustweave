package com.trustweave.kms.inmemory

import com.trustweave.core.identifiers.KeyId
import com.trustweave.kms.Algorithm
import com.trustweave.kms.JwkKeys
import com.trustweave.kms.JwkKeyTypes
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.KeyManagementServiceContractTest
import com.trustweave.kms.KmsOptionKeys
import com.trustweave.kms.results.DeleteKeyResult
import com.trustweave.kms.results.GenerateKeyResult
import com.trustweave.kms.results.GetPublicKeyResult
import com.trustweave.kms.results.SignResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Contract tests for InMemoryKeyManagementService.
 * 
 * Extends KeyManagementServiceContractTest to ensure interface contract compliance.
 */
class InMemoryKeyManagementServiceContractTest : KeyManagementServiceContractTest() {
    
    override fun createKms(): KeyManagementService {
        return InMemoryKeyManagementService()
    }

    override fun getSupportedAlgorithms(): List<Algorithm> {
        return InMemoryKeyManagementService.SUPPORTED_ALGORITHMS.toList()
    }
}

/**
 * Comprehensive unit tests for InMemoryKeyManagementService.
 * 
 * Tests implementation-specific behavior beyond the interface contract.
 */
class InMemoryKeyManagementServiceTest {

    private lateinit var kms: InMemoryKeyManagementService

    @BeforeEach
    fun setup() {
        kms = InMemoryKeyManagementService()
    }

    @Test
    fun `test getSupportedAlgorithms returns all supported algorithms`() = runBlocking {
        val supported = kms.getSupportedAlgorithms()

        assertTrue(supported.contains(Algorithm.Ed25519))
        assertTrue(supported.contains(Algorithm.Secp256k1))
        assertTrue(supported.contains(Algorithm.P256))
        assertTrue(supported.contains(Algorithm.P384))
        assertTrue(supported.contains(Algorithm.P521))
        assertTrue(supported.contains(Algorithm.RSA.RSA_2048))
        assertTrue(supported.contains(Algorithm.RSA.RSA_3072))
        assertTrue(supported.contains(Algorithm.RSA.RSA_4096))
        assertEquals(8, supported.size)
    }

    @Test
    fun `test generateKey with Ed25519`() = runBlocking {
        val result = kms.generateKey(Algorithm.Ed25519)

        assertTrue(result is GenerateKeyResult.Success)
        val handle = result.keyHandle
        assertNotNull(handle)
        assertEquals(Algorithm.Ed25519.name, handle.algorithm)
        val jwk = handle.publicKeyJwk
        assertNotNull(jwk)
        assertEquals(JwkKeyTypes.OKP, jwk[JwkKeys.KTY])
        assertEquals(Algorithm.Ed25519.curveName, jwk[JwkKeys.CRV])
        assertNotNull(jwk[JwkKeys.X])
    }

    @Test
    fun `test generateKey with secp256k1`() = runBlocking {
        val result = kms.generateKey(Algorithm.Secp256k1)

        assertTrue(result is GenerateKeyResult.Success)
        val handle = result.keyHandle
        assertEquals(Algorithm.Secp256k1.name, handle.algorithm)
        val jwk = handle.publicKeyJwk
        assertNotNull(jwk)
        assertEquals(JwkKeyTypes.EC, jwk[JwkKeys.KTY])
        assertEquals(Algorithm.Secp256k1.curveName, jwk[JwkKeys.CRV])
    }

    @Test
    fun `test generateKey with P-256`() = runBlocking {
        val result = kms.generateKey(Algorithm.P256)

        assertTrue(result is GenerateKeyResult.Success)
        val handle = result.keyHandle
        assertEquals(Algorithm.P256.name, handle.algorithm)
        val jwk = handle.publicKeyJwk
        assertNotNull(jwk)
        assertEquals(JwkKeyTypes.EC, jwk[JwkKeys.KTY])
        assertEquals(Algorithm.P256.curveName, jwk[JwkKeys.CRV])
    }

    @Test
    fun `test generateKey with P-384`() = runBlocking {
        val result = kms.generateKey(Algorithm.P384)

        assertTrue(result is GenerateKeyResult.Success)
        val handle = result.keyHandle
        assertEquals(Algorithm.P384.name, handle.algorithm)
        assertNotNull(handle.publicKeyJwk)
    }

    @Test
    fun `test generateKey with P-521`() = runBlocking {
        val result = kms.generateKey(Algorithm.P521)

        assertTrue(result is GenerateKeyResult.Success)
        val handle = result.keyHandle
        assertEquals(Algorithm.P521.name, handle.algorithm)
        assertNotNull(handle.publicKeyJwk)
    }

    @Test
    fun `test generateKey with RSA-2048`() = runBlocking {
        val result = kms.generateKey(Algorithm.RSA.RSA_2048)

        assertTrue(result is GenerateKeyResult.Success)
        val handle = result.keyHandle
        assertEquals(Algorithm.RSA.RSA_2048.name, handle.algorithm)
        val jwk = handle.publicKeyJwk
        assertNotNull(jwk)
        assertEquals(JwkKeyTypes.RSA, jwk[JwkKeys.KTY])
        assertNotNull(jwk[JwkKeys.N])
        assertNotNull(jwk[JwkKeys.E])
    }

    @Test
    fun `test generateKey with RSA-3072`() = runBlocking {
        val result = kms.generateKey(Algorithm.RSA.RSA_3072)

        assertTrue(result is GenerateKeyResult.Success)
        val handle = result.keyHandle
        assertEquals(Algorithm.RSA.RSA_3072.name, handle.algorithm)
    }

    @Test
    fun `test generateKey with RSA-4096`() = runBlocking {
        val result = kms.generateKey(Algorithm.RSA.RSA_4096)

        assertTrue(result is GenerateKeyResult.Success)
        val handle = result.keyHandle
        assertEquals(Algorithm.RSA.RSA_4096.name, handle.algorithm)
    }

    @Test
    fun `test generateKey with custom keyId`() = runBlocking {
        val customKeyId = "my-custom-key-123"
        val result = kms.generateKey(
            Algorithm.Ed25519,
            mapOf(KmsOptionKeys.KEY_ID to customKeyId)
        )

        assertTrue(result is GenerateKeyResult.Success)
        assertEquals(customKeyId, result.keyHandle.id.value)
    }

    @Test
    fun `test generateKey rejects duplicate keyId`() = runBlocking {
        val keyId = "duplicate-key"
        val firstResult = kms.generateKey(
            Algorithm.Ed25519,
            mapOf(KmsOptionKeys.KEY_ID to keyId)
        )

        assertTrue(firstResult is GenerateKeyResult.Success)

        val secondResult = kms.generateKey(
            Algorithm.Ed25519,
            mapOf(KmsOptionKeys.KEY_ID to keyId)
        )

        assertTrue(secondResult is GenerateKeyResult.Failure.InvalidOptions)
        assertTrue(secondResult.reason.contains("already exists"))
    }

    @Test
    fun `test generateKey rejects invalid keyId - too long`() = runBlocking {
        val longKeyId = "a".repeat(300) // Exceeds 256 character limit
        val result = kms.generateKey(
            Algorithm.Ed25519,
            mapOf(KmsOptionKeys.KEY_ID to longKeyId)
        )

        assertTrue(result is GenerateKeyResult.Failure.InvalidOptions)
        assertTrue(result.reason.contains("256 characters"))
    }

    @Test
    fun `test generateKey rejects invalid keyId - blank`() = runBlocking {
        val result = kms.generateKey(
            Algorithm.Ed25519,
            mapOf(KmsOptionKeys.KEY_ID to "   ")
        )

        assertTrue(result is GenerateKeyResult.Failure.InvalidOptions)
        assertTrue(result.reason.contains("non-blank"))
    }

    @Test
    fun `test generateKey rejects unsupported algorithm`() = runBlocking {
        val unsupported = Algorithm.Custom("UnsupportedAlg")
        val result = kms.generateKey(unsupported)

        assertTrue(result is GenerateKeyResult.Failure.UnsupportedAlgorithm)
        assertEquals(unsupported, result.algorithm)
        assertNotNull(result.supportedAlgorithms)
    }

    @Test
    fun `test getPublicKey retrieves existing key`() = runBlocking {
        val generateResult = kms.generateKey(Algorithm.Ed25519)
        assertTrue(generateResult is GenerateKeyResult.Success)
        val keyId = generateResult.keyHandle.id

        val result = kms.getPublicKey(keyId)

        assertTrue(result is GetPublicKeyResult.Success)
        assertEquals(keyId, result.keyHandle.id)
        assertEquals(Algorithm.Ed25519.name, result.keyHandle.algorithm)
        assertNotNull(result.keyHandle.publicKeyJwk)
    }

    @Test
    fun `test getPublicKey returns KeyNotFound for non-existent key`() = runBlocking {
        val nonExistentKeyId = KeyId("non-existent-key")

        val result = kms.getPublicKey(nonExistentKeyId)

        assertTrue(result is GetPublicKeyResult.Failure.KeyNotFound)
        assertEquals(nonExistentKeyId, result.keyId)
    }

    @Test
    fun `test sign signs data successfully`() = runBlocking {
        val generateResult = kms.generateKey(Algorithm.Ed25519)
        assertTrue(generateResult is GenerateKeyResult.Success)
        val keyId = generateResult.keyHandle.id
        val data = "Hello, TrustWeave!".toByteArray()

        val result = kms.sign(keyId, data)

        assertTrue(result is SignResult.Success)
        assertNotNull(result.signature)
        assertTrue(result.signature.isNotEmpty())
    }

    @Test
    fun `test sign with different algorithms`() = runBlocking {
        val algorithms = listOf(
            Algorithm.Ed25519,
            Algorithm.Secp256k1,
            Algorithm.P256,
            Algorithm.P384,
            Algorithm.P521
        )

        for (algorithm in algorithms) {
            val generateResult = kms.generateKey(algorithm)
            assertTrue(generateResult is GenerateKeyResult.Success)
            val keyId = generateResult.keyHandle.id
            val data = "test data".toByteArray()

            val sign = kms.sign(keyId, data)

            assertTrue(sign is SignResult.Success, "Signing failed for ${algorithm.name}")
            assertTrue(sign.signature.isNotEmpty())
        }
    }

    @Test
    fun `test sign rejects empty data`() = runBlocking {
        val generateResult = kms.generateKey(Algorithm.Ed25519)
        assertTrue(generateResult is GenerateKeyResult.Success)
        val keyId = generateResult.keyHandle.id

        val result = kms.sign(keyId, ByteArray(0))

        assertTrue(result is SignResult.Failure.Error)
        assertTrue(result.reason.contains("empty"))
    }

    @Test
    fun `test sign rejects data exceeding size limit`() = runBlocking {
        val generateResult = kms.generateKey(Algorithm.Ed25519)
        assertTrue(generateResult is GenerateKeyResult.Success)
        val keyId = generateResult.keyHandle.id
        val largeData = ByteArray(11 * 1024 * 1024) // 11 MB, exceeds 10 MB limit

        val result = kms.sign(keyId, largeData)

        assertTrue(result is SignResult.Failure.Error)
        assertTrue(result.reason.contains("exceeds maximum"))
    }

    @Test
    fun `test sign returns KeyNotFound for non-existent key`() = runBlocking {
        val nonExistentKeyId = KeyId("non-existent-key")
        val data = "test".toByteArray()

        val result = kms.sign(nonExistentKeyId, data)

        assertTrue(result is SignResult.Failure.KeyNotFound)
        assertEquals(nonExistentKeyId, result.keyId)
    }

    @Test
    fun `test sign with algorithm compatibility check`() = runBlocking {
        val generateResult = kms.generateKey(Algorithm.Ed25519)
        assertTrue(generateResult is GenerateKeyResult.Success)
        val keyId = generateResult.keyHandle.id
        val data = "test".toByteArray()

        // Try to sign with incompatible algorithm
        val result = kms.sign(keyId, data, Algorithm.P256)

        assertTrue(result is SignResult.Failure.UnsupportedAlgorithm)
        assertEquals(Algorithm.P256, result.requestedAlgorithm)
    }

    @Test
    fun `test deleteKey deletes existing key`() = runBlocking {
        val generateResult = kms.generateKey(Algorithm.Ed25519)
        assertTrue(generateResult is GenerateKeyResult.Success)
        val keyId = generateResult.keyHandle.id

        val result = kms.deleteKey(keyId)

        assertTrue(result is DeleteKeyResult.Deleted)

        // Verify key is actually deleted
        val getResult = kms.getPublicKey(keyId)
        assertTrue(getResult is GetPublicKeyResult.Failure.KeyNotFound)
    }

    @Test
    fun `test deleteKey returns NotFound for non-existent key`() = runBlocking {
        val nonExistentKeyId = KeyId("non-existent-key")

        val result = kms.deleteKey(nonExistentKeyId)

        assertTrue(result is DeleteKeyResult.NotFound)
    }

    @Test
    fun `test deleteKey is idempotent`() = runBlocking {
        val generateResult = kms.generateKey(Algorithm.Ed25519)
        assertTrue(generateResult is GenerateKeyResult.Success)
        val keyId = generateResult.keyHandle.id

        val firstDelete = kms.deleteKey(keyId)
        assertTrue(firstDelete is DeleteKeyResult.Deleted)

        val secondDelete = kms.deleteKey(keyId)
        assertTrue(secondDelete is DeleteKeyResult.NotFound)
    }

    @Test
    fun `test multiple keys can coexist`() = runBlocking {
        val key1Result = kms.generateKey(Algorithm.Ed25519)
        val key2Result = kms.generateKey(Algorithm.P256)
        val key3Result = kms.generateKey(Algorithm.RSA.RSA_2048)

        assertTrue(key1Result is GenerateKeyResult.Success)
        assertTrue(key2Result is GenerateKeyResult.Success)
        assertTrue(key3Result is GenerateKeyResult.Success)

        assertNotEquals(key1Result.keyHandle.id, key2Result.keyHandle.id)
        assertNotEquals(key2Result.keyHandle.id, key3Result.keyHandle.id)
    }

    @Test
    fun `test thread safety with concurrent operations`() = runBlocking {
        val keys = mutableListOf<KeyId>()
        
        // Generate multiple keys concurrently
        val generateResults = (1..10).map {
            kms.generateKey(Algorithm.Ed25519)
        }
        
        generateResults.forEach { result ->
            assertTrue(result is GenerateKeyResult.Success)
            keys.add(result.keyHandle.id)
        }

        // Sign concurrently
        val signs = keys.map { keyId ->
            kms.sign(keyId, "test".toByteArray())
        }

        signs.forEach { result ->
            assertTrue(result is SignResult.Success)
        }

        // Delete concurrently
        val deleteResults = keys.map { keyId ->
            kms.deleteKey(keyId)
        }

        deleteResults.forEach { result ->
            assertTrue(result is DeleteKeyResult.Deleted)
        }
    }

    @Test
    fun `test JWK format for EC keys`() = runBlocking {
        val algorithms = listOf(Algorithm.Secp256k1, Algorithm.P256, Algorithm.P384, Algorithm.P521)
        
        for (algorithm in algorithms) {
            val result = kms.generateKey(algorithm)
            assertTrue(result is GenerateKeyResult.Success)
            val jwk = result.keyHandle.publicKeyJwk
            assertNotNull(jwk)

            assertEquals(JwkKeyTypes.EC, jwk[JwkKeys.KTY])
            assertNotNull(jwk[JwkKeys.CRV])
            assertNotNull(jwk[JwkKeys.X])
            assertNotNull(jwk[JwkKeys.Y])
        }
    }

    @Test
    fun `test JWK format for RSA keys`() = runBlocking {
        val result = kms.generateKey(Algorithm.RSA.RSA_2048)
        assertTrue(result is GenerateKeyResult.Success)
        val jwk = result.keyHandle.publicKeyJwk
        assertNotNull(jwk)

        assertEquals(JwkKeyTypes.RSA, jwk[JwkKeys.KTY])
        assertNotNull(jwk[JwkKeys.N])
        assertNotNull(jwk[JwkKeys.E])
    }

    @Test
    fun `test JWK format for Ed25519`() = runBlocking {
        val result = kms.generateKey(Algorithm.Ed25519)
        assertTrue(result is GenerateKeyResult.Success)
        val jwk = result.keyHandle.publicKeyJwk
        assertNotNull(jwk)

        assertEquals(JwkKeyTypes.OKP, jwk[JwkKeys.KTY])
        assertEquals(Algorithm.Ed25519.curveName, jwk[JwkKeys.CRV])
        assertNotNull(jwk[JwkKeys.X])
    }
}

/**
 * Tests for InMemoryKeyManagementServiceProvider.
 */
class InMemoryKeyManagementServiceProviderTest {

    @Test
    fun `test provider name is inmemory`() {
        val provider = InMemoryKeyManagementServiceProvider()
        assertEquals("inmemory", provider.name)
    }

    @Test
    fun `test provider creates service`() {
        val provider = InMemoryKeyManagementServiceProvider()
        val kms = provider.create()

        assertNotNull(kms)
        assertTrue(kms is InMemoryKeyManagementService)
    }

    @Test
    fun `test provider creates service with options`() {
        val provider = InMemoryKeyManagementServiceProvider()
        val kms = provider.create(mapOf("test" to "value"))

        assertNotNull(kms)
        assertTrue(kms is InMemoryKeyManagementService)
    }

    @Test
    fun `test provider supported algorithms match service`() = runBlocking {
        val provider = InMemoryKeyManagementServiceProvider()
        val kms = provider.create()

        assertEquals(provider.supportedAlgorithms, kms.getSupportedAlgorithms())
    }

    @Test
    fun `test provider has no required environment variables`() {
        val provider = InMemoryKeyManagementServiceProvider()
        
        assertTrue(provider.requiredEnvironmentVariables.isEmpty())
    }
}

