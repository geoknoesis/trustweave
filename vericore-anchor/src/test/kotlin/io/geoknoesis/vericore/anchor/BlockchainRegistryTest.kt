package io.geoknoesis.vericore.anchor

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive tests for BlockchainRegistry API.
 */
class BlockchainRegistryTest {

    @BeforeEach
    fun setup() {
        BlockchainRegistry.clear()
    }

    @AfterEach
    fun cleanup() {
        BlockchainRegistry.clear()
    }

    @Test
    fun `test register client`() = runBlocking {
        val client = createTestClient("algorand:testnet")
        
        BlockchainRegistry.register("algorand:testnet", client)
        
        assertEquals(client, BlockchainRegistry.get("algorand:testnet"))
    }

    @Test
    fun `test get client`() = runBlocking {
        val client1 = createTestClient("algorand:testnet")
        val client2 = createTestClient("eip155:137")
        
        BlockchainRegistry.register("algorand:testnet", client1)
        BlockchainRegistry.register("eip155:137", client2)
        
        assertEquals(client1, BlockchainRegistry.get("algorand:testnet"))
        assertEquals(client2, BlockchainRegistry.get("eip155:137"))
    }

    @Test
    fun `test get client returns null when not registered`() = runBlocking {
        assertNull(BlockchainRegistry.get("nonexistent:chain"))
    }

    @Test
    fun `test clear all clients`() = runBlocking {
        val client1 = createTestClient("algorand:testnet")
        val client2 = createTestClient("eip155:137")
        
        BlockchainRegistry.register("algorand:testnet", client1)
        BlockchainRegistry.register("eip155:137", client2)
        
        BlockchainRegistry.clear()
        
        assertNull(BlockchainRegistry.get("algorand:testnet"))
        assertNull(BlockchainRegistry.get("eip155:137"))
    }

    @Test
    fun `test anchorTyped helper function`() = runBlocking {
        val client = createTestClient("algorand:testnet")
        BlockchainRegistry.register("algorand:testnet", client)
        
        @Serializable
        data class TestData(val name: String, val value: Int)
        
        val data = TestData("test", 42)
        val result = anchorTyped(data, TestData.serializer(), "algorand:testnet")
        
        assertNotNull(result)
        assertNotNull(result.ref.txHash)
        assertEquals("algorand:testnet", result.ref.chainId)
    }

    @Test
    fun `test anchorTyped fails when client not registered`() = runBlocking {
        @Serializable
        data class TestData(val name: String)
        
        val data = TestData("test")
        
        assertFailsWith<IllegalArgumentException> {
            anchorTyped(data, TestData.serializer(), "nonexistent:chain")
        }
    }

    @Test
    fun `test readTyped helper function`() = runBlocking {
        val client = createTestClient("algorand:testnet")
        BlockchainRegistry.register("algorand:testnet", client)
        
        @Serializable
        data class TestData(val name: String, val value: Int)
        
        val data = TestData("test", 42)
        val anchorResult = anchorTyped(data, TestData.serializer(), "algorand:testnet")
        
        val readData = readTyped<TestData>(anchorResult.ref, TestData.serializer())
        
        assertEquals(data.name, readData.name)
        assertEquals(data.value, readData.value)
    }

    @Test
    fun `test readTyped fails when client not registered`() = runBlocking {
        @Serializable
        data class TestData(val name: String)
        
        val ref = AnchorRef(chainId = "nonexistent:chain", txHash = "tx-123")
        
        assertFailsWith<IllegalArgumentException> {
            readTyped<TestData>(ref, TestData.serializer())
        }
    }

    private fun createTestClient(chainId: String): BlockchainAnchorClient {
        return object : BlockchainAnchorClient {
            private val storage = mutableMapOf<String, AnchorResult>()
            private var txCounter = 0
            
            override suspend fun writePayload(payload: JsonElement, mediaType: String): AnchorResult {
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
                return storage[ref.txHash] ?: throw io.geoknoesis.vericore.core.NotFoundException("Not found: ${ref.txHash}")
            }
        }
    }
}

