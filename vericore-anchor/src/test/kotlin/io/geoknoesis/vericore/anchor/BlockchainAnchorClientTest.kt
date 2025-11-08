package io.geoknoesis.vericore.anchor

import io.geoknoesis.vericore.core.NotFoundException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Comprehensive tests for BlockchainAnchorClient API.
 * Uses a simple in-memory implementation for testing.
 */
class BlockchainAnchorClientTest {

    private lateinit var client: TestBlockchainAnchorClient
    private val chainId = "algorand:testnet"
    private val contract = "app-123"

    @BeforeEach
    fun setup() {
        client = TestBlockchainAnchorClient(chainId, contract)
    }

    @AfterEach
    fun cleanup() {
        client.clear()
    }
    
    /**
     * Simple test implementation of BlockchainAnchorClient.
     */
    private class TestBlockchainAnchorClient(
        private val testChainId: String,
        private val testContract: String?
    ) : BlockchainAnchorClient {
        private val storage = ConcurrentHashMap<String, AnchorResult>()
        private val txCounter = AtomicLong(0)
        
        override suspend fun writePayload(
            payload: JsonElement,
            mediaType: String
        ): AnchorResult {
            val txHash = "tx_${txCounter.incrementAndGet()}_${System.currentTimeMillis()}"
            val ref = AnchorRef(
                chainId = testChainId,
                txHash = txHash,
                contract = testContract
            )
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
            if (ref.chainId != testChainId) {
                throw IllegalArgumentException("Chain ID mismatch: expected $testChainId, got ${ref.chainId}")
            }
            return storage[ref.txHash]
                ?: throw NotFoundException("Anchor not found: ${ref.txHash}")
        }
        
        fun clear() {
            storage.clear()
            txCounter.set(0)
        }
        
        fun size(): Int = storage.size
    }

    @Test
    fun `test write payload successfully`() = runBlocking {
        val payload = buildJsonObject {
            put("vcDigest", "uABC123")
            put("issuer", "did:key:issuer")
        }
        
        val result = client.writePayload(payload, "application/json")
        
        assertNotNull(result)
        assertEquals(chainId, result.ref.chainId)
        assertNotNull(result.ref.txHash)
        assertEquals(contract, result.ref.contract)
        assertEquals(payload, result.payload)
        assertEquals("application/json", result.mediaType)
        assertNotNull(result.timestamp)
        assertTrue(result.timestamp!! > 0)
    }

    @Test
    fun `test read payload successfully`() = runBlocking {
        val payload = buildJsonObject {
            put("vcDigest", "uABC123")
            put("issuer", "did:key:issuer")
        }
        
        val writeResult = client.writePayload(payload, "application/json")
        
        val readResult = client.readPayload(writeResult.ref)
        
        assertEquals(writeResult.ref.txHash, readResult.ref.txHash)
        assertEquals(payload, readResult.payload)
        assertEquals("application/json", readResult.mediaType)
    }

    @Test
    fun `test read payload fails when not found`() = runBlocking {
        val ref = AnchorRef(
            chainId = chainId,
            txHash = "nonexistent-tx-hash",
            contract = contract
        )
        
        assertFailsWith<NotFoundException> {
            client.readPayload(ref)
        }
    }

    @Test
    fun `test read payload fails when chain ID mismatch`() = runBlocking {
        val payload = buildJsonObject {
            put("vcDigest", "uABC123")
        }
        val writeResult = client.writePayload(payload, "application/json")
        
        val wrongChainRef = writeResult.ref.copy(chainId = "different:chain")
        
        assertFailsWith<IllegalArgumentException> {
            client.readPayload(wrongChainRef)
        }
    }

    @Test
    fun `test write multiple payloads`() = runBlocking {
        val payload1 = buildJsonObject { put("id", "1") }
        val payload2 = buildJsonObject { put("id", "2") }
        val payload3 = buildJsonObject { put("id", "3") }
        
        val result1 = client.writePayload(payload1, "application/json")
        val result2 = client.writePayload(payload2, "application/json")
        val result3 = client.writePayload(payload3, "application/json")
        
        assertNotEquals(result1.ref.txHash, result2.ref.txHash)
        assertNotEquals(result2.ref.txHash, result3.ref.txHash)
        assertEquals(3, client.size())
    }

    @Test
    fun `test write payload with different media types`() = runBlocking {
        val payload = buildJsonObject { put("data", "test") }
        
        val jsonResult = client.writePayload(payload, "application/json")
        val cborResult = client.writePayload(payload, "application/cbor")
        
        assertEquals("application/json", jsonResult.mediaType)
        assertEquals("application/cbor", cborResult.mediaType)
    }

    @Test
    fun `test write payload with complex JSON structure`() = runBlocking {
        val payload = buildJsonObject {
            put("vcDigest", "uABC123")
            put("issuer", "did:key:issuer")
            put("metadata", buildJsonObject {
                put("timestamp", System.currentTimeMillis())
                put("tags", buildJsonArray {
                    add("important")
                    add("verified")
                })
            })
        }
        
        val result = client.writePayload(payload, "application/json")
        
        val readResult = client.readPayload(result.ref)
        assertEquals(payload, readResult.payload)
    }

    @Test
    fun `test write payload with array payload`() = runBlocking {
        val payload = buildJsonArray {
            add(buildJsonObject { put("id", "1") })
            add(buildJsonObject { put("id", "2") })
        }
        
        val result = client.writePayload(payload, "application/json")
        
        val readResult = client.readPayload(result.ref)
        assertEquals(payload, readResult.payload)
    }

    @Test
    fun `test write payload generates unique transaction hashes`() = runBlocking {
        val payload = buildJsonObject { put("data", "test") }
        
        val result1 = client.writePayload(payload, "application/json")
        Thread.sleep(10) // Small delay to ensure different timestamps
        val result2 = client.writePayload(payload, "application/json")
        
        assertNotEquals(result1.ref.txHash, result2.ref.txHash)
    }

    @Test
    fun `test write payload includes timestamp`() = runBlocking {
        val beforeWrite = System.currentTimeMillis() / 1000
        val payload = buildJsonObject { put("data", "test") }
        
        val result = client.writePayload(payload, "application/json")
        
        val afterWrite = System.currentTimeMillis() / 1000
        assertNotNull(result.timestamp)
        assertTrue(result.timestamp!! >= beforeWrite)
        assertTrue(result.timestamp!! <= afterWrite)
    }

    @Test
    fun `test clear removes all anchors`() = runBlocking {
        val payload = buildJsonObject { put("data", "test") }
        
        client.writePayload(payload, "application/json")
        client.writePayload(payload, "application/json")
        assertEquals(2, client.size())
        
        client.clear()
        
        assertEquals(0, client.size())
    }

    @Test
    fun `test anchor ref contains correct metadata`() = runBlocking {
        val payload = buildJsonObject { put("vcDigest", "uABC123") }
        
        val result = client.writePayload(payload, "application/json")
        
        assertEquals(chainId, result.ref.chainId)
        assertNotNull(result.ref.txHash)
        assertTrue(result.ref.txHash.startsWith("tx_"))
        assertEquals(contract, result.ref.contract)
    }

    @Test
    fun `test write payload without contract`() = runBlocking {
        val clientWithoutContract = TestBlockchainAnchorClient(chainId, null)
        val payload = buildJsonObject { put("data", "test") }
        
        val result = clientWithoutContract.writePayload(payload, "application/json")
        
        assertNull(result.ref.contract)
    }

    @Test
    fun `test read payload preserves all fields`() = runBlocking {
        val payload = buildJsonObject {
            put("vcDigest", "uABC123")
            put("issuer", "did:key:issuer")
        }
        
        val writeResult = client.writePayload(payload, "application/json")
        
        val readResult = client.readPayload(writeResult.ref)
        
        assertEquals(writeResult.ref.chainId, readResult.ref.chainId)
        assertEquals(writeResult.ref.txHash, readResult.ref.txHash)
        assertEquals(writeResult.ref.contract, readResult.ref.contract)
        assertEquals(writeResult.payload, readResult.payload)
        assertEquals(writeResult.mediaType, readResult.mediaType)
        assertEquals(writeResult.timestamp, readResult.timestamp)
    }
}

