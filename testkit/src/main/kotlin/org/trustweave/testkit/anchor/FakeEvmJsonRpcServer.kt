package org.trustweave.testkit.anchor

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

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
 * @param headBlockHex current chain head (must be above [blockNumberHex] for the tx to be buried)
 */
class FakeEvmJsonRpcServer(
    private val txHash: String,
    private val payloadJson: String = """{"anchored":"payload"}""",
    private val status: String = "0x1",
    private val from: String = DEFAULT_SENDER,
    private val to: String? = from,
    private val blockNumberHex: String = "0x1",
    private val headBlockHex: String = "0x64",
) : AutoCloseable {
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    /** `http://127.0.0.1:<port>` — loopback, so plaintext passes the clients' transport checks. */
    val url: String

    init {
        server.createContext("/") { exchange ->
            val request = String(exchange.requestBody.readBytes(), StandardCharsets.UTF_8)
            val bytes = respond(request).toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        url = "http://127.0.0.1:${server.address.port}"
    }

    private fun respond(request: String): String {
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
                    """{"hash":"$txHash","nonce":"0x0","blockHash":"$blockHash","blockNumber":"$blockNumberHex",
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
    }

    companion object {
        /** Sender used when none is given; anchors are self-sends, so it is also the recipient. */
        const val DEFAULT_SENDER: String = "0x2222222222222222222222222222222222222222"
    }
}
