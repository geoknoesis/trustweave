package org.trustweave.anchor.ganache

import org.trustweave.anchor.*
import org.trustweave.anchor.evm.EvmGas
import org.trustweave.anchor.exceptions.BlockchainException
import org.trustweave.anchor.options.GanacheOptions

import org.trustweave.core.exception.TrustWeaveException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.core.methods.response.TransactionReceipt
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
                reason = "Invalid private key format: ${e.message ?: "Unknown error"}",
                cause = e
            )
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
        return "ganache_test_${uniqueTestHashSuffix()}"
    }

    override protected fun getBlockchainName(): String {
        return "Ganache"
    }

    private suspend fun submitTransaction(data: ByteArray): String {
        val gasPrice = web3j.ethGasPrice().send().gasPrice
        // PENDING (not LATEST) so rapid successive anchors don't reuse a nonce.
        val nonce = web3j.ethGetTransactionCount(
            credentials.address,
            org.web3j.protocol.core.DefaultBlockParameterName.PENDING
        ).send().transactionCount

        // An anchor is a data-carrying value transfer: its cost is the intrinsic
        // calldata gas (+10% margin), not the block gas limit.
        val gasLimit = EvmGas.txGasLimit(data)

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

        // eth_sendRawTransaction only means the node accepted the tx into its pool —
        // wait for on-chain confirmation so dropped or reverted txs are never
        // reported as successful anchors (Ganache instamines, so this is fast).
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

