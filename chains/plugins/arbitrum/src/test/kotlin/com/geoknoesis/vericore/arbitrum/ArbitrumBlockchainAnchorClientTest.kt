package com.geoknoesis.vericore.arbitrum

import com.geoknoesis.vericore.anchor.*
import com.geoknoesis.vericore.testkit.anchor.InMemoryBlockchainAnchorClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
}

