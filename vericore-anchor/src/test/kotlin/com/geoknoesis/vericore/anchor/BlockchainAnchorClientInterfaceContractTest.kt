package com.geoknoesis.vericore.anchor

import com.geoknoesis.vericore.core.NotFoundException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * Comprehensive interface contract tests for BlockchainAnchorClient.
 * Tests all methods, branches, and edge cases.
 */
class BlockchainAnchorClientInterfaceContractTest {

    @Test
    fun `test BlockchainAnchorClient writePayload returns anchor result`() = runBlocking {
        val client = createMockClient("algorand:testnet")
        val payload = buildJsonObject {
            put("data", "test")
        }
        
        val result = client.writePayload(payload)
        
        assertNotNull(result)
        assertNotNull(result.ref)
        assertEquals("algorand:testnet", result.ref.chainId)
        assertNotNull(result.ref.txHash)
        assertEquals(payload, result.payload)
    }

    @Test
    fun `test BlockchainAnchorClient writePayload with custom media type`() = runBlocking {
        val client = createMockClient("algorand:testnet")
        val payload = buildJsonObject {
            put("data", "test")
        }
        
        val result = client.writePayload(payload, "application/json+ld")
        
        assertNotNull(result)
        assertEquals("application/json+ld", result.mediaType)
    }

    @Test
    fun `test BlockchainAnchorClient readPayload returns anchor result`() = runBlocking {
        val client = createMockClient("algorand:testnet")
        val payload = buildJsonObject {
            put("data", "test")
        }
        val writeResult = client.writePayload(payload)
        
        val readResult = client.readPayload(writeResult.ref)
        
        assertNotNull(readResult)
        assertEquals(writeResult.ref, readResult.ref)
        assertEquals(payload, readResult.payload)
    }

    @Test
    fun `test BlockchainAnchorClient readPayload throws NotFoundException`() = runBlocking {
        val client = createMockClient("algorand:testnet")
        val ref = AnchorRef(
            chainId = "algorand:testnet",
            txHash = "non-existent-hash"
        )
        
        assertFailsWith<NotFoundException> {
            client.readPayload(ref)
        }
    }

    @Test
    fun `test BlockchainAnchorClient writePayload with complex payload`() = runBlocking {
        val client = createMockClient("algorand:testnet")
        val payload = buildJsonObject {
            put("id", "credential-1")
            put("type", buildJsonArray { add("VerifiableCredential") })
            put("issuer", "did:key:issuer")
            put("credentialSubject", buildJsonObject {
                put("id", "did:key:subject")
                put("name", "John Doe")
            })
            put("metadata", buildJsonObject {
                put("created", "2024-01-01T00:00:00Z")
                put("tags", buildJsonArray { add("important"); add("verified") })
            })
        }
        
        val result = client.writePayload(payload)
        
        assertNotNull(result)
        assertEquals(payload, result.payload)
    }

    @Test
    fun `test BlockchainAnchorClient writePayload with array payload`() = runBlocking {
        val client = createMockClient("algorand:testnet")
        val payload = buildJsonArray {
            add("item1")
            add("item2")
            add("item3")
        }
        
        val result = client.writePayload(payload)
        
        assertNotNull(result)
        assertEquals(payload, result.payload)
    }

    @Test
    fun `test BlockchainAnchorClient writePayload with primitive payload`() = runBlocking {
        val client = createMockClient("algorand:testnet")
        val payload = JsonPrimitive("simple string")
        
        val result = client.writePayload(payload)
        
        assertNotNull(result)
        assertEquals(payload, result.payload)
    }

    @Test
    fun `test BlockchainAnchorClient writePayload includes timestamp`() = runBlocking {
        val client = createMockClient("algorand:testnet")
        val payload = buildJsonObject {
            put("data", "test")
        }
        
        val result = client.writePayload(payload)
        
        assertNotNull(result.timestamp)
        assertTrue(result.timestamp!! > 0)
    }

    @Test
    fun `test BlockchainAnchorClient writePayload with contract address`() = runBlocking {
        val client = createMockClient("algorand:testnet", "contract-123")
        val payload = buildJsonObject {
            put("data", "test")
        }
        
        val result = client.writePayload(payload)
        
        assertNotNull(result.ref.contract)
        assertEquals("contract-123", result.ref.contract)
    }

    @Test
    fun `test BlockchainAnchorClient writePayload with extra metadata`() = runBlocking {
        val client = createMockClient("algorand:testnet")
        val payload = buildJsonObject {
            put("data", "test")
        }
        
        val result = client.writePayload(payload)
        
        // Extra metadata may or may not be present
        assertNotNull(result.ref)
    }

    @Test
    fun `test BlockchainAnchorClient readPayload preserves media type`() = runBlocking {
        val client = createMockClient("algorand:testnet")
        val payload = buildJsonObject {
            put("data", "test")
        }
        val writeResult = client.writePayload(payload, "application/json+ld")
        
        val readResult = client.readPayload(writeResult.ref)
        
        assertEquals("application/json+ld", readResult.mediaType)
    }

    @Test
    fun `test BlockchainAnchorClient multiple writes create different hashes`() = runBlocking {
        val client = createMockClient("algorand:testnet")
        val payload1 = buildJsonObject { put("data", "test1") }
        val payload2 = buildJsonObject { put("data", "test2") }
        
        val result1 = client.writePayload(payload1)
        val result2 = client.writePayload(payload2)
        
        assertNotEquals(result1.ref.txHash, result2.ref.txHash)
    }

    private fun createMockClient(chainId: String, contract: String? = null): BlockchainAnchorClient {
        return object : BlockchainAnchorClient {
            private val storage = mutableMapOf<String, AnchorResult>()
            
            override suspend fun writePayload(
                payload: JsonElement,
                mediaType: String
            ): AnchorResult {
                val txHash = "tx-${java.util.UUID.randomUUID()}"
                val ref = AnchorRef(
                    chainId = chainId,
                    txHash = txHash,
                    contract = contract,
                    extra = emptyMap()
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
                val result = storage[ref.txHash]
                    ?: throw NotFoundException("Anchor not found: ${ref.txHash}")
                return result
            }
        }
    }
}



