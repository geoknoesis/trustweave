package org.trustweave.testkit.services

import org.trustweave.kms.services.KmsService
import org.trustweave.kms.KeyHandle
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.GenerateKeyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * KMS Service implementation for testkit.
 */
class TestkitKmsService : KmsService {
    override suspend fun generateKey(
        kms: KeyManagementService,
        algorithm: String,
        options: Map<String, Any?>
    ): GenerateKeyResult = withContext(Dispatchers.IO) {
        kms.generateKey(algorithm, options)
    }

    override fun getKeyId(keyHandle: KeyHandle): String = keyHandle.id.value

    override fun getPublicKeyJwk(keyHandle: KeyHandle): Map<String, Any?>? = keyHandle.publicKeyJwk
}
