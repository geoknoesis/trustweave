package org.trustweave.anchor.bitcoin

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.Utils
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.script.ScriptBuilder
import org.junit.jupiter.api.Test
import org.trustweave.anchor.AnchorRef
import org.trustweave.anchor.AnchorResult
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals
import kotlin.test.assertFails

/**
 * What a verifier may conclude from the transaction a Bitcoin node hands back.
 *
 * A txid is the hash of the transaction's own bytes, so bitcoinj recomputes it from whatever hex
 * the node returned. That makes the check here stronger than an equality test on two strings the
 * node controls: if the returned bytes are not the transaction that was asked for, their hash
 * cannot match the requested id. Without the check, a node that answers `getrawtransaction` with a
 * different transaction has its OP_RETURN payload compared against the caller's, and `verifyAnchor`
 * reports on an anchor that was never read.
 */
class BitcoinAnchorReadIntegrityTest {
    private val params = MainNetParams.get()
    private val payloadJson = """{"anchored":"payload"}"""

    private fun clientFor(options: Map<String, Any?>) = BitcoinBlockchainAnchorClient(BitcoinBlockchainAnchorClient.MAINNET, options)

    private suspend fun read(
        options: Map<String, Any?>,
        txHash: String,
    ): AnchorResult =
        clientFor(options).readPayload(
            AnchorRef(chainId = BitcoinBlockchainAnchorClient.MAINNET, txHash = txHash),
        )

    /** A real transaction carrying [payloadJson] in an OP_RETURN, plus its genuine txid. */
    private fun opReturnTransaction(): Pair<String, String> {
        val tx = Transaction(params)
        // A transaction with no inputs serializes with a segwit marker that the parser then reads
        // back as a superfluous witness record, so give it one dummy input.
        tx.addInput(org.bitcoinj.core.Sha256Hash.ZERO_HASH, 0L, ScriptBuilder.createEmpty())
        tx.addOutput(
            org.bitcoinj.core.Coin.ZERO,
            ScriptBuilder.createOpReturnScript(payloadJson.toByteArray(StandardCharsets.UTF_8)),
        )
        return Utils.HEX.encode(tx.bitcoinSerialize()) to tx.txId.toString()
    }

    private fun withRpc(
        rawTxHex: String,
        block: (options: Map<String, Any?>) -> Unit,
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val request = String(exchange.requestBody.readBytes(), StandardCharsets.UTF_8)
            // getrawtransaction with verbosity returns an object; without it, the raw hex string.
            val result =
                if (request.contains("\"getrawtransaction\"") && request.contains(", 1")) {
                    """{"blockhash":null}"""
                } else {
                    "\"$rawTxHex\""
                }
            val body = """{"result":$result,"error":null,"id":"1"}"""
            val bytes = body.toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            block(
                mapOf(
                    "rpcUrl" to "http://127.0.0.1:${server.address.port}",
                    "rpcUser" to "user",
                    "rpcPassword" to "password",
                    "network" to "mainnet",
                ),
            )
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `the transaction that was asked for reads back its payload`() {
        val (rawHex, realTxId) = opReturnTransaction()

        withRpc(rawHex) { options ->
            runBlocking {
                val result = read(options, realTxId)

                assertEquals("application/json", result.mediaType)
            }
        }
    }

    @Test
    fun `a node answering with a different transaction is rejected`() {
        val (rawHex, _) = opReturnTransaction()
        val requested = "0".repeat(64)

        withRpc(rawHex) { options ->
            runBlocking {
                assertFails("Raw transaction bytes whose hash is not the requested txid must be rejected") {
                    runBlocking { read(options, requested) }
                }
            }
        }
    }
}
