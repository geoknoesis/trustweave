package org.trustweave.anchor.zksync

import org.trustweave.anchor.*
import org.trustweave.anchor.evm.EvmGas
import org.trustweave.anchor.exceptions.BlockchainException

import org.trustweave.core.exception.TrustWeaveException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.protocol.http.HttpService
import org.web3j.tx.RawTransactionManager
import org.web3j.tx.TransactionManager
import java.math.BigInteger
import java.nio.charset.StandardCharsets

/**
 * zkSync Era blockchain anchor client implementation.
 *
 * Supports zkSync Era mainnet and Sepolia testnet chains.
 * Uses Ethereum-compatible transaction data fields to store payload data.
 *
 * Chain ID format: "eip155:<chain-id>"
 * Examples:
 * - "eip155:324" (zkSync Era mainnet)
 * - "eip155:300" (zkSync Era Sepolia testnet)
 *
 * **Example:**
 * ```kotlin
 * val client = ZkSyncBlockchainAnchorClient(
 *     chainId = "eip155:324",
 *     options = mapOf(
 *         "rpcUrl" to "https://mainnet.era.zksync.io",
 *         "privateKey" to "0x..."
 *     )
 * )
 * ```
 */
class ZkSyncBlockchainAnchorClient(
    chainId: String,
    options: Map<String, Any?> = emptyMap()
) : AbstractBlockchainAnchorClient(chainId, options), java.io.Closeable {

    companion object {
        const val MAINNET = "eip155:324"  // zkSync Era mainnet
        const val SEPOLIA = "eip155:300" // zkSync Era Sepolia testnet

        // Network RPC endpoints
        private const val MAINNET_RPC_URL = "https://mainnet.era.zksync.io"
        private const val SEPOLIA_RPC_URL = "https://sepolia.era.zksync.io"

        // Safety margin applied on top of eth_estimateGas, in percent. zkSync Era's
        // actual gas usage moves with L1 gas prices and pubdata costs between
        // estimation and execution, so leave generous headroom.
        internal const val ESTIMATE_GAS_MARGIN_PERCENT = 20

        private val logger = LoggerFactory.getLogger(ZkSyncBlockchainAnchorClient::class.java)

        /**
         * Derives the transaction gas limit from an `eth_estimateGas` result:
         * estimate +[ESTIMATE_GAS_MARGIN_PERCENT]% margin, floored at the Ethereum
         * intrinsic gas (the protocol minimum for tx validity).
         *
         * When estimation failed ([estimatedGas] is null), falls back to
         * [EvmGas.txGasLimit] — which is very likely too low on zkSync Era, whose
         * gas model charges bootloader/validation overhead and pubdata costs inside
         * the gas limit (a simple transfer needs ~10^5+ gas) — and logs a warning.
         */
        internal fun gasLimitFrom(estimatedGas: BigInteger?, data: ByteArray): BigInteger {
            if (estimatedGas == null) {
                val fallback = EvmGas.txGasLimit(data)
                logger.warn(
                    "eth_estimateGas failed; falling back to Ethereum intrinsic gas limit {} — " +
                        "this is likely too low on zkSync Era (bootloader/validation overhead and " +
                        "pubdata costs are charged inside the gas limit) and the transaction may be rejected.",
                    fallback
                )
                return fallback
            }
            return maxOf(
                EvmGas.withMargin(estimatedGas, ESTIMATE_GAS_MARGIN_PERCENT),
                EvmGas.intrinsicGas(data)
            )
        }
    }

    private val web3j: Web3j
    private val transactionManager: TransactionManager?
    private val credentials: org.web3j.crypto.Credentials?

    init {
        require(chainId.startsWith("eip155:")) {
            "Invalid chain ID for zkSync: $chainId"
        }
        val chainIdNum = chainId.substringAfter(":").toIntOrNull()
        require(chainIdNum == 324 || chainIdNum == 300) {
            "Unsupported zkSync chain ID: $chainId. Use 'eip155:324' (mainnet) or 'eip155:300' (Sepolia testnet)"
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
                    reason = "Invalid zkSync private key: ${e.message ?: "Unknown error"}",
                    cause = e
                )
            }
        }
        credentials = creds
        transactionManager = creds?.let {
            val chainIdLong = when (chainId) {
                MAINNET -> 324L
                SEPOLIA -> 300L
                else -> throw IllegalArgumentException("Unsupported chain: $chainId")
            }
            RawTransactionManager(web3j, it, chainIdLong)
        }
    }

    override protected fun canSubmitTransaction(): Boolean {
        return transactionManager != null
    }

    override protected suspend fun submitTransactionToBlockchain(
        payloadBytes: ByteArray
    ): String {
        return submitTransaction(payloadBytes)
    }

    override protected suspend fun readTransactionFromBlockchain(txHash: String): AnchorResult {
        return readTransactionFromBlockchainImpl(txHash)
    }

    private suspend fun submitTransaction(data: ByteArray): String {
        val creds = credentials ?: throw IllegalStateException("Credentials not configured. Provide 'privateKey' in options.")

        val gasPrice = web3j.ethGasPrice().send().gasPrice
        // PENDING (not LATEST) so rapid successive anchors don't reuse a nonce.
        val nonce = web3j.ethGetTransactionCount(creds.address, org.web3j.protocol.core.DefaultBlockParameterName.PENDING)
            .send().transactionCount

        // zkSync Era's gas model charges bootloader/validation overhead and pubdata
        // costs inside the gas limit, so the Ethereum intrinsic calldata gas is far
        // too low here. eth_estimateGas is authoritative; add headroom for L2
        // variability and fall back to intrinsic gas only if estimation fails.
        val gasLimit = estimateGasLimit(data, creds.address)
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
                reason = "Transaction failed: ${error?.message ?: "Unknown error"}",
                chainId = chainId,
                txHash = null,
                operation = "submitTransaction",
                payloadSize = data.size.toLong(),
                gasUsed = gasLimit.toLong()
            )
        }

        // eth_sendRawTransaction only means the node accepted the tx into its pool —
        // wait for on-chain confirmation so dropped or reverted txs are never
        // reported as successful anchors.
        return waitForReceipt(ethSendTransaction.transactionHash, data.size.toLong()).transactionHash
    }

    /**
     * Asks the node how much gas the anchor transaction needs via `eth_estimateGas`
     * (the authoritative source on zkSync Era) and derives the gas limit through
     * [gasLimitFrom]; a failed or empty estimation yields the intrinsic-gas fallback.
     */
    private fun estimateGasLimit(data: ByteArray, from: String): BigInteger {
        val estimated = try {
            val tx = org.web3j.protocol.core.methods.request.Transaction.createFunctionCallTransaction(
                from,
                null,
                null,
                null,
                from, // Anchors are data-carrying self-sends
                BigInteger.ZERO,
                org.web3j.utils.Numeric.toHexString(data)
            )
            val response = web3j.ethEstimateGas(tx).send()
            if (response.hasError() || response.result == null) null else response.amountUsed
        } catch (_: Exception) {
            null
        }
        return gasLimitFrom(estimated, data)
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

    override protected fun getContractAddress(): String? {
        return options["contractAddress"] as? String
    }

    override protected fun buildExtraMetadata(mediaType: String): Map<String, String> {
        val network = when (chainId) {
            MAINNET -> "zksync-era-mainnet"
            SEPOLIA -> "zksync-era-sepolia"
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
        return "zkSync Era"
    }

    override fun close() {
        try {
            web3j.shutdown()
        } catch (e: Exception) {
            // Ignore errors during shutdown
        }
    }
}

