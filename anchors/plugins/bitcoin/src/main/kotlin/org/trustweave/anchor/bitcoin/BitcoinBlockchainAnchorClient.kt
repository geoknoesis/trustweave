package org.trustweave.anchor.bitcoin

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonUnquotedLiteral
import kotlinx.serialization.json.add
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.bitcoinj.core.Transaction
import org.bitcoinj.script.ScriptPattern
import org.trustweave.anchor.AbstractBlockchainAnchorClient
import org.trustweave.anchor.AnchorResult
import org.trustweave.anchor.exceptions.BlockchainException
import org.trustweave.core.exception.TrustWeaveException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.HexFormat

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
 * **Reads** only succeed for a transaction that is in a block with at least `minConfirmations` blocks on
 * top of it (default 1; the block must also be on the best chain and list the transaction). An RPC error while
 * resolving the block is an error, never "unconfirmed". The optional `expectedSender` option (an address) requires
 * every input of the anchor transaction to spend an output of that address.
 *
 * **Writes** spend only confirmed outputs (`listunspent` minconf 1), pick inputs largest-first until the fee is
 * covered rather than spending everything, price the transaction at the `estimatesmartfee` rate
 * (`confirmationTarget`, default 6 blocks; `fallbackFeeRateSatPerVb` is used only when the node cannot estimate;
 * `maxFeeRateSatPerVb` rejects a higher rate) times a script-type-aware virtual size, and drop a change output
 * below the dust threshold into the fee.
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
    options: Map<String, Any?> = emptyMap(),
) : AbstractBlockchainAnchorClient(chainId, options),
    java.io.Closeable {
    companion object {
        const val MAINNET = "bip122:000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f"
        const val TESTNET = "bip122:000000000933ea01ad0ee984209779baaec3ced90fa3f408719526f8d77f4943"

        // OP_RETURN data limit (80 bytes)
        private const val OP_RETURN_MAX_SIZE = 80

        /** Option: blocks (including the anchor's own) required on the best chain before a read (default 1). */
        const val OPTION_MIN_CONFIRMATIONS = "minConfirmations"

        /** Option: address that must fund every input of an anchor transaction being read. */
        const val OPTION_EXPECTED_SENDER = "expectedSender"

        /** Option: `estimatesmartfee` confirmation target in blocks (default 6). */
        const val OPTION_CONFIRMATION_TARGET = "confirmationTarget"

        /** Option: fee rate in sat/vB used only when the node cannot estimate one. */
        const val OPTION_FALLBACK_FEE_RATE = "fallbackFeeRateSatPerVb"

        /** Option: a quoted fee rate above this (sat/vB) fails the write instead of being paid. */
        const val OPTION_MAX_FEE_RATE = "maxFeeRateSatPerVb"

        private const val DEFAULT_MIN_CONFIRMATIONS = 1L
        private const val DEFAULT_CONFIRMATION_TARGET = 6
    }

    private val networkName: String
    private val rpcUrl: String?
    private val rpcUser: String?
    private val rpcPassword: String?
    private val httpClient: OkHttpClient

    init {
        val network = options["network"] as? String ?: "mainnet"
        when (network.lowercase()) {
            "mainnet", "testnet", "testnet3" -> Unit
            else -> throw IllegalArgumentException("Unsupported Bitcoin network: $network. Use 'mainnet' or 'testnet'")
        }
        networkName = network

        // Refused at construction, not at first use: an operator who mistyped https as http should
        // find out when the client is built, not when a signed transaction and the RPC credentials
        // have already crossed the open internet in the clear. Loopback and private hosts stay on
        // plaintext - that is the documented local-node setup.
        rpcUrl =
            (options["rpcUrl"] as? String)?.let {
                try {
                    org.trustweave.core.net.TransportSecurity.requireSecureForPublicHosts(
                        it,
                        "Bitcoin RPC credentials and signed transactions",
                    )
                } catch (failure: IllegalArgumentException) {
                    throw BlockchainException.ConfigurationFailed(
                        chainId = chainId,
                        configKey = "rpcUrl",
                        reason = "Bitcoin RPC requires HTTPS for public hosts and forbids embedded credentials",
                        cause = failure,
                    )
                }
            }
        rpcUser = options["rpcUser"] as? String
        rpcPassword = options["rpcPassword"] as? String

        // Create HTTP client with basic auth for RPC
        val clientBuilder = OkHttpClient.Builder()
        if (rpcUser != null && rpcPassword != null) {
            val credentials = okhttp3.Credentials.basic(rpcUser, rpcPassword)
            clientBuilder.addInterceptor { chain ->
                val originalRequest = chain.request()
                val authenticatedRequest =
                    originalRequest
                        .newBuilder()
                        .header("Authorization", credentials)
                        .build()
                chain.proceed(authenticatedRequest)
            }
        }
        httpClient = clientBuilder.build()
    }

    protected override fun canSubmitTransaction(): Boolean {
        // Check if RPC credentials are provided
        return options["rpcUrl"] != null &&
            options["rpcUser"] != null &&
            options["rpcPassword"] != null
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    protected override suspend fun submitTransactionToBlockchain(payloadBytes: ByteArray): String =
        withContext(Dispatchers.IO) {
            // Bitcoin OP_RETURN has 80-byte limit
            if (payloadBytes.size > OP_RETURN_MAX_SIZE) {
                throw BlockchainException.TransactionFailed(
                    reason =
                        "Payload size (${payloadBytes.size} bytes) exceeds Bitcoin OP_RETURN limit " +
                            "($OP_RETURN_MAX_SIZE bytes). Consider using hash-based anchoring.",
                    chainId = chainId,
                    txHash = null,
                    operation = "submitTransaction",
                    payloadSize = payloadBytes.size.toLong(),
                )
            }

            if (rpcUrl == null || rpcUser == null || rpcPassword == null) {
                throw IllegalStateException(
                    "Bitcoin RPC credentials not configured. Provide 'rpcUrl', 'rpcUser', and 'rpcPassword' in options.",
                )
            }

            // Use Bitcoin RPC to create and send transaction with OP_RETURN
            // This is a simplified approach - full implementation would use BitcoinJ for transaction building
            // For now, we'll use RPC's createrawtransaction and signrawtransaction

            val address =
                options["address"] as? String
                    ?: throw IllegalStateException("Bitcoin address required for transaction creation")

            // Get confirmed unspent outputs
            val utxos = getUnspentOutputs()
            if (utxos.isEmpty()) {
                throw BlockchainException.TransactionFailed(
                    reason = "No confirmed unspent outputs available for transaction",
                    chainId = chainId,
                    txHash = null,
                    operation = "submitTransaction",
                    payloadSize = payloadBytes.size.toLong(),
                )
            }

            val feeRate = resolveFeeRate()
            val plan =
                BitcoinTxPlanner.plan(
                    utxos = utxos.map { BitcoinTxPlanner.Utxo(it.txid, it.vout, it.amountSats, it.scriptPubKey.hex) },
                    payloadSize = payloadBytes.size,
                    feeRateSatPerVb = feeRate,
                )
                    ?: throw BlockchainException.TransactionFailed(
                        reason =
                            "Insufficient funds: ${utxos.sumOf { it.amountSats }} sats in confirmed outputs " +
                                "cannot cover the fee at $feeRate sat/vB",
                        chainId = chainId,
                        txHash = null,
                        operation = "submitTransaction",
                        payloadSize = payloadBytes.size.toLong(),
                    )

            // Build transaction inputs (only the selected ones)
            val inputs =
                plan.inputs.map { utxo ->
                    buildJsonObject {
                        put("txid", utxo.txid)
                        put("vout", utxo.vout)
                    }
                }

            // Build transaction outputs
            // OP_RETURN output: data in hex
            val opReturnHex = HexFormat.of().formatHex(payloadBytes)
            // All amount math is integer satoshis; BTC decimals only appear (exactly)
            // at the JSON-RPC boundary.
            val changeSats = plan.changeSats
            val outputs =
                buildJsonObject {
                    put("data", opReturnHex)
                    if (changeSats > 0) {
                        // Exact decimal BTC value (plain notation, never floating point).
                        put(address, JsonUnquotedLiteral(BitcoinAmounts.satoshisToBtcString(changeSats)))
                    }
                }

            // Create raw transaction via RPC
            val rawTx = rpcCall("createrawtransaction", listOf(inputs, outputs))

            // Sign transaction
            val privateKey =
                options["privateKey"] as? String
                    ?: throw IllegalStateException("Private key required for signing Bitcoin transactions")
            val signedTxJson = rpcCallJson("signrawtransactionwithkey", listOf(rawTx, listOf(privateKey))).jsonObject

            // Extract signed transaction hex
            val signedTxHex =
                signedTxJson["hex"]?.jsonPrimitive?.content
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

    protected override suspend fun readTransactionFromBlockchain(txHash: String): AnchorResult =
        withContext(Dispatchers.IO) {
            if (rpcUrl == null || rpcUser == null || rpcPassword == null) {
                throw IllegalStateException("Bitcoin RPC credentials not configured")
            }

            // Get transaction via RPC
            val txHex = getRawTransaction(txHash)
            val tx = Transaction.read(ByteBuffer.wrap(HexFormat.of().parseHex(txHex)))

            // A txid is the hash of the transaction's own bytes, so this recomputes it rather than
            // comparing two strings the node controls: bytes that are not the requested transaction
            // cannot hash to its id. Without this a node answering getrawtransaction with a different
            // transaction has its OP_RETURN compared against the caller's payload, and verifyAnchor
            // reports on an anchor that was never read.
            val returnedTxId = tx.txId.toString()
            if (!returnedTxId.equals(txHash, ignoreCase = true)) {
                throw BlockchainException.TransactionFailed(
                    chainId = chainId,
                    txHash = txHash,
                    operation = "readPayload",
                    reason =
                        "RPC returned transaction $returnedTxId for requested $txHash; " +
                            "refusing to read an anchor from it",
                )
            }

            // Find OP_RETURN output
            var opReturnData: ByteArray? = null
            for (output in tx.outputs) {
                val script = output.scriptPubKey
                // Check if script is OP_RETURN (starts with OP_RETURN opcode)
                if (ScriptPattern.isOpReturn(script) ||
                    (script.chunks.isNotEmpty() && script.chunks[0].opcode == org.bitcoinj.script.ScriptOpCodes.OP_RETURN)
                ) {
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

            // The anchor only counts once it is in a block on the best chain, buried to the configured
            // depth; the timestamp is that block's time. RPC failures here propagate: they are not evidence
            // that the transaction is unconfirmed, and must not be read as such.
            val blockTime = requireSettled(txHash)

            AnchorResult(
                ref =
                    buildAnchorRef(
                        txHash = txHash,
                        contract = null,
                    ),
                payload = payload,
                mediaType = "application/json",
                timestamp = blockTime,
            )
        }

    protected override fun getContractAddress(): String? {
        // Bitcoin doesn't use contracts
        return null
    }

    protected override fun buildExtraMetadata(mediaType: String): Map<String, String> =
        mapOf(
            "network" to networkName,
            "mediaType" to mediaType,
            "protocol" to "OP_RETURN",
        )

    protected override fun generateTestTxHash(): String {
        // Generate a unique test transaction hash (64 hex characters, like a Bitcoin txid)
        return uniqueTestHashHex()
    }

    protected override fun getBlockchainName(): String = "Bitcoin"

    /**
     * Bitcoin RPC call helper returning the raw `result` element. Use this for
     * methods whose result is a JSON object or array (`listunspent`, `getblock`,
     * `signrawtransactionwithkey`, verbose `getrawtransaction`, …).
     */
    private suspend fun rpcCallJson(
        method: String,
        params: List<Any?>,
    ): JsonElement =
        withContext(Dispatchers.IO) {
            val requestBody =
                buildJsonObject {
                    put("jsonrpc", "1.0")
                    put("id", "TrustWeave")
                    put("method", method)
                    put(
                        "params",
                        buildJsonArray {
                            params.forEach { param ->
                                when (param) {
                                    is JsonElement -> add(param)
                                    is String -> add(param)
                                    is Number -> add(param)
                                    is Boolean -> add(param)
                                    is List<*> -> {
                                        // Handle list of addresses, keys, or pre-built JSON elements
                                        add(
                                            buildJsonArray {
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
                                                            add(
                                                                buildJsonObject {
                                                                    mapItem?.forEach { (key, value) ->
                                                                        put(key, value.toString())
                                                                    }
                                                                },
                                                            )
                                                        }
                                                        else -> add(item.toString())
                                                    }
                                                }
                                            },
                                        )
                                    }
                                    is Map<*, *> -> {
                                        // Handle map (for transaction outputs)
                                        @Suppress("UNCHECKED_CAST")
                                        val mapParam = param as? Map<String, Any?>
                                        add(
                                            buildJsonObject {
                                                mapParam?.forEach { (key, value) ->
                                                    when (value) {
                                                        is String -> put(key, value)
                                                        is Number -> put(key, value)
                                                        is Boolean -> put(key, value)
                                                        else -> put(key, value.toString())
                                                    }
                                                }
                                            },
                                        )
                                    }
                                    null -> add(JsonNull)
                                    else -> add(param.toString())
                                }
                            }
                        },
                    )
                }

            val request =
                Request
                    .Builder()
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
                throw TrustWeaveException.Unknown(
                    message = "Bitcoin RPC error: ${error.jsonObject["message"]?.jsonPrimitive?.content ?: "Unknown error"}",
                )
            }

            jsonResponse["result"]?.takeIf { it !is JsonNull }
                ?: throw TrustWeaveException.Unknown(message = "No result in Bitcoin RPC response")
        }

    /**
     * Bitcoin RPC call helper for methods whose result is a primitive
     * (e.g. `createrawtransaction`, `sendrawtransaction`, `getrawtransaction`).
     */
    private suspend fun rpcCall(
        method: String,
        params: List<Any?>,
    ): String = rpcCallJson(method, params).jsonPrimitive.content

    /**
     * Get unspent transaction outputs. Amounts are parsed exactly into Long
     * satoshis (the RPC reports decimal BTC).
     */
    private suspend fun getUnspentOutputs(): List<UtxoInfo> {
        val address =
            options["address"] as? String
                ?: throw IllegalStateException("Bitcoin address required for transaction creation")

        val utxosJson = rpcCallJson("listunspent", listOf(1, 9999999, listOf(address))).jsonArray

        return utxosJson.map { utxoJson ->
            val utxo = utxoJson.jsonObject
            UtxoInfo(
                txid = utxo["txid"]?.jsonPrimitive?.content ?: "",
                vout = utxo["vout"]?.jsonPrimitive?.int ?: 0,
                scriptPubKey =
                    ScriptPubKey(
                        hex = utxo["scriptPubKey"]?.jsonPrimitive?.content ?: "",
                    ),
                amountSats =
                    utxo["amount"]
                        ?.jsonPrimitive
                        ?.content
                        ?.let { BitcoinAmounts.btcToSatoshis(it) } ?: 0L,
            )
        }
    }

    /**
     * Send raw transaction via RPC.
     */
    private suspend fun sendRawTransaction(txHex: String): String = rpcCall("sendrawtransaction", listOf(txHex))

    /**
     * Get raw transaction hex via RPC.
     */
    private suspend fun getRawTransaction(txHash: String): String = rpcCall("getrawtransaction", listOf(txHash))

    /**
     * Asserts [txHash] is in a block on the best chain with at least [minConfirmations] blocks (itself
     * included) and returns the block time (epoch seconds).
     */
    private suspend fun requireSettled(txHash: String): Long {
        fun reject(reason: String): Nothing =
            throw BlockchainException.TransactionFailed(
                chainId = chainId,
                txHash = txHash,
                operation = "readPayload",
                reason = reason,
            )

        val txJson = rpcCallJson("getrawtransaction", listOf(txHash, 1)).jsonObject
        val blockHash =
            txJson["blockhash"]
                ?.takeIf { it !is JsonNull }
                ?.jsonPrimitive
                ?.content
                ?.takeIf { it.isNotBlank() }
                ?: reject("Transaction is not in a block (unconfirmed); refusing to read it as an anchor")
        requireExpectedSender(txHash, txJson)

        val block = rpcCallJson("getblock", listOf(blockHash)).jsonObject
        val confirmations =
            block["confirmations"]?.jsonPrimitive?.long
                ?: reject("Node did not report confirmations for block $blockHash")
        if (confirmations < 1) {
            reject("Block $blockHash is not on the best chain (confirmations=$confirmations); refusing to read an anchor from it")
        }
        val listed = block["tx"]?.jsonArray?.map { it.jsonPrimitive.content }
        if (listed != null && listed.none { it.equals(txHash, ignoreCase = true) }) {
            reject("Block $blockHash does not list transaction $txHash; refusing to read an anchor from it")
        }
        if (confirmations < minConfirmations) {
            reject(
                "Transaction has $confirmations confirmation(s); $minConfirmations required. " +
                    "Configure via '$OPTION_MIN_CONFIRMATIONS'",
            )
        }
        return block["time"]?.jsonPrimitive?.long ?: reject("Node did not report a time for block $blockHash")
    }

    private val minConfirmations: Long
        get() =
            when (val v = options[OPTION_MIN_CONFIRMATIONS]) {
                is Number -> v.toLong()
                is String -> v.toLongOrNull() ?: DEFAULT_MIN_CONFIRMATIONS
                else -> DEFAULT_MIN_CONFIRMATIONS
            }.coerceAtLeast(1L)

    /**
     * When [OPTION_EXPECTED_SENDER] is set, every input of the transaction must spend an output paying that
     * address (looked up from the funding transactions); coinbase inputs never qualify.
     */
    private suspend fun requireExpectedSender(
        txHash: String,
        txJson: kotlinx.serialization.json.JsonObject,
    ) {
        val expected = (options[OPTION_EXPECTED_SENDER] as? String)?.takeIf { it.isNotBlank() } ?: return

        fun reject(reason: String): Nothing =
            throw BlockchainException.TransactionFailed(
                chainId = chainId,
                txHash = txHash,
                operation = "readPayload",
                reason = reason,
            )

        val vin = txJson["vin"]?.jsonArray ?: reject("Node did not report the inputs of the transaction; cannot check the sender")
        if (vin.isEmpty()) reject("Transaction has no inputs; cannot check the sender")
        for (input in vin) {
            val spent = input.jsonObject
            val prevTxId =
                spent["txid"]?.jsonPrimitive?.content
                    ?: reject("Transaction input does not spend an output (coinbase); not an anchor from $expected")
            val prevVout = spent["vout"]?.jsonPrimitive?.int ?: reject("Transaction input has no output index")
            val prev = rpcCallJson("getrawtransaction", listOf(prevTxId, 1)).jsonObject
            val output =
                prev["vout"]?.jsonArray?.firstOrNull { it.jsonObject["n"]?.jsonPrimitive?.int == prevVout }?.jsonObject
                    ?: reject("Funding output $prevTxId:$prevVout not reported by the node")
            val script = output["scriptPubKey"]?.jsonObject
            val address =
                script?.get("address")?.jsonPrimitive?.content
                    ?: script
                        ?.get("addresses")
                        ?.jsonArray
                        ?.singleOrNull()
                        ?.jsonPrimitive
                        ?.content
            if (address != expected) {
                reject(
                    "Transaction input $prevTxId:$prevVout is funded by ${address ?: "a non-address script"}, not the expected anchoring address $expected",
                )
            }
        }
    }

    /**
     * The fee rate to pay, in sat/vB: the node's `estimatesmartfee` for the configured confirmation target
     * (never below the 1 sat/vB relay floor), or [OPTION_FALLBACK_FEE_RATE] when the node cannot estimate.
     * Fails closed when neither is available or the rate exceeds [OPTION_MAX_FEE_RATE].
     */
    private suspend fun resolveFeeRate(): java.math.BigDecimal {
        val target = (options[OPTION_CONFIRMATION_TARGET] as? Number)?.toInt() ?: DEFAULT_CONFIRMATION_TARGET
        val estimated: java.math.BigDecimal? =
            try {
                rpcCallJson("estimatesmartfee", listOf(target))
                    .jsonObject["feerate"]
                    ?.jsonPrimitive
                    ?.content
                    // BTC per kvB -> sat per vB
                    ?.let {
                        java.math
                            .BigDecimal(it)
                            .movePointRight(8)
                            .divide(java.math.BigDecimal(1000))
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: TrustWeaveException) {
                null // The node answered with an RPC error or no result: fall back, or fail below.
            }
        val fallback =
            (options[OPTION_FALLBACK_FEE_RATE] as? Number)?.let { java.math.BigDecimal(it.toString()) }
        val rate =
            (estimated ?: fallback)?.max(java.math.BigDecimal.ONE)
                ?: throw BlockchainException.TransactionFailed(
                    reason =
                        "The node could not estimate a fee rate and no '$OPTION_FALLBACK_FEE_RATE' option is set; " +
                            "refusing to guess a fee",
                    chainId = chainId,
                    txHash = null,
                    operation = "submitTransaction",
                )
        (options[OPTION_MAX_FEE_RATE] as? Number)?.let { cap ->
            if (rate > java.math.BigDecimal(cap.toString())) {
                throw BlockchainException.TransactionFailed(
                    reason = "Fee rate $rate sat/vB exceeds the configured '$OPTION_MAX_FEE_RATE' of $cap",
                    chainId = chainId,
                    txHash = null,
                    operation = "submitTransaction",
                )
            }
        }
        return rate
    }

    /**
     * UTXO information. [amountSats] is the output value in integer satoshis.
     */
    private data class UtxoInfo(
        val txid: String,
        val vout: Int,
        val scriptPubKey: ScriptPubKey,
        val amountSats: Long,
    )

    private data class ScriptPubKey(
        val hex: String,
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
        java.math
            .BigDecimal(btc)
            .movePointRight(8)
            .longValueExact()

    /**
     * Formats satoshis as a plain decimal BTC string (never scientific
     * notation, never floating point).
     */
    fun satoshisToBtcString(satoshis: Long): String =
        java.math.BigDecimal
            .valueOf(satoshis)
            .movePointLeft(8)
            .stripTrailingZeros()
            .toPlainString()
}
