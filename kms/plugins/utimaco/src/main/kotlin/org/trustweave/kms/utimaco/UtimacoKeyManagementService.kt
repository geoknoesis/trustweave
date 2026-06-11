package org.trustweave.kms.utimaco

import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.Algorithm
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.DeleteKeyResult
import org.trustweave.kms.results.GenerateKeyResult
import org.trustweave.kms.results.GetPublicKeyResult
import org.trustweave.kms.results.SignResult

/**
 * Utimaco HSM implementation of KeyManagementService.
 *
 * **STUB — NOT IMPLEMENTED.**
 *
 * No operation works. Per the [KeyManagementService] never-throw contract, every
 * operation returns the corresponding `*Result.Failure` with a "not implemented"
 * reason instead of throwing. Nothing is ever generated, signed, or stored.
 *
 * A real implementation would require the Utimaco SDK and HSM access,
 * neither of which exists here.
 *
 * [SUPPORTED_ALGORITHMS] documents what a real implementation is expected to
 * support — currently none of them work.
 */
class UtimacoKeyManagementService(
    @Suppress("unused") private val config: UtimacoKmsConfig
) : KeyManagementService {

    companion object {
        private const val NOT_IMPLEMENTED =
            "Utimaco KMS plugin is a stub and is not implemented " +
                "(requires the Utimaco SDK and HSM access)"

        /**
         * Algorithms a real implementation is expected to support.
         * **None of them work** — this plugin is a stub.
         */
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
    ): GenerateKeyResult = GenerateKeyResult.Failure.Error(
        algorithm = algorithm,
        reason = NOT_IMPLEMENTED
    )

    override suspend fun getPublicKey(keyId: KeyId): GetPublicKeyResult =
        GetPublicKeyResult.Failure.Error(
            keyId = keyId,
            reason = NOT_IMPLEMENTED
        )

    override suspend fun sign(
        keyId: KeyId,
        data: ByteArray,
        algorithm: Algorithm?
    ): SignResult = SignResult.Failure.Error(
        keyId = keyId,
        reason = NOT_IMPLEMENTED
    )

    override suspend fun deleteKey(keyId: KeyId): DeleteKeyResult =
        DeleteKeyResult.Failure.Error(
            keyId = keyId,
            reason = NOT_IMPLEMENTED
        )
}
