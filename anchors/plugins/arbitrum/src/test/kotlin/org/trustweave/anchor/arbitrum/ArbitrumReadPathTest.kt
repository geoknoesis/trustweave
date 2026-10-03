package org.trustweave.anchor.arbitrum

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.trustweave.anchor.AnchorRef
import org.trustweave.anchor.evm.AbstractEvmAnchorClient
import org.trustweave.testkit.anchor.FakeEvmJsonRpcServer
import kotlin.test.assertEquals
import kotlin.test.assertFails

/** The shared EVM read-path checks as the Arbitrum plugin applies them. */
class ArbitrumReadPathTest {
    private val txHash = "0x" + "ab".repeat(32)
    private val stranger = "0x" + "33".repeat(20)
    private val ref = AnchorRef(ArbitrumBlockchainAnchorClient.ARBITRUM_SEPOLIA, txHash)

    private fun client(
        rpc: FakeEvmJsonRpcServer,
        extra: Map<String, Any?> = emptyMap(),
    ) = ArbitrumBlockchainAnchorClient(ArbitrumBlockchainAnchorClient.ARBITRUM_SEPOLIA, mapOf("rpcUrl" to rpc.url) + extra)

    private fun assertRejected(
        rpc: FakeEvmJsonRpcServer,
        extra: Map<String, Any?> = emptyMap(),
    ) {
        rpc.use { client(it, extra).use { client -> assertFails { runBlocking { client.readPayload(ref) } } } }
    }

    @Test
    fun `reads a well-formed self-send anchor`() {
        val read = FakeEvmJsonRpcServer(txHash).use { rpc -> client(rpc).use { runBlocking { it.readPayload(ref) } } }
        assertEquals(Json.parseToJsonElement("""{"anchored":"payload"}"""), read.payload)
    }

    @Test
    fun `rejects a reverted transaction`() {
        assertRejected(FakeEvmJsonRpcServer(txHash, status = "0x0"))
    }

    @Test
    fun `rejects an unexpected sender`() {
        assertRejected(FakeEvmJsonRpcServer(txHash), mapOf(AbstractEvmAnchorClient.OPTION_EXPECTED_SENDER to stranger))
    }

    @Test
    fun `rejects a transaction to another recipient`() {
        assertRejected(FakeEvmJsonRpcServer(txHash, to = stranger))
    }
}
