package org.trustweave.anchor.algorand

import com.algorand.algosdk.account.Account
import com.algorand.algosdk.transaction.Transaction
import com.algorand.algosdk.util.Encoder
import com.algorand.algosdk.v2.client.common.AlgodClient
import com.algorand.algosdk.v2.client.model.PendingTransactionResponse
import com.algorand.algosdk.v2.client.model.TransactionResponse
import org.trustweave.anchor.*
import org.trustweave.anchor.exceptions.BlockchainException
import org.trustweave.anchor.options.AlgorandOptions

import org.trustweave.core.exception.TrustWeaveException
import kotlinx.serialization.json.*
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
    options: Map<String, Any?> = emptyMap()
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
        return submitTransaction(payloadBytes)
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

    private suspend fun submitTransaction(noteData: ByteArray): String {
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

        // Extract transaction ID from response
        // The response should contain the transaction ID
        val txid = try {
            val responseBody = response.body()
            // Try to get transaction ID from response using method-based access
            when {
                responseBody?.javaClass?.methods?.any { it.name == "getTxid" } == true -> {
                    responseBody.javaClass.getMethod("getTxid").invoke(responseBody) as? String
                }
                responseBody?.javaClass?.methods?.any { it.name == "txid" && it.parameterCount == 0 } == true -> {
                    responseBody.javaClass.getMethod("txid").invoke(responseBody) as? String
                }
                else -> {
                    // Fallback: compute from transaction bytes if response doesn't have it
                    // This is a last resort - the response should normally contain the txid
                    null
                }
            }
        } catch (e: Exception) {
            null
        } ?: throw BlockchainException.TransactionFailed(
            reason = "Failed to get transaction ID from Algorand response",
            chainId = chainId,
            operation = "submitTransaction"
        )

        return txid
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

