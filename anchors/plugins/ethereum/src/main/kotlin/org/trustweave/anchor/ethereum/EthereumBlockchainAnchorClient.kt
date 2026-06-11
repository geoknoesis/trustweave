package org.trustweave.anchor.ethereum

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.trustweave.anchor.AnchorResult
import org.trustweave.anchor.evm.AbstractEvmAnchorClient
import org.trustweave.anchor.evm.EvmChainConfig
import org.trustweave.anchor.evm.EvmGas
import org.trustweave.anchor.exceptions.BlockchainException
import org.trustweave.anchor.exceptions.TreasuryException
import org.trustweave.anchor.payment.AssetRef
import org.trustweave.anchor.payment.OperationDescriptor
import org.trustweave.anchor.payment.PaymentContext
import org.trustweave.anchor.payment.TokenAmount
import org.trustweave.anchor.payment.isUnmanaged
import org.trustweave.core.exception.TrustWeaveException
import java.math.BigInteger
import java.nio.charset.StandardCharsets

/**
 * Ethereum mainnet blockchain anchor client implementation.
 *
 * Supports Ethereum mainnet and Sepolia testnet chains.
 * Uses Ethereum transaction data fields to store payload data.
 * Common chain mechanics (signing, nonce, gas, confirmation, reads) live in
 * [AbstractEvmAnchorClient]; this plugin adds the payment plane
 * (fee estimation and treasury-managed writes).
 *
 * **Example Usage:**
 * ```kotlin
 * val options = mapOf(
 *     "rpcUrl" to "https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY",
 *     "privateKey" to "0x..."
 * )
 * val client = EthereumBlockchainAnchorClient(EthereumBlockchainAnchorClient.MAINNET, options)
 * ```
 */
class EthereumBlockchainAnchorClient(
    chainId: String,
    options: Map<String, Any?> = emptyMap()
) : AbstractEvmAnchorClient(chainId, options, resolveChain(chainId)) {

    companion object {
        const val MAINNET = "eip155:1"  // Ethereum mainnet
        const val SEPOLIA = "eip155:11155111" // Sepolia testnet

        // Network RPC endpoints
        private const val MAINNET_RPC_URL = "https://eth.llamarpc.com"
        private const val SEPOLIA_RPC_URL = "https://eth-sepolia.g.alchemy.com/v2/demo"

        private fun resolveChain(chainId: String): EvmChainConfig {
            require(chainId.startsWith("eip155:")) {
                "Invalid chain ID for Ethereum: $chainId"
            }
            val chainIdNum = chainId.substringAfter(":").toIntOrNull()
            require(chainIdNum == 1 || chainIdNum == 11155111) {
                "Unsupported Ethereum chain ID: $chainId. Use 'eip155:1' (mainnet) or 'eip155:11155111' (Sepolia testnet)"
            }
            return when (chainId) {
                MAINNET -> EvmChainConfig(
                    numericChainId = 1L,
                    defaultRpcUrl = MAINNET_RPC_URL,
                    blockchainName = "Ethereum",
                    networkName = "ethereum-mainnet"
                )
                else -> EvmChainConfig(
                    numericChainId = 11155111L,
                    defaultRpcUrl = SEPOLIA_RPC_URL,
                    blockchainName = "Ethereum",
                    networkName = "sepolia-testnet"
                )
            }
        }
    }

    override suspend fun estimate(op: OperationDescriptor): TokenAmount = withContext(Dispatchers.IO) {
        val gasPrice = web3j.ethGasPrice().send().gasPrice
        val gasLimit = op.contractCall?.let {
            // eth_estimateGas is authoritative for contract calls; fall back to
            // calldata math (never a blanket multi-million default) on failure.
            try {
                val from = credentials?.address ?: "0x0000000000000000000000000000000000000000"
                val tx = org.web3j.protocol.core.methods.request.Transaction.createFunctionCallTransaction(
                    from,
                    null,
                    null,
                    null,
                    it.contractAddress,
                    it.value?.amount ?: BigInteger.ZERO,
                    org.web3j.utils.Numeric.toHexString(it.callData),
                )
                web3j.ethEstimateGas(tx).send().amountUsed
            } catch (_: Exception) {
                EvmGas.txGasLimit(it.callData)
            }
        } ?: run {
            // Plain anchor tx: a data-carrying value transfer costs exactly the
            // intrinsic gas of its calldata — size it from the payload bytes.
            val payloadBytes = op.payload?.let {
                Json.encodeToString(JsonElement.serializer(), it).toByteArray(StandardCharsets.UTF_8)
            }
            when {
                payloadBytes != null -> EvmGas.txGasLimit(payloadBytes)
                else -> EvmGas.txGasLimitForSize(op.payloadSizeBytes ?: 0L)
            }
        }

        val gasCost = gasPrice.multiply(gasLimit)
        val valueWei = op.contractCall?.value?.amount ?: BigInteger.ZERO
        TokenAmount(op.chainId, AssetRef.Native, gasCost + valueWei)
    }

    override suspend fun writePayload(
        payload: JsonElement,
        ctx: PaymentContext,
        mediaType: String,
    ): AnchorResult {
        if (ctx.isUnmanaged) return writePayload(payload, mediaType)
        require(ctx.chainId == chainId) {
            "PaymentContext.chainId (${ctx.chainId}) does not match client chainId ($chainId)"
        }

        // In digest payload mode the envelope — not the raw payload — is what goes
        // on-chain, so estimation and submission must both use the anchored bytes.
        val submittedBytes = encodeAnchoredBytes(payload, mediaType)

        val estimate = estimate(
            OperationDescriptor(
                kind = "anchor.writePayload",
                chainId = chainId,
                payload = if (digestPayloadMode) null else payload,
                payloadSizeBytes = submittedBytes.size.toLong(),
            ),
        )
        ctx.maxFee?.let { cap ->
            if (estimate > cap) {
                throw TreasuryException.CallerCapExceeded(
                    correlationId = ctx.correlationId,
                    chainId = chainId,
                    estimated = estimate,
                    callerMax = cap,
                )
            }
        }

        return withContext(Dispatchers.IO) {
            try {
                val creds = credentials
                    ?: throw IllegalStateException("Credentials not configured. Provide 'privateKey' in options.")
                val receipt = submitTransaction(submittedBytes)
                val feePaid = computeActualFee(receipt)

                AnchorResult(
                    ref = buildAnchorRef(
                        txHash = receipt.transactionHash,
                        contract = getContractAddress(),
                        extra = anchorExtraMetadata(mediaType),
                    ),
                    payload = payload,
                    mediaType = mediaType,
                    timestamp = System.currentTimeMillis() / 1000,
                    fee = feePaid,
                    payerAddress = creds.address,
                )
            } catch (e: TrustWeaveException) {
                throw e
            } catch (e: Exception) {
                throw BlockchainException.TransactionFailed(
                    chainId = chainId,
                    operation = "writePayload",
                    payloadSize = submittedBytes.size.toLong(),
                    reason = "Failed to anchor payload to ${getBlockchainName()}: ${e.message ?: "Unknown error"}",
                    cause = e,
                )
            }
        }
    }
}
