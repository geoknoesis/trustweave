package org.trustweave.anchor.evm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
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
 * @param defaultRpcUrl RPC endpoint used when the `rpcUrl` option is absent. Blank
 *   ([EvmChainConfig.NO_DEFAULT_RPC_URL]) means there is no default and the `rpcUrl` option is
 *   required — used for mainnets, which must never silently fall back to a shared public
 *   endpoint whose answers a verifier would then trust
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
) {
    public companion object {
        /** [defaultRpcUrl] value meaning "no default: the `rpcUrl` option is required". */
        public const val NO_DEFAULT_RPC_URL: String = ""
    }
}

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
 * - PENDING-nonce retrieval, serialised per client by a mutex held from the nonce read to the send
 *   (one retry on "nonce too low"), so concurrent anchors from one account never reuse a nonce
 * - gas-limit derivation via the overridable [deriveGasLimit] strategy
 *   (default: intrinsic calldata gas via [EvmGas.txGasLimit]; chains where
 *   `eth_estimateGas` is authoritative override it using [tryEstimateGas])
 * - EIP-155 raw-transaction build/sign/send ([submitTransaction], [signWithChainId])
 * - receipt polling bounded by the confirmation options ([waitForReceipt])
 * - receipt-based fee computation ([computeActualFee])
 * - payload reads via `eth_getTransactionByHash` with real block timestamps
 *   ([readTransactionFromBlockchain])
 *
 * All JSON-RPC calls (web3j's `.send()` blocks the calling thread) run on [Dispatchers.IO].
 *
 * **Legacy digest envelopes.** Reading an anchor is chain-level; whether a digest envelope without
 * `canon = "JCS"` counts as verified is decided by [AbstractBlockchainAnchorClient.OPTION_REQUIRE_CANONICAL_ENVELOPE]
 * (default: accepted, with a once-per-client warning). Prefer `requireCanonicalEnvelope=true` for
 * deployments that never wrote legacy anchors.
 *
 * Subclasses provide only chain identity (validation + [EvmChainConfig]) and any
 * chain-specific behavior (e.g. the Ethereum plugin's payment plane).
 *
 * **Options** (in addition to [AbstractBlockchainAnchorClient]'s):
 * - `rpcUrl` (String): JSON-RPC endpoint; defaults to [EvmChainConfig.defaultRpcUrl]
 * - `privateKey` (String): hex private key, with or without `0x` prefix; required
 *   for real transactions
 * - `contractAddress` (String): optional registry contract recorded on anchor refs
 * - `expectedSender` (String): address that must have sent every anchor this client reads
 *   ([OPTION_EXPECTED_SENDER]); defaults to the address of the configured `privateKey` account.
 *   A client without credentials (verify-only) must set it, or explicitly opt in to
 *   `acceptAnySelfSend=true` ([OPTION_ACCEPT_ANY_SELF_SEND]); otherwise reads fail with a clear
 *   error, because any third party can self-send arbitrary calldata
 * - `expectedRecipient` (String): address every anchor this client reads must be sent to
 *   ([OPTION_EXPECTED_RECIPIENT]); unset means the anchor must be a self-send, the only
 *   shape [submitTransaction] produces
 */
abstract class AbstractEvmAnchorClient(
    chainId: String,
    options: Map<String, Any?>,
    protected val chain: EvmChainConfig,
) : AbstractBlockchainAnchorClient(chainId, options),
    java.io.Closeable {
    /** The resolved JSON-RPC endpoint (the `rpcUrl` option or the chain default). */
    protected val rpcUrl: String =
        requireTransportSecurity(
            (options["rpcUrl"] as? String)?.takeIf { it.isNotBlank() }
                ?: chain.defaultRpcUrl.takeIf { it.isNotBlank() }
                ?: throw BlockchainException.ConfigurationFailed(
                    chainId = chainId,
                    configKey = "rpcUrl",
                    reason =
                        "rpcUrl is required for ${chain.blockchainName} ${chain.networkName}: there is no " +
                            "default endpoint for this network. Configure your own (or your provider's) " +
                            "JSON-RPC URL; anchors read through it are trusted for verification.",
                ),
        )

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

    override suspend fun readTransactionFromBlockchain(txHash: String): AnchorResult =
        withContext(Dispatchers.IO) {
            val ethGetTransactionReceipt = web3j.ethGetTransactionReceipt(txHash).send()
            if (!ethGetTransactionReceipt.transactionReceipt.isPresent) {
                throw TrustWeaveException.NotFound(resource = "Transaction receipt not found: $txHash")
            }

            val receipt = ethGetTransactionReceipt.transactionReceipt.get()
            requireSameTransaction(requested = txHash, returned = receipt.transactionHash, source = "receipt")
            // A reverted transaction still carries its calldata, but nothing was anchored by it.
            if (!receipt.isStatusOK) {
                throw BlockchainException.TransactionFailed(
                    chainId = chainId,
                    txHash = txHash,
                    operation = "readTransaction",
                    reason = "Transaction reverted on chain (status=${receipt.status}); it is not an anchor",
                )
            }

            val tx =
                web3j
                    .ethGetTransactionByHash(txHash)
                    .send()
                    .transaction
                    .orElse(null)
                    ?: throw TrustWeaveException.NotFound(resource = "Transaction not found: $txHash")
            requireSameTransaction(requested = txHash, returned = tx.hash, source = "transaction")
            requireSameBlock(txHash, receipt, tx)
            requireExpectedParties(txHash, from = tx.from, to = tx.to)

            requireBuried(txHash, receipt)

            val input = tx.input
            if (input == null || input.isEmpty() || input == "0x") {
                throw TrustWeaveException.NotFound(resource = "Transaction data not found: $txHash")
            }

            val payload = parseAnchoredPayload(txHash, input)

            AnchorResult(
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

    /**
     * Decodes calldata as the JSON anchor payload. Calldata that is not valid UTF-8 or not valid JSON
     * (anyone can put arbitrary bytes on chain) is reported as a clear [BlockchainException.TransactionFailed],
     * never as a raw serialization error, so a verifier sees "this is not an anchor" rather than a
     * parser stack trace.
     */
    private fun parseAnchoredPayload(
        txHash: String,
        input: String,
    ): kotlinx.serialization.json.JsonElement {
        fun notAnAnchor(
            why: String,
            cause: Throwable?,
        ): Nothing =
            throw BlockchainException.TransactionFailed(
                chainId = chainId,
                txHash = txHash,
                operation = "readTransaction",
                reason = "Transaction calldata is not a JSON anchor payload ($why); it is not an anchor",
                cause = cause,
            )

        val dataBytes =
            try {
                org.web3j.utils.Numeric
                    .hexStringToByteArray(input)
            } catch (e: RuntimeException) {
                notAnAnchor("calldata is not valid hex", e)
            }
        val text =
            try {
                StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(dataBytes))
                    .toString()
            } catch (e: java.nio.charset.CharacterCodingException) {
                notAnAnchor("calldata is not valid UTF-8", e)
            }
        return try {
            Json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            notAnAnchor("calldata is not valid JSON", e)
        } catch (e: IllegalArgumentException) {
            notAnAnchor("calldata is not valid JSON", e)
        }
    }

    /**
     * Asserts the receipt and the transaction describe the same inclusion: both must name a block,
     * and the block hash and number must agree. One node answering with a receipt from one block and a
     * transaction from another (a reorg race, a buggy or malicious proxy) must not be read as one anchor.
     */
    private fun requireSameBlock(
        txHash: String,
        receipt: TransactionReceipt,
        tx: org.web3j.protocol.core.methods.response.Transaction,
    ) {
        fun reject(reason: String): Nothing =
            throw BlockchainException.TransactionFailed(
                chainId = chainId,
                txHash = txHash,
                operation = "readTransaction",
                reason = reason,
            )

        val receiptHash = receipt.blockHash
        val txBlockHash = tx.blockHash
        if (receiptHash.isNullOrBlank() || txBlockHash.isNullOrBlank()) {
            reject("Receipt or transaction does not name a containing block; refusing to treat it as a settled anchor")
        }
        if (!receiptHash.equals(txBlockHash, ignoreCase = true)) {
            reject("Receipt is in block $receiptHash but the transaction reports block $txBlockHash; refusing to read an anchor from it")
        }
        val receiptNumber = receipt.blockNumber
        val txNumber = tx.blockNumber
        if (receiptNumber == null || txNumber == null || receiptNumber != txNumber) {
            reject("Receipt is at block number $receiptNumber but the transaction reports $txNumber; refusing to read an anchor from it")
        }
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
    protected suspend fun submitTransaction(data: ByteArray): TransactionReceipt =
        withContext(Dispatchers.IO) {
            val creds =
                credentials
                    ?: throw IllegalStateException("Credentials not configured. Provide 'privateKey' in options.")

            // One account, one nonce sequence: the read of the PENDING count, the signature and the send
            // must not interleave with another submission from this client, or both would use the same
            // nonce and one anchor would replace (or be rejected as a duplicate of) the other. The lock
            // is released before the receipt wait, so successive anchors still overlap their confirmations.
            val txHash = nonceMutex.withLock { sendWithFreshNonce(data, creds) }
            waitForReceipt(txHash, data.size.toLong())
        }

    /** Held from the nonce read to the end of `eth_sendRawTransaction`; see [submitTransaction]. */
    private val nonceMutex = Mutex()

    /**
     * Reads the PENDING nonce, signs and sends; on "nonce too low" (another sender, or a node that
     * lagged, used the nonce) re-reads once and tries again. Any other error, or a second "too low", fails.
     */
    private fun sendWithFreshNonce(
        data: ByteArray,
        creds: org.web3j.crypto.Credentials,
    ): String {
        var attempt = 0
        while (true) {
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
            if (!ethSendTransaction.hasError()) return ethSendTransaction.transactionHash

            val error = ethSendTransaction.error
            if (attempt == 0 && NONCE_TOO_LOW.containsMatchIn(error?.message.orEmpty())) {
                attempt++
                continue
            }
            throw BlockchainException.TransactionFailed(
                chainId = chainId,
                txHash = null,
                operation = "submitTransaction",
                payloadSize = data.size.toLong(),
                gasUsed = gasLimit.toLong(),
                reason = "Transaction failed: ${error?.message ?: "Unknown error"}",
            )
        }
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
    ): TransactionReceipt =
        withContext(Dispatchers.IO) {
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
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    null
                                },
                            reason = "Transaction reverted on chain (status=${receipt.status})",
                        )
                    }
                    return@withContext receipt
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
            @Suppress("UNREACHABLE_CODE")
            error("unreachable: the polling loop only exits by returning or throwing")
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
     * Asserts the transaction was sent by the configured anchoring account and to the expected
     * recipient.
     *
     * Anyone can put arbitrary calldata on chain, so without these checks a third party could
     * publish a transaction with the right payload and have it accepted as "the" anchor. The
     * sender must equal [OPTION_EXPECTED_SENDER], or the client's own account when it has credentials. The recipient must equal
     * [OPTION_EXPECTED_RECIPIENT] when configured; otherwise the transaction must be a self-send
     * (`to == from`), which is the only shape this client writes. Address case is not significant.
     */
    private fun requireExpectedParties(
        txHash: String,
        from: String?,
        to: String?,
    ) {
        fun reject(reason: String): Nothing =
            throw BlockchainException.TransactionFailed(
                chainId = chainId,
                txHash = txHash,
                operation = "readTransaction",
                reason = reason,
            )

        if (from.isNullOrBlank()) reject("Transaction has no sender; refusing to treat it as an anchor")
        val senderToCheck = expectedSender ?: credentials?.address
        if (senderToCheck != null) {
            if (!from.equals(senderToCheck, ignoreCase = true)) {
                reject("Transaction was sent by $from, not the expected anchoring account $senderToCheck")
            }
        } else if (!acceptAnySelfSend) {
            reject(
                "No expected sender is known: this client has no signing credentials and no " +
                    "'$OPTION_EXPECTED_SENDER' option, so it cannot tell the anchoring account's " +
                    "transactions from a third party's. Set '$OPTION_EXPECTED_SENDER' to the anchoring " +
                    "account, or set '$OPTION_ACCEPT_ANY_SELF_SEND'=true to accept any sender explicitly",
            )
        }
        val expectedTo = expectedRecipient ?: from
        if (to == null || !to.equals(expectedTo, ignoreCase = true)) {
            val what = if (expectedRecipient != null) "the expected recipient" else "a self-send from $from"
            reject("Transaction is sent to $to, not $what ($expectedTo)")
        }
    }

    private val expectedSender: String?
        get() = (options[OPTION_EXPECTED_SENDER] as? String)?.takeIf { it.isNotBlank() }

    private val acceptAnySelfSend: Boolean
        get() =
            when (val v = options[OPTION_ACCEPT_ANY_SELF_SEND]) {
                is Boolean -> v
                is String -> v.equals("true", ignoreCase = true)
                else -> false
            }

    private val expectedRecipient: String?
        get() = (options[OPTION_EXPECTED_RECIPIENT] as? String)?.takeIf { it.isNotBlank() }

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
        private val NONCE_TOO_LOW = Regex("nonce.{0,16}too low", RegexOption.IGNORE_CASE)

        /**
         * Blocks that must sit on top of an anchor's block before [readTransactionFromBlockchain]
         * will read it. 0 disables the check.
         */
        public const val OPTION_MIN_CONFIRMATIONS: String = "minConfirmations"

        /**
         * Address (0x-hex) that must be the sender of every anchor read by this client. Set it to
         * the anchoring account when verifying anchors; unset falls back to the configured signing account
         * (see [OPTION_ACCEPT_ANY_SELF_SEND] for verify-only clients).
         */
        public const val OPTION_EXPECTED_SENDER: String = "expectedSender"

        /**
         * Explicit opt-in (`true`) for a verify-only client (no `privateKey`, no
         * [OPTION_EXPECTED_SENDER]) to accept an anchor sent by any account. Insecure: anyone can
         * self-send a transaction carrying a chosen payload. Ignored when a sender is known.
         */
        public const val OPTION_ACCEPT_ANY_SELF_SEND: String = "acceptAnySelfSend"

        /**
         * Address (0x-hex) that must be the recipient of every anchor read by this client. Unset
         * requires a self-send, which is what this client writes.
         */
        public const val OPTION_EXPECTED_RECIPIENT: String = "expectedRecipient"

        /** Excludes only the head block, where a depth-one re-org still removes the transaction. */
        public const val DEFAULT_MIN_CONFIRMATIONS: Long = 1L
    }
}
