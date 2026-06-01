package org.trustweave.trust.domain.treasury

import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.anchor.payment.OperationDescriptor
import org.trustweave.anchor.payment.TokenAmount
import org.trustweave.core.identifiers.KeyId
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.SignResult

/**
 * Phase 2 in-memory [ChainAccount] adapter.
 *
 * Bridges to the registered [BlockchainAnchorClient] for fee estimation and to
 * a [KeyManagementService] for signing. Phase 2 plugins keep their embedded
 * credentials, so [balance] returns [TokenAmount.unknown] — real on-chain
 * balance lookup is out of scope until plugins surface it.
 */
class InMemoryChainAccount(
    override val chainId: String,
    override val address: String,
    override val keyRef: KmsKeyRef,
    private val anchorClient: BlockchainAnchorClient,
    private val kms: KeyManagementService,
) : ChainAccount {

    override suspend fun balance(): TokenAmount = TokenAmount.unknown(chainId)

    override suspend fun estimateFee(op: OperationDescriptor): TokenAmount =
        anchorClient.estimate(op)

    override suspend fun sign(tx: UnsignedTx): SignedTx {
        require(tx.chainId == chainId) {
            "tx.chainId (${tx.chainId}) must match account.chainId ($chainId)"
        }
        return when (val result = kms.sign(KeyId(keyRef.keyId), tx.bytes)) {
            is SignResult.Success -> SignedTx(chainId, result.signature)
            is SignResult.Failure.KeyNotFound ->
                throw IllegalStateException("Signing failed: key not found: ${result.keyId.value}")
            is SignResult.Failure.UnsupportedAlgorithm ->
                throw IllegalStateException(
                    "Signing failed: unsupported algorithm: ${result.reason ?: "algorithm mismatch"}",
                )
            is SignResult.Failure.Error ->
                throw IllegalStateException("Signing failed: ${result.reason}")
        }
    }
}
