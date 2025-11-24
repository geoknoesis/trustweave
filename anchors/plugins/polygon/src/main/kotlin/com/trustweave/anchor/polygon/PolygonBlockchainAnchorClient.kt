package com.trustweave.anchor.polygon

import com.trustweave.anchor.*
import com.trustweave.anchor.exceptions.BlockchainTransactionException
import com.trustweave.anchor.options.PolygonOptions
import com.trustweave.core.exception.NotFoundException
import com.trustweave.core.exception.TrustWeaveException
import kotlinx.serialization.json.*
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import org.web3j.tx.RawTransactionManager
import org.web3j.tx.TransactionManager
import java.math.BigInteger
import java.nio.charset.StandardCharsets

/**
 * Polygon blockchain anchor client implementation.
 * 
 * Supports Polygon mainnet and Mumbai testnet chains.
 * Uses Ethereum-compatible transaction data fields to store payload data.
 * 
 * **Type-Safe Options**: Use [PolygonOptions] for type-safe configuration:
 * ```
 * val options = PolygonOptions(
 *     rpcUrl = "https://rpc-mumbai.maticvigil.com",
 *     privateKey = "0x..."
 * )
 * val client = PolygonBlockchainAnchorClient(chainId, options)
 * ```
 */
class PolygonBlockchainAnchorClient(
    chainId: String,
    options: Map<String, Any?> = emptyMap()
) : AbstractBlockchainAnchorClient(chainId, options), java.io.Closeable {
    
    /**
     * Convenience constructor using type-safe [PolygonOptions].
     */
    constructor(chainId: String, options: PolygonOptions) : this(chainId, options.toMap())

    companion object {
        const val MAINNET = "eip155:137"  // Polygon mainnet
        const val MUMBAI = "eip155:80001" // Mumbai testnet
        
        // Network RPC endpoints
        private const val MAINNET_RPC_URL = "https://polygon-rpc.com"
        private const val MUMBAI_RPC_URL = "https://rpc-mumbai.maticvigil.com"
    }

    private val web3j: Web3j
    private val transactionManager: TransactionManager?
    private val credentials: org.web3j.crypto.Credentials?

    init {
        require(chainId.startsWith("eip155:")) {
            "Invalid chain ID for Polygon/Ethereum: $chainId"
        }
        val chainIdNum = chainId.substringAfter(":").toIntOrNull()
        require(chainIdNum == 137 || chainIdNum == 80001) {
            "Unsupported Polygon chain ID: $chainId. Use 'eip155:137' (mainnet) or 'eip155:80001' (Mumbai testnet)"
        }
        
        // Initialize Web3j client based on chain
        val rpcUrl = when (chainId) {
            MAINNET -> options["rpcUrl"] as? String ?: MAINNET_RPC_URL
            MUMBAI -> options["rpcUrl"] as? String ?: MUMBAI_RPC_URL
            else -> throw IllegalArgumentException("Unsupported chain: $chainId")
        }
        web3j = Web3j.build(HttpService(rpcUrl))

        // Initialize transaction manager if credentials are provided
        val creds = (options["privateKey"] as? String)?.let { privateKeyHex ->
            try {
                org.web3j.crypto.Credentials.create(privateKeyHex.removePrefix("0x"))
            } catch (e: Exception) {
                null
            }
        }
        credentials = creds
        transactionManager = creds?.let {
            try {
                val chainIdLong = when (chainId) {
                    MAINNET -> 137L
                    MUMBAI -> 80001L
                    else -> throw IllegalArgumentException("Unsupported chain: $chainId")
                }
                RawTransactionManager(web3j, it, chainIdLong)
            } catch (e: Exception) {
                null
            }
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
            MAINNET -> "polygon-mainnet"
            MUMBAI -> "mumbai-testnet"
            else -> chainId
        }
        return mapOf(
            "network" to network,
            "mediaType" to mediaType
        )
    }

    override protected fun generateTestTxHash(): String {
        return "0x${(0..1000000).random().toString(16).padStart(64, '0')}"
    }

    override protected fun getBlockchainName(): String {
        return "Polygon"
    }

    private suspend fun submitTransaction(data: ByteArray): String {
        val creds = credentials ?: throw IllegalStateException("Credentials not configured. Provide 'privateKey' in options.")

        val gasPrice = web3j.ethGasPrice().send().gasPrice
        val nonce = web3j.ethGetTransactionCount(creds.address, org.web3j.protocol.core.DefaultBlockParameterName.LATEST)
            .send().transactionCount

        val transaction = org.web3j.tx.gas.DefaultGasProvider()
        val rawTransaction = org.web3j.crypto.RawTransaction.createTransaction(
            nonce,
            gasPrice,
            transaction.gasLimit,
            creds.address, // Send to self
            BigInteger.ZERO,
            org.web3j.utils.Numeric.toHexString(data)
        )

        val signedTransaction = org.web3j.crypto.TransactionEncoder.signMessage(rawTransaction, creds)
        val hexValue = org.web3j.utils.Numeric.toHexString(signedTransaction)
        
        val ethSendTransaction = web3j.ethSendRawTransaction(hexValue).send()
        if (ethSendTransaction.hasError()) {
            val error = ethSendTransaction.error
            throw BlockchainTransactionException(
                message = "Transaction failed: ${error?.message ?: "Unknown error"}",
                chainId = chainId,
                txHash = null,
                operation = "submitTransaction",
                payloadSize = data.size.toLong(),
                gasUsed = transaction.gasLimit?.toLong(),
                cause = null
            )
        }

        return ethSendTransaction.transactionHash
    }

    private suspend fun readTransactionFromBlockchainImpl(txHash: String): AnchorResult {
        val ethGetTransactionReceipt = web3j.ethGetTransactionReceipt(txHash).send()
        if (!ethGetTransactionReceipt.transactionReceipt.isPresent) {
            throw NotFoundException("Transaction receipt not found: $txHash")
        }

        val receipt = ethGetTransactionReceipt.transactionReceipt.get()
        val tx = web3j.ethGetTransactionByHash(txHash).send().transaction.orElse(null)
            ?: throw NotFoundException("Transaction not found: $txHash")

        val input = tx.input
        if (input == null || input.isEmpty() || input == "0x") {
            throw NotFoundException("Transaction data not found: $txHash")
        }

        val dataBytes = org.web3j.utils.Numeric.hexStringToByteArray(input)
        val payloadJson = String(dataBytes, StandardCharsets.UTF_8)
        val payload = Json.parseToJsonElement(payloadJson)
        
        // Extract block number - Web3j 5.0.1 API may differ
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

