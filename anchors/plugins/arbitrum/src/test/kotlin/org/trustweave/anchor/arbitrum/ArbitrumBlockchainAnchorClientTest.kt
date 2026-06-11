package org.trustweave.anchor.arbitrum

import org.trustweave.anchor.*
import org.trustweave.anchor.evm.EvmGas
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import java.math.BigInteger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArbitrumBlockchainAnchorClientTest {

    @Test
    fun `test chain IDs`() {
        assertEquals("eip155:42161", ArbitrumBlockchainAnchorClient.MAINNET)
        assertEquals("eip155:421614", ArbitrumBlockchainAnchorClient.ARBITRUM_SEPOLIA)
    }

    @Test
    fun `test create client with valid chain ID`() = runBlocking {
        val client = InMemoryBlockchainAnchorClient(ArbitrumBlockchainAnchorClient.MAINNET)

        // Test that we can write and read to verify chain ID is correct
        val testPayload = buildJsonObject { put("test", "value") }
        val result = client.writePayload(testPayload)
        assertEquals(ArbitrumBlockchainAnchorClient.MAINNET, result.ref.chainId)
    }

    @Test
    fun `test anchor payload`() = runBlocking {
        val client = InMemoryBlockchainAnchorClient(ArbitrumBlockchainAnchorClient.ARBITRUM_SEPOLIA)
        val payload = buildJsonObject {
            put("digest", "uABC123...")
        }

        val result = client.writePayload(payload)

        assertNotNull(result.ref)
        assertEquals(ArbitrumBlockchainAnchorClient.ARBITRUM_SEPOLIA, result.ref.chainId)
        assertNotNull(result.ref.txHash)
    }

    @Test
    fun `gas limit uses eth_estimateGas result plus margin as primary`() {
        // On Arbitrum Nitro gasUsed includes the L1 calldata-posting component
        // (gasUsedForL1), so a node estimate well above the intrinsic gas is normal.
        val data = """{"digest":"uABC123"}""".toByteArray()
        val estimated = BigInteger.valueOf(500_000)

        val gasLimit = ArbitrumBlockchainAnchorClient.gasLimitFrom(estimated, data)

        // estimate + 20% margin
        assertEquals(
            EvmGas.withMargin(estimated, ArbitrumBlockchainAnchorClient.ESTIMATE_GAS_MARGIN_PERCENT),
            gasLimit
        )
        assertEquals(BigInteger.valueOf(600_000), gasLimit)
    }

    @Test
    fun `gas limit is floored at the intrinsic gas when estimate is implausibly low`() {
        val data = ByteArray(1000) { 0x42 }
        val bogusEstimate = BigInteger.valueOf(1_000)

        val gasLimit = ArbitrumBlockchainAnchorClient.gasLimitFrom(bogusEstimate, data)

        assertEquals(EvmGas.intrinsicGas(data), gasLimit)
    }

    @Test
    fun `gas limit falls back to intrinsic calldata gas when estimation fails`() {
        val data = """{"digest":"uABC123"}""".toByteArray()

        val gasLimit = ArbitrumBlockchainAnchorClient.gasLimitFrom(null, data)

        assertEquals(EvmGas.txGasLimit(data), gasLimit)
        assertTrue(gasLimit >= EvmGas.intrinsicGas(data))
    }
}

