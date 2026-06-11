package org.trustweave.anchor.optimism

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.trustweave.anchor.AbstractBlockchainAnchorClient
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for the Optimism anchor client, exercised through the opt-in
 * in-memory test mode (no network, no credentials).
 */
class OptimismBlockchainAnchorClientTest {

    private val payload = buildJsonObject { put("test", "data") }

    private fun client(extraOptions: Map<String, Any?> = emptyMap()) =
        OptimismBlockchainAnchorClient(
            chainId = OptimismBlockchainAnchorClient.SEPOLIA,
            options = mapOf(
                AbstractBlockchainAnchorClient.OPTION_IN_MEMORY_TEST_MODE to true,
            ) + extraOptions,
        )

    @Test
    fun `legacy contract option key flows into the anchor ref contract`() = runBlocking {
        val client = client(mapOf("contract" to "0x1111111111111111111111111111111111111111"))

        val result = client.writePayload(payload)

        assertEquals(
            "0x1111111111111111111111111111111111111111",
            result.ref.contract,
            "legacy 'contract' option key must be honored"
        )
    }

    @Test
    fun `contractAddress option key still flows into the anchor ref contract`() = runBlocking {
        val client = client(mapOf("contractAddress" to "0x2222222222222222222222222222222222222222"))

        val result = client.writePayload(payload)

        assertEquals("0x2222222222222222222222222222222222222222", result.ref.contract)
    }

    @Test
    fun `legacy contract key wins over contractAddress when both are set`() = runBlocking {
        val client = client(
            mapOf(
                "contract" to "0x1111111111111111111111111111111111111111",
                "contractAddress" to "0x2222222222222222222222222222222222222222",
            )
        )

        val result = client.writePayload(payload)

        assertEquals("0x1111111111111111111111111111111111111111", result.ref.contract)
    }

    @Test
    fun `no contract option leaves the anchor ref contract unset`() = runBlocking {
        val result = client().writePayload(payload)

        assertNull(result.ref.contract)
    }
}
