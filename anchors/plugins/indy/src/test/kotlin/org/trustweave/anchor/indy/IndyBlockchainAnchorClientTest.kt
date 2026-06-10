package org.trustweave.anchor.indy

import org.trustweave.anchor.*
import org.trustweave.anchor.exceptions.BlockchainException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * Tests for IndyBlockchainAnchorClient.
 * Note: These tests opt in to the in-memory test mode (no wallet required).
 */
class IndyBlockchainAnchorClientTest {

    private val testModeOptions =
        mapOf(AbstractBlockchainAnchorClient.OPTION_IN_MEMORY_TEST_MODE to true)

    @Test
    fun `should create client with testnet chain ID`() = runBlocking {
        val client = IndyBlockchainAnchorClient(
            chainId = IndyBlockchainAnchorClient.BCOVRIN_TESTNET,
            options = emptyMap()
        )
        assertNotNull(client)
    }

    @Test
    fun `should anchor payload using opt-in in-memory test mode`() = runBlocking {
        val client = IndyBlockchainAnchorClient(
            chainId = IndyBlockchainAnchorClient.BCOVRIN_TESTNET,
            options = testModeOptions
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
    fun `should fail closed on write without signer when test mode is off`() = runBlocking<Unit> {
        val client = IndyBlockchainAnchorClient(
            chainId = IndyBlockchainAnchorClient.BCOVRIN_TESTNET,
            options = emptyMap()
        )

        val payload = buildJsonObject { put("test", "data") }

        assertFailsWith<BlockchainException.ConfigurationFailed> {
            client.writePayload(payload)
        }
    }

    @Test
    fun `should read anchored payload`() = runBlocking {
        val client = IndyBlockchainAnchorClient(
            chainId = IndyBlockchainAnchorClient.BCOVRIN_TESTNET,
            options = testModeOptions
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
            options = mapOf("poolEndpoint" to "https://custom.pool.example.com") + testModeOptions
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

