package org.trustweave.anchor.ethereum

import org.trustweave.anchor.*
import org.trustweave.testkit.anchor.InMemoryBlockchainAnchorClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class EthereumBlockchainAnchorClientTest {

    @Test
    fun `test chain IDs`() {
        assertEquals("eip155:1", EthereumBlockchainAnchorClient.MAINNET)
        assertEquals("eip155:11155111", EthereumBlockchainAnchorClient.SEPOLIA)
    }

    @Test
    fun `test create client with valid chain ID`() {
        val client = InMemoryBlockchainAnchorClient(EthereumBlockchainAnchorClient.MAINNET)

        // Test that we can write and read to verify chain ID is correct
        val testPayload = buildJsonObject { put("test", "value") }
        val result = runBlocking { client.writePayload(testPayload) }
        assertEquals(EthereumBlockchainAnchorClient.MAINNET, result.ref.chainId)
    }

    @Test
    fun `test anchor payload`() = runBlocking {
        val client = InMemoryBlockchainAnchorClient(EthereumBlockchainAnchorClient.SEPOLIA)
        val payload = buildJsonObject {
            put("digest", "uABC123...")
        }

        val result = client.writePayload(payload)

        assertNotNull(result.ref)
        assertEquals(EthereumBlockchainAnchorClient.SEPOLIA, result.ref.chainId)
        assertNotNull(result.ref.txHash)
    }

    @Test
    fun `test read anchored payload`() = runBlocking {
        val client = InMemoryBlockchainAnchorClient(EthereumBlockchainAnchorClient.MAINNET)
        val payload = buildJsonObject {
            put("digest", "uABC123...")
        }

        val writeResult = client.writePayload(payload)
        val readResult = client.readPayload(writeResult.ref)

        assertEquals(payload, readResult.payload)
    }
}

