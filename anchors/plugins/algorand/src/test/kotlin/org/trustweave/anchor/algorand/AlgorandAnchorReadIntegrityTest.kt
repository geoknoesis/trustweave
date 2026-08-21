package org.trustweave.anchor.algorand

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.anchor.AnchorRef
import org.trustweave.anchor.AnchorResult
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFails

/**
 * What a verifier may conclude from the transaction an Algorand indexer hands back.
 *
 * `readPayload` takes the note field of whatever `lookupTransaction` returns and hands it to
 * `verifyAnchor` as the anchored payload. The indexer is a single, separately operated service, so
 * the transaction id it reports must be checked against the one that was asked for rather than
 * assumed — otherwise a wrong or malicious answer has its note compared against the caller's
 * payload, and the result describes an anchor that was never read.
 */
class AlgorandAnchorReadIntegrityTest {
    private val chainId = "algorand:mainnet"
    private val requestedTxId = "REQUESTEDTXID000000000000000000000000000000000000000000"
    private val otherTxId = "OTHERTXID00000000000000000000000000000000000000000000000"
    private val payloadJson = """{"anchored":"payload"}"""

    private fun withIndexer(
        reportedTxId: String,
        block: (options: Map<String, Any?>) -> Unit,
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val note = Base64.getEncoder().encodeToString(payloadJson.toByteArray(StandardCharsets.UTF_8))
            val body =
                """
                {"current-round":1000,
                 "transaction":{"id":"$reportedTxId","tx-type":"pay","round-time":1700000000,
                   "note":"$note","fee":1000,"first-valid":1,"last-valid":1000,
                   "sender":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"}}
                """.trimIndent()
            val bytes = body.toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            block(
                mapOf(
                    "algodUrl" to "http://127.0.0.1:${server.address.port}",
                    "indexerUrl" to "http://127.0.0.1:${server.address.port}",
                ),
            )
        } finally {
            server.stop(0)
        }
    }

    private suspend fun read(
        options: Map<String, Any?>,
        txHash: String,
    ): AnchorResult =
        AlgorandBlockchainAnchorClient(chainId, options)
            .readPayload(AnchorRef(chainId = chainId, txHash = txHash))

    @Test
    fun `the transaction that was asked for reads back its payload`() {
        withIndexer(reportedTxId = requestedTxId) { options ->
            runBlocking {
                val result = read(options, requestedTxId)

                assertEquals("application/json", result.mediaType)
            }
        }
    }

    @Test
    fun `an indexer answering with a different transaction is rejected`() {
        withIndexer(reportedTxId = otherTxId) { options ->
            runBlocking {
                assertFails("An indexer transaction whose id is not the requested one must be rejected") {
                    runBlocking { read(options, requestedTxId) }
                }
            }
        }
    }
}
