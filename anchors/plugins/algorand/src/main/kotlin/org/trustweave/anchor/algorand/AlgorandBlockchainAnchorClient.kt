package org.trustweave.anchor.algorand

import com.algorand.algosdk.account.Account
import com.algorand.algosdk.transaction.SignedTransaction
import com.algorand.algosdk.transaction.Transaction
import com.algorand.algosdk.transaction.TxGroup
import com.algorand.algosdk.util.Encoder
import com.algorand.algosdk.v2.client.common.AlgodClient
import com.algorand.algosdk.v2.client.model.PendingTransactionResponse
import com.algorand.algosdk.v2.client.model.TransactionResponse
import kotlinx.coroutines.Dispatchers
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
 *     privateKey = "base64-encoded-key"
 * )
 * val client = AlgorandBlockchainAnchorClient(chainId, options)
 * ```
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

        // Initialize account if private key is provided
        account = (options["privateKey"] as? String)?.let { privateKeyHex ->
            try {
                Account(Encoder.decodeFromBase64(privateKeyHex))
            } catch (e: Exception) {
                null // Account creation failed, will use in-memory storage
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
                ).apply { initCause(e) }
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
    private fun submitSponsored(
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

        val txid = extractTxid(response.body())
            ?: throw BlockchainException.TransactionFailed(
                reason = "Failed to get transaction ID from Algorand response (sponsored group)",
                chainId = chainId,
                operation = "writePayload",
            )

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

    private fun extractTxid(responseBody: Any?): String? {
        if (responseBody == null) return null
        return try {
            when {
                responseBody.javaClass.methods.any { it.name == "getTxid" } ->
                    responseBody.javaClass.getMethod("getTxid").invoke(responseBody) as? String
                responseBody.javaClass.methods.any { it.name == "txid" && it.parameterCount == 0 } ->
                    responseBody.javaClass.getMethod("txid").invoke(responseBody) as? String
                else -> null
            }
        } catch (e: Exception) {
            null
        }
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
        return "algo_test_${System.currentTimeMillis()}_${(0..1000000).random()}"
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

        val txid = extractTxid(response.body())
            ?: throw BlockchainException.TransactionFailed(
                reason = "Failed to get transaction ID from Algorand response",
                chainId = chainId,
                operation = "submitTransaction",
            )

        return txid to feeMicroAlgos
    }

    private suspend fun readTransactionFromBlockchainImpl(txHash: String): AnchorResult {
        val txInfo = algodClient.PendingTransactionInformation(txHash).execute().body()
            ?: throw TrustWeaveException.NotFound(resource = "Transaction not found: $txHash")

        // Use proper SDK API methods instead of reflection
        val transaction: TransactionResponse? = try {
            // PendingTransactionResponse should have a transaction property
            // Try to get transaction using proper API
            txInfo.javaClass.methods.find { it.name == "getTransaction" }?.invoke(txInfo) as? TransactionResponse
                ?: txInfo.javaClass.methods.find { it.name == "transaction" && it.parameterCount == 0 }?.invoke(txInfo) as? TransactionResponse
        } catch (e: Exception) {
            null
        }

        if (transaction == null) {
            throw TrustWeaveException.NotFound(resource = "Transaction not found: $txHash")
        }

        // Extract note from transaction
        val note: ByteArray? = try {
            // Try to get note using proper API methods
            when {
                transaction.javaClass.methods.any { it.name == "getNote" } -> {
                    transaction.javaClass.getMethod("getNote").invoke(transaction) as? ByteArray
                }
                transaction.javaClass.methods.any { it.name == "note" && it.parameterCount == 0 } -> {
                    transaction.javaClass.getMethod("note").invoke(transaction) as? ByteArray
                }
                else -> {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }

        if (note == null) {
            throw TrustWeaveException.NotFound(
                resource = "Transaction note not found: $txHash. Transaction may not contain note data."
            )
        }

        val payloadJson = String(note, StandardCharsets.UTF_8)
        val payload = Json.parseToJsonElement(payloadJson)

        // Extract confirmed round using proper API
        val confirmedRound: Long? = try {
            txInfo.javaClass.methods.find { it.name == "getConfirmedRound" }?.invoke(txInfo) as? Long
                ?: txInfo.javaClass.methods.find { it.name == "confirmedRound" && it.parameterCount == 0 }?.invoke(txInfo) as? Long
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
            timestamp = confirmedRound?.let { System.currentTimeMillis() / 1000 }
        )
    }
}

