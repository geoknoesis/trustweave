package org.trustweave.anchor.polygon

import org.trustweave.anchor.AbstractBlockchainAnchorClient
import org.trustweave.anchor.AnchorDigest
import org.trustweave.anchor.AnchorRef
import org.trustweave.anchor.exceptions.BlockchainException
import org.trustweave.core.exception.TrustWeaveException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for Polygon blockchain anchor client.
 * All tests use Amoy testnet only for safety.
 */
class PolygonBlockchainAnchorClientTest {

    @Test
    fun `should create client for Amoy testnet`() = runBlocking {
        val client = PolygonBlockchainAnchorClient(PolygonBlockchainAnchorClient.AMOY)
        assertNotNull(client)
    }

    @Test
    fun `should write and read payload in opt-in in-memory test mode`() = runBlocking {
        val client = PolygonBlockchainAnchorClient(
            PolygonBlockchainAnchorClient.AMOY,
            mapOf(AbstractBlockchainAnchorClient.OPTION_IN_MEMORY_TEST_MODE to true)
        )
        val payload = buildJsonObject {
            put("test", "data")
            put("number", 42)
        }

        val result = client.writePayload(payload)
        assertNotNull(result.ref)
        assertEquals(PolygonBlockchainAnchorClient.AMOY, result.ref.chainId)
        assertTrue(result.ref.txHash.startsWith("0x"))
        assertEquals(payload, result.payload)

        val readResult = client.readPayload(result.ref)
        assertEquals(result.payload, readResult.payload)
        assertEquals(result.ref, readResult.ref)
    }

    @Test
    fun `should generate collision-free test hashes in in-memory test mode`() = runBlocking {
        val client = PolygonBlockchainAnchorClient(
            PolygonBlockchainAnchorClient.AMOY,
            mapOf(AbstractBlockchainAnchorClient.OPTION_IN_MEMORY_TEST_MODE to true)
        )
        val payload = buildJsonObject { put("test", "data") }

        val hashes = (1..200).map { client.writePayload(payload).ref.txHash }

        assertEquals(hashes.size, hashes.toSet().size, "fabricated test hashes must never collide")
        hashes.forEach { hash ->
            assertTrue(hash.startsWith("0x") && hash.length == 66, "expected 0x-prefixed 32-byte hash, got $hash")
        }
    }

    @Test
    fun `should anchor digest envelope and verify in digest payload mode`() = runBlocking {
        val client = PolygonBlockchainAnchorClient(
            PolygonBlockchainAnchorClient.AMOY,
            mapOf(
                AbstractBlockchainAnchorClient.OPTION_IN_MEMORY_TEST_MODE to true,
                AbstractBlockchainAnchorClient.OPTION_PAYLOAD_MODE to
                    AbstractBlockchainAnchorClient.PAYLOAD_MODE_DIGEST
            )
        )
        val payload = buildJsonObject { put("ssn", "123-45-6789") }

        val result = client.writePayload(payload)
        assertEquals(payload, result.payload, "write result echoes the caller payload")
        assertEquals(
            AbstractBlockchainAnchorClient.PAYLOAD_MODE_DIGEST,
            result.ref.extra[AbstractBlockchainAnchorClient.OPTION_PAYLOAD_MODE]
        )

        // Reads return the digest envelope — the payload itself is never on-chain.
        val readResult = client.readPayload(result.ref)
        assertTrue(AnchorDigest.isEnvelope(readResult.payload))

        // Third-party verification: original payload verifies, tampered does not.
        assertTrue(client.verifyAnchor(payload, result.ref))
        val tampered = buildJsonObject { put("ssn", "999-99-9999") }
        assertEquals(false, client.verifyAnchor(tampered, result.ref))
    }

    @Test
    fun `should verify full-mode anchors structurally`() = runBlocking {
        val client = PolygonBlockchainAnchorClient(
            PolygonBlockchainAnchorClient.AMOY,
            mapOf(AbstractBlockchainAnchorClient.OPTION_IN_MEMORY_TEST_MODE to true)
        )
        val payload = buildJsonObject {
            put("a", 1)
            put("b", 2)
        }

        val result = client.writePayload(payload)

        assertTrue(client.verifyAnchor(payload, result.ref))
        // Structural comparison: key order does not matter in full mode.
        val reordered = buildJsonObject {
            put("b", 2)
            put("a", 1)
        }
        assertTrue(client.verifyAnchor(reordered, result.ref))
        assertEquals(false, client.verifyAnchor(buildJsonObject { put("a", 1) }, result.ref))
    }

    @Test
    fun `should throw NotFoundException for non-existent transaction`() = runBlocking {
        val client = PolygonBlockchainAnchorClient(PolygonBlockchainAnchorClient.AMOY)
        val ref = AnchorRef(
            chainId = PolygonBlockchainAnchorClient.AMOY,
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
    fun `should fail closed on write without credentials when test mode is off`() = runBlocking<Unit> {
        val client = PolygonBlockchainAnchorClient(PolygonBlockchainAnchorClient.AMOY)
        val payload = buildJsonObject { put("test", "data") }

        assertFailsWith<BlockchainException.ConfigurationFailed> {
            client.writePayload(payload)
        }
    }

    @Test
    fun `should reject invalid private key with configuration error`() {
        val exception = assertFailsWith<BlockchainException.ConfigurationFailed> {
            PolygonBlockchainAnchorClient(
                PolygonBlockchainAnchorClient.AMOY,
                mapOf("privateKey" to "not-a-valid-key")
            )
        }
        assertNotNull(exception.cause, "Parse failure must be carried as the cause")
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
        assertTrue(provider.supportedChains.contains(PolygonBlockchainAnchorClient.AMOY))

        val testnetClient = provider.create(PolygonBlockchainAnchorClient.AMOY)
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
        assertTrue(result.registeredChains.contains(PolygonBlockchainAnchorClient.AMOY))

        val testnetClient = result.registry.get(PolygonBlockchainAnchorClient.AMOY)
        assertNotNull(testnetClient)
    }

    @Test
    fun `should setup Amoy testnet chain for testing`() {
        val result = PolygonIntegration.setup(
            chainIds = listOf(PolygonBlockchainAnchorClient.AMOY)
        )
        assertEquals(1, result.registeredChains.size)
        assertEquals(PolygonBlockchainAnchorClient.AMOY, result.registeredChains[0])

        val client = result.registry.get(PolygonBlockchainAnchorClient.AMOY)
        assertNotNull(client)
    }
}

