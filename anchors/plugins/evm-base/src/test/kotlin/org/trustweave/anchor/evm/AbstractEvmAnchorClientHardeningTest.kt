package org.trustweave.anchor.evm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.trustweave.anchor.AbstractBlockchainAnchorClient
import org.trustweave.anchor.AnchorDigest
import org.trustweave.anchor.AnchorRef
import org.trustweave.anchor.exceptions.BlockchainException
import org.trustweave.testkit.anchor.FakeEvmJsonRpcServer
import org.web3j.crypto.Credentials
import org.web3j.crypto.TransactionDecoder
import java.math.BigInteger
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Submission serialisation, nonce recovery, off-thread RPC, receipt/transaction consistency, calldata
 * parsing and legacy-digest handling of [AbstractEvmAnchorClient], against the testkit's scripted node.
 */
class AbstractEvmAnchorClientHardeningTest {
    private val txHash = "0x" + "ab".repeat(32)
    private val key = "ac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"
    private val account = Credentials.create(key).address
    private val chain = EvmChainConfig(1337L, "http://localhost:18545", "TestEvm", "test-evm-local")

    private class Client(
        chain: EvmChainConfig,
        options: Map<String, Any?>,
    ) : AbstractEvmAnchorClient("eip155:${chain.numericChainId}", options, chain) {
        val gasThreads = CopyOnWriteArrayList<String>()
        val contractThreads = CopyOnWriteArrayList<String>()
        val legacyAccepted = AtomicInteger()

        override fun deriveGasLimit(
            data: ByteArray,
            from: String,
        ): BigInteger {
            gasThreads += Thread.currentThread().name
            return super.deriveGasLimit(data, from)
        }

        override fun getContractAddress(): String? {
            contractThreads += Thread.currentThread().name
            return super.getContractAddress()
        }

        override fun onLegacyDigestAccepted(ref: AnchorRef) {
            legacyAccepted.incrementAndGet()
        }

        suspend fun submit(data: ByteArray) = submitTransaction(data)

        suspend fun read(txHash: String) = readTransactionFromBlockchain(txHash)

        suspend fun await(txHash: String) = waitForReceipt(txHash, 1L)
    }

    private fun options(
        rpc: FakeEvmJsonRpcServer,
        extra: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> =
        mapOf(
            "rpcUrl" to rpc.url,
            "privateKey" to key,
            AbstractBlockchainAnchorClient.OPTION_CONFIRMATION_TIMEOUT_MS to 5_000L,
            AbstractBlockchainAnchorClient.OPTION_CONFIRMATION_POLL_INTERVAL_MS to 10L,
        ) + extra

    private fun nonceOf(raw: String): Long = TransactionDecoder.decode(raw).nonce.toLong()

    private val payload: JsonObject = buildJsonObject { put("k", "v") }

    // --- (a) serialised submissions and nonce-too-low retry ---

    @Test
    fun `concurrent anchors from one client never reuse a nonce`() =
        runBlocking<Unit> {
            FakeEvmJsonRpcServer(txHash, from = account, nonceReadDelayMs = 40).use { rpc ->
                Client(chain, options(rpc)).use { client ->
                    (1..8).map { async(Dispatchers.Default) { client.writePayload(payload) } }.awaitAll()

                    val nonces = rpc.rawTransactions.map(::nonceOf)
                    assertEquals((0L..7L).toList(), nonces.sorted(), "every anchor must get its own nonce: $nonces")
                }
            }
        }

    @Test
    fun `nonce too low is retried once with a fresh nonce`() =
        runBlocking<Unit> {
            FakeEvmJsonRpcServer(txHash, from = account).use { rpc ->
                rpc.failNextSends("nonce too low: next nonce 1, tx nonce 0")
                Client(chain, options(rpc)).use { client ->
                    client.submit("{}".toByteArray())

                    assertEquals(listOf(1L), rpc.rawTransactions.map(::nonceOf), "the retry must use the re-read nonce")
                    assertEquals(2, rpc.methods.count { it == "eth_getTransactionCount" })
                }
            }
        }

    @Test
    fun `a second nonce too low is not retried again`() =
        runBlocking<Unit> {
            FakeEvmJsonRpcServer(txHash, from = account).use { rpc ->
                rpc.failNextSends("nonce too low", "nonce too low")
                Client(chain, options(rpc)).use { client ->
                    val failure = assertFailsWith<BlockchainException.TransactionFailed> { client.submit("{}".toByteArray()) }

                    assertTrue(failure.message.contains("nonce too low"))
                    assertEquals(2, rpc.methods.count { it == "eth_sendRawTransaction" })
                    assertTrue(rpc.rawTransactions.isEmpty())
                }
            }
        }

    @Test
    fun `other send errors are not retried`() =
        runBlocking<Unit> {
            FakeEvmJsonRpcServer(txHash, from = account).use { rpc ->
                rpc.failNextSends("insufficient funds for gas * price + value")
                Client(chain, options(rpc)).use { client ->
                    assertFailsWith<BlockchainException.TransactionFailed> { client.submit("{}".toByteArray()) }

                    assertEquals(1, rpc.methods.count { it == "eth_sendRawTransaction" })
                }
            }
        }

    // --- (b) RPC off the caller's thread ---

    @Test
    fun `blocking RPC runs on the IO dispatcher, not on the caller's thread`() =
        runBlocking<Unit> {
            FakeEvmJsonRpcServer(txHash, from = account).use { rpc ->
                Client(chain, options(rpc)).use { client ->
                    val caller = Thread.currentThread().name
                    client.submit("{}".toByteArray())
                    client.read(txHash)
                    client.await(txHash)

                    val seen = client.gasThreads + client.contractThreads
                    assertTrue(seen.isNotEmpty())
                    assertTrue(seen.none { it == caller }, "ran on the caller's thread $caller: $seen")
                    assertTrue(seen.all { it.startsWith("DefaultDispatcher-worker") }, "not an IO worker: $seen")
                }
            }
        }

    // --- (c) receipt and transaction must describe the same block ---

    @Test
    fun `a transaction reporting another block hash than its receipt is refused`() =
        runBlocking<Unit> {
            FakeEvmJsonRpcServer(txHash, from = account, txBlockHash = "0x" + "99".repeat(32)).use { rpc ->
                Client(chain, options(rpc)).use { client ->
                    val failure = assertFailsWith<BlockchainException.TransactionFailed> { client.read(txHash) }

                    assertTrue(failure.message.contains("block"), failure.message)
                }
            }
        }

    @Test
    fun `a transaction reporting another block number than its receipt is refused`() =
        runBlocking<Unit> {
            FakeEvmJsonRpcServer(txHash, from = account, txBlockNumberHex = "0x2").use { rpc ->
                Client(chain, options(rpc)).use { client ->
                    assertFailsWith<BlockchainException.TransactionFailed> { client.read(txHash) }
                }
            }
        }

    @Test
    fun `a consistent receipt and transaction are read`() =
        runBlocking<Unit> {
            FakeEvmJsonRpcServer(txHash, payloadJson = """{"k":"v"}""", from = account).use { rpc ->
                Client(chain, options(rpc)).use { client ->
                    assertEquals(payload, client.read(txHash).payload)
                }
            }
        }

    // --- (d) calldata that is not JSON ---

    @Test
    fun `non JSON calldata is a clear failure, not a raw serialization error`() =
        runBlocking<Unit> {
            for (junk in listOf("hello world", """{"unterminated":""")) {
                FakeEvmJsonRpcServer(txHash, payloadJson = junk, from = account).use { rpc ->
                    Client(chain, options(rpc)).use { client ->
                        val failure = assertFailsWith<BlockchainException.TransactionFailed> { client.read(txHash) }
                        assertTrue(failure.message.contains("not a JSON anchor payload"), failure.message)
                        assertFalse(failure.cause is SerializationException && failure.message.contains("Unexpected JSON token"))
                        assertNotNull(failure.cause)
                    }
                }
            }
        }

    @Test
    fun `verifyAnchor reports non JSON calldata as unverified instead of throwing`() =
        runBlocking<Unit> {
            FakeEvmJsonRpcServer(txHash, payloadJson = "not json", from = account).use { rpc ->
                Client(chain, options(rpc)).use { client ->
                    val detail = client.verifyAnchorDetailed(payload, AnchorRef("eip155:1337", txHash), requireCanonicalEnvelope = false)

                    assertFalse(detail.verified)
                    assertTrue(detail.reason!!.contains("not a JSON anchor payload"), detail.reason)
                }
            }
        }

    // --- (e) legacy digest envelopes ---

    private fun legacyEnvelopeJson(): String =
        AnchorDigest
            .envelope(
                kotlinx.serialization.json.Json
                    .encodeToString(JsonObject.serializer(), payload)
                    .toByteArray(),
                "application/json",
            ).toString()

    @Test
    fun `a legacy digest envelope verifies by default and warns exactly once per client`() =
        runBlocking<Unit> {
            FakeEvmJsonRpcServer(txHash, payloadJson = legacyEnvelopeJson(), from = account).use { rpc ->
                Client(chain, options(rpc)).use { client ->
                    val ref = AnchorRef("eip155:1337", txHash)

                    assertTrue(client.verifyAnchor(payload, ref))
                    assertTrue(client.verifyAnchor(payload, ref))

                    assertEquals(1, client.legacyAccepted.get(), "warn once per client, not per verification")
                }
            }
        }

    @Test
    fun `requireCanonicalEnvelope rejects a legacy digest envelope and does not warn`() =
        runBlocking<Unit> {
            FakeEvmJsonRpcServer(txHash, payloadJson = legacyEnvelopeJson(), from = account).use { rpc ->
                val strict = options(rpc, mapOf(AbstractBlockchainAnchorClient.OPTION_REQUIRE_CANONICAL_ENVELOPE to true))
                Client(chain, strict).use { client ->
                    assertFalse(client.verifyAnchor(payload, AnchorRef("eip155:1337", txHash)))
                    assertEquals(0, client.legacyAccepted.get())
                }
            }
        }

    @Test
    fun `a canonical envelope never triggers the legacy warning`() =
        runBlocking<Unit> {
            val canonical = AnchorDigest.envelope(payload, "application/json").toString()
            FakeEvmJsonRpcServer(txHash, payloadJson = canonical, from = account).use { rpc ->
                Client(chain, options(rpc)).use { client ->
                    assertTrue(client.verifyAnchor(payload, AnchorRef("eip155:1337", txHash)))
                    assertEquals(0, client.legacyAccepted.get())
                }
            }
        }
}
