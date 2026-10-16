package org.trustweave.anchor.algorand

import com.algorand.algosdk.account.Account
import com.algorand.algosdk.transaction.Transaction
import com.algorand.algosdk.util.Encoder
import com.algorand.algosdk.v2.client.model.PendingTransactionResponse
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.Test
import org.trustweave.anchor.AnchorRef
import org.trustweave.anchor.exceptions.BlockchainException
import org.trustweave.anchor.exceptions.TreasuryException
import org.trustweave.anchor.payment.AssetRef
import org.trustweave.anchor.payment.FeeStrategy
import org.trustweave.anchor.payment.PaymentContext
import org.trustweave.anchor.payment.TokenAmount
import java.math.BigInteger
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Confirmed-round, mandatory id, sender and pool-error checks on reads, and the fee cap on the fee actually signed. */
class AlgorandReadAndFeeChecksTest {
    private val chainId = "algorand:mainnet"
    private val payloadJson = """{"anchored":"payload"}"""
    private val note = payloadJson.toByteArray(StandardCharsets.UTF_8)
    private val seed = ByteArray(32) { (it + 1).toByte() }
    private val account = Account(seed)
    private val otherAccount = Account(ByteArray(32) { (it + 50).toByte() })

    private class Node(
        val handler: (HttpExchange, String) -> String,
    ) : AutoCloseable {
        val requests = CopyOnWriteArrayList<String>()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val url: String

        init {
            server.createContext("/") { ex ->
                val path = ex.requestURI.path
                requests += "${ex.requestMethod} $path"
                val body = handler(ex, path).toByteArray()
                ex.responseHeaders.set("Content-Type", "application/json")
                ex.sendResponseHeaders(200, body.size.toLong())
                ex.responseBody.use { it.write(body) }
            }
            server.start()
            url = "http://127.0.0.1:${server.address.port}"
        }

        override fun close() = server.stop(0)
    }

    private fun indexerBody(
        id: String?,
        confirmedRound: Long?,
        sender: String,
    ): String {
        val encodedNote = Base64.getEncoder().encodeToString(note)
        val idPart = id?.let { "\"id\":\"$it\"," } ?: ""
        val roundPart = confirmedRound?.let { "\"confirmed-round\":$it," } ?: ""
        return """{"current-round":1000,"transaction":{$idPart$roundPart"tx-type":"pay","round-time":1700000000,
            "note":"$encodedNote","fee":1000,"first-valid":1,"last-valid":1000,"sender":"$sender"}}"""
    }

    private fun readViaIndexer(
        body: String,
        extra: Map<String, Any?> = emptyMap(),
        txId: String = "TX1",
    ) = Node { _, _ -> body }.use { node ->
        runBlocking {
            AlgorandBlockchainAnchorClient(chainId, mapOf("algodUrl" to node.url, "indexerUrl" to node.url) + extra)
                .readPayload(AnchorRef(chainId = chainId, txHash = txId))
        }
    }

    @Test
    fun `an indexer answer without an id is rejected`() {
        assertFailsWith<BlockchainException.TransactionFailed> { readViaIndexer(indexerBody(null, 10, account.address.toString())) }
    }

    @Test
    fun `an unconfirmed indexer transaction is rejected`() {
        assertFailsWith<BlockchainException.TransactionFailed> { readViaIndexer(indexerBody("TX1", null, account.address.toString())) }
        assertFailsWith<BlockchainException.TransactionFailed> { readViaIndexer(indexerBody("TX1", 0, account.address.toString())) }
        assertEquals("application/json", readViaIndexer(indexerBody("TX1", 5, account.address.toString())).mediaType)
    }

    @Test
    fun `the optional expectedSender is enforced`() {
        val sender = account.address.toString()
        val body = indexerBody("TX1", 5, sender)
        assertEquals("application/json", readViaIndexer(body, mapOf("expectedSender" to sender)).mediaType)
        assertFailsWith<BlockchainException.TransactionFailed> {
            readViaIndexer(body, mapOf("expectedSender" to otherAccount.address.toString()))
        }
    }

    // --- pending-pool path ---

    private fun signedPayment(): Pair<com.algorand.algosdk.transaction.SignedTransaction, String> {
        val tx =
            Transaction
                .PaymentTransactionBuilder()
                .sender(account.address)
                .receiver(account.address)
                .amount(0)
                .note(note)
                .firstValid(1)
                .lastValid(1000)
                .fee(1000)
                .genesisHash(
                    com.algorand.algosdk.crypto
                        .Digest(ByteArray(32)),
                ).build()
        val signed = account.signTransaction(tx)
        return signed to signed.transactionID
    }

    private fun pendingBody(
        confirmedRound: Long?,
        poolError: String = "",
    ): Pair<String, String> {
        val (signed, id) = signedPayment()
        val response = PendingTransactionResponse()
        response.txn = signed
        response.confirmedRound = confirmedRound
        response.poolError = poolError
        return Encoder.encodeToJson(response) to id
    }

    private fun readViaPending(
        body: String,
        txId: String,
    ) = Node { _, _ -> body }.use { node ->
        runBlocking {
            AlgorandBlockchainAnchorClient(chainId, mapOf("algodUrl" to node.url))
                .readPayload(AnchorRef(chainId = chainId, txHash = txId))
        }
    }

    @Test
    fun `the pending path requires confirmation, no pool error and the matching transaction id`() {
        val (confirmed, id) = pendingBody(confirmedRound = 7)
        assertEquals("application/json", readViaPending(confirmed, id).mediaType)
        assertFailsWith<BlockchainException.TransactionFailed> { readViaPending(pendingBody(0).first, id) }
        assertFailsWith<BlockchainException.TransactionFailed> { readViaPending(pendingBody(null).first, id) }
        assertFailsWith<BlockchainException.TransactionFailed> { readViaPending(pendingBody(7, "overspend").first, id) }
        assertFailsWith<BlockchainException.TransactionFailed> { readViaPending(confirmed, "SOMEOTHERID") }
    }

    // --- fee cap on the signed fee ---

    @Test
    fun `maxFee is enforced against the fee actually signed before anything is submitted`() {
        val params =
            """{"consensus-version":"v1","fee":100,"genesis-hash":"${Base64.getEncoder().encodeToString(ByteArray(32))}",
            "genesis-id":"mainnet-v1.0","last-round":10,"min-fee":1000}"""
        Node { _, path -> if (path.endsWith("/transactions/params")) params else "{}" }.use { node ->
            val client =
                AlgorandBlockchainAnchorClient(
                    chainId,
                    mapOf(
                        "algodUrl" to node.url,
                        "privateKey" to Base64.getEncoder().encodeToString(seed),
                    ),
                )
            // The estimate is max(suggested 100, min 1000) = 1000, so a 2000 cap passes it; 100 microAlgos per
            // byte makes the real transaction cost far more.
            val ctx =
                PaymentContext(
                    domainId = "d",
                    payerDid = "did:key:p",
                    chainId = chainId,
                    feeStrategy = FeeStrategy.SelfPay,
                    maxFee = TokenAmount(chainId, AssetRef.Native, BigInteger.valueOf(2_000)),
                )
            val failure =
                assertFailsWith<TreasuryException.CallerCapExceeded> {
                    runBlocking { client.writePayload(buildJsonObject { }, ctx) }
                }
            assertTrue(failure.estimated.amount > BigInteger.valueOf(2_000))
            assertTrue(node.requests.none { it.startsWith("POST") && it.endsWith("/transactions") }, node.requests.toString())
        }
    }
}
