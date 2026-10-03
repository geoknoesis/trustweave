package org.trustweave.anchor.evm

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.trustweave.anchor.AnchorResult
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * What a verifier is entitled to conclude from a single RPC node.
 *
 * `verifyAnchor` compares the caller's payload against whatever `readPayload` returns, so the read
 * is the whole trust boundary. Two things the node says must be checked rather than believed: that
 * the transaction it handed back is the one that was asked for, and that the block containing it is
 * actually buried rather than sitting at the tip where a re-org still removes it.
 */
class EvmAnchorReadIntegrityTest {
    private val chain =
        EvmChainConfig(
            numericChainId = 1337L,
            defaultRpcUrl = "http://localhost:18545",
            blockchainName = "TestEvm",
            networkName = "test-evm-local",
        )

    private val requestedTxHash = "0x" + "ab".repeat(32)
    private val otherTxHash = "0x" + "cd".repeat(32)
    private val sender = "0x" + "22".repeat(20)
    private val stranger = "0x" + "33".repeat(20)
    private val anySender = mapOf(AbstractEvmAnchorClient.OPTION_ACCEPT_ANY_SELF_SEND to true)

    // Well-known throwaway test key (hardhat account #0) and its address.
    private val testKey = "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"
    private val testAddress = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"

    private class TestEvmClient(
        chain: EvmChainConfig,
        options: Map<String, Any?> = emptyMap(),
    ) : AbstractEvmAnchorClient("eip155:${chain.numericChainId}", options, chain) {
        suspend fun read(txHash: String): AnchorResult = readTransactionFromBlockchain(txHash)
    }

    private fun payloadHex(json: String): String = "0x" + json.toByteArray(StandardCharsets.UTF_8).joinToString("") { "%02x".format(it) }

    /**
     * JSON-RPC stub that answers per method, so a single read (receipt, then transaction, then
     * head and block) can be given inconsistent answers on purpose.
     */
    private fun withRpc(
        receiptTxHash: String,
        transactionTxHash: String,
        blockNumberHex: String,
        headBlockHex: String,
        payloadJson: String = """{"anchored":"payload"}""",
        status: String = "0x1",
        from: String = sender,
        to: String? = sender,
        block: (rpcUrl: String) -> Unit,
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val request = String(exchange.requestBody.readBytes(), StandardCharsets.UTF_8)
            val response =
                when {
                    request.contains("eth_getTransactionReceipt") ->
                        """{"jsonrpc":"2.0","id":1,"result":{
                            "transactionHash":"$receiptTxHash","transactionIndex":"0x0",
                            "blockHash":"0x${"11".repeat(32)}","blockNumber":"$blockNumberHex",
                            "cumulativeGasUsed":"0x5208","gasUsed":"0x5208","status":"$status",
                            "from":"$from","to":${to?.let { "\"$it\"" } ?: "null"},
                            "logs":[],"logsBloom":"0x0"}}"""

                    request.contains("eth_getTransactionByHash") ->
                        """{"jsonrpc":"2.0","id":1,"result":{
                            "hash":"$transactionTxHash","nonce":"0x0",
                            "blockHash":"0x${"11".repeat(32)}","blockNumber":"$blockNumberHex",
                            "transactionIndex":"0x0",
                            "from":"$from","to":${to?.let { "\"$it\"" } ?: "null"},
                            "value":"0x0","gas":"0x5208","gasPrice":"0x1",
                            "input":"${payloadHex(payloadJson)}"}}"""

                    request.contains("eth_blockNumber") ->
                        """{"jsonrpc":"2.0","id":1,"result":"$headBlockHex"}"""

                    request.contains("eth_getBlockByNumber") ->
                        """{"jsonrpc":"2.0","id":1,"result":{
                            "number":"$blockNumberHex","hash":"0x${"11".repeat(32)}",
                            "parentHash":"0x${"00".repeat(32)}","nonce":"0x0",
                            "sha3Uncles":"0x${"00".repeat(32)}","logsBloom":"0x0",
                            "transactionsRoot":"0x${"00".repeat(32)}",
                            "stateRoot":"0x${"00".repeat(32)}",
                            "receiptsRoot":"0x${"00".repeat(32)}",
                            "miner":"0x${"22".repeat(20)}","difficulty":"0x1",
                            "totalDifficulty":"0x1","extraData":"0x","size":"0x1",
                            "gasLimit":"0x5208","gasUsed":"0x5208","timestamp":"0x64000000",
                            "transactions":[],"uncles":[]}}"""

                    else -> """{"jsonrpc":"2.0","id":1,"result":null}"""
                }
            val bytes = response.toByteArray()
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `a receipt for a different transaction is rejected`() =
        withRpc(
            receiptTxHash = otherTxHash,
            transactionTxHash = requestedTxHash,
            blockNumberHex = "0x1",
            headBlockHex = "0x64",
        ) { url ->
            runBlocking {
                TestEvmClient(chain.copy(defaultRpcUrl = url)).use { client ->
                    assertFails("A receipt whose transactionHash is not the requested one must be rejected") {
                        runBlocking { client.read(requestedTxHash) }
                    }
                }
            }
        }

    @Test
    fun `a transaction body for a different transaction is rejected`() =
        withRpc(
            receiptTxHash = requestedTxHash,
            transactionTxHash = otherTxHash,
            blockNumberHex = "0x1",
            headBlockHex = "0x64",
        ) { url ->
            runBlocking {
                TestEvmClient(chain.copy(defaultRpcUrl = url)).use { client ->
                    assertFails("A transaction whose hash is not the requested one must be rejected") {
                        runBlocking { client.read(requestedTxHash) }
                    }
                }
            }
        }

    @Test
    fun `a transaction still sitting at the chain tip is rejected`() =
        withRpc(
            receiptTxHash = requestedTxHash,
            transactionTxHash = requestedTxHash,
            blockNumberHex = "0x64",
            headBlockHex = "0x64",
        ) { url ->
            runBlocking {
                TestEvmClient(chain.copy(defaultRpcUrl = url)).use { client ->
                    assertFails("A transaction with no confirmations on top of it must be rejected") {
                        runBlocking { client.read(requestedTxHash) }
                    }
                }
            }
        }

    @Test
    fun `a consistent buried transaction reads back its payload`() =
        withRpc(
            receiptTxHash = requestedTxHash,
            transactionTxHash = requestedTxHash,
            blockNumberHex = "0x1",
            headBlockHex = "0x64",
        ) { url ->
            runBlocking {
                TestEvmClient(chain.copy(defaultRpcUrl = url), anySender).use { client ->
                    val result = client.read(requestedTxHash)

                    assertEquals("application/json", result.mediaType)
                }
            }
        }

    private fun assertReadRejected(
        message: String,
        options: Map<String, Any?> = anySender,
        status: String = "0x1",
        from: String = sender,
        to: String? = sender,
    ) = withRpc(
        receiptTxHash = requestedTxHash,
        transactionTxHash = requestedTxHash,
        blockNumberHex = "0x1",
        headBlockHex = "0x64",
        status = status,
        from = from,
        to = to,
    ) { url ->
        TestEvmClient(chain.copy(defaultRpcUrl = url), options).use { client ->
            assertFails(message) { runBlocking { client.read(requestedTxHash) } }
        }
    }

    @Test
    fun `a reverted transaction is not an anchor`() = assertReadRejected("A receipt with status 0x0 must be rejected", status = "0x0")

    @Test
    fun `a transaction from someone other than the expected sender is rejected`() =
        assertReadRejected(
            "A transaction not sent by the configured anchoring account must be rejected",
            options = mapOf(AbstractEvmAnchorClient.OPTION_EXPECTED_SENDER to stranger),
        )

    @Test
    fun `a transaction that is not a self-send is rejected by default`() =
        assertReadRejected("A transaction to another address must be rejected", to = stranger)

    @Test
    fun `a contract creation is rejected`() = assertReadRejected("A transaction without a recipient must be rejected", to = null)

    @Test
    fun `a transaction to a recipient other than the configured one is rejected`() =
        assertReadRejected(
            "The configured recipient must be enforced",
            options = mapOf(AbstractEvmAnchorClient.OPTION_EXPECTED_RECIPIENT to stranger),
        )

    @Test
    fun `configured sender and recipient that match are accepted, case-insensitively`() =
        withRpc(
            receiptTxHash = requestedTxHash,
            transactionTxHash = requestedTxHash,
            blockNumberHex = "0x1",
            headBlockHex = "0x64",
            to = stranger,
        ) { url ->
            val options =
                mapOf(
                    AbstractEvmAnchorClient.OPTION_EXPECTED_SENDER to sender.uppercase().replace("0X", "0x"),
                    AbstractEvmAnchorClient.OPTION_EXPECTED_RECIPIENT to stranger,
                )
            TestEvmClient(chain.copy(defaultRpcUrl = url), options).use { client ->
                assertEquals("application/json", runBlocking { client.read(requestedTxHash) }.mediaType)
            }
        }

    @Test
    fun `a verify-only client with no expected sender fails with a clear error`() =
        withRpc(
            receiptTxHash = requestedTxHash,
            transactionTxHash = requestedTxHash,
            blockNumberHex = "0x1",
            headBlockHex = "0x64",
        ) { url ->
            TestEvmClient(chain.copy(defaultRpcUrl = url)).use { client ->
                val failure = assertFails { runBlocking { client.read(requestedTxHash) } }
                assertTrue(failure.toString().contains(AbstractEvmAnchorClient.OPTION_EXPECTED_SENDER), failure.toString())
            }
        }

    @Test
    fun `a client with credentials defaults the expected sender to its own account`() {
        // A self-send from a stranger must be rejected without any expectedSender option.
        withRpc(
            receiptTxHash = requestedTxHash,
            transactionTxHash = requestedTxHash,
            blockNumberHex = "0x1",
            headBlockHex = "0x64",
            from = stranger,
            to = stranger,
        ) { url ->
            TestEvmClient(chain.copy(defaultRpcUrl = url), mapOf("privateKey" to testKey)).use { client ->
                assertFails { runBlocking { client.read(requestedTxHash) } }
            }
        }
        // ... and a self-send from the client's own account is accepted.
        withRpc(
            receiptTxHash = requestedTxHash,
            transactionTxHash = requestedTxHash,
            blockNumberHex = "0x1",
            headBlockHex = "0x64",
            from = testAddress,
            to = testAddress,
        ) { url ->
            TestEvmClient(chain.copy(defaultRpcUrl = url), mapOf("privateKey" to testKey)).use { client ->
                assertEquals("application/json", runBlocking { client.read(requestedTxHash) }.mediaType)
            }
        }
    }
}
