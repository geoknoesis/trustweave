package io.geoknoesis.vericore.testkit.services

import io.geoknoesis.vericore.spi.services.KmsService
import io.geoknoesis.vericore.kms.KeyManagementService
import io.geoknoesis.vericore.kms.KeyHandle
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
        
        val keyHandle = keyManagementService.generateKey(algorithm, options)
        return@withContext keyHandle as Any
    }
    
    override fun getKeyId(keyHandle: Any): String {
        val handle = keyHandle as? KeyHandle
            ?: throw IllegalArgumentException("Expected KeyHandle, got ${keyHandle.javaClass.name}")
        return handle.id
    }
    
    override fun getPublicKeyJwk(keyHandle: Any): Map<String, Any?>? {
        val handle = keyHandle as? KeyHandle
            ?: throw IllegalArgumentException("Expected KeyHandle, got ${keyHandle.javaClass.name}")
        return handle.publicKeyJwk
    }
}

