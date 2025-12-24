package org.trustweave.testkit.services

import org.trustweave.kms.services.KmsService
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.KeyHandle
import org.trustweave.kms.results.GenerateKeyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * KMS Service implementation for testkit.
 */
class TestkitKmsService : KmsService {
    override suspend fun generateKey(
        kms: Any,
        algorithm: String,
        options: Map<String, Any?>
    ): Any = withContext(Dispatchers.IO) {
        val keyManagementService = kms as? KeyManagementService
            ?: throw IllegalArgumentException("Expected KeyManagementService, got ${kms.javaClass.name}")

        val result = keyManagementService.generateKey(algorithm, options)
        val keyHandle = when (result) {
            is org.trustweave.kms.results.GenerateKeyResult.Success -> result.keyHandle
            is org.trustweave.kms.results.GenerateKeyResult.Failure.UnsupportedAlgorithm -> throw IllegalArgumentException(
                "Algorithm not supported: ${result.algorithm}"
            )
            is org.trustweave.kms.results.GenerateKeyResult.Failure.InvalidOptions -> throw IllegalArgumentException(
                "Invalid options: ${result.reason}"
            )
            is org.trustweave.kms.results.GenerateKeyResult.Failure.Error -> throw IllegalArgumentException(
                "Failed to generate key: ${result.reason}"
            )
        }
        return@withContext keyHandle as Any
    }

    override fun getKeyId(keyHandle: Any): String {
        val handle = keyHandle as? KeyHandle
            ?: throw IllegalArgumentException("Expected KeyHandle, got ${keyHandle.javaClass.name}")
        return handle.id.value
    }

    override fun getPublicKeyJwk(keyHandle: Any): Map<String, Any?>? {
        val handle = keyHandle as? KeyHandle
            ?: throw IllegalArgumentException("Expected KeyHandle, got ${keyHandle.javaClass.name}")
        return handle.publicKeyJwk
    }
}

