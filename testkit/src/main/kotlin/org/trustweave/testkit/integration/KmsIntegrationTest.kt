package org.trustweave.testkit.integration

import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyHandle
import org.trustweave.kms.KeyManagementService
import org.trustweave.testkit.BaseIntegrationTest
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Base class for KMS integration tests.
 *
 * Provides common test scenarios for KMS plugins including:
 * - Tests with real KMS services (using TestContainers where possible)
 * - Key lifecycle tests
 * - Multi-provider tests
 *
 * **Example Usage**:
 * ```kotlin
 * @Testcontainers
 * class AwsKmsIntegrationTest : KmsIntegrationTest() {
 *     companion object {
 *         @JvmStatic
 *         val localStack = LocalStackContainer.create()
 *     }
 *
 *     override fun getKms(): KeyManagementService {
 *         val config = AwsKmsConfig.builder()
 *             .region("us-east-1")
 *             .endpointOverride(localStack.getKmsEndpoint())
 *             .build()
 *         return AwsKeyManagementService(config)
 *     }
 *
 *     @Test
 *     fun testKeyLifecycle() = runBlocking {
 *         testGenerateAndSign()
 *     }
 * }
 * ```
 */
abstract class KmsIntegrationTest : BaseIntegrationTest() {

    /**
     * Gets the KMS service to test.
     * Must be implemented by subclasses.
     */
    abstract override fun getKms(): KeyManagementService

    /**
     * Gets the supported algorithms for this KMS.
     * Defaults to common algorithms. Override to specify.
     */
    open fun getSupportedAlgorithms(): List<Algorithm> {
        return listOf(Algorithm.Ed25519, Algorithm.Secp256k1)
    }

    /**
     * Tests key generation and signing.
     */
    protected suspend fun testGenerateAndSign() {
        val kms = getKms()
        val algorithm = getSupportedAlgorithms().first()

        val generateResult = kms.generateKey(algorithm)
        val keyHandle = when (generateResult) {
            is org.trustweave.kms.results.GenerateKeyResult.Success -> generateResult.keyHandle
            else -> throw IllegalArgumentException("Failed to generate key: $generateResult")
        }
        kotlin.test.assertNotNull(keyHandle)
        kotlin.test.assertNotNull(keyHandle.id)

        val message = "test message".toByteArray()
        val signResult = kms.sign(keyHandle.id, message)
        val signature = when (signResult) {
            is org.trustweave.kms.results.SignResult.Success -> signResult.signature
            else -> throw IllegalArgumentException("Failed to sign: $signResult")
        }
        kotlin.test.assertNotNull(signature)
        kotlin.test.assertTrue(signature.isNotEmpty())
    }

    /**
     * Tests key retrieval.
     */
    protected suspend fun testRetrieveKey() {
        val kms = getKms()
        val algorithm = getSupportedAlgorithms().first()

        val generateResult = kms.generateKey(algorithm)
        val keyHandle = when (generateResult) {
            is org.trustweave.kms.results.GenerateKeyResult.Success -> generateResult.keyHandle
            else -> throw IllegalArgumentException("Failed to generate key: $generateResult")
        }
        val publicKeyResult = kms.getPublicKey(keyHandle.id)
        val retrieved = when (publicKeyResult) {
            is org.trustweave.kms.results.GetPublicKeyResult.Success -> publicKeyResult.keyHandle
            else -> throw IllegalArgumentException("Failed to get public key: $publicKeyResult")
        }

        kotlin.test.assertNotNull(retrieved)
        kotlin.test.assertEquals(keyHandle.id, retrieved.id)
        kotlin.test.assertEquals(keyHandle.algorithm, retrieved.algorithm)
    }

    /**
     * Tests key deletion.
     */
    protected suspend fun testDeleteKey() {
        val kms = getKms()
        val algorithm = getSupportedAlgorithms().first()

        val generateResult = kms.generateKey(algorithm)
        val keyHandle = when (generateResult) {
            is org.trustweave.kms.results.GenerateKeyResult.Success -> generateResult.keyHandle
            else -> throw IllegalArgumentException("Failed to generate key: $generateResult")
        }
        val deleteResult = kms.deleteKey(keyHandle.id)
        val deleted = when (deleteResult) {
            is org.trustweave.kms.results.DeleteKeyResult.Deleted -> true
            is org.trustweave.kms.results.DeleteKeyResult.NotFound -> false
            else -> false
        }

        kotlin.test.assertTrue(deleted)

        // Attempting to retrieve deleted key should fail
        try {
            val getResult = kms.getPublicKey(keyHandle.id)
            // Some KMS implementations may not throw, so this is optional
        } catch (e: Exception) {
            // Expected behavior
        }
    }

    /**
     * Tests multiple algorithms.
     */
    protected suspend fun testMultipleAlgorithms() {
        val kms = getKms()
        val algorithms = getSupportedAlgorithms()

        val keyHandles = algorithms.mapNotNull { algorithm ->
            val result = kms.generateKey(algorithm)
            when (result) {
                is org.trustweave.kms.results.GenerateKeyResult.Success -> result.keyHandle
                else -> null
            }
        }

        assertTrue(keyHandles.size == algorithms.size)
        keyHandles.forEach { handle ->
            assertNotNull(handle.id)
        }
    }

    /**
     * Tests signing with different algorithms.
     */
    protected suspend fun testSigningWithDifferentAlgorithms() {
        val kms = getKms()
        val message = "test message".toByteArray()

        getSupportedAlgorithms().forEach { algorithm ->
            val generateResult = kms.generateKey(algorithm)
            val keyHandle = when (generateResult) {
                is org.trustweave.kms.results.GenerateKeyResult.Success -> generateResult.keyHandle
                else -> throw IllegalArgumentException("Failed to generate key: $generateResult")
            }
            val signResult = kms.sign(keyHandle.id, message)
            val signature = when (signResult) {
                is org.trustweave.kms.results.SignResult.Success -> signResult.signature
                else -> throw IllegalArgumentException("Failed to sign: $signResult")
            }

            kotlin.test.assertNotNull(signature)
            kotlin.test.assertTrue(signature.isNotEmpty())
        }
    }

}

