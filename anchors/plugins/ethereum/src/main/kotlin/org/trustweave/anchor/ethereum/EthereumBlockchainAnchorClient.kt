package org.trustweave.anchor.ethereum

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.trustweave.anchor.*
import org.trustweave.anchor.exceptions.BlockchainException
import org.trustweave.anchor.exceptions.TreasuryException
import org.trustweave.anchor.payment.AssetRef
import org.trustweave.anchor.payment.OperationDescriptor
import org.trustweave.anchor.payment.PaymentContext
import org.trustweave.anchor.payment.TokenAmount
import org.trustweave.anchor.payment.isUnmanaged
import org.trustweave.core.exception.TrustWeaveException
import org.web3j.protocol.Web3j
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
        return submitTransaction(payloadBytes)
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
        return "0x${(0..1000000).random().toString(16).padStart(64, '0')}"
    }

    override protected fun getBlockchainName(): String {
        return "Ethereum"
    }

    override suspend fun estimate(op: OperationDescriptor): TokenAmount = withContext(Dispatchers.IO) {
        val gasPrice = web3j.ethGasPrice().send().gasPrice
        val gasLimit = op.contractCall?.let {
            // Best-effort calldata-based gas size; fall back to default on failure.
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
                org.web3j.tx.gas.DefaultGasProvider.GAS_LIMIT
            }
        } ?: org.web3j.tx.gas.DefaultGasProvider.GAS_LIMIT

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
                val txHash = submitTransaction(payloadBytes)
                val feePaid = computeActualFee(txHash)

                AnchorResult(
                    ref = buildAnchorRef(
                        txHash = txHash,
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

    private fun computeActualFee(txHash: String): TokenAmount? = try {
        val receiptOpt = web3j.ethGetTransactionReceipt(txHash).send().transactionReceipt
        if (receiptOpt.isPresent) {
            val receipt = receiptOpt.get()
            val gasUsed = receipt.gasUsed ?: BigInteger.ZERO
            // effectiveGasPrice — exposed by EIP-1559 receipts; fall back to tx gasPrice.
            val effectivePrice = try {
                val getter = receipt.javaClass.getMethod("getEffectiveGasPrice")
                (getter.invoke(receipt) as? String)
                    ?.let { org.web3j.utils.Numeric.decodeQuantity(it) }
            } catch (_: Exception) {
                null
            } ?: run {
                web3j.ethGetTransactionByHash(txHash).send().transaction.orElse(null)?.gasPrice
                    ?: BigInteger.ZERO
            }
            TokenAmount(chainId, AssetRef.Native, gasUsed.multiply(effectivePrice))
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    private suspend fun submitTransaction(data: ByteArray): String {
        val creds = credentials ?: throw IllegalStateException("Credentials not configured. Provide 'privateKey' in options.")

        val gasPrice = web3j.ethGasPrice().send().gasPrice
        val nonce = web3j.ethGetTransactionCount(creds.address, org.web3j.protocol.core.DefaultBlockParameterName.LATEST)
            .send().transactionCount

        val transaction = org.web3j.tx.gas.DefaultGasProvider()
        val rawTransaction = org.web3j.crypto.RawTransaction.createTransaction(
            nonce,
            gasPrice,
            transaction.gasLimit,
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
                gasUsed = transaction.gasLimit?.toLong(),
                reason = "Transaction failed: ${error?.message ?: "Unknown error"}"
            )
        }

        return ethSendTransaction.transactionHash
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

        val blockNumber = try {
            receipt.blockNumber?.toLong()
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
            timestamp = blockNumber?.let { System.currentTimeMillis() / 1000 }
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

