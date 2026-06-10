package org.trustweave.anchor.cardano

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.trustweave.anchor.AbstractBlockchainAnchorClient
import org.trustweave.anchor.AnchorRef
import org.trustweave.anchor.exceptions.BlockchainException
import org.trustweave.core.exception.TrustWeaveException
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CardanoBlockchainAnchorClientTest {

    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun client(inMemoryTestMode: Boolean = false): CardanoBlockchainAnchorClient {
        val cfg = CardanoAnchorConfig(
            blockfrostProjectId = "preview-test",
            network = CardanoNetwork.Preview,
            blockfrostBaseUrlOverride = server.url("/api/v0").toString().trimEnd('/'),
        )
        val options = cfg.toMap() +
            mapOf(AbstractBlockchainAnchorClient.OPTION_IN_MEMORY_TEST_MODE to inMemoryTestMode)
        return CardanoBlockchainAnchorClient(CardanoBlockchainAnchorClient.PREVIEW, options, cfg)
    }

    @Test
    fun `rejects chain id from wrong network`() {
        assertThrows<IllegalArgumentException> {
            CardanoBlockchainAnchorClient("ethereum:mainnet")
        }
    }

    @Test
    fun `rejects mismatched chainId and config network`() {
        val cfg = CardanoAnchorConfig(
            blockfrostProjectId = "preview-test",
            network = CardanoNetwork.Mainnet,
        )
        assertThrows<IllegalArgumentException> {
            CardanoBlockchainAnchorClient(CardanoBlockchainAnchorClient.PREVIEW, cfg)
        }
    }

    @Test
    fun `writePayload uses in-memory fallback when no submitter configured and test mode is on`() = runBlocking {
        client(inMemoryTestMode = true).use { c ->
            val payload = buildJsonObject {
                put("kind", JsonPrimitive("test"))
                put("nonce", JsonPrimitive(42))
            }
            val result = c.writePayload(payload)
            assertNotNull(result.ref.txHash)
            assertTrue(result.ref.txHash.startsWith("cardano_test_"))
            assertEquals(CardanoBlockchainAnchorClient.PREVIEW, result.ref.chainId)
            assertEquals(payload, result.payload)
            assertEquals("preview", result.ref.extra["network"])
            assertEquals("cip-20", result.ref.extra["protocol"])
            assertEquals("674", result.ref.extra["label"])
        }
    }

    @Test
    fun `writePayload fails closed when no submitter configured and test mode is off`() = runBlocking {
        client().use { c ->
            val payload = buildJsonObject { put("kind", JsonPrimitive("test")) }
            assertThrows<BlockchainException.ConfigurationFailed> {
                runBlocking { c.writePayload(payload) }
            }
            Unit
        }
    }

    @Test
    fun `readPayload returns NotFound on 404`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"status_code":404,"error":"Not Found","message":"x"}"""))
        client().use { c ->
            val ref = AnchorRef(
                chainId = CardanoBlockchainAnchorClient.PREVIEW,
                txHash = "00".repeat(32),
            )
            // Test mode is off, so AbstractBlockchainAnchorClient.readPayload must surface
            // the chain's NotFound directly (no in-memory fallback).
            assertThrows<TrustWeaveException.NotFound> {
                runBlocking { c.readPayload(ref) }
            }
        }
    }

    @Test
    fun `readPayload returns BlockchainException on 500`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"error":"boom"}"""))
        client().use { c ->
            val ref = AnchorRef(
                chainId = CardanoBlockchainAnchorClient.PREVIEW,
                txHash = "ff".repeat(32),
            )
            assertThrows<BlockchainException> {
                runBlocking { c.readPayload(ref) }
            }
        }
    }

    @Test
    fun `readPayload deserialises Blockfrost CIP-20 response`() = runBlocking {
        val body = """
            [
              {
                "label": "674",
                "json_metadata": {
                  "msg": ["{\"hello\":\"world\",\"n\":", "7}"]
                }
              }
            ]
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))

        client().use { c ->
            val ref = AnchorRef(
                chainId = CardanoBlockchainAnchorClient.PREVIEW,
                txHash = "aa".repeat(32),
            )
            val result = c.readPayload(ref)
            val obj = result.payload as JsonObject
            assertEquals("world", obj["hello"]!!.jsonPrimitive.content)
            assertEquals("7", obj["n"]!!.jsonPrimitive.content)
        }
    }
}

class CardanoProviderTest {

    @Test
    fun `provider supports cardano chains`() {
        val p = CardanoBlockchainAnchorClientProvider()
        assertEquals("cardano", p.name)
        assertTrue(p.supportedChains.contains(CardanoBlockchainAnchorClient.MAINNET))
        assertTrue(p.supportedChains.contains(CardanoBlockchainAnchorClient.PREVIEW))
        assertTrue(p.supportedChains.contains(CardanoBlockchainAnchorClient.PREPROD))
    }

    @Test
    fun `provider returns null for non-cardano chain`() {
        val p = CardanoBlockchainAnchorClientProvider()
        assertEquals(null, p.create("ethereum:mainnet"))
        assertEquals(null, p.create("cardano:bogusnet"))
    }

    @Test
    fun `provider returns null when no project id available`() {
        // Ensure env var is not set during the test run
        if (System.getenv("CARDANO_BLOCKFROST_PROJECT_ID") != null) return
        val p = CardanoBlockchainAnchorClientProvider()
        assertEquals(null, p.create(CardanoBlockchainAnchorClient.PREVIEW))
    }

    @Test
    fun `provider creates client with explicit project id`() {
        val p = CardanoBlockchainAnchorClientProvider()
        val client = p.create(
            CardanoBlockchainAnchorClient.PREVIEW,
            mapOf(CardanoAnchorConfig.KEY_PROJECT_ID to "preview-x"),
        )
        assertNotNull(client)
    }
}
