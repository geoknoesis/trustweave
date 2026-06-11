package org.trustweave.anchor.algorand

import com.algorand.algosdk.account.Account
import com.algorand.algosdk.transaction.SignedTransaction
import com.algorand.algosdk.transaction.Transaction
import com.algorand.algosdk.transaction.TxGroup
import com.algorand.algosdk.util.Encoder
import com.algorand.algosdk.v2.client.common.AlgodClient
import com.algorand.algosdk.v2.client.common.IndexerClient
import com.algorand.algosdk.v2.client.model.PendingTransactionResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.trustweave.anchor.*
import org.trustweave.anchor.exceptions.BlockchainException
import org.trustweave.anchor.exceptions.TreasuryException
import org.trustweave.anchor.options.AlgorandOptions
import org.trustweave.anchor.payment.AssetRef
import org.trustweave.anchor.payment.FeeStrategy
import org.trustweave.anchor.payment.OperationDescriptor
import org.trustweave.anchor.payment.PaymentContext
import org.trustweave.anchor.payment.TokenAmount
import org.trustweave.anchor.payment.isUnmanaged
import org.trustweave.core.exception.TrustWeaveException
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.charset.StandardCharsets

/**
 * Algorand blockchain anchor client implementation.
 *
 * Supports Algorand mainnet, testnet, and betanet chains.
 * Uses Algorand transaction note fields to store payload data.
 *
 * **Type-Safe Options**: Use [AlgorandOptions] for type-safe configuration:
 * ```
 * val options = AlgorandOptions(
 *     algodUrl = "https://testnet-api.algonode.cloud",
 *     // Base64 of either the 32-byte Ed25519 seed or the 64-byte
 *     // exported secret key (seed || public key, e.g. JS SDK `account.sk`).
 *     privateKey = "base64-encoded-key"
 * )
 * val client = AlgorandBlockchainAnchorClient(chainId, options)
 * ```
 *
 * **Reading historical anchors**: algod's pending-transaction endpoint only tracks
 * recently submitted transactions (roughly until they age out of the node's pending
 * window after confirmation). To read historical anchors, configure an Algorand
 * Indexer via the `indexerUrl` (and optional `indexerToken`) options; [readPayload]
 * then resolves transactions through the indexer and reports the actual on-chain
 * round time as the anchor timestamp. Without an indexer, reads of older
 * transactions fail with `NotFound` and the timestamp is left unset.
 *
 * **Confirmation**: writes wait for the transaction to be confirmed
 * (confirmed-round > 0), bounded by the `confirmationTimeoutMs` option
 * (default 30s).
 */
class AlgorandBlockchainAnchorClient(
    chainId: String,
    options: Map<String, Any?> = emptyMap(),
    private val sponsorRegistry: SponsorRegistry = ConfigSponsorRegistry(options),
) : AbstractBlockchainAnchorClient(chainId, options) {

    /**
     * Convenience constructor using type-safe [AlgorandOptions].
     */
    constructor(chainId: String, options: AlgorandOptions) : this(chainId, options.toMap())

    companion object {
        const val MAINNET = "algorand:mainnet"
        const val TESTNET = "algorand:testnet"
        const val BETANET = "algorand:betanet"

        // Network endpoints
        private const val MAINNET_ALGOD_URL = "https://mainnet-api.algonode.cloud"
        private const val TESTNET_ALGOD_URL = "https://testnet-api.algonode.cloud"
        private const val BETANET_ALGOD_URL = "https://betanet-api.algonode.cloud"
    }

    private val algodClient: AlgodClient
    private val account: Account?

    /**
     * Optional Algorand Indexer client used for reads of historical anchors;
     * configured via the `indexerUrl` / `indexerToken` options.
     */
    private val indexerClient: IndexerClient?

    init {
        require(chainId.startsWith("algorand:")) {
            "Invalid chain ID for Algorand: $chainId"
        }

        // Initialize Algod client based on chain
        val algodUrl = when (chainId) {
            MAINNET -> options["algodUrl"] as? String ?: MAINNET_ALGOD_URL
            TESTNET -> options["algodUrl"] as? String ?: TESTNET_ALGOD_URL
            BETANET -> options["algodUrl"] as? String ?: BETANET_ALGOD_URL
            else -> throw IllegalArgumentException("Unsupported chain: $chainId")
        }

        val algodToken = options["algodToken"] as? String ?: ""
        // AlgodClient constructor: (host: String, port: Int, token: String)
        val url = java.net.URI.create(algodUrl).toURL()
        val port = url.port.takeIf { it != -1 } ?: url.defaultPort
        algodClient = AlgodClient(url.host, port, algodToken)

        // Optional Indexer client for historical reads.
        indexerClient = (options["indexerUrl"] as? String)?.let { indexerUrl ->
            val indexer = java.net.URI.create(indexerUrl).toURL()
            val indexerPort = indexer.port.takeIf { it != -1 } ?: indexer.defaultPort
            IndexerClient(indexer.host, indexerPort, options["indexerToken"] as? String ?: "")
        }

        // Initialize account if private key is provided.
        // A present-but-invalid private key is a configuration error and must fail
        // closed instead of silently degrading to the in-memory test fallback.
        account = (options["privateKey"] as? String)?.let { encodedKey ->
            try {
                // Decode strictly: the SDK's lenient decoder + seed-based Account
                // constructor accept almost any input, so garbage keys would
                // otherwise produce a "valid" account for the wrong address.
                // Accepted formats: 32-byte Ed25519 seed, or the 64-byte exported
                // secret key (seed || public key) produced by common Algorand
                // tooling (JS SDK `account.sk`, `algokey`) — the seed is its
                // first 32 bytes.
                val decoded = java.util.Base64.getDecoder().decode(encodedKey)
                require(decoded.size == 32 || decoded.size == 64) {
                    "decoded key must be a 32-byte Ed25519 seed or 64-byte exported secret key, " +
                        "got ${decoded.size} bytes"
                }
                Account(decoded.copyOf(32))
            } catch (e: Exception) {
                throw BlockchainException.ConfigurationFailed(
                    chainId = chainId,
                    configKey = "privateKey",
                    reason = "Invalid Algorand private key (expected base64 of a 32-byte seed " +
                        "or 64-byte exported secret key): ${e.message ?: "Unknown error"}",
                    cause = e
                )
            }
        }
    }

    override protected fun canSubmitTransaction(): Boolean {
        return account != null
    }

    override protected suspend fun submitTransactionToBlockchain(payloadBytes: ByteArray): String {
        return submitTransaction(payloadBytes).first
    }

    override suspend fun estimate(op: OperationDescriptor): TokenAmount = withContext(Dispatchers.IO) {
        val params = algodClient.TransactionParams().execute().body()
            ?: throw BlockchainException.TransactionFailed(
                reason = "Failed to retrieve Algorand suggested params for estimate",
                chainId = chainId,
                operation = "estimate",
            )
        val suggested = params.fee ?: 0L
        val minFee = params.minFee ?: 1_000L
        val microAlgos = BigInteger.valueOf(maxOf(suggested, minFee))
        TokenAmount(op.chainId, AssetRef.Native, microAlgos)
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

        // For Sponsored, resolve the sponsor first so an unknown sponsor fails
        // closed before we touch the network.
        val sponsor: SponsorEntry? = (ctx.feeStrategy as? FeeStrategy.Sponsored)?.let { s ->
            sponsorRegistry.resolve(s.sponsorDid)
                ?: throw TreasuryException.SponsorNotAllowed(
                    domainId = ctx.domainId,
                    sponsorDid = s.sponsorDid,
                )
        }

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
                val acct = account
                    ?: throw IllegalStateException("Account not configured. Provide 'privateKey' in options.")
                when (ctx.feeStrategy) {
                    is FeeStrategy.Sponsored -> submitSponsored(
                        payload = payload,
                        payloadBytes = payloadBytes,
                        mediaType = mediaType,
                        senderAccount = acct,
                        sponsor = sponsor!!,
                    )
                    else -> {
                        val (txHash, feeMicroAlgos) = submitTransaction(payloadBytes)
                        AnchorResult(
                            ref = buildAnchorRef(
                                txHash = txHash,
                                contract = getContractAddress(),
                                extra = buildExtraMetadata(mediaType),
                            ),
                            payload = payload,
                            mediaType = mediaType,
                            timestamp = System.currentTimeMillis() / 1000,
                            fee = TokenAmount(chainId, AssetRef.Native, feeMicroAlgos),
                            payerAddress = acct.address.toString(),
                        )
                    }
                }
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
     * Build a fee-payer atomic group:
     *   txn[0] — data txn from [senderAccount], 0-microAlgo self-payment, fee = 0
     *   txn[1] — 1-microAlgo payment from the sponsor to itself, flatFee = 2× minFee
     *
     * The sponsor's flatFee covers both transactions: Algorand validates
     * `sum(fee) >= len(group) * minFee`, so the data txn's zero fee is paid by
     * the sponsor's surplus.
     *
     * [AnchorResult.fee] reports the combined fee charged on chain, and
     * [AnchorResult.payerAddress] is the sponsor address (not the sender).
     */
    private suspend fun submitSponsored(
        payload: JsonElement,
        payloadBytes: ByteArray,
        mediaType: String,
        senderAccount: Account,
        sponsor: SponsorEntry,
    ): AnchorResult {
        val params = algodClient.TransactionParams().execute().body()
            ?: throw BlockchainException.TransactionFailed(
                reason = "Failed to retrieve Algorand suggested params for sponsored group",
                chainId = chainId,
                operation = "writePayload",
            )
        val minFee = params.minFee ?: 1_000L
        val sponsorFlatFee = minFee * 2

        val sponsorAddress = com.algorand.algosdk.crypto.Address(sponsor.address)

        val dataTxn: Transaction = Transaction.PaymentTransactionBuilder()
            .sender(senderAccount.address)
            .receiver(senderAccount.address)
            .amount(0)
            .note(payloadBytes)
            .suggestedParams(params)
            .flatFee(0L)
            .build()

        val sponsorTxn: Transaction = Transaction.PaymentTransactionBuilder()
            .sender(sponsorAddress)
            .receiver(sponsorAddress)
            .amount(1)
            .suggestedParams(params)
            .flatFee(sponsorFlatFee)
            .build()

        TxGroup.assignGroupID(dataTxn, sponsorTxn)

        val signedData: SignedTransaction = senderAccount.signTransaction(dataTxn)
        val signedSponsor: SignedTransaction = sponsor.sign(sponsorTxn)

        val concatenated = ByteArrayOutputStream().use { out ->
            out.write(Encoder.encodeToMsgPack(signedData))
            out.write(Encoder.encodeToMsgPack(signedSponsor))
            out.toByteArray()
        }

        val response = try {
            algodClient.RawTransaction().rawtxn(concatenated).execute()
        } catch (e: Exception) {
            throw BlockchainException.TransactionFailed(
                reason = "Failed to submit sponsored atomic group to Algorand: ${e.message ?: "Unknown error"}",
                chainId = chainId,
                operation = "writePayload",
            )
        }

        val txid = response.body()?.txId
            ?: throw BlockchainException.TransactionFailed(
                reason = "Failed to get transaction ID from Algorand response (sponsored group)",
                chainId = chainId,
                operation = "writePayload",
            )

        // Submission only means the node accepted the group into its pool — wait
        // for on-chain confirmation so dropped/rejected groups never report success.
        waitForConfirmation(txid)

        val totalFee = (dataTxn.fee ?: BigInteger.ZERO) + (sponsorTxn.fee ?: BigInteger.ZERO)

        return AnchorResult(
            ref = buildAnchorRef(
                txHash = txid,
                contract = getContractAddress(),
                extra = buildExtraMetadata(mediaType) + ("sponsor" to sponsor.address),
            ),
            payload = payload,
            mediaType = mediaType,
            timestamp = System.currentTimeMillis() / 1000,
            fee = TokenAmount(chainId, AssetRef.Native, totalFee),
            payerAddress = sponsor.address,
        )
    }

    override protected suspend fun readTransactionFromBlockchain(txHash: String): AnchorResult {
        return readTransactionFromBlockchainImpl(txHash)
    }

    override protected fun getContractAddress(): String? {
        return options["appId"] as? String
    }

    override protected fun buildExtraMetadata(mediaType: String): Map<String, String> {
        return mapOf(
            "network" to chainId.substringAfter(":"),
            "mediaType" to mediaType
        )
    }

    override protected fun generateTestTxHash(): String {
        return "algo_test_${uniqueTestHashSuffix()}"
    }

    override protected fun getBlockchainName(): String {
        return "Algorand"
    }

    private suspend fun submitTransaction(noteData: ByteArray): Pair<String, BigInteger> {
        if (account == null) {
            throw IllegalStateException("Account not configured. Provide 'privateKey' in options.")
        }

        val params = algodClient.TransactionParams().execute().body()
        val tx = Transaction.PaymentTransactionBuilder()
            .sender(account.address)
            .receiver(account.address) // Send to self
            .amount(0)
            .note(noteData)
            .suggestedParams(params)
            .build()

        val signedTx = account.signTransaction(tx)
        val txBytes = Encoder.encodeToMsgPack(signedTx)
        val feeMicroAlgos: BigInteger = tx.fee ?: BigInteger.ZERO

        // Submit transaction to network and get response
        val response = try {
            algodClient.RawTransaction().rawtxn(txBytes).execute()
        } catch (e: Exception) {
            throw BlockchainException.TransactionFailed(
                reason = "Failed to submit transaction to Algorand: ${e.message ?: "Unknown error"}",
                chainId = chainId,
                operation = "submitTransaction"
            )
        }

        val txid = response.body()?.txId
            ?: throw BlockchainException.TransactionFailed(
                reason = "Failed to get transaction ID from Algorand response",
                chainId = chainId,
                operation = "submitTransaction",
            )

        // Submission only means the node accepted the tx into its pool — wait for
        // on-chain confirmation so dropped/rejected txs never report success.
        waitForConfirmation(txid)

        return txid to feeMicroAlgos
    }

    /**
     * Polls the node's pending-transaction endpoint until the transaction is
     * confirmed (confirmed-round > 0), bounded by [confirmationTimeoutMs]
     * (option [OPTION_CONFIRMATION_TIMEOUT_MS]). Throws
     * [BlockchainException.TransactionFailed] if the node rejects the transaction
     * (pool error) or the timeout elapses.
     */
    private suspend fun waitForConfirmation(txid: String): PendingTransactionResponse {
        val deadline = System.currentTimeMillis() + confirmationTimeoutMs
        while (true) {
            val pending = try {
                algodClient.PendingTransactionInformation(txid).execute().body()
            } catch (e: Exception) {
                null
            }
            if (pending != null) {
                val poolError = pending.poolError
                if (!poolError.isNullOrBlank()) {
                    throw BlockchainException.TransactionFailed(
                        chainId = chainId,
                        txHash = txid,
                        operation = "submitTransaction",
                        reason = "Transaction rejected by Algorand node: $poolError"
                    )
                }
                if ((pending.confirmedRound ?: 0L) > 0L) {
                    return pending
                }
            }
            if (System.currentTimeMillis() >= deadline) {
                throw BlockchainException.TransactionFailed(
                    chainId = chainId,
                    txHash = txid,
                    operation = "submitTransaction",
                    reason = "Transaction not confirmed within $confirmationTimeoutMs ms " +
                        "(configure via '$OPTION_CONFIRMATION_TIMEOUT_MS' option)"
                )
            }
            delay(confirmationPollIntervalMs)
        }
    }

    private suspend fun readTransactionFromBlockchainImpl(txHash: String): AnchorResult {
        return if (indexerClient != null) {
            readFromIndexer(indexerClient, txHash)
        } else {
            readFromAlgodPendingPool(txHash)
        }
    }

    /**
     * Resolves a transaction through the configured Algorand Indexer.
     * The anchor timestamp is the actual on-chain round time.
     */
    private fun readFromIndexer(indexer: IndexerClient, txHash: String): AnchorResult {
        val response = try {
            indexer.lookupTransaction(txHash).execute()
        } catch (e: Exception) {
            throw BlockchainException.TransactionFailed(
                chainId = chainId,
                txHash = txHash,
                operation = "readPayload",
                reason = "Failed to query Algorand indexer: ${e.message ?: "Unknown error"}",
                cause = e
            )
        }
        val transaction = response.takeIf { it.isSuccessful }?.body()?.transaction
            ?: throw TrustWeaveException.NotFound(resource = "Transaction not found on Algorand indexer: $txHash")

        val note = transaction.note
            ?: throw TrustWeaveException.NotFound(
                resource = "Transaction note not found: $txHash. Transaction may not contain note data."
            )

        val payload = Json.parseToJsonElement(String(note, StandardCharsets.UTF_8))

        return AnchorResult(
            ref = buildAnchorRef(
                txHash = txHash,
                contract = getContractAddress()
            ),
            payload = payload,
            mediaType = "application/json",
            // Actual on-chain round time (epoch seconds) from the indexer.
            timestamp = transaction.roundTime
        )
    }

    /**
     * Resolves a transaction through algod's pending-transaction endpoint.
     * This only serves recently submitted transactions; configure `indexerUrl`
     * for historical lookups.
     */
    private fun readFromAlgodPendingPool(txHash: String): AnchorResult {
        val txInfo = try {
            algodClient.PendingTransactionInformation(txHash).execute().body()
        } catch (e: Exception) {
            null
        } ?: throw TrustWeaveException.NotFound(
            resource = "Transaction not found via algod pending-transaction endpoint: $txHash. " +
                "Without an 'indexerUrl' option, reads can only resolve recently submitted " +
                "transactions still tracked by the node; configure an Algorand Indexer for " +
                "historical lookups."
        )

        val note = txInfo.txn?.tx?.note
            ?: throw TrustWeaveException.NotFound(
                resource = "Transaction note not found: $txHash. Transaction may not contain note data."
            )

        val payload = Json.parseToJsonElement(String(note, StandardCharsets.UTF_8))

        return AnchorResult(
            ref = buildAnchorRef(
                txHash = txHash,
                contract = getContractAddress()
            ),
            payload = payload,
            mediaType = "application/json",
            // The pending-transaction response carries no block time; leave the
            // timestamp unset rather than fabricating one. Configure 'indexerUrl'
            // to get the actual round time.
            timestamp = null
        )
    }
}

