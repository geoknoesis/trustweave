package com.trustweave.testkit.anchor

import com.trustweave.anchor.AnchorRef
import com.trustweave.anchor.AnchorResult
import com.trustweave.core.exception.TrustWeaveException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Branch coverage tests for InMemoryBlockchainAnchorClient.
 */
class InMemoryBlockchainAnchorClientBranchCoverageTest {

    private lateinit var client: InMemoryBlockchainAnchorClient

    @BeforeEach
    fun setup() {
        client = InMemoryBlockchainAnchorClient("algorand:testnet", "contract-123")
    }

    @Test
    fun `test InMemoryBlockchainAnchorClient constructor with chainId and contract`() {
        val client = InMemoryBlockchainAnchorClient("algorand:testnet", "contract-123")

        assertNotNull(client)
        assertEquals(0, client.size())
    }

    @Test
    fun `test InMemoryBlockchainAnchorClient constructor with chainId only`() {
        val client = InMemoryBlockchainAnchorClient("algorand:testnet")

        assertNotNull(client)
    }

    @Test
    fun `test InMemoryBlockchainAnchorClient writePayload stores payload`() = runBlocking {
        val payload = buildJsonObject {
            put("test", "data")
        }

        val result = client.writePayload(payload, "application/json")

        assertNotNull(result)
        assertNotNull(result.ref)
        assertEquals("algorand:testnet", result.ref.chainId)
        assertEquals("contract-123", result.ref.contract)
        assertEquals(payload, result.payload)
    }

    @Test
    fun `test InMemoryBlockchainAnchorClient writePayload generates unique txHash`() = runBlocking {
        val payload = buildJsonObject {
            put("test", "data")
        }

        val result1 = client.writePayload(payload, "application/json")
        val result2 = client.writePayload(payload, "application/json")

        assertNotEquals(result1.ref.txHash, result2.ref.txHash)
    }

    @Test
    fun `test InMemoryBlockchainAnchorClient writePayload increments counter`() = runBlocking {
        val payload = buildJsonObject {
            put("test", "data")
        }

        val result1 = client.writePayload(payload, "application/json")
        val result2 = client.writePayload(payload, "application/json")

        assertTrue(result2.ref.txHash > result1.ref.txHash)
    }

    @Test
    fun `test InMemoryBlockchainAnchorClient readPayload returns stored payload`() = runBlocking {
        val payload = buildJsonObject {
            put("test", "data")
        }

        val writeResult = client.writePayload(payload, "application/json")
        val readResult = client.readPayload(writeResult.ref)

        assertEquals(writeResult.ref.txHash, readResult.ref.txHash)
        assertEquals(payload, readResult.payload)
    }

    @Test
    fun `test InMemoryBlockchainAnchorClient readPayload throws when not found`() = runBlocking {
        val ref = AnchorRef(
            chainId = "algorand:testnet",
            txHash = "nonexistent",
            contract = "contract-123"
        )

        assertFailsWith<TrustWeaveException.NotFound> {
            client.readPayload(ref)
        }
    }

    @Test
    fun `test InMemoryBlockchainAnchorClient readPayload throws when chainId mismatch`() = runBlocking {
        val payload = buildJsonObject {
            put("test", "data")
        }
        val writeResult = client.writePayload(payload, "application/json")

        val wrongRef = AnchorRef(
            chainId = "polygon:mainnet",
            txHash = writeResult.ref.txHash,
            contract = writeResult.ref.contract
        )

        assertFailsWith<IllegalArgumentException> {
            client.readPayload(wrongRef)
        }
    }

    @Test
    fun `test InMemoryBlockchainAnchorClient clear removes all anchors`() = runBlocking {
        val payload = buildJsonObject {
            put("test", "data")
        }
        client.writePayload(payload, "application/json")
        client.writePayload(payload, "application/json")
        assertEquals(2, client.size())

        client.clear()

        assertEquals(0, client.size())
    }

    @Test
    fun `test InMemoryBlockchainAnchorClient size returns correct count`() = runBlocking {
        assertEquals(0, client.size())

        val payload = buildJsonObject {
            put("test", "data")
        }
        client.writePayload(payload, "application/json")
        assertEquals(1, client.size())

        client.writePayload(payload, "application/json")
        assertEquals(2, client.size())
    }

    @Test
    fun `test InMemoryBlockchainAnchorClient writePayload with different media types`() = runBlocking {
        val payload1 = buildJsonObject {
            put("test", "data1")
        }
        val payload2 = buildJsonObject {
            put("test", "data2")
        }

        val result1 = client.writePayload(payload1, "application/json")
        val result2 = client.writePayload(payload2, "application/cbor")

        assertEquals("application/json", result1.mediaType)
        assertEquals("application/cbor", result2.mediaType)
    }

    @Test
    fun `test InMemoryBlockchainAnchorClient writePayload with null contract`() = runBlocking {
        val client = InMemoryBlockchainAnchorClient("algorand:testnet", null)
        val payload = buildJsonObject {
            put("test", "data")
        }

        val result = client.writePayload(payload, "application/json")

        assertNull(result.ref.contract)
    }
}



