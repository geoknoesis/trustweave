package com.trustweave.anchor.bitcoin

import com.trustweave.anchor.*
import com.trustweave.anchor.exceptions.BlockchainTransactionException
import com.trustweave.core.exception.NotFoundException
import com.trustweave.core.exception.TrustWeaveException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.bitcoinj.core.*
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.script.ScriptBuilder
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Bitcoin blockchain anchor client implementation.
 * 
 * Supports Bitcoin mainnet and testnet chains.
 * Uses OP_RETURN outputs to store payload data (up to 80 bytes).
 * 
 * Chain ID format: "bip122:<blockhash>"
 * Examples:
 * - "bip122:000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f" (Bitcoin mainnet)
 * - "bip122:000000000933ea01ad0ee984209779baaec3ced90fa3f408719526f8d77f4943" (Bitcoin testnet)
 * 
 * **Note:** Bitcoin OP_RETURN has a 80-byte limit. For larger payloads, consider using
 * a hash-based approach or external storage with hash anchoring.
 * 
 * **Example:**
 * ```kotlin
 * val client = BitcoinBlockchainAnchorClient(
 *     chainId = "bip122:000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f",
 *     options = mapOf(
 *         "network" to "mainnet",
 *         "rpcUrl" to "http://localhost:8332",
 *         "rpcUser" to "user",
 *         "rpcPassword" to "password"
 *     )
 * )
 * ```
 */
class BitcoinBlockchainAnchorClient(
    chainId: String,
    options: Map<String, Any?> = emptyMap()
) : AbstractBlockchainAnchorClient(chainId, options), java.io.Closeable {
    
    companion object {
        const val MAINNET = "bip122:000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f"
        const val TESTNET = "bip122:000000000933ea01ad0ee984209779baaec3ced90fa3f408719526f8d77f4943"
        
        // OP_RETURN data limit (80 bytes)
        private const val OP_RETURN_MAX_SIZE = 80
    }

    private val networkParams: NetworkParameters
    private val networkName: String
    private val rpcUrl: String?
    private val rpcUser: String?
    private val rpcPassword: String?
    private val httpClient: OkHttpClient

    init {
        val network = options["network"] as? String ?: "mainnet"
        networkParams = when (network.lowercase()) {
            "mainnet" -> MainNetParams.get()
            "testnet", "testnet3" -> TestNet3Params.get()
            else -> throw IllegalArgumentException("Unsupported Bitcoin network: $network. Use 'mainnet' or 'testnet'")
        }
        networkName = network
        
        rpcUrl = options["rpcUrl"] as? String
        rpcUser = options["rpcUser"] as? String
        rpcPassword = options["rpcPassword"] as? String
        
        // Create HTTP client with basic auth for RPC
        val clientBuilder = OkHttpClient.Builder()
        if (rpcUser != null && rpcPassword != null) {
            val credentials = okhttp3.Credentials.basic(rpcUser, rpcPassword)
            clientBuilder.addInterceptor { chain ->
                val originalRequest = chain.request()
                val authenticatedRequest = originalRequest.newBuilder()
                    .header("Authorization", credentials)
                    .build()
                chain.proceed(authenticatedRequest)
            }
        }
        httpClient = clientBuilder.build()
    }

    override protected fun canSubmitTransaction(): Boolean {
        // Check if RPC credentials are provided
        return options["rpcUrl"] != null && 
               options["rpcUser"] != null && 
               options["rpcPassword"] != null
    }

    override protected suspend fun submitTransactionToBlockchain(
        payloadBytes: ByteArray
    ): String = withContext(Dispatchers.IO) {
        // Bitcoin OP_RETURN has 80-byte limit
        if (payloadBytes.size > OP_RETURN_MAX_SIZE) {
            throw BlockchainTransactionException(
                message = "Payload size (${payloadBytes.size} bytes) exceeds Bitcoin OP_RETURN limit ($OP_RETURN_MAX_SIZE bytes). Consider using hash-based anchoring.",
                chainId = chainId,
                txHash = null,
                operation = "submitTransaction",
                payloadSize = payloadBytes.size.toLong(),
                cause = null
            )
        }
        
        if (rpcUrl == null || rpcUser == null || rpcPassword == null) {
            throw IllegalStateException("Bitcoin RPC credentials not configured. Provide 'rpcUrl', 'rpcUser', and 'rpcPassword' in options.")
        }
        
        // Use Bitcoin RPC to create and send transaction with OP_RETURN
        // This is a simplified approach - full implementation would use BitcoinJ for transaction building
        // For now, we'll use RPC's createrawtransaction and signrawtransaction
        
        val address = options["address"] as? String
            ?: throw IllegalStateException("Bitcoin address required for transaction creation")
        
        // Get unspent outputs
        val utxos = getUnspentOutputs()
        if (utxos.isEmpty()) {
            throw BlockchainTransactionException(
                message = "No unspent outputs available for transaction",
                chainId = chainId,
                txHash = null,
                operation = "submitTransaction",
                payloadSize = payloadBytes.size.toLong(),
                cause = null
            )
        }
        
        // Build transaction inputs
        val inputs = utxos.map { utxo ->
            buildJsonObject {
                put("txid", utxo.txid)
                put("vout", utxo.vout)
            }
        }
        
        // Build transaction outputs
        // OP_RETURN output: data in hex
        val opReturnHex = Utils.HEX.encode(payloadBytes)
        val outputs = buildJsonObject {
            put("data", opReturnHex)
            // Add change output if needed
            val totalInput = utxos.sumOf { it.amount }
            val fee = 1000L // Estimated fee in satoshis
            val changeAmount = (totalInput - fee) / 100_000_000.0 // Convert back to BTC
            if (changeAmount > 0) {
                put(address, changeAmount)
            }
        }
        
        // Create raw transaction via RPC
        val rawTx = rpcCall("createrawtransaction", listOf(inputs, outputs))
        
        // Sign transaction
        val privateKey = options["privateKey"] as? String
            ?: throw IllegalStateException("Private key required for signing Bitcoin transactions")
        val signedTx = rpcCall("signrawtransactionwithkey", listOf(rawTx, listOf(privateKey)))
        
        // Extract signed transaction hex
        val signedTxJson = Json.parseToJsonElement(signedTx).jsonObject
        val signedTxHex = signedTxJson["hex"]?.jsonPrimitive?.content
            ?: throw TrustWeaveException("Failed to sign transaction")
        
        // Check if transaction is complete
        val complete = signedTxJson["complete"]?.jsonPrimitive?.boolean ?: false
        if (!complete) {
            throw TrustWeaveException("Transaction signing incomplete")
        }
        
        // Broadcast transaction
        val txHash = sendRawTransaction(signedTxHex)
        
        txHash
    }

    override protected suspend fun readTransactionFromBlockchain(txHash: String): AnchorResult = withContext(Dispatchers.IO) {
        if (rpcUrl == null || rpcUser == null || rpcPassword == null) {
            throw IllegalStateException("Bitcoin RPC credentials not configured")
        }
        
        // Get transaction via RPC
        val txHex = getRawTransaction(txHash)
        val tx = Transaction(networkParams, Utils.HEX.decode(txHex))
        
        // Find OP_RETURN output
        var opReturnData: ByteArray? = null
        for (output in tx.outputs) {
            val script = output.scriptPubKey
            // Check if script is OP_RETURN (starts with OP_RETURN opcode)
            if (script.isOpReturn || (script.chunks.isNotEmpty() && script.chunks[0].opcode == org.bitcoinj.script.ScriptOpCodes.OP_RETURN)) {
                if (script.chunks.size > 1) {
                    opReturnData = script.chunks[1].data
                }
                break
            }
        }
        
        if (opReturnData == null) {
            throw NotFoundException("OP_RETURN data not found in transaction: $txHash")
        }
        
        // Parse payload JSON
        val payloadJson = String(opReturnData, StandardCharsets.UTF_8)
        val payload = Json.parseToJsonElement(payloadJson)
        
        // Get block information
        val blockHash = getTransactionBlockHash(txHash)
        val blockNumber = blockHash?.let { getBlockHeight(it) }
        
        AnchorResult(
            ref = buildAnchorRef(
                txHash = txHash,
                contract = null
            ),
            payload = payload,
            mediaType = "application/json",
            timestamp = blockNumber?.let { System.currentTimeMillis() / 1000 }
        )
    }

    override protected fun getContractAddress(): String? {
        // Bitcoin doesn't use contracts
        return null
    }

    override protected fun buildExtraMetadata(mediaType: String): Map<String, String> {
        return mapOf(
            "network" to networkName,
            "mediaType" to mediaType,
            "protocol" to "OP_RETURN"
        )
    }

    override protected fun generateTestTxHash(): String {
        // Generate a test transaction hash (64 hex characters)
        return "0x${(0..1000000).random().toString(16).padStart(64, '0')}"
    }

    override protected fun getBlockchainName(): String {
        return "Bitcoin"
    }

    /**
     * Bitcoin RPC call helper.
     */
    private suspend fun rpcCall(method: String, params: List<Any?>): String = withContext(Dispatchers.IO) {
        val requestBody = buildJsonObject {
            put("jsonrpc", "1.0")
            put("id", "TrustWeave")
            put("method", method)
            put("params", buildJsonArray {
                params.forEach { param ->
                    when (param) {
                        is String -> add(param)
                        is Number -> add(param)
                        is Boolean -> add(param)
                        is List<*> -> {
                            // Handle list of addresses or other lists
                            add(buildJsonArray { 
                                param.forEach { item ->
                                    when (item) {
                                        is String -> add(item)
                                        is Number -> add(item)
                                        is Boolean -> add(item)
                        is Map<*, *> -> {
                            // Handle map (for transaction inputs)
                            @Suppress("UNCHECKED_CAST")
                            val mapItem = item as? Map<String, Any?>
                            add(buildJsonObject {
                                mapItem?.forEach { (key, value) ->
                                    put(key, value.toString())
                                }
                            })
                        }
                                        else -> add(item.toString())
                                    }
                                }
                            })
                        }
                        is Map<*, *> -> {
                            // Handle map (for transaction outputs)
                            @Suppress("UNCHECKED_CAST")
                            val mapParam = param as? Map<String, Any?>
                            add(buildJsonObject {
                                mapParam?.forEach { (key, value) ->
                                    when (value) {
                                        is String -> put(key, value)
                                        is Number -> put(key, value)
                                        is Boolean -> put(key, value)
                                        else -> put(key, value.toString())
                                    }
                                }
                            })
                        }
                        null -> add(JsonNull)
                        else -> add(param.toString())
                    }
                }
            })
        }
        
        val request = Request.Builder()
            .url(rpcUrl!!)
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()
        
        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string()
        
        if (!response.isSuccessful) {
            throw TrustWeaveException("Bitcoin RPC call failed: ${response.code} - ${response.message}")
        }
        
        val jsonResponse = Json.parseToJsonElement(responseBody ?: "{}").jsonObject
        val error = jsonResponse["error"]
        if (error != null && error !is JsonNull) {
            throw TrustWeaveException("Bitcoin RPC error: ${error.jsonObject["message"]?.jsonPrimitive?.content}")
        }
        
        jsonResponse["result"]?.jsonPrimitive?.content
            ?: throw TrustWeaveException("No result in Bitcoin RPC response")
    }
    
    /**
     * Get unspent transaction outputs.
     */
    private suspend fun getUnspentOutputs(): List<UtxoInfo> {
        val address = options["address"] as? String
            ?: throw IllegalStateException("Bitcoin address required for transaction creation")
        
        val result = rpcCall("listunspent", listOf(0, 9999999, listOf(address)))
        val utxosJson = Json.parseToJsonElement(result).jsonArray
        
        return utxosJson.map { utxoJson ->
            val utxo = utxoJson.jsonObject
            UtxoInfo(
                txid = utxo["txid"]?.jsonPrimitive?.content ?: "",
                vout = utxo["vout"]?.jsonPrimitive?.int ?: 0,
                scriptPubKey = ScriptPubKey(
                    hex = utxo["scriptPubKey"]?.jsonPrimitive?.content ?: ""
                ),
                amount = ((utxo["amount"]?.jsonPrimitive?.double ?: 0.0) * 100_000_000).toLong() // Convert to satoshis
            )
        }
    }
    
    /**
     * Send raw transaction via RPC.
     */
    private suspend fun sendRawTransaction(txHex: String): String {
        return rpcCall("sendrawtransaction", listOf(txHex))
    }
    
    /**
     * Get raw transaction hex via RPC.
     */
    private suspend fun getRawTransaction(txHash: String): String {
        return rpcCall("getrawtransaction", listOf(txHash))
    }
    
    /**
     * Get transaction block hash.
     */
    private suspend fun getTransactionBlockHash(txHash: String): String? {
        return try {
            val result = rpcCall("getrawtransaction", listOf(txHash, 1))
            val txJson = Json.parseToJsonElement(result).jsonObject
            txJson["blockhash"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get block height from block hash.
     */
    private suspend fun getBlockHeight(blockHash: String): Long? {
        return try {
            val result = rpcCall("getblock", listOf(blockHash))
            val blockJson = Json.parseToJsonElement(result).jsonObject
            blockJson["height"]?.jsonPrimitive?.long
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * UTXO information.
     */
    private data class UtxoInfo(
        val txid: String,
        val vout: Int,
        val scriptPubKey: ScriptPubKey,
        val amount: Long
    )
    
    private data class ScriptPubKey(
        val hex: String
    )
    
    override fun close() {
        httpClient.dispatcher.executorService.shutdown()
    }
}

