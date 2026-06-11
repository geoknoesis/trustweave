package org.trustweave.anchor.bitcoin

import org.trustweave.anchor.*
import org.trustweave.anchor.exceptions.BlockchainException

import org.trustweave.core.exception.TrustWeaveException
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

        /**
         * Flat transaction fee in satoshis. Hardcoded for now; making this
         * configurable (or rate-based via `estimatesmartfee`) is a follow-up.
         * All fee/change arithmetic is integer satoshis — never floating point.
         */
        private const val FEE_SATS = 1_000L
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

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    override protected suspend fun submitTransactionToBlockchain(
        payloadBytes: ByteArray
    ): String = withContext(Dispatchers.IO) {
        // Bitcoin OP_RETURN has 80-byte limit
        if (payloadBytes.size > OP_RETURN_MAX_SIZE) {
            throw BlockchainException.TransactionFailed(
                reason = "Payload size (${payloadBytes.size} bytes) exceeds Bitcoin OP_RETURN limit ($OP_RETURN_MAX_SIZE bytes). Consider using hash-based anchoring.",
                chainId = chainId,
                txHash = null,
                operation = "submitTransaction",
                payloadSize = payloadBytes.size.toLong()
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
            throw BlockchainException.TransactionFailed(
                reason = "No unspent outputs available for transaction",
                chainId = chainId,
                txHash = null,
                operation = "submitTransaction",
                payloadSize = payloadBytes.size.toLong()
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
        // All amount math is integer satoshis; BTC decimals only appear (exactly)
        // at the JSON-RPC boundary.
        val totalInputSats = utxos.sumOf { it.amountSats }
        if (totalInputSats < FEE_SATS) {
            throw BlockchainException.TransactionFailed(
                reason = "Insufficient funds: inputs total $totalInputSats sats, flat fee is $FEE_SATS sats",
                chainId = chainId,
                txHash = null,
                operation = "submitTransaction",
                payloadSize = payloadBytes.size.toLong()
            )
        }
        val changeSats = totalInputSats - FEE_SATS
        val outputs = buildJsonObject {
            put("data", opReturnHex)
            if (changeSats > 0) {
                // Exact decimal BTC value (plain notation, never floating point).
                put(address, JsonUnquotedLiteral(BitcoinAmounts.satoshisToBtcString(changeSats)))
            }
        }

        // Create raw transaction via RPC
        val rawTx = rpcCall("createrawtransaction", listOf(inputs, outputs))

        // Sign transaction
        val privateKey = options["privateKey"] as? String
            ?: throw IllegalStateException("Private key required for signing Bitcoin transactions")
        val signedTxJson = rpcCallJson("signrawtransactionwithkey", listOf(rawTx, listOf(privateKey))).jsonObject

        // Extract signed transaction hex
        val signedTxHex = signedTxJson["hex"]?.jsonPrimitive?.content
            ?: throw TrustWeaveException.Unknown(message = "Failed to sign transaction")

        // Check if transaction is complete
        val complete = signedTxJson["complete"]?.jsonPrimitive?.boolean ?: false
        if (!complete) {
            throw TrustWeaveException.Unknown(message = "Transaction signing incomplete")
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
            throw TrustWeaveException.NotFound(resource = "OP_RETURN data not found in transaction: $txHash")
        }

        // Parse payload JSON
        val payloadJson = String(opReturnData, StandardCharsets.UTF_8)
        val payload = Json.parseToJsonElement(payloadJson)

        // Use the actual block timestamp; unconfirmed (mempool) transactions have
        // none, so leave it unset rather than fabricating one.
        val blockHash = getTransactionBlockHash(txHash)
        val blockTime = blockHash?.let { getBlockTime(it) }

        AnchorResult(
            ref = buildAnchorRef(
                txHash = txHash,
                contract = null
            ),
            payload = payload,
            mediaType = "application/json",
            timestamp = blockTime
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
        // Generate a unique test transaction hash (64 hex characters, like a Bitcoin txid)
        return uniqueTestHashHex()
    }

    override protected fun getBlockchainName(): String {
        return "Bitcoin"
    }

    /**
     * Bitcoin RPC call helper returning the raw `result` element. Use this for
     * methods whose result is a JSON object or array (`listunspent`, `getblock`,
     * `signrawtransactionwithkey`, verbose `getrawtransaction`, …).
     */
    private suspend fun rpcCallJson(method: String, params: List<Any?>): JsonElement = withContext(Dispatchers.IO) {
        val requestBody = buildJsonObject {
            put("jsonrpc", "1.0")
            put("id", "TrustWeave")
            put("method", method)
            put("params", buildJsonArray {
                params.forEach { param ->
                    when (param) {
                        is JsonElement -> add(param)
                        is String -> add(param)
                        is Number -> add(param)
                        is Boolean -> add(param)
                        is List<*> -> {
                            // Handle list of addresses, keys, or pre-built JSON elements
                            add(buildJsonArray {
                                param.forEach { item ->
                                    when (item) {
                                        is JsonElement -> add(item)
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
            .url(rpcUrl ?: error("rpcUrl not configured"))
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string()

        if (!response.isSuccessful) {
            throw TrustWeaveException.Unknown(message = "Bitcoin RPC call failed: ${response.code} - ${response.message}")
        }

        val jsonResponse = Json.parseToJsonElement(responseBody ?: "{}").jsonObject
        val error = jsonResponse["error"]
        if (error != null && error !is JsonNull) {
            throw TrustWeaveException.Unknown(message = "Bitcoin RPC error: ${error.jsonObject["message"]?.jsonPrimitive?.content ?: "Unknown error"}")
        }

        jsonResponse["result"]?.takeIf { it !is JsonNull }
            ?: throw TrustWeaveException.Unknown(message = "No result in Bitcoin RPC response")
    }

    /**
     * Bitcoin RPC call helper for methods whose result is a primitive
     * (e.g. `createrawtransaction`, `sendrawtransaction`, `getrawtransaction`).
     */
    private suspend fun rpcCall(method: String, params: List<Any?>): String =
        rpcCallJson(method, params).jsonPrimitive.content

    /**
     * Get unspent transaction outputs. Amounts are parsed exactly into Long
     * satoshis (the RPC reports decimal BTC).
     */
    private suspend fun getUnspentOutputs(): List<UtxoInfo> {
        val address = options["address"] as? String
            ?: throw IllegalStateException("Bitcoin address required for transaction creation")

        val utxosJson = rpcCallJson("listunspent", listOf(0, 9999999, listOf(address))).jsonArray

        return utxosJson.map { utxoJson ->
            val utxo = utxoJson.jsonObject
            UtxoInfo(
                txid = utxo["txid"]?.jsonPrimitive?.content ?: "",
                vout = utxo["vout"]?.jsonPrimitive?.int ?: 0,
                scriptPubKey = ScriptPubKey(
                    hex = utxo["scriptPubKey"]?.jsonPrimitive?.content ?: ""
                ),
                amountSats = utxo["amount"]?.jsonPrimitive?.content
                    ?.let { BitcoinAmounts.btcToSatoshis(it) } ?: 0L
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
     * Get transaction block hash (null while the transaction is unconfirmed).
     */
    private suspend fun getTransactionBlockHash(txHash: String): String? {
        return try {
            val txJson = rpcCallJson("getrawtransaction", listOf(txHash, 1)).jsonObject
            txJson["blockhash"]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get the block timestamp (epoch seconds) from a block hash.
     */
    private suspend fun getBlockTime(blockHash: String): Long? {
        return try {
            val blockJson = rpcCallJson("getblock", listOf(blockHash)).jsonObject
            blockJson["time"]?.jsonPrimitive?.long
        } catch (e: Exception) {
            null
        }
    }

    /**
     * UTXO information. [amountSats] is the output value in integer satoshis.
     */
    private data class UtxoInfo(
        val txid: String,
        val vout: Int,
        val scriptPubKey: ScriptPubKey,
        val amountSats: Long
    )

    private data class ScriptPubKey(
        val hex: String
    )

    override fun close() {
        httpClient.dispatcher.executorService.shutdown()
    }
}

/**
 * Exact Bitcoin amount conversions. All wallet math in this plugin is integer
 * satoshis end-to-end; decimal BTC strings only appear at the JSON-RPC boundary.
 */
internal object BitcoinAmounts {

    /** Number of satoshis in one BTC. */
    const val SATOSHIS_PER_BTC: Long = 100_000_000L

    /**
     * Parses a decimal BTC amount (raw JSON number text, e.g. `"0.00012345"`)
     * into Long satoshis, exactly.
     *
     * @throws ArithmeticException if the value has more than 8 decimal places
     *   or does not fit in a Long
     */
    fun btcToSatoshis(btc: String): Long =
        java.math.BigDecimal(btc).movePointRight(8).longValueExact()

    /**
     * Formats satoshis as a plain decimal BTC string (never scientific
     * notation, never floating point).
     */
    fun satoshisToBtcString(satoshis: Long): String =
        java.math.BigDecimal.valueOf(satoshis).movePointLeft(8).stripTrailingZeros().toPlainString()
}

