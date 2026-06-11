package org.trustweave.anchor.ethereum

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.trustweave.anchor.*
import org.trustweave.anchor.evm.EvmGas
import org.trustweave.anchor.exceptions.BlockchainException
import org.trustweave.anchor.exceptions.TreasuryException
import org.trustweave.anchor.payment.AssetRef
import org.trustweave.anchor.payment.OperationDescriptor
import org.trustweave.anchor.payment.PaymentContext
import org.trustweave.anchor.payment.TokenAmount
import org.trustweave.anchor.payment.isUnmanaged
import org.trustweave.core.exception.TrustWeaveException
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.protocol.http.HttpService
import org.web3j.tx.RawTransactionManager
import org.web3j.tx.TransactionManager
import java.math.BigInteger
import java.nio.charset.StandardCharsets

/**
 * Ethereum mainnet blockchain anchor client implementation.
 *
 * Supports Ethereum mainnet and Sepolia testnet chains.
 * Uses Ethereum transaction data fields to store payload data.
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
) : AbstractBlockchainAnchorClient(chainId, options), java.io.Closeable {

    companion object {
        const val MAINNET = "eip155:1"  // Ethereum mainnet
        const val SEPOLIA = "eip155:11155111" // Sepolia testnet

        // Network RPC endpoints
        private const val MAINNET_RPC_URL = "https://eth.llamarpc.com"
        private const val SEPOLIA_RPC_URL = "https://eth-sepolia.g.alchemy.com/v2/demo"
    }

    private val web3j: Web3j
    private val transactionManager: TransactionManager?
    private val credentials: org.web3j.crypto.Credentials?

    init {
        require(chainId.startsWith("eip155:")) {
            "Invalid chain ID for Ethereum: $chainId"
        }
        val chainIdNum = chainId.substringAfter(":").toIntOrNull()
        require(chainIdNum == 1 || chainIdNum == 11155111) {
            "Unsupported Ethereum chain ID: $chainId. Use 'eip155:1' (mainnet) or 'eip155:11155111' (Sepolia testnet)"
        }

        // Initialize Web3j client based on chain
        val rpcUrl = when (chainId) {
            MAINNET -> options["rpcUrl"] as? String ?: MAINNET_RPC_URL
            SEPOLIA -> options["rpcUrl"] as? String ?: SEPOLIA_RPC_URL
            else -> throw IllegalArgumentException("Unsupported chain: $chainId")
        }
        web3j = Web3j.build(HttpService(rpcUrl))

        // Initialize transaction manager if credentials are provided.
        // A present-but-invalid private key is a configuration error and must fail
        // closed instead of silently degrading to the in-memory test fallback.
        val creds = (options["privateKey"] as? String)?.let { privateKeyHex ->
            try {
                org.web3j.crypto.Credentials.create(privateKeyHex.removePrefix("0x"))
            } catch (e: Exception) {
                throw BlockchainException.ConfigurationFailed(
                    chainId = chainId,
                    configKey = "privateKey",
                    reason = "Invalid Ethereum private key: ${e.message ?: "Unknown error"}",
                    cause = e
                )
            }
        }
        credentials = creds
        transactionManager = creds?.let {
            val chainIdLong = when (chainId) {
                MAINNET -> 1L
                SEPOLIA -> 11155111L
                else -> throw IllegalArgumentException("Unsupported chain: $chainId")
            }
            RawTransactionManager(web3j, it, chainIdLong)
        }
    }

    override protected fun canSubmitTransaction(): Boolean {
        return transactionManager != null
    }

    override protected suspend fun submitTransactionToBlockchain(payloadBytes: ByteArray): String {
        return submitTransaction(payloadBytes).transactionHash
    }

    override protected suspend fun readTransactionFromBlockchain(txHash: String): AnchorResult {
        return readTransactionFromBlockchainImpl(txHash)
    }

    override protected fun getContractAddress(): String? {
        return options["contractAddress"] as? String
    }

    override protected fun buildExtraMetadata(mediaType: String): Map<String, String> {
        val network = when (chainId) {
            MAINNET -> "ethereum-mainnet"
            SEPOLIA -> "sepolia-testnet"
            else -> chainId
        }
        return mapOf(
            "network" to network,
            "mediaType" to mediaType
        )
    }

    override protected fun generateTestTxHash(): String {
        return "0x${uniqueTestHashHex()}"
    }

    override protected fun getBlockchainName(): String {
        return "Ethereum"
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

        val payloadJson = Json.encodeToString(JsonElement.serializer(), payload)
        val payloadBytes = payloadJson.toByteArray(StandardCharsets.UTF_8)

        val estimate = estimate(
            OperationDescriptor(
                kind = "anchor.writePayload",
                chainId = chainId,
                payload = payload,
                payloadSizeBytes = payloadBytes.size.toLong(),
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
                val receipt = submitTransaction(payloadBytes)
                val feePaid = computeActualFee(receipt)

                AnchorResult(
                    ref = buildAnchorRef(
                        txHash = receipt.transactionHash,
                        contract = getContractAddress(),
                        extra = buildExtraMetadata(mediaType),
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
                    payloadSize = payloadBytes.size.toLong(),
                    reason = "Failed to anchor payload to ${getBlockchainName()}: ${e.message ?: "Unknown error"}",
                    cause = e,
                )
            }
        }
    }

    /**
     * Computes the fee actually paid from the confirmed [receipt].
     * `effectiveGasPrice` is populated on EIP-1559 chains; falls back to the
     * transaction's gas price otherwise.
     */
    private fun computeActualFee(receipt: TransactionReceipt): TokenAmount? = try {
        val gasUsed = receipt.gasUsed ?: BigInteger.ZERO
        val effectivePrice = receipt.effectiveGasPrice
            ?.let { org.web3j.utils.Numeric.decodeQuantity(it) }
            ?: web3j.ethGetTransactionByHash(receipt.transactionHash).send()
                .transaction.orElse(null)?.gasPrice
            ?: BigInteger.ZERO
        TokenAmount(chainId, AssetRef.Native, gasUsed.multiply(effectivePrice))
    } catch (_: Exception) {
        null
    }

    private suspend fun submitTransaction(data: ByteArray): TransactionReceipt {
        val creds = credentials ?: throw IllegalStateException("Credentials not configured. Provide 'privateKey' in options.")

        val gasPrice = web3j.ethGasPrice().send().gasPrice
        // PENDING (not LATEST) so rapid successive anchors don't reuse a nonce.
        val nonce = web3j.ethGetTransactionCount(creds.address, org.web3j.protocol.core.DefaultBlockParameterName.PENDING)
            .send().transactionCount

        // An anchor is a data-carrying value transfer: its cost is the intrinsic
        // calldata gas (+10% margin), not a blanket multi-million default limit.
        val gasLimit = EvmGas.txGasLimit(data)
        val rawTransaction = org.web3j.crypto.RawTransaction.createTransaction(
            nonce,
            gasPrice,
            gasLimit,
            creds.address, // Send to self
            BigInteger.ZERO,
            org.web3j.utils.Numeric.toHexString(data)
        )

        val signedTransaction = org.web3j.crypto.TransactionEncoder.signMessage(rawTransaction, creds)
        val hexValue = org.web3j.utils.Numeric.toHexString(signedTransaction)

        val ethSendTransaction = web3j.ethSendRawTransaction(hexValue).send()
        if (ethSendTransaction.hasError()) {
            val error = ethSendTransaction.error
            throw BlockchainException.TransactionFailed(
                chainId = chainId,
                txHash = null,
                operation = "submitTransaction",
                payloadSize = data.size.toLong(),
                gasUsed = gasLimit.toLong(),
                reason = "Transaction failed: ${error?.message ?: "Unknown error"}"
            )
        }

        // eth_sendRawTransaction only means the node accepted the tx into its pool —
        // wait for on-chain confirmation so dropped or reverted txs are never
        // reported as successful anchors.
        return waitForReceipt(ethSendTransaction.transactionHash, data.size.toLong())
    }

    /**
     * Polls `eth_getTransactionReceipt` until the transaction is mined, bounded by
     * [confirmationTimeoutMs] (option [OPTION_CONFIRMATION_TIMEOUT_MS]). Throws
     * [BlockchainException.TransactionFailed] on revert or timeout.
     */
    private suspend fun waitForReceipt(txHash: String, payloadSize: Long): TransactionReceipt {
        val deadline = System.currentTimeMillis() + confirmationTimeoutMs
        while (true) {
            val receipt = web3j.ethGetTransactionReceipt(txHash).send().transactionReceipt.orElse(null)
            if (receipt != null) {
                if (!receipt.isStatusOK) {
                    throw BlockchainException.TransactionFailed(
                        chainId = chainId,
                        txHash = txHash,
                        operation = "submitTransaction",
                        payloadSize = payloadSize,
                        gasUsed = try {
                            receipt.gasUsed?.toLong()
                        } catch (_: Exception) {
                            null
                        },
                        reason = "Transaction reverted on chain (status=${receipt.status})"
                    )
                }
                return receipt
            }
            if (System.currentTimeMillis() >= deadline) {
                throw BlockchainException.TransactionFailed(
                    chainId = chainId,
                    txHash = txHash,
                    operation = "submitTransaction",
                    payloadSize = payloadSize,
                    reason = "Transaction not confirmed within $confirmationTimeoutMs ms " +
                        "(configure via '$OPTION_CONFIRMATION_TIMEOUT_MS' option)"
                )
            }
            delay(confirmationPollIntervalMs)
        }
    }

    private suspend fun readTransactionFromBlockchainImpl(txHash: String): AnchorResult {
        val ethGetTransactionReceipt = web3j.ethGetTransactionReceipt(txHash).send()
        if (!ethGetTransactionReceipt.transactionReceipt.isPresent) {
            throw TrustWeaveException.NotFound(
                resource = "Transaction receipt $txHash"
            )
        }

        val receipt = ethGetTransactionReceipt.transactionReceipt.get()
        val tx = web3j.ethGetTransactionByHash(txHash).send().transaction.orElse(null)
            ?: throw TrustWeaveException.NotFound(
                resource = "Transaction $txHash"
            )

        val input = tx.input
        if (input == null || input.isEmpty() || input == "0x") {
            throw TrustWeaveException.NotFound(resource = "Transaction data not found: $txHash")
        }

        val dataBytes = org.web3j.utils.Numeric.hexStringToByteArray(input)
        val payloadJson = String(dataBytes, StandardCharsets.UTF_8)
        val payload = Json.parseToJsonElement(payloadJson)

        // Use the actual block timestamp (one extra RPC call); if it cannot be
        // resolved, leave the timestamp unset rather than fabricating one.
        val blockTimestamp = try {
            receipt.blockNumber?.let { blockNumber ->
                web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(blockNumber), false)
                    .send().block?.timestamp?.toLong()
            }
        } catch (e: Exception) {
            null
        }

        return AnchorResult(
            ref = buildAnchorRef(
                txHash = txHash,
                contract = getContractAddress()
            ),
            payload = payload,
            mediaType = "application/json",
            timestamp = blockTimestamp
        )
    }

    override fun close() {
        try {
            web3j.shutdown()
        } catch (e: Exception) {
            // Ignore errors during shutdown
        }
    }
}

