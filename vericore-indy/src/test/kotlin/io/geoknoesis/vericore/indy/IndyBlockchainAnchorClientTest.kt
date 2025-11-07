package io.geoknoesis.vericore.indy

import io.geoknoesis.vericore.anchor.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests for IndyBlockchainAnchorClient.
 * Note: These tests use in-memory fallback mode (no wallet required).
 */
class IndyBlockchainAnchorClientTest {

    @Test
    fun `should create client with testnet chain ID`() = runBlocking {
        val client = IndyBlockchainAnchorClient(
            chainId = IndyBlockchainAnchorClient.BCOVRIN_TESTNET,
            options = emptyMap()
        )
        assertNotNull(client)
    }

    @Test
    fun `should anchor payload using in-memory fallback`() = runBlocking {
        val client = IndyBlockchainAnchorClient(
            chainId = IndyBlockchainAnchorClient.BCOVRIN_TESTNET,
            options = emptyMap()
        )

        val payload = buildJsonObject {
            put("test", "data")
            put("digest", "uABC123...")
        }

        val result = client.writePayload(payload)
        assertNotNull(result.ref.txHash)
        assertEquals(payload, result.payload)
    }

    @Test
    fun `should read anchored payload`() = runBlocking {
        val client = IndyBlockchainAnchorClient(
            chainId = IndyBlockchainAnchorClient.BCOVRIN_TESTNET,
            options = emptyMap()
        )

        val payload = buildJsonObject {
            put("test", "data")
        }

        val anchorResult = client.writePayload(payload)
        val readResult = client.readPayload(anchorResult.ref)

        assertEquals(payload, readResult.payload)
    }

    @Test
    fun `should handle custom chain ID`() = runBlocking {
        val client = IndyBlockchainAnchorClient(
            chainId = "indy:testnet:custom-pool",
            options = mapOf("poolEndpoint" to "https://custom.pool.example.com")
        )

        val payload = buildJsonObject {
            put("custom", "test")
        }

        val result = client.writePayload(payload)
        assertEquals("indy:testnet:custom-pool", result.ref.chainId)
        assertEquals("testnet", result.ref.extra["network"])
        assertEquals("custom-pool", result.ref.extra["pool"])
    }

    @Test
    fun `should throw exception for invalid chain ID`() {
        try {
            IndyBlockchainAnchorClient(
                chainId = "invalid-chain-id",
                options = emptyMap()
            )
            kotlin.test.fail("Should throw exception for invalid chain ID")
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
        }
    }
}

