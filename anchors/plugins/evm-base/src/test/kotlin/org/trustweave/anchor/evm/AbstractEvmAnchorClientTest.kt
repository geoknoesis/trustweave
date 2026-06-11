package org.trustweave.anchor.evm

import org.trustweave.anchor.AbstractBlockchainAnchorClient
import org.trustweave.anchor.exceptions.BlockchainException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    ) : AbstractEvmAnchorClient("eip155:1337", options, chain) {
        fun gasLimit(data: ByteArray): BigInteger = deriveGasLimit(data, "0x0")

        fun testHash(): String = generateTestTxHash()

        fun extra(mediaType: String): Map<String, String> = buildExtraMetadata(mediaType)
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
}
