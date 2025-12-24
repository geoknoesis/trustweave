package org.trustweave.anchor

import org.trustweave.core.exception.TrustWeaveException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for BlockchainRegistry API.
 */
class BlockchainRegistryTest {

    private lateinit var registry: BlockchainAnchorRegistry

    @BeforeEach
    fun setup() {
        registry = BlockchainAnchorRegistry()
    }

    @AfterEach
    fun cleanup() {
        registry.clear()
    }

    @Test
    fun `test register client`() = runBlocking {
        val client = createTestClient("algorand:testnet")

        registry.register("algorand:testnet", client)

        assertEquals(client, registry.get("algorand:testnet"))
    }

    @Test
    fun `test get client`() = runBlocking {
        val client1 = createTestClient("algorand:testnet")
        val client2 = createTestClient("eip155:137")

        registry.register("algorand:testnet", client1)
        registry.register("eip155:137", client2)

        assertEquals(client1, registry.get("algorand:testnet"))
        assertEquals(client2, registry.get("eip155:137"))
    }

    @Test
    fun `test get client returns null when not registered`() = runBlocking {
        assertNull(registry.get("nonexistent:chain"))
    }

    @Test
    fun `test clear all clients`() = runBlocking {
        val client1 = createTestClient("algorand:testnet")
        val client2 = createTestClient("eip155:137")

        registry.register("algorand:testnet", client1)
        registry.register("eip155:137", client2)

        registry.clear()

        assertNull(registry.get("algorand:testnet"))
        assertNull(registry.get("eip155:137"))
    }

    @Test
    fun `test anchorTyped extension function`() = runBlocking {
        val registry = BlockchainAnchorRegistry()
        val client = createTestClient("algorand:testnet")
        registry.register("algorand:testnet", client)

        @Serializable
        data class TestData(val name: String, val value: Int)

        val data = TestData("test", 42)
        val result = registry.anchorTyped(data, TestData.serializer(), "algorand:testnet")

        assertNotNull(result)
        assertNotNull(result.ref.txHash)
        assertEquals("algorand:testnet", result.ref.chainId)
    }

    @Test
    fun `test anchorTyped extension fails when client not registered`() = runBlocking {
        val registry = BlockchainAnchorRegistry()

        @Serializable
        data class TestData(val name: String)

        val data = TestData("test")

        assertFailsWith<IllegalArgumentException> {
            registry.anchorTyped(data, TestData.serializer(), "nonexistent:chain")
        }
    }

    @Test
    fun `test readTyped extension function`() = runBlocking {
        val registry = BlockchainAnchorRegistry()
        val client = createTestClient("algorand:testnet")
        registry.register("algorand:testnet", client)

        @Serializable
        data class TestData(val name: String, val value: Int)

        val data = TestData("test", 42)
        val anchorResult = registry.anchorTyped(data, TestData.serializer(), "algorand:testnet")

        val readData = registry.readTyped<TestData>(anchorResult.ref, TestData.serializer())

        assertEquals(data.name, readData.name)
        assertEquals(data.value, readData.value)
    }

    @Test
    fun `test readTyped extension fails when client not registered`() = runBlocking {
        val registry = BlockchainAnchorRegistry()

        @Serializable
        data class TestData(val name: String)

        val ref = AnchorRef(chainId = "nonexistent:chain", txHash = "tx-123")

        assertFailsWith<IllegalArgumentException> {
            registry.readTyped<TestData>(ref, TestData.serializer())
        }
    }

    private fun createTestClient(chainId: String): BlockchainAnchorClient {
        return object : BlockchainAnchorClient {
            private val storage = mutableMapOf<String, AnchorResult>()
            private var txCounter = 0

            override suspend fun writePayload(payload: kotlinx.serialization.json.JsonElement, mediaType: String): AnchorResult {
                val txHash = "tx_${txCounter++}_${System.currentTimeMillis()}"
                val ref = AnchorRef(chainId = chainId, txHash = txHash)
                val result = AnchorResult(
                    ref = ref,
                    payload = payload,
                    mediaType = mediaType,
                    timestamp = System.currentTimeMillis() / 1000
                )
                storage[txHash] = result
                return result
            }

            override suspend fun readPayload(ref: AnchorRef): AnchorResult {
                return storage[ref.txHash] ?: throw TrustWeaveException.NotFound("Not found: ${ref.txHash}")
            }
        }
    }
}

