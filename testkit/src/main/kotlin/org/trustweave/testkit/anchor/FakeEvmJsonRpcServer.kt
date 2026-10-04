package org.trustweave.testkit.anchor

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Loopback JSON-RPC stub that answers the calls an EVM anchor client makes when it READS an anchor
 * (`eth_getTransactionReceipt`, `eth_getTransactionByHash`, `eth_blockNumber`,
 * `eth_getBlockByNumber`) with one scripted transaction.
 *
 * Use it to test the read-path checks of EVM plugins (reverted receipts, unexpected sender or
 * recipient, …) without a node:
 *
 * ```kotlin
 * FakeEvmJsonRpcServer(txHash, status = "0x0").use { rpc ->
 *     val client = SomeEvmClient(chainId, mapOf("rpcUrl" to rpc.url))
 *     assertFails { client.readPayload(AnchorRef(chainId, txHash)) }
 * }
 * ```
 *
 * @param txHash the transaction hash every receipt/transaction answer carries
 * @param payloadJson the anchored payload returned as calldata
 * @param status receipt status (`0x1` success, `0x0` reverted)
 * @param from transaction sender
 * @param to transaction recipient; `null` for a contract creation
 * @param blockNumberHex block containing the transaction
 * It also answers the WRITE path (`eth_gasPrice`, `eth_getTransactionCount`, `eth_estimateGas`,
 * `eth_sendRawTransaction`) like a node with one account: the pending nonce advances by one for every
 * accepted raw transaction, which are recorded in [rawTransactions]. [nonceReadDelayMs] widens the window
 * between reading a nonce and sending, so a client that does not serialise its submissions visibly reuses
 * nonces; [failNextSends] scripts `eth_sendRawTransaction` errors such as "nonce too low". Requests are
 * handled on a thread pool, so concurrent callers really overlap.
 *
 * @param headBlockHex current chain head (must be above [blockNumberHex] for the tx to be buried)
 * @param txBlockHash block hash the TRANSACTION reports (the receipt always reports `0x11…11`); default is the receipt's
 * @param txBlockNumberHex block number the TRANSACTION reports; default is [blockNumberHex]
 * @param nonceReadDelayMs artificial server-side delay of `eth_getTransactionCount`
 */
class FakeEvmJsonRpcServer
    @JvmOverloads
    constructor(
        private val txHash: String,
        private val payloadJson: String = """{"anchored":"payload"}""",
        private val status: String = "0x1",
        private val from: String = DEFAULT_SENDER,
        private val to: String? = from,
        private val blockNumberHex: String = "0x1",
        private val headBlockHex: String = "0x64",
        private val txBlockHash: String? = null,
        private val txBlockNumberHex: String? = null,
        private val nonceReadDelayMs: Long = 0L,
    ) : AutoCloseable {
        /** Every raw transaction (0x-hex) accepted by `eth_sendRawTransaction`, in arrival order. */
        val rawTransactions: MutableList<String> = CopyOnWriteArrayList()

        /** The JSON-RPC method of every request, in arrival order. */
        val methods: MutableList<String> = CopyOnWriteArrayList()

        /** Messages for the next `eth_sendRawTransaction` calls to fail with; each is consumed by one call. */
        private val sendErrors = ConcurrentLinkedQueue<String>()

        /** The account's pending transaction count, as returned by `eth_getTransactionCount`. */
        val pendingNonce = AtomicLong(0)

        /** Number of requests currently being handled, and the highest value ever seen. */
        private val inFlight = AtomicInteger()
        val maxConcurrentRequests = AtomicInteger()

        /**
         * Makes the next `eth_sendRawTransaction` calls fail with each of [messages] in turn. A message
         * containing "nonce too low" also advances [pendingNonce], as if another sender had used the nonce.
         */
        fun failNextSends(vararg messages: String) {
            sendErrors.addAll(messages)
        }

        private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

        /** `http://127.0.0.1:<port>` — loopback, so plaintext passes the clients' transport checks. */
        val url: String

        init {
            server.executor = Executors.newCachedThreadPool()
            server.createContext("/") { exchange ->
                val request = String(exchange.requestBody.readBytes(), StandardCharsets.UTF_8)
                maxConcurrentRequests.accumulateAndGet(inFlight.incrementAndGet()) { a, b -> maxOf(a, b) }
                val bytes =
                    try {
                        respond(request).toByteArray(StandardCharsets.UTF_8)
                    } finally {
                        inFlight.decrementAndGet()
                    }
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.start()
            url = "http://127.0.0.1:${server.address.port}"
        }

        private fun method(request: String): String = Regex("\"method\"\\s*:\\s*\"([^\"]+)\"").find(request)?.groupValues?.get(1) ?: ""

        private fun respond(request: String): String {
            methods += method(request)
            when (method(request)) {
                "eth_gasPrice" -> return """{"jsonrpc":"2.0","id":${requestId(request)},"result":"0x3b9aca00"}"""
                "eth_estimateGas" -> return """{"jsonrpc":"2.0","id":${requestId(request)},"result":"0x5208"}"""
                "eth_getTransactionCount" -> {
                    val nonce = pendingNonce.get()
                    if (nonceReadDelayMs > 0) Thread.sleep(nonceReadDelayMs)
                    return """{"jsonrpc":"2.0","id":${requestId(request)},"result":"0x${nonce.toString(16)}"}"""
                }
                "eth_sendRawTransaction" -> {
                    val failure = sendErrors.poll()
                    if (failure != null) {
                        if (failure.contains("nonce too low", ignoreCase = true)) pendingNonce.incrementAndGet()
                        return """{"jsonrpc":"2.0","id":${requestId(request)},"error":{"code":-32000,"message":"$failure"}}"""
                    }
                    val raw = Regex("\"params\"\\s*:\\s*\\[\\s*\"(0x[0-9a-fA-F]+)\"").find(request)?.groupValues?.get(1)
                    if (raw != null) rawTransactions += raw
                    pendingNonce.incrementAndGet()
                    return """{"jsonrpc":"2.0","id":${requestId(request)},"result":"$txHash"}"""
                }
            }
            val toJson = to?.let { "\"$it\"" } ?: "null"
            val blockHash = "0x" + "11".repeat(32)
            val zero32 = "0x" + "00".repeat(32)
            val result =
                when {
                    "eth_getTransactionReceipt" in request ->
                        """{"transactionHash":"$txHash","transactionIndex":"0x0","blockHash":"$blockHash",
                    "blockNumber":"$blockNumberHex","cumulativeGasUsed":"0x5208","gasUsed":"0x5208",
                    "status":"$status","from":"$from","to":$toJson,"logs":[],"logsBloom":"0x0"}"""
                    "eth_getTransactionByHash" in request ->
                        """{"hash":"$txHash","nonce":"0x0","blockHash":"${txBlockHash ?: blockHash}","blockNumber":"${txBlockNumberHex ?: blockNumberHex}",
                    "transactionIndex":"0x0","from":"$from","to":$toJson,"value":"0x0","gas":"0x5208",
                    "gasPrice":"0x1","input":"${hex(payloadJson)}"}"""
                    "eth_blockNumber" in request -> "\"$headBlockHex\""
                    "eth_getBlockByNumber" in request ->
                        """{"number":"$blockNumberHex","hash":"$blockHash","parentHash":"$zero32","nonce":"0x0",
                    "sha3Uncles":"$zero32","logsBloom":"0x0","transactionsRoot":"$zero32","stateRoot":"$zero32",
                    "receiptsRoot":"$zero32","miner":"$from","difficulty":"0x1","totalDifficulty":"0x1",
                    "extraData":"0x","size":"0x1","gasLimit":"0x5208","gasUsed":"0x5208",
                    "timestamp":"0x64000000","transactions":[],"uncles":[]}"""
                    else -> "null"
                }
            return """{"jsonrpc":"2.0","id":${requestId(request)},"result":$result}"""
        }

        private fun requestId(request: String): String = Regex("\"id\"\\s*:\\s*(\\d+)").find(request)?.groupValues?.get(1) ?: "1"

        private fun hex(text: String): String = "0x" + text.toByteArray(StandardCharsets.UTF_8).joinToString("") { "%02x".format(it) }

        override fun close() {
            server.stop(0)
            (server.executor as? java.util.concurrent.ExecutorService)?.shutdownNow()
        }

        companion object {
            /** Sender used when none is given; anchors are self-sends, so it is also the recipient. */
            const val DEFAULT_SENDER: String = "0x2222222222222222222222222222222222222222"
        }
    }
