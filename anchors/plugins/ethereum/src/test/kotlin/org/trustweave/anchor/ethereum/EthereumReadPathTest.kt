package org.trustweave.anchor.ethereum

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.trustweave.anchor.AnchorRef
import org.trustweave.anchor.evm.AbstractEvmAnchorClient
import org.trustweave.anchor.exceptions.BlockchainException
import org.trustweave.testkit.anchor.FakeEvmJsonRpcServer
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** RPC defaults and the read-path checks as the Ethereum plugin applies them. */
class EthereumReadPathTest {
    private val txHash = "0x" + "ab".repeat(32)
    private val stranger = "0x" + "33".repeat(20)

    @Test
    fun `mainnet requires an explicit rpcUrl instead of a shared public default`() {
        val e =
            assertFailsWith<BlockchainException.ConfigurationFailed> {
                EthereumBlockchainAnchorClient(EthereumBlockchainAnchorClient.MAINNET)
            }
        assertTrue(e.message.contains("rpcUrl"))
    }

    @Test
    fun `sepolia still has a keyless default endpoint`() {
        EthereumBlockchainAnchorClient(EthereumBlockchainAnchorClient.SEPOLIA).close()
    }

    @Test
    fun `mainnet reads a well-formed self-send anchor`() =
        FakeEvmJsonRpcServer(txHash).use { rpc ->
            EthereumBlockchainAnchorClient(EthereumBlockchainAnchorClient.MAINNET, mapOf("rpcUrl" to rpc.url)).use { client ->
                val result = runBlocking { client.readPayload(AnchorRef(EthereumBlockchainAnchorClient.MAINNET, txHash)) }
                assertEquals(Json.parseToJsonElement("""{"anchored":"payload"}"""), result.payload)
            }
        }

    @Test
    fun `mainnet rejects a reverted transaction`() {
        FakeEvmJsonRpcServer(txHash, status = "0x0").use { rpc ->
            EthereumBlockchainAnchorClient(EthereumBlockchainAnchorClient.MAINNET, mapOf("rpcUrl" to rpc.url)).use { client ->
                assertFails { runBlocking { client.readPayload(AnchorRef(EthereumBlockchainAnchorClient.MAINNET, txHash)) } }
            }
        }
    }

    @Test
    fun `mainnet rejects an anchor not sent by the expected account`() {
        FakeEvmJsonRpcServer(txHash).use { rpc ->
            val options = mapOf("rpcUrl" to rpc.url, AbstractEvmAnchorClient.OPTION_EXPECTED_SENDER to stranger)
            EthereumBlockchainAnchorClient(EthereumBlockchainAnchorClient.MAINNET, options).use { client ->
                assertFails { runBlocking { client.readPayload(AnchorRef(EthereumBlockchainAnchorClient.MAINNET, txHash)) } }
            }
        }
    }

    @Test
    fun `mainnet rejects an anchor sent to another address`() =
        FakeEvmJsonRpcServer(txHash, to = stranger).use { rpc ->
            EthereumBlockchainAnchorClient(EthereumBlockchainAnchorClient.MAINNET, mapOf("rpcUrl" to rpc.url)).use { client ->
                assertFails { runBlocking { client.readPayload(AnchorRef(EthereumBlockchainAnchorClient.MAINNET, txHash)) } }
                assertTrue(
                    !runBlocking {
                        client.verifyAnchor(
                            Json.parseToJsonElement("""{"anchored":"payload"}"""),
                            AnchorRef(EthereumBlockchainAnchorClient.MAINNET, txHash),
                        )
                    },
                    "verifyAnchor must not accept a transaction the read path rejects",
                )
            }
        }
}
