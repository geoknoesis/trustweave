package org.trustweave.anchor.evm

import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.trustweave.anchor.AbstractBlockchainAnchorClient
import org.trustweave.anchor.AnchorResult
import org.trustweave.anchor.exceptions.BlockchainException
import org.trustweave.anchor.payment.AssetRef
import org.trustweave.anchor.payment.TokenAmount
import org.trustweave.core.exception.TrustWeaveException
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.protocol.http.HttpService
import org.web3j.tx.RawTransactionManager
import org.web3j.tx.TransactionManager
import java.math.BigInteger
import java.nio.charset.StandardCharsets

/**
 * Static, per-instance configuration of an EVM chain, resolved by the concrete
 * plugin (typically in its companion) BEFORE the base constructor runs, so
 * chain-id validation happens before any client state is built.
 *
 * @param numericChainId The EIP-155 numeric chain id used for transaction signing
 * @param defaultRpcUrl RPC endpoint used when the `rpcUrl` option is absent
 * @param blockchainName Human-readable chain name for error messages
 * @param networkName Value of the `network` key in [org.trustweave.anchor.AnchorRef.extra]
 * @param credentialsRequired When true, a missing `privateKey` option fails
 *   construction (e.g. Ganache) instead of producing a read-only client
 */
data class EvmChainConfig(
    val numericChainId: Long,
    val defaultRpcUrl: String,
    val blockchainName: String,
    val networkName: String,
    val credentialsRequired: Boolean = false,
)

/**
 * Shared base class for EVM-compatible blockchain anchor clients.
 *
 * Anchoring on an EVM chain is identical everywhere: the payload is carried as
 * calldata on a zero-value self-send, signed locally (legacy raw transaction) and
 * submitted via `eth_sendRawTransaction`, then confirmed by polling
 * `eth_getTransactionReceipt`. This class holds that whole pipeline once:
 *
 * - web3j client construction (`rpcUrl` option, falling back to
 *   [EvmChainConfig.defaultRpcUrl])
 * - credential parsing — a present-but-invalid `privateKey` fails closed with
 *   [BlockchainException.ConfigurationFailed] carrying the parse failure as cause
 * - PENDING-nonce retrieval so rapid successive anchors never reuse a nonce
 * - gas-limit derivation via the overridable [deriveGasLimit] strategy
 *   (default: intrinsic calldata gas via [EvmGas.txGasLimit]; chains where
 *   `eth_estimateGas` is authoritative override it using [tryEstimateGas])
 * - legacy raw-transaction build/sign/send ([submitTransaction])
 * - receipt polling bounded by the confirmation options ([waitForReceipt])
 * - receipt-based fee computation ([computeActualFee])
 * - payload reads via `eth_getTransactionByHash` with real block timestamps
 *   ([readTransactionFromBlockchain])
 *
 * Subclasses provide only chain identity (validation + [EvmChainConfig]) and any
 * chain-specific behavior (e.g. the Ethereum plugin's payment plane).
 *
 * **Options** (in addition to [AbstractBlockchainAnchorClient]'s):
 * - `rpcUrl` (String): JSON-RPC endpoint; defaults to [EvmChainConfig.defaultRpcUrl]
 * - `privateKey` (String): hex private key, with or without `0x` prefix; required
 *   for real transactions
 * - `contractAddress` (String): optional registry contract recorded on anchor refs
 */
abstract class AbstractEvmAnchorClient(
    chainId: String,
    options: Map<String, Any?>,
    protected val chain: EvmChainConfig,
) : AbstractBlockchainAnchorClient(chainId, options), java.io.Closeable {

    /** The resolved JSON-RPC endpoint (the `rpcUrl` option or the chain default). */
    protected val rpcUrl: String = options["rpcUrl"] as? String ?: chain.defaultRpcUrl

    /** The web3j client for [rpcUrl]. Shut down via [close]. */
    protected val web3j: Web3j = Web3j.build(HttpService(rpcUrl))

    /**
     * Signing credentials parsed from the `privateKey` option, or null when the
     * client is read-only. A present-but-invalid private key is a configuration
     * error and fails closed instead of silently degrading to the in-memory
     * test fallback.
     */
    protected val credentials: org.web3j.crypto.Credentials? = run {
        val privateKeyHex = options["privateKey"] as? String
        when {
            privateKeyHex != null -> try {
                org.web3j.crypto.Credentials.create(privateKeyHex.removePrefix("0x"))
            } catch (e: Exception) {
                throw BlockchainException.ConfigurationFailed(
                    chainId = chainId,
                    configKey = "privateKey",
                    reason = "Invalid ${chain.blockchainName} private key: ${e.message ?: "Unknown error"}",
                    cause = e
                )
            }
            chain.credentialsRequired -> throw BlockchainException.ConfigurationFailed(
                chainId = chainId,
                configKey = "privateKey",
                reason = "privateKey is required for ${this::class.java.simpleName}"
            )
            else -> null
        }
    }

    /** EIP-155 transaction manager for [credentials], or null when read-only. */
    protected val transactionManager: TransactionManager? =
        credentials?.let { RawTransactionManager(web3j, it, chain.numericChainId) }

    override fun canSubmitTransaction(): Boolean = credentials != null

    override suspend fun submitTransactionToBlockchain(payloadBytes: ByteArray): String =
        submitTransaction(payloadBytes).transactionHash

    override suspend fun readTransactionFromBlockchain(txHash: String): AnchorResult {
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

        return AnchorResult(
            ref = buildAnchorRef(
                txHash = txHash,
                contract = getContractAddress()
            ),
            payload = payload,
            mediaType = "application/json",
            timestamp = readBlockTimestamp(receipt)
        )
    }

    override fun getContractAddress(): String? {
        return options["contractAddress"] as? String
    }

    override fun buildExtraMetadata(mediaType: String): Map<String, String> {
        return mapOf(
            "network" to chain.networkName,
            "mediaType" to mediaType
        )
    }

    override fun generateTestTxHash(): String {
        return "0x${uniqueTestHashHex()}"
    }

    override fun getBlockchainName(): String = chain.blockchainName

    /**
     * Gas limit for an anchor transaction carrying [data] as calldata, sent by [from].
     *
     * Default strategy: an anchor is a data-carrying value transfer, so its cost is
     * exactly the intrinsic calldata gas (+10% margin) — never a blanket multi-million
     * default limit. Chains whose gas model charges more than the Ethereum intrinsic
     * cost (Arbitrum Nitro, zkSync Era, …) override this with an
     * `eth_estimateGas`-primary strategy built on [tryEstimateGas].
     */
    protected open fun deriveGasLimit(data: ByteArray, from: String): BigInteger =
        EvmGas.txGasLimit(data)

    /**
     * Asks the node how much gas the anchor transaction (a data-carrying self-send
     * of [data] from/to [from]) needs via `eth_estimateGas`. Returns null when the
     * node reports an error or the call fails, letting callers fall back to
     * intrinsic-gas math.
     */
    protected fun tryEstimateGas(data: ByteArray, from: String): BigInteger? = try {
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

    /**
     * Builds, signs and submits a legacy raw transaction carrying [data] as calldata
     * (zero-value self-send) and waits for on-chain confirmation.
     *
     * `eth_sendRawTransaction` only means the node accepted the tx into its pool —
     * the receipt wait ensures dropped or reverted txs are never reported as
     * successful anchors.
     *
     * @return the confirmed transaction receipt
     */
    protected suspend fun submitTransaction(data: ByteArray): TransactionReceipt {
        val creds = credentials
            ?: throw IllegalStateException("Credentials not configured. Provide 'privateKey' in options.")

        val gasPrice = web3j.ethGasPrice().send().gasPrice
        // PENDING (not LATEST) so rapid successive anchors don't reuse a nonce.
        val nonce = web3j.ethGetTransactionCount(creds.address, DefaultBlockParameterName.PENDING)
            .send().transactionCount

        val gasLimit = deriveGasLimit(data, creds.address)
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

        return waitForReceipt(ethSendTransaction.transactionHash, data.size.toLong())
    }

    /**
     * Polls `eth_getTransactionReceipt` until the transaction is mined, bounded by
     * [confirmationTimeoutMs] (option [OPTION_CONFIRMATION_TIMEOUT_MS]). Throws
     * [BlockchainException.TransactionFailed] on revert or timeout.
     */
    protected suspend fun waitForReceipt(txHash: String, payloadSize: Long): TransactionReceipt {
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

    /**
     * Computes the fee actually paid from the confirmed [receipt].
     * `effectiveGasPrice` is populated on EIP-1559 chains; falls back to the
     * transaction's gas price otherwise. Returns null when the fee cannot be
     * resolved.
     */
    protected fun computeActualFee(receipt: TransactionReceipt): TokenAmount? = try {
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

    /**
     * Resolves the timestamp of the block containing [receipt] (one extra RPC call);
     * returns null if it cannot be resolved, rather than fabricating one.
     */
    protected fun readBlockTimestamp(receipt: TransactionReceipt): Long? = try {
        receipt.blockNumber?.let { blockNumber ->
            web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(blockNumber), false)
                .send().block?.timestamp?.toLong()
        }
    } catch (_: Exception) {
        null
    }

    override fun close() {
        try {
            web3j.shutdown()
        } catch (_: Exception) {
            // Ignore errors during shutdown
        }
    }
}
