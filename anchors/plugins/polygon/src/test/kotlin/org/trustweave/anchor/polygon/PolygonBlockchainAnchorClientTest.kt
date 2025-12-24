package org.trustweave.anchor.polygon

import org.trustweave.anchor.AnchorRef
import org.trustweave.core.exception.TrustWeaveException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for Polygon blockchain anchor client.
 * All tests use Mumbai testnet only for safety.
 */
class PolygonBlockchainAnchorClientTest {

    @Test
    fun `should create client for Mumbai testnet`() = runBlocking {
        val client = PolygonBlockchainAnchorClient(PolygonBlockchainAnchorClient.MUMBAI)
        assertNotNull(client)
    }

    @Test
    fun `should write and read payload`() = runBlocking {
        val client = PolygonBlockchainAnchorClient(PolygonBlockchainAnchorClient.MUMBAI)
        val payload = buildJsonObject {
            put("test", "data")
            put("number", 42)
        }

        val result = client.writePayload(payload)
        assertNotNull(result.ref)
        assertEquals(PolygonBlockchainAnchorClient.MUMBAI, result.ref.chainId)
        assertTrue(result.ref.txHash.startsWith("0x"))
        assertEquals(payload, result.payload)

        val readResult = client.readPayload(result.ref)
        assertEquals(result.payload, readResult.payload)
        assertEquals(result.ref, readResult.ref)
    }

    @Test
    fun `should throw NotFoundException for non-existent transaction`() = runBlocking {
        val client = PolygonBlockchainAnchorClient(PolygonBlockchainAnchorClient.MUMBAI)
        val ref = AnchorRef(
            chainId = PolygonBlockchainAnchorClient.MUMBAI,
            txHash = "0x0000000000000000000000000000000000000000000000000000000000000000"
        )

        try {
            client.readPayload(ref)
            assert(false) { "Should have thrown NotFound" }
        } catch (e: TrustWeaveException.NotFound) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun `should reject invalid chain ID`() {
        try {
            PolygonBlockchainAnchorClient("algorand:mainnet")
            assert(false) { "Should have thrown IllegalArgumentException" }
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
            assertTrue(e.message?.contains("Invalid chain ID") == true)
        }
    }

    @Test
    fun `should reject unsupported chain ID`() {
        try {
            PolygonBlockchainAnchorClient("eip155:1") // Ethereum mainnet
            assert(false) { "Should have thrown IllegalArgumentException" }
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
            assertTrue(e.message?.contains("Unsupported") == true)
        }
    }
}

class PolygonBlockchainAnchorClientProviderTest {

    @Test
    fun `should create client for supported chains`() {
        val provider = PolygonBlockchainAnchorClientProvider()
        assertEquals("polygon", provider.name)
        assertTrue(provider.supportedChains.contains(PolygonBlockchainAnchorClient.MAINNET))
        assertTrue(provider.supportedChains.contains(PolygonBlockchainAnchorClient.MUMBAI))

        val testnetClient = provider.create(PolygonBlockchainAnchorClient.MUMBAI)
        assertNotNull(testnetClient)
    }

    @Test
    fun `should return null for unsupported chain`() {
        val provider = PolygonBlockchainAnchorClientProvider()
        val client = provider.create("eip155:1") // Ethereum mainnet
        assertEquals(null, client)
    }
}

class PolygonIntegrationTest {

    @Test
    fun `should discover and register via SPI`() {
        val result = PolygonIntegration.discoverAndRegister()
        assertTrue(result.registeredChains.isNotEmpty())
        assertTrue(result.registeredChains.contains(PolygonBlockchainAnchorClient.MAINNET))
        assertTrue(result.registeredChains.contains(PolygonBlockchainAnchorClient.MUMBAI))

        val testnetClient = result.registry.get(PolygonBlockchainAnchorClient.MUMBAI)
        assertNotNull(testnetClient)
    }

    @Test
    fun `should setup Mumbai testnet chain for testing`() {
        val result = PolygonIntegration.setup(
            chainIds = listOf(PolygonBlockchainAnchorClient.MUMBAI)
        )
        assertEquals(1, result.registeredChains.size)
        assertEquals(PolygonBlockchainAnchorClient.MUMBAI, result.registeredChains[0])

        val client = result.registry.get(PolygonBlockchainAnchorClient.MUMBAI)
        assertNotNull(client)
    }
}

