package io.geoknoesis.vericore.algorand

import io.geoknoesis.vericore.anchor.*
import io.geoknoesis.vericore.core.NotFoundException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for Algorand blockchain anchor client.
 * All tests use testnet only for safety.
 */
class AlgorandBlockchainAnchorClientTest {

    @AfterEach
    fun cleanup() {
        BlockchainRegistry.clear()
    }

    @Test
    fun `should create client for testnet`() = runBlocking {
        val client = AlgorandBlockchainAnchorClient(AlgorandBlockchainAnchorClient.TESTNET)
        assertNotNull(client)
    }

    @Test
    fun `should write and read payload`() = runBlocking {
        val client = AlgorandBlockchainAnchorClient(AlgorandBlockchainAnchorClient.TESTNET)
        val payload = buildJsonObject {
            put("test", "data")
            put("number", 42)
        }

        val result = client.writePayload(payload)
        assertNotNull(result.ref)
        assertEquals(AlgorandBlockchainAnchorClient.TESTNET, result.ref.chainId)
        assertTrue(result.ref.txHash.startsWith("algo_"))
        assertEquals(payload, result.payload)

        val readResult = client.readPayload(result.ref)
        assertEquals(result.payload, readResult.payload)
        assertEquals(result.ref, readResult.ref)
    }

    @Test
    fun `should throw NotFoundException for non-existent transaction`() = runBlocking {
        val client = AlgorandBlockchainAnchorClient(AlgorandBlockchainAnchorClient.TESTNET)
        val ref = AnchorRef(
            chainId = AlgorandBlockchainAnchorClient.TESTNET,
            txHash = "nonexistent_tx"
        )

        try {
            client.readPayload(ref)
            assert(false) { "Should have thrown NotFoundException" }
        } catch (e: NotFoundException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun `should reject invalid chain ID`() {
        try {
            AlgorandBlockchainAnchorClient("ethereum:mainnet")
            assert(false) { "Should have thrown IllegalArgumentException" }
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
            assertTrue(e.message?.contains("Invalid chain ID") == true)
        }
    }
}

class AlgorandBlockchainAnchorClientProviderTest {

    @Test
    fun `should create client for supported chains`() {
        val provider = AlgorandBlockchainAnchorClientProvider()
        assertEquals("algorand", provider.name)
        assertTrue(provider.supportedChains.contains(AlgorandBlockchainAnchorClient.MAINNET))
        assertTrue(provider.supportedChains.contains(AlgorandBlockchainAnchorClient.TESTNET))
        assertTrue(provider.supportedChains.contains(AlgorandBlockchainAnchorClient.BETANET))

        val testnetClient = provider.create(AlgorandBlockchainAnchorClient.TESTNET)
        assertNotNull(testnetClient)
    }

    @Test
    fun `should return null for unsupported chain`() {
        val provider = AlgorandBlockchainAnchorClientProvider()
        val client = provider.create("ethereum:mainnet")
        assertEquals(null, client)
    }
}

class AlgorandIntegrationTest {

    @AfterEach
    fun cleanup() {
        BlockchainRegistry.clear()
    }

    @Test
    fun `should discover and register via SPI`() {
        val registeredChains = AlgorandIntegration.discoverAndRegister()
        assertTrue(registeredChains.isNotEmpty())
        assertTrue(registeredChains.contains(AlgorandBlockchainAnchorClient.MAINNET))
        assertTrue(registeredChains.contains(AlgorandBlockchainAnchorClient.TESTNET))
        assertTrue(registeredChains.contains(AlgorandBlockchainAnchorClient.BETANET))

        val testnetClient = BlockchainRegistry.get(AlgorandBlockchainAnchorClient.TESTNET)
        assertNotNull(testnetClient)
    }

    @Test
    fun `should setup testnet chain for testing`() {
        val registeredChains = AlgorandIntegration.setup(
            chainIds = listOf(AlgorandBlockchainAnchorClient.TESTNET)
        )
        assertEquals(1, registeredChains.size)
        assertEquals(AlgorandBlockchainAnchorClient.TESTNET, registeredChains[0])

        val client = BlockchainRegistry.get(AlgorandBlockchainAnchorClient.TESTNET)
        assertNotNull(client)
    }
}

