package com.trustweave.kms.entrust

import com.trustweave.core.identifiers.KeyId
import com.trustweave.kms.Algorithm
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.results.DeleteKeyResult
import com.trustweave.kms.results.GenerateKeyResult
import com.trustweave.kms.results.GetPublicKeyResult
import com.trustweave.kms.results.SignResult

/**
 * Entrust nShield HSM implementation of KeyManagementService.
 *
 * **⚠️ EXPERIMENTAL - NOT YET IMPLEMENTED ⚠️**
 *
 * This plugin is a placeholder and will throw [UnsupportedOperationException] if used.
 * Entrust nShield HSM integration requires access to Entrust SDK and HSM access.
 *
 * **Status**: Implementation pending.
 *
 * **Supported Algorithms** (when implemented):
 * - Ed25519, secp256k1, P-256, P-384, P-521
 * - RSA-2048, RSA-3072, RSA-4096
 */
class EntrustKeyManagementService(
    private val config: EntrustKmsConfig
) : KeyManagementService {

    init {
        throw UnsupportedOperationException(
            "Entrust nShield KMS plugin is not yet implemented. " +
            "This plugin requires Entrust SDK and HSM access. " +
            "See https://github.com/trustweave/trustweave/issues for implementation status."
        )
    }

    companion object {
        val SUPPORTED_ALGORITHMS = setOf(
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

    override suspend fun getSupportedAlgorithms(): Set<Algorithm> = SUPPORTED_ALGORITHMS

    override suspend fun generateKey(
        algorithm: Algorithm,
        options: Map<String, Any?>
    ): GenerateKeyResult {
        throw UnsupportedOperationException("Entrust nShield KMS plugin is not yet implemented")
    }

    override suspend fun getPublicKey(keyId: KeyId): GetPublicKeyResult {
        throw UnsupportedOperationException("Entrust nShield KMS plugin is not yet implemented")
    }

    override suspend fun sign(
        keyId: KeyId,
        data: ByteArray,
        algorithm: Algorithm?
    ): SignResult {
        throw UnsupportedOperationException("Entrust nShield KMS plugin is not yet implemented")
    }

    override suspend fun deleteKey(keyId: KeyId): DeleteKeyResult {
        throw UnsupportedOperationException("Entrust nShield KMS plugin is not yet implemented")
    }
}

