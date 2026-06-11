package org.trustweave.anchor.base

import org.trustweave.anchor.*
import org.trustweave.anchor.evm.EvmGas
import org.trustweave.anchor.exceptions.BlockchainException
import org.trustweave.core.exception.TrustWeaveException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.protocol.http.HttpService
import org.web3j.tx.RawTransactionManager
import org.web3j.tx.TransactionManager
import java.math.BigInteger
import java.nio.charset.StandardCharsets

/**
 * Base (Coinbase L2) blockchain anchor client implementation.
 *
 * Supports Base mainnet and Base Sepolia testnet chains.
 * Uses Ethereum-compatible transaction data fields to store payload data.
 *
 * **Example Usage:**
 * ```kotlin
 * val options = mapOf(
 *     "rpcUrl" to "https://mainnet.base.org",
 *     "privateKey" to "0x..."
 * )
 * val client = BaseBlockchainAnchorClient(BaseBlockchainAnchorClient.MAINNET, options)
 * ```
 */
class BaseBlockchainAnchorClient(
    chainId: String,
    options: Map<String, Any?> = emptyMap()
) : AbstractBlockchainAnchorClient(chainId, options), java.io.Closeable {

    companion object {
        const val MAINNET = "eip155:8453"  // Base mainnet
        const val BASE_SEPOLIA = "eip155:84532" // Base Sepolia testnet

        // Network RPC endpoints
        private const val MAINNET_RPC_URL = "https://mainnet.base.org"
        private const val BASE_SEPOLIA_RPC_URL = "https://sepolia.base.org"
    }

    private val web3j: Web3j
    private val transactionManager: TransactionManager?
    private val credentials: org.web3j.crypto.Credentials?

    init {
        require(chainId.startsWith("eip155:")) {
            "Invalid chain ID for Base: $chainId"
        }
        val chainIdNum = chainId.substringAfter(":").toIntOrNull()
        require(chainIdNum == 8453 || chainIdNum == 84532) {
            "Unsupported Base chain ID: $chainId. Use 'eip155:8453' (mainnet) or 'eip155:84532' (Base Sepolia testnet)"
        }

        // Initialize Web3j client based on chain
        val rpcUrl = when (chainId) {
            MAINNET -> options["rpcUrl"] as? String ?: MAINNET_RPC_URL
            BASE_SEPOLIA -> options["rpcUrl"] as? String ?: BASE_SEPOLIA_RPC_URL
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
                    reason = "Invalid Base private key: ${e.message ?: "Unknown error"}",
                    cause = e
                )
            }
        }
        credentials = creds
        transactionManager = creds?.let {
            val chainIdLong = when (chainId) {
                MAINNET -> 8453L
                BASE_SEPOLIA -> 84532L
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
            MAINNET -> "base-mainnet"
            BASE_SEPOLIA -> "base-sepolia-testnet"
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
        return "Base"
    }

    private suspend fun submitTransaction(data: ByteArray): String {
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
        return waitForReceipt(ethSendTransaction.transactionHash, data.size.toLong()).transactionHash
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
            throw TrustWeaveException.NotFound(
                resource = "Transaction data for $txHash"
            )
        }

        val dataBytes = org.web3j.utils.Numeric.hexStringToByteArray(input)
        val payloadJson = String(dataBytes, StandardCharsets.UTF_8)
        val payload: JsonElement = Json.parseToJsonElement(payloadJson)

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

