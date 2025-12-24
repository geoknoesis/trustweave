package org.trustweave.awskms

import org.trustweave.kms.Algorithm
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.KeySpec
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AwsKeyManagementServiceTest {

    private lateinit var mockKmsClient: KmsClient
    private lateinit var kmsService: AwsKeyManagementService
    private lateinit var config: AwsKmsConfig

    @BeforeEach
    fun setup() {
        config = AwsKmsConfig.builder()
            .region("us-east-1")
            .endpointOverride("http://localhost:4566") // LocalStack endpoint
            .build()

        // Note: In a real test, we'd use a mocking library like MockK
        // For now, we'll test the configuration and algorithm mapping
    }

    @Test
    fun `test get supported algorithms`() = runBlocking {
        // Create service with mock (would need actual mock setup)
        val supported = AwsKeyManagementService.SUPPORTED_ALGORITHMS

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
    fun `test algorithm mapping to AWS key spec`() {
        // Test that mapping returns valid KeySpec values
        val ed25519Spec = AlgorithmMapping.toAwsKeySpec(Algorithm.Ed25519)
        assertNotNull(ed25519Spec)

        val secp256k1Spec = AlgorithmMapping.toAwsKeySpec(Algorithm.Secp256k1)
        assertNotNull(secp256k1Spec)

        assertEquals(KeySpec.ECC_NIST_P256, AlgorithmMapping.toAwsKeySpec(Algorithm.P256))
        assertEquals(KeySpec.ECC_NIST_P384, AlgorithmMapping.toAwsKeySpec(Algorithm.P384))
        assertEquals(KeySpec.ECC_NIST_P521, AlgorithmMapping.toAwsKeySpec(Algorithm.P521))
        assertEquals(KeySpec.RSA_2048, AlgorithmMapping.toAwsKeySpec(Algorithm.RSA.RSA_2048))
        assertEquals(KeySpec.RSA_3072, AlgorithmMapping.toAwsKeySpec(Algorithm.RSA.RSA_3072))
        assertEquals(KeySpec.RSA_4096, AlgorithmMapping.toAwsKeySpec(Algorithm.RSA.RSA_4096))
    }

    @Test
    fun `test algorithm mapping to AWS signing algorithm`() {
        assertEquals(SigningAlgorithmSpec.ECDSA_SHA_256, AlgorithmMapping.toAwsSigningAlgorithm(Algorithm.Ed25519))
        assertEquals(SigningAlgorithmSpec.ECDSA_SHA_256, AlgorithmMapping.toAwsSigningAlgorithm(Algorithm.Secp256k1))
        assertEquals(SigningAlgorithmSpec.ECDSA_SHA_256, AlgorithmMapping.toAwsSigningAlgorithm(Algorithm.P256))
        assertEquals(SigningAlgorithmSpec.ECDSA_SHA_384, AlgorithmMapping.toAwsSigningAlgorithm(Algorithm.P384))
        assertEquals(SigningAlgorithmSpec.ECDSA_SHA_512, AlgorithmMapping.toAwsSigningAlgorithm(Algorithm.P521))
        assertEquals(SigningAlgorithmSpec.RSASSA_PKCS1_V1_5_SHA_256, AlgorithmMapping.toAwsSigningAlgorithm(Algorithm.RSA.RSA_2048))
    }

    @Test
    fun `test unsupported algorithm throws exception`() {
        assertThrows<IllegalArgumentException> {
            AlgorithmMapping.toAwsKeySpec(Algorithm.BLS12_381)
        }
    }

    @Test
    fun `test resolve key ID`() {
        assertEquals("key-123", AlgorithmMapping.resolveKeyId("key-123"))
        assertEquals("arn:aws:kms:us-east-1:123456789012:key/123",
            AlgorithmMapping.resolveKeyId("arn:aws:kms:us-east-1:123456789012:key/123"))
        assertEquals("alias/my-key", AlgorithmMapping.resolveKeyId("alias/my-key"))
    }

    @Test
    fun `test AwsKmsConfig from map`() {
        val config = AwsKmsConfig.fromMap(mapOf(
            "region" to "us-west-2",
            "accessKeyId" to "AKIA...",
            "secretAccessKey" to "secret",
            "endpointOverride" to "http://localhost:4566"
        ))

        assertEquals("us-west-2", config.region)
        assertEquals("AKIA...", config.accessKeyId)
        assertEquals("secret", config.secretAccessKey)
        assertEquals("http://localhost:4566", config.endpointOverride)
    }

    @Test
    fun `test AwsKmsConfig from map without region throws exception`() {
        assertThrows<IllegalArgumentException> {
            AwsKmsConfig.fromMap(emptyMap())
        }
    }

    @Test
    fun `test AwsKmsConfig builder`() {
        val config = AwsKmsConfig.builder()
            .region("eu-west-1")
            .accessKeyId("AKIA123")
            .build()

        assertEquals("eu-west-1", config.region)
        assertEquals("AKIA123", config.accessKeyId)
    }

    @Test
    fun `test AwsKeyManagementServiceProvider`() {
        val provider = AwsKeyManagementServiceProvider()

        assertEquals("aws", provider.name)
        assertEquals(AwsKeyManagementService.SUPPORTED_ALGORITHMS, provider.supportedAlgorithms)
        assertTrue(provider.supportsAlgorithm(Algorithm.Ed25519))
        assertFalse(provider.supportsAlgorithm(Algorithm.BLS12_381))
    }

    @Test
    fun `test AwsKeyManagementServiceProvider create with options`() {
        val provider = AwsKeyManagementServiceProvider()

        val kms = provider.create(mapOf(
            "region" to "us-east-1",
            "endpointOverride" to "http://localhost:4566"
        ))

        assertNotNull(kms)
        assertTrue(kms is AwsKeyManagementService)
    }

    @Test
    fun `test AwsKeyManagementServiceProvider create without region throws exception`() {
        val provider = AwsKeyManagementServiceProvider()

        assertThrows<IllegalArgumentException> {
            provider.create(emptyMap())
        }
    }
}

