package org.trustweave.trust.services

import org.trustweave.kms.KeyHandle
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.services.KmsService

/**
 * Default [KmsService] adapter that delegates directly to the [KeyManagementService]
 * passed into each call.
 *
 * Wired automatically by [org.trustweave.trust.dsl.TrustWeaveFactory] so facade
 * operations that need a [KmsService] (e.g. `TrustWeave.rotateKey`) work out of the
 * box with whatever KMS the `keys { ... }` block configured — no separate setup step.
 */
internal class DefaultKmsService : KmsService {
    override suspend fun generateKey(
        kms: KeyManagementService,
        algorithm: String,
        options: Map<String, Any?>
    ): GenerateKeyResult = kms.generateKey(algorithm, options)

    override fun getKeyId(keyHandle: KeyHandle): String = keyHandle.id.value

    override fun getPublicKeyJwk(keyHandle: KeyHandle): Map<String, Any?>? = keyHandle.publicKeyJwk
}
