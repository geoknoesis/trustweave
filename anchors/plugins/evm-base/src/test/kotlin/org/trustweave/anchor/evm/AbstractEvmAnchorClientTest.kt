package org.trustweave.anchor.evm

import com.sun.net.httpserver.HttpServer
import org.trustweave.anchor.AbstractBlockchainAnchorClient
import org.trustweave.anchor.exceptions.BlockchainException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.SignedRawTransaction
import org.web3j.crypto.TransactionDecoder
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.net.InetSocketAddress
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for the shared EVM anchor base class, via a minimal test chain.
 * Chain-specific behavior remains covered by each plugin's own tests.
 */
class AbstractEvmAnchorClientTest {

    private val chain = EvmChainConfig(
        numericChainId = 1337L,
        defaultRpcUrl = "http://localhost:18545",
        blockchainName = "TestEvm",
        networkName = "test-evm-local"
    )

    private class TestEvmClient(
        chain: EvmChainConfig,
        options: Map<String, Any?> = emptyMap(),
    ) : AbstractEvmAnchorClient("eip155:${chain.numericChainId}", options, chain) {
        fun gasLimit(data: ByteArray): BigInteger = deriveGasLimit(data, "0x0")

        fun testHash(): String = generateTestTxHash()

        fun extra(mediaType: String): Map<String, String> = buildExtraMetadata(mediaType)

        suspend fun await(txHash: String): TransactionReceipt = waitForReceipt(txHash, payloadSize = 10L)
    }

    @Test
    fun `rejects invalid private key with configuration error carrying the cause`() {
        val exception = assertFailsWith<BlockchainException.ConfigurationFailed> {
            TestEvmClient(chain, mapOf("privateKey" to "not-a-valid-key"))
        }
        assertNotNull(exception.cause, "Parse failure must be carried as the cause")
        assertTrue(exception.message.contains("TestEvm"))
    }

    @Test
    fun `fails construction when credentials are required but missing`() {
        val required = chain.copy(credentialsRequired = true)
        val exception = assertFailsWith<BlockchainException.ConfigurationFailed> {
            TestEvmClient(required)
        }
        assertTrue(exception.message.contains("privateKey"))
    }

    @Test
    fun `fails closed on write without credentials when test mode is off`() = runBlocking<Unit> {
        val client = TestEvmClient(chain)

        assertFailsWith<BlockchainException.ConfigurationFailed> {
            client.writePayload(buildJsonObject { put("test", "data") })
        }
    }

    @Test
    fun `write and read roundtrip in opt-in in-memory test mode`() = runBlocking {
        val client = TestEvmClient(
            chain,
            mapOf(AbstractBlockchainAnchorClient.OPTION_IN_MEMORY_TEST_MODE to true)
        )
        val payload = buildJsonObject { put("test", "data") }

        val result = client.writePayload(payload)

        assertEquals("eip155:1337", result.ref.chainId)
        assertTrue(result.ref.txHash.startsWith("0x") && result.ref.txHash.length == 66)
        assertEquals(payload, client.readPayload(result.ref).payload)
    }

    @Test
    fun `default gas limit strategy is the intrinsic calldata gas`() {
        val client = TestEvmClient(chain)
        val data = """{"digest":"uABC123"}""".toByteArray()

        assertEquals(EvmGas.txGasLimit(data), client.gasLimit(data))
    }

    @Test
    fun `extra metadata carries the configured network name`() {
        val client = TestEvmClient(chain)

        assertEquals(
            mapOf("network" to "test-evm-local", "mediaType" to "application/json"),
            client.extra("application/json")
        )
    }

    @Test
    fun `fabricated test hashes are EVM-shaped and collision-free`() {
        val client = TestEvmClient(chain)

        val hashes = (1..100).map { client.testHash() }

        assertEquals(hashes.size, hashes.toSet().size)
        hashes.forEach { assertTrue(it.startsWith("0x") && it.length == 66) }
    }

    // ---------------------------------------------------------------------
    // EIP-155 replay protection
    // ---------------------------------------------------------------------

    /** Well-known throwaway test key (Hardhat/Anvil account #0). Never funded. */
    private val signerKey =
        Credentials.create("ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80")

    private fun anchorLikeRawTransaction(to: String): RawTransaction =
        RawTransaction.createTransaction(
            BigInteger.ONE,                       // nonce
            BigInteger.valueOf(1_000_000_000L),   // gas price
            BigInteger.valueOf(25_000L),          // gas limit
            to,                                   // anchors are self-sends
            BigInteger.ZERO,
            Numeric.toHexString("""{"digest":"uABC123"}""".toByteArray())
        )

    @Test
    fun `signs with EIP-155 replay protection - v encodes the chain id`() {
        TestEvmClient(chain).use { client ->
            val signed = client.signWithChainId(anchorLikeRawTransaction(signerKey.address), signerKey)

            val decoded = TransactionDecoder.decode(Numeric.toHexString(signed))
            assertTrue(decoded is SignedRawTransaction, "Round-trip must decode as a signed transaction")
            val v = Numeric.toBigInt(decoded.signatureData.v).toLong()
            assertTrue(
                v == 1337L * 2 + 35 || v == 1337L * 2 + 36,
                "v=$v must encode chain id 1337 (expected ${1337L * 2 + 35} or ${1337L * 2 + 36})"
            )
            assertEquals(1337L, decoded.chainId, "Decoded chain id must match the client's chain")
            assertEquals(signerKey.address, decoded.from, "Signature must recover the signer")
        }
    }

    @Test
    fun `signed bytes are chain-bound - other chains produce other v values`() {
        val rawTx = anchorLikeRawTransaction(signerKey.address)

        val onTestChain = TestEvmClient(chain).use { it.signWithChainId(rawTx, signerKey) }
        val onPolygonLikeChain = TestEvmClient(chain.copy(numericChainId = 137L))
            .use { it.signWithChainId(rawTx, signerKey) }

        val decodedTest = TransactionDecoder.decode(Numeric.toHexString(onTestChain)) as SignedRawTransaction
        val decodedPolygon = TransactionDecoder.decode(Numeric.toHexString(onPolygonLikeChain)) as SignedRawTransaction
        assertEquals(1337L, decodedTest.chainId)
        assertEquals(137L, decodedPolygon.chainId)
        assertNotEquals(
            Numeric.toBigInt(decodedTest.signatureData.v),
            Numeric.toBigInt(decodedPolygon.signatureData.v),
            "Identical payloads signed for different chains must not be byte-replayable"
        )
    }

    @Test
    fun `eip155ChainId mirrors the configured numeric chain id`() {
        TestEvmClient(chain).use { assertEquals(1337L, it.eip155ChainId) }
        TestEvmClient(chain.copy(numericChainId = 80002L)).use { assertEquals(80002L, it.eip155ChainId) }
    }

    // ---------------------------------------------------------------------
    // waitForReceipt behavior (stub JSON-RPC server, no real chain)
    // ---------------------------------------------------------------------

    private val txHash = "0x" + "ab".repeat(32)

    private fun receiptResponse(status: String): String =
        """{"jsonrpc":"2.0","id":1,"result":{
            "transactionHash":"$txHash","transactionIndex":"0x0",
            "blockHash":"0x${"11".repeat(32)}","blockNumber":"0x1",
            "cumulativeGasUsed":"0x5208","gasUsed":"0x5208","status":"$status",
            "from":"0x${"22".repeat(20)}","to":"0x${"22".repeat(20)}",
            "logs":[],"logsBloom":"0x0"}}"""

    private val noReceiptResponse = """{"jsonrpc":"2.0","id":1,"result":null}"""

    /** Minimal JSON-RPC stub answering every POST with [response]. */
    private fun withStubRpc(response: String, block: (rpcUrl: String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            exchange.requestBody.readBytes()
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

    private fun pollFastOptions(timeoutMs: Long) = mapOf(
        AbstractBlockchainAnchorClient.OPTION_CONFIRMATION_TIMEOUT_MS to timeoutMs,
        AbstractBlockchainAnchorClient.OPTION_CONFIRMATION_POLL_INTERVAL_MS to 20L,
    )

    @Test
    fun `waitForReceipt returns the receipt once mined with OK status`() = withStubRpc(receiptResponse("0x1")) { url ->
        runBlocking {
            TestEvmClient(chain.copy(defaultRpcUrl = url), pollFastOptions(5_000L)).use { client ->
                val receipt = client.await(txHash)

                assertEquals(txHash, receipt.transactionHash)
                assertTrue(receipt.isStatusOK)
            }
        }
    }

    @Test
    fun `waitForReceipt fails on a reverted transaction instead of reporting success`() =
        withStubRpc(receiptResponse("0x0")) { url ->
            runBlocking {
                TestEvmClient(chain.copy(defaultRpcUrl = url), pollFastOptions(5_000L)).use { client ->
                    val exception = assertFailsWith<BlockchainException.TransactionFailed> {
                        client.await(txHash)
                    }

                    assertTrue(exception.message.contains("reverted"), "Got: ${exception.message}")
                    assertFalse(exception.message.contains("not confirmed within"))
                }
            }
        }

    @Test
    fun `waitForReceipt fails when the transaction is not confirmed within the timeout`() =
        withStubRpc(noReceiptResponse) { url ->
            runBlocking {
                TestEvmClient(chain.copy(defaultRpcUrl = url), pollFastOptions(150L)).use { client ->
                    val exception = assertFailsWith<BlockchainException.TransactionFailed> {
                        client.await(txHash)
                    }

                    assertTrue(exception.message.contains("not confirmed within"), "Got: ${exception.message}")
                }
            }
        }
}
