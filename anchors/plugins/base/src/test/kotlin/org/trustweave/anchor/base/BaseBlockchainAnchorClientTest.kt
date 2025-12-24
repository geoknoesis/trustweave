package org.trustweave.anchor.base

import org.trustweave.anchor.*
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BaseBlockchainAnchorClientTest {

    @Test
    fun `test chain IDs`() {
        assertEquals("eip155:8453", BaseBlockchainAnchorClient.MAINNET)
        assertEquals("eip155:84532", BaseBlockchainAnchorClient.BASE_SEPOLIA)
    }

    @Test
    fun `test create client with valid chain ID`() = runBlocking {
        val client = InMemoryBlockchainAnchorClient(BaseBlockchainAnchorClient.MAINNET)

        // Test that we can write and read to verify chain ID is correct
        val testPayload = buildJsonObject { put("test", "value") }
        val result = client.writePayload(testPayload)
        assertEquals(BaseBlockchainAnchorClient.MAINNET, result.ref.chainId)
    }

    @Test
    fun `test anchor payload`() = runBlocking {
        val client = InMemoryBlockchainAnchorClient(BaseBlockchainAnchorClient.BASE_SEPOLIA)
        val payload = buildJsonObject {
            put("digest", "uABC123...")
        }

        val result = client.writePayload(payload)

        assertNotNull(result.ref)
        assertEquals(BaseBlockchainAnchorClient.BASE_SEPOLIA, result.ref.chainId)
        assertNotNull(result.ref.txHash)
    }
}

