package org.trustweave.anchor.ganache

import org.trustweave.anchor.*
import org.trustweave.anchor.exceptions.BlockchainException
import org.trustweave.anchor.options.GanacheOptions

import org.trustweave.core.exception.TrustWeaveException
import kotlinx.serialization.json.*
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.tx.RawTransactionManager
import org.web3j.tx.TransactionManager
import java.math.BigInteger
import java.nio.charset.StandardCharsets

/**
 * Ganache (local Ethereum) blockchain anchor client implementation.
 *
 * Supports local Ethereum networks via Ganache.
 * Uses Ethereum transaction data fields to store payload data.
 *
 * For testing, use a fixed mnemonic to get deterministic accounts:
 * ganache --port 8545 --mnemonic "test test test test test test test test test test test junk"
 *
 * **Resource Management**: This client implements `Closeable` to properly clean up
 * Web3j resources. Always use `use {}` or call `close()` when done:
 *
 * ```
 * GanacheBlockchainAnchorClient(chainId, options).use { client ->
 *     // Use client
 * }
 * ```
 *
 * **Type-Safe Options**: Use [GanacheOptions] for type-safe configuration:
 * ```
 * val options = GanacheOptions(
 *     rpcUrl = "http://localhost:8545",
 *     privateKey = "0x..."
 * )
 * val client = GanacheBlockchainAnchorClient(chainId, options)
 * ```
 */
class GanacheBlockchainAnchorClient(
    chainId: String,
    options: Map<String, Any?> = emptyMap()
) : AbstractBlockchainAnchorClient(chainId, options), java.io.Closeable {

    /**
     * Convenience constructor using type-safe [GanacheOptions].
     */
    constructor(chainId: String, options: GanacheOptions) : this(chainId, options.toMap())

    companion object {
        const val LOCAL = "eip155:1337"  // Ganache default chain ID

        // Default Ganache RPC endpoint
        private const val DEFAULT_RPC_URL = "http://localhost:8545"
    }

    private val web3j: Web3j
    private val transactionManager: TransactionManager
    private val credentials: org.web3j.crypto.Credentials

    init {
        require(chainId.startsWith("eip155:")) {
            "Invalid chain ID for Ethereum/Ganache: $chainId"
        }

        // Initialize Web3j client
        val rpcUrl = options["rpcUrl"] as? String ?: DEFAULT_RPC_URL
        web3j = Web3j.build(HttpService(rpcUrl))

        // Initialize transaction manager - private key is required
        val privateKeyHex = options["privateKey"] as? String
            ?: throw BlockchainException.ConfigurationFailed(
                chainId = chainId,
                configKey = "privateKey",
                reason = "privateKey is required for GanacheBlockchainAnchorClient"
            )

        credentials = try {
            org.web3j.crypto.Credentials.create(privateKeyHex.removePrefix("0x"))
        } catch (e: Exception) {
            throw BlockchainException.ConfigurationFailed(
                chainId = chainId,
                configKey = "privateKey",
                reason = "Invalid private key format: ${e.message ?: "Unknown error"}"
            ).apply { initCause(e) }
        }

        val chainIdNum = chainId.substringAfter(":").toLongOrNull() ?: 1337L
        transactionManager = RawTransactionManager(web3j, credentials, chainIdNum)
    }

    override protected fun canSubmitTransaction(): Boolean {
        return true // Ganache always requires credentials
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
        return mapOf(
            "network" to "ganache-local",
            "mediaType" to mediaType
        )
    }

    override protected fun generateTestTxHash(): String {
        return "ganache_test_${System.currentTimeMillis()}_${(0..1000000).random()}"
    }

    override protected fun getBlockchainName(): String {
        return "Ganache"
    }

    private suspend fun submitTransaction(data: ByteArray): String {
        val gasPrice = web3j.ethGasPrice().send().gasPrice
        val nonce = web3j.ethGetTransactionCount(
            credentials.address,
            org.web3j.protocol.core.DefaultBlockParameterName.LATEST
        ).send().transactionCount

        // Use a higher gas limit for data transactions
        // Ganache container is configured with --gasLimit 12000000
        // Use block gas limit (which should be 12M) or fallback to 10M
        val blockGasLimit = try {
            web3j.ethGetBlockByNumber(
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST,
                false
            ).send().block.gasLimit
        } catch (e: Exception) {
            // Fallback to 10M if we can't get block gas limit
            java.math.BigInteger.valueOf(10_000_000)
        }

        // Use block gas limit, but ensure it's at least 8M
        val minGasLimit = java.math.BigInteger.valueOf(8_000_000)
        val gasLimit = if (blockGasLimit.compareTo(minGasLimit) > 0) blockGasLimit else minGasLimit

        val rawTransaction = org.web3j.crypto.RawTransaction.createTransaction(
            nonce,
            gasPrice,
            gasLimit,
            credentials.address, // Send to self
            BigInteger.ZERO,
            org.web3j.utils.Numeric.toHexString(data)
        )

        val signedTransaction = org.web3j.crypto.TransactionEncoder.signMessage(rawTransaction, credentials)
        val hexValue = org.web3j.utils.Numeric.toHexString(signedTransaction)

        val ethSendTransaction = web3j.ethSendRawTransaction(hexValue).send()
        if (ethSendTransaction.hasError()) {
            val error = ethSendTransaction.error
            throw BlockchainException.TransactionFailed(
                reason = "Transaction failed: ${error?.message ?: "Unknown error"}",
                chainId = chainId,
                txHash = null,
                operation = "submitTransaction"
            )
        }

        return ethSendTransaction.transactionHash
    }

    private suspend fun readTransactionFromBlockchainImpl(txHash: String): AnchorResult {
        val ethGetTransactionReceipt = web3j.ethGetTransactionReceipt(txHash).send()
        if (!ethGetTransactionReceipt.transactionReceipt.isPresent) {
            throw TrustWeaveException.NotFound(resource = "Transaction receipt not found: $txHash")
        }

        val receipt = ethGetTransactionReceipt.transactionReceipt.get()
        val tx = web3j.ethGetTransactionByHash(txHash).send().transaction.orElse(null)
            ?: throw TrustWeaveException.NotFound(resource = "Transaction not found: $txHash")

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

