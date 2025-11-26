package com.trustweave.anchor.zksync

import com.trustweave.anchor.*
import com.trustweave.anchor.exceptions.BlockchainException

import com.trustweave.core.exception.TrustWeaveException
import kotlinx.serialization.json.*
import org.web3j.protocol.Web3j
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
                    MAINNET -> 324L
                    SEPOLIA -> 300L
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
            throw BlockchainException.TransactionFailed(
                reason = "Transaction failed: ${error?.message ?: "Unknown error"}",
                chainId = chainId,
                txHash = null,
                operation = "submitTransaction",
                payloadSize = data.size.toLong(),
                gasUsed = transaction.gasLimit?.toLong()
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
        
        // Extract block number
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
        return "0x${(0..1000000).random().toString(16).padStart(64, '0')}"
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

