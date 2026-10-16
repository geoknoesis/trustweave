package org.trustweave.anchor.bitcoin

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList

/** Scripted bitcoind JSON-RPC: [handler] gets (method, params) and returns the raw `result` JSON or throws [RpcError]. */
internal class BitcoinRpcStub(
    private val handler: (String, JsonArray) -> String,
) : AutoCloseable {
    class RpcError(
        val rpcMessage: String,
    ) : RuntimeException(rpcMessage)

    val calls = CopyOnWriteArrayList<Pair<String, JsonArray>>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val options: Map<String, Any?>

    init {
        server.createContext("/") { exchange ->
            val request = Json.parseToJsonElement(String(exchange.requestBody.readBytes(), StandardCharsets.UTF_8)).jsonObject
            val method = request["method"]!!.jsonPrimitive.content
            val params = request["params"]?.jsonArray ?: JsonArray(emptyList())
            calls += method to params
            val body =
                try {
                    """{"result":${handler(method, params)},"error":null,"id":"1"}"""
                } catch (e: RpcError) {
                    """{"result":null,"error":{"code":-5,"message":"${e.rpcMessage}"},"id":"1"}"""
                }.toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        options =
            mapOf(
                "rpcUrl" to "http://127.0.0.1:${server.address.port}",
                "rpcUser" to "user",
                "rpcPassword" to "password",
                "network" to "mainnet",
            )
    }

    override fun close() = server.stop(0)
}
