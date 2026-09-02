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
    /**
     * Blocks required on top of an anchor's block before it can be read back, unless the
     * `minConfirmations` option overrides it.
     *
     * Chain-level policy rather than a global constant: a single-node development chain mines a
     * block per transaction and never re-orgs, so requiring a confirmation there would reject an
     * anchor that was just written. Public chains keep the default.
     */
    val defaultMinConfirmations: Long = AbstractEvmAnchorClient.DEFAULT_MIN_CONFIRMATIONS,
)

/**
 * Shared base class for EVM-compatible blockchain anchor clients.
 *
 * Anchoring on an EVM chain is identical everywhere: the payload is carried as
 * calldata on a zero-value self-send, signed locally with EIP-155 replay
 * protection (the signature encodes [EvmChainConfig.numericChainId]) and
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
 * - EIP-155 raw-transaction build/sign/send ([submitTransaction], [signWithChainId])
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
) : AbstractBlockchainAnchorClient(chainId, options),
    java.io.Closeable {
    /** The resolved JSON-RPC endpoint (the `rpcUrl` option or the chain default). */
    protected val rpcUrl: String =
        requireTransportSecurity(options["rpcUrl"] as? String ?: chain.defaultRpcUrl)

    /** The web3j client for [rpcUrl]. Shut down via [close]. */
    protected val web3j: Web3j = Web3j.build(HttpService(rpcUrl))

    /**
     * Signing credentials parsed from the `privateKey` option, or null when the
     * client is read-only. A present-but-invalid private key is a configuration
     * error and fails closed instead of silently degrading to the in-memory
     * test fallback.
     */
    protected val credentials: org.web3j.crypto.Credentials? =
        run {
            val privateKeyHex = options["privateKey"] as? String
            when {
                privateKeyHex != null ->
                    try {
                        org.web3j.crypto.Credentials
                            .create(privateKeyHex.removePrefix("0x"))
                    } catch (e: Exception) {
                        throw BlockchainException.ConfigurationFailed(
                            chainId = chainId,
                            configKey = "privateKey",
                            reason = "Invalid ${chain.blockchainName} private key: ${e.message ?: "Unknown error"}",
                            cause = e,
                        )
                    }
                chain.credentialsRequired -> throw BlockchainException.ConfigurationFailed(
                    chainId = chainId,
                    configKey = "privateKey",
                    reason = "privateKey is required for ${this::class.java.simpleName}",
                )
                else -> null
            }
        }

    /**
     * The numeric chain id every transaction from this client is signed with
     * (EIP-155 replay protection). Always [EvmChainConfig.numericChainId].
     */
    val eip155ChainId: Long get() = chain.numericChainId

    /**
     * Rejects a plaintext JSON-RPC endpoint on a public host.
     *
     * Everything this client does crosses that connection: the signed raw transaction on the way
     * out, and on the way back the receipt and calldata a verifier treats as the anchored payload.
     * Over plaintext both are readable and rewritable in transit. The chain defaults are https, but
     * the `rpcUrl` option overrides them with no check at all.
     *
     * `http` stays available for loopback and private-range hosts, where a local or in-cluster
     * development node is the normal case and TLS buys nothing.
     */
    private fun requireTransportSecurity(url: String): String =
        try {
            // Delegated so the locality decision lives in one place. The previous inline version
            // asked PrivateNetworkGuard.rejectionReason() and read "has a reason" as "is local" —
            // but that reports a reason both for a private host and for one that cannot be
            // resolved, so an unresolvable public host was treated as local and got plaintext.
            // The shared helper establishes locality positively instead.
            org.trustweave.core.net.TransportSecurity.requireSecureForPublicHosts(
                url,
                "Signed transactions and the anchored payload",
            )
        } catch (e: IllegalArgumentException) {
            throw BlockchainException.ConfigurationFailed(
                chainId = "eip155:${chain.numericChainId}",
                configKey = "rpcUrl",
                reason = e.message ?: "Refusing an insecure JSON-RPC endpoint",
                cause = e,
            )
        }

    override fun canSubmitTransaction(): Boolean = credentials != null

    override suspend fun submitTransactionToBlockchain(payloadBytes: ByteArray): String = submitTransaction(payloadBytes).transactionHash

    override suspend fun readTransactionFromBlockchain(txHash: String): AnchorResult {
        val ethGetTransactionReceipt = web3j.ethGetTransactionReceipt(txHash).send()
        if (!ethGetTransactionReceipt.transactionReceipt.isPresent) {
            throw TrustWeaveException.NotFound(resource = "Transaction receipt not found: $txHash")
        }

        val receipt = ethGetTransactionReceipt.transactionReceipt.get()
        requireSameTransaction(requested = txHash, returned = receipt.transactionHash, source = "receipt")

        val tx =
            web3j
                .ethGetTransactionByHash(txHash)
                .send()
                .transaction
                .orElse(null)
                ?: throw TrustWeaveException.NotFound(resource = "Transaction not found: $txHash")
        requireSameTransaction(requested = txHash, returned = tx.hash, source = "transaction")

        requireBuried(txHash, receipt)

        val input = tx.input
        if (input == null || input.isEmpty() || input == "0x") {
            throw TrustWeaveException.NotFound(resource = "Transaction data not found: $txHash")
        }

        val dataBytes =
            org.web3j.utils.Numeric
                .hexStringToByteArray(input)
        val payloadJson = String(dataBytes, StandardCharsets.UTF_8)
        val payload = Json.parseToJsonElement(payloadJson)

        return AnchorResult(
            ref =
                buildAnchorRef(
                    txHash = txHash,
                    contract = getContractAddress(),
                ),
            payload = payload,
            mediaType = "application/json",
            timestamp = readBlockTimestamp(receipt),
        )
    }

    override fun getContractAddress(): String? = options["contractAddress"] as? String

    override fun buildExtraMetadata(mediaType: String): Map<String, String> =
        mapOf(
            "network" to chain.networkName,
            "mediaType" to mediaType,
        )

    override fun generateTestTxHash(): String = "0x${uniqueTestHashHex()}"

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
    protected open fun deriveGasLimit(
        data: ByteArray,
        from: String,
    ): BigInteger = EvmGas.txGasLimit(data)

    /**
     * Asks the node how much gas the anchor transaction (a data-carrying self-send
     * of [data] from/to [from]) needs via `eth_estimateGas`. Returns null when the
     * node reports an error or the call fails, letting callers fall back to
     * intrinsic-gas math.
     */
    protected fun tryEstimateGas(
        data: ByteArray,
        from: String,
    ): BigInteger? =
        try {
            val tx =
                org.web3j.protocol.core.methods.request.Transaction.createFunctionCallTransaction(
                    from,
                    null,
                    null,
                    null,
                    from, // Anchors are data-carrying self-sends
                    BigInteger.ZERO,
                    org.web3j.utils.Numeric
                        .toHexString(data),
                )
            val response = web3j.ethEstimateGas(tx).send()
            if (response.hasError() || response.result == null) null else response.amountUsed
        } catch (_: Exception) {
            null
        }

    /**
     * Builds, signs ([signWithChainId]) and submits a raw transaction carrying [data]
     * as calldata (zero-value self-send) and waits for on-chain confirmation.
     *
     * `eth_sendRawTransaction` only means the node accepted the tx into its pool —
     * the receipt wait ensures dropped or reverted txs are never reported as
     * successful anchors.
     *
     * @return the confirmed transaction receipt
     */
    protected suspend fun submitTransaction(data: ByteArray): TransactionReceipt {
        val creds =
            credentials
                ?: throw IllegalStateException("Credentials not configured. Provide 'privateKey' in options.")

        val gasPrice = web3j.ethGasPrice().send().gasPrice
        // PENDING (not LATEST) so rapid successive anchors don't reuse a nonce.
        val nonce =
            web3j
                .ethGetTransactionCount(creds.address, DefaultBlockParameterName.PENDING)
                .send()
                .transactionCount

        val gasLimit = deriveGasLimit(data, creds.address)
        val rawTransaction =
            org.web3j.crypto.RawTransaction.createTransaction(
                nonce,
                gasPrice,
                gasLimit,
                creds.address, // Send to self
                BigInteger.ZERO,
                org.web3j.utils.Numeric
                    .toHexString(data),
            )

        val signedTransaction = signWithChainId(rawTransaction, creds)
        val hexValue =
            org.web3j.utils.Numeric
                .toHexString(signedTransaction)

        val ethSendTransaction = web3j.ethSendRawTransaction(hexValue).send()
        if (ethSendTransaction.hasError()) {
            val error = ethSendTransaction.error
            throw BlockchainException.TransactionFailed(
                chainId = chainId,
                txHash = null,
                operation = "submitTransaction",
                payloadSize = data.size.toLong(),
                gasUsed = gasLimit.toLong(),
                reason = "Transaction failed: ${error?.message ?: "Unknown error"}",
            )
        }

        return waitForReceipt(ethSendTransaction.transactionHash, data.size.toLong())
    }

    /**
     * Signs [rawTransaction] with EIP-155 replay protection: the signature's
     * recovery value encodes [eip155ChainId] (`v = chainId * 2 + 35 + recId`),
     * so the signed bytes are valid ONLY on this chain and cannot be replayed
     * on another EVM network where the sender has funds.
     *
     * Never use the chain-agnostic legacy overload
     * `TransactionEncoder.signMessage(rawTransaction, credentials)` — its
     * signature (v = 27/28) is replayable on every EVM chain.
     */
    internal fun signWithChainId(
        rawTransaction: org.web3j.crypto.RawTransaction,
        creds: org.web3j.crypto.Credentials,
    ): ByteArray =
        org.web3j.crypto.TransactionEncoder
            .signMessage(rawTransaction, chain.numericChainId, creds)

    /**
     * Polls `eth_getTransactionReceipt` until the transaction is mined, bounded by
     * [confirmationTimeoutMs] (option [OPTION_CONFIRMATION_TIMEOUT_MS]). Throws
     * [BlockchainException.TransactionFailed] on revert or timeout.
     */
    protected suspend fun waitForReceipt(
        txHash: String,
        payloadSize: Long,
    ): TransactionReceipt {
        val deadline = System.currentTimeMillis() + confirmationTimeoutMs
        while (true) {
            val receipt =
                web3j
                    .ethGetTransactionReceipt(txHash)
                    .send()
                    .transactionReceipt
                    .orElse(null)
            if (receipt != null) {
                if (!receipt.isStatusOK) {
                    throw BlockchainException.TransactionFailed(
                        chainId = chainId,
                        txHash = txHash,
                        operation = "submitTransaction",
                        payloadSize = payloadSize,
                        gasUsed =
                            try {
                                receipt.gasUsed?.toLong()
                            } catch (_: Exception) {
                                null
                            },
                        reason = "Transaction reverted on chain (status=${receipt.status})",
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
                    reason =
                        "Transaction not confirmed within $confirmationTimeoutMs ms " +
                            "(configure via '$OPTION_CONFIRMATION_TIMEOUT_MS' option)",
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
    protected fun computeActualFee(receipt: TransactionReceipt): TokenAmount? =
        try {
            val gasUsed = receipt.gasUsed ?: BigInteger.ZERO
            val effectivePrice =
                receipt.effectiveGasPrice
                    ?.let {
                        org.web3j.utils.Numeric
                            .decodeQuantity(it)
                    }
                    ?: web3j
                        .ethGetTransactionByHash(receipt.transactionHash)
                        .send()
                        .transaction
                        .orElse(null)
                        ?.gasPrice
                    ?: BigInteger.ZERO
            TokenAmount(chainId, AssetRef.Native, gasUsed.multiply(effectivePrice))
        } catch (_: Exception) {
            null
        }

    /**
     * Asserts the node handed back the transaction that was asked for.
     *
     * `verifyAnchor` treats whatever this read returns as what was anchored, and the answer comes
     * from one RPC endpoint. A node that is compromised, buggy, or simply pointed at a forked chain
     * can answer with a different transaction, and its payload would then be compared against the
     * caller's — so the identity is checked rather than assumed. Hex casing is not significant.
     */
    private fun requireSameTransaction(
        requested: String,
        returned: String?,
        source: String,
    ) {
        if (returned == null || !returned.equals(requested, ignoreCase = true)) {
            throw BlockchainException.TransactionFailed(
                chainId = chainId,
                txHash = requested,
                operation = "readTransaction",
                reason =
                    "RPC returned a $source for a different transaction ($returned); " +
                        "refusing to read an anchor from it",
            )
        }
    }

    /**
     * Asserts the containing block has at least [minConfirmations] blocks on top of it.
     *
     * A transaction in the current head block is not settled — a re-org of depth one removes it,
     * and an anchor verified against it would later refer to nothing. The depth is configurable
     * via [OPTION_MIN_CONFIRMATIONS]; the default of
     * [DEFAULT_MIN_CONFIRMATIONS] only excludes the tip, which is the cheapest useful guard.
     * Deployments anchoring high-value data should raise it to their chain's usual settlement
     * depth, and may set 0 to restore the previous unchecked behaviour.
     */
    private fun requireBuried(
        txHash: String,
        receipt: TransactionReceipt,
    ) {
        val required = minConfirmations
        if (required <= 0) return

        val blockNumber =
            receipt.blockNumber
                ?: throw BlockchainException.TransactionFailed(
                    chainId = chainId,
                    txHash = txHash,
                    operation = "readTransaction",
                    reason = "Transaction is not in a block yet; cannot confirm it is settled",
                )
        val head = web3j.ethBlockNumber().send().blockNumber
        val confirmations = head.subtract(blockNumber).toLong()
        if (confirmations < required) {
            throw BlockchainException.TransactionFailed(
                chainId = chainId,
                txHash = txHash,
                operation = "readTransaction",
                reason =
                    "Transaction has $confirmations confirmation(s) at block $blockNumber " +
                        "(head $head); $required required. Configure via " +
                        "'$OPTION_MIN_CONFIRMATIONS'",
            )
        }
    }

    /**
     * Resolves the timestamp of the block containing [receipt] (one extra RPC call);
     * returns null if it cannot be resolved, rather than fabricating one.
     */
    protected fun readBlockTimestamp(receipt: TransactionReceipt): Long? =
        try {
            receipt.blockNumber?.let { blockNumber ->
                web3j
                    .ethGetBlockByNumber(DefaultBlockParameter.valueOf(blockNumber), false)
                    .send()
                    .block
                    ?.timestamp
                    ?.toLong()
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

    /** Blocks required on top of an anchor's block before it is read (see [OPTION_MIN_CONFIRMATIONS]). */
    private val minConfirmations: Long
        get() =
            when (val value = options[OPTION_MIN_CONFIRMATIONS]) {
                is Number -> value.toLong()
                is String -> value.toLongOrNull() ?: chain.defaultMinConfirmations
                else -> chain.defaultMinConfirmations
            }

    public companion object {
        /**
         * Blocks that must sit on top of an anchor's block before [readTransactionFromBlockchain]
         * will read it. 0 disables the check.
         */
        public const val OPTION_MIN_CONFIRMATIONS: String = "minConfirmations"

        /** Excludes only the head block, where a depth-one re-org still removes the transaction. */
        public const val DEFAULT_MIN_CONFIRMATIONS: Long = 1L
    }
}
