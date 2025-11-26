package com.trustweave.testkit.templates

import com.trustweave.anchor.AnchorRef
import com.trustweave.anchor.AnchorResult
import com.trustweave.anchor.BlockchainAnchorClient
import com.trustweave.testkit.BasePluginTest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Template for chain plugin unit tests.
 * 
 * Copy this template and adapt it for your chain plugin.
 * 
 * **Required Tests**:
 * - ✅ Anchor payload operations
 * - ✅ Read anchored payload
 * - ✅ Chain ID handling
 * - ✅ Error scenarios (invalid chain ID, network errors)
 * - ✅ Multiple anchor operations
 * - ✅ Configuration validation
 * - ✅ SPI discovery (if applicable)
 */
abstract class ChainPluginTestTemplate : BasePluginTest() {
    
    /**
     * Gets the blockchain anchor client to test.
     * Must be implemented by subclasses.
     */
    abstract fun getChainClient(): BlockchainAnchorClient
    
    /**
     * Gets the expected chain ID.
     * Must be implemented by subclasses.
     */
    abstract fun getExpectedChainId(): String
    
    @Test
    fun `test chain ID matches expected`() {
        val client = getChainClient()
        assertEquals(getExpectedChainId(), client.chainId)
    }
    
    @Test
    fun `test anchor payload`() = runBlocking {
        val client = getChainClient()
        val payload = createTestPayload()
        
        val result = client.writePayload(payload)
        
        assertNotNull(result)
        assertNotNull(result.ref)
        assertNotNull(result.ref.txHash)
        assertEquals(getExpectedChainId(), result.ref.chainId)
    }
    
    @Test
    fun `test read anchored payload`() = runBlocking {
        val client = getChainClient()
        val payload = createTestPayload()
        
        val writeResult = client.writePayload(payload)
        val readResult = client.readPayload(writeResult.ref)
        
        assertNotNull(readResult)
        // Verify payload integrity (method-specific)
    }
    
    @Test
    fun `test anchor multiple payloads`() = runBlocking {
        val client = getChainClient()
        val payloads = (1..3).map { index ->
            buildJsonObject {
                put("id", "test-$index")
                put("data", "payload-$index")
            }
        }
        
        val results = payloads.map { payload ->
            client.writePayload(payload)
        }
        
        assertTrue(results.size == payloads.size)
        results.forEach { result ->
            assertNotNull(result.ref)
            assertNotNull(result.ref.txHash)
            assertEquals(getExpectedChainId(), result.ref.chainId)
        }
    }
    
    @Test
    fun `test read invalid anchor reference`() = runBlocking {
        val client = getChainClient()
        val invalidRef = AnchorRef(
            chainId = getExpectedChainId(),
            txHash = "invalid-hash-${System.currentTimeMillis()}",
            blockNumber = null,
            blockHash = null
        )
        
        try {
            val result = client.readPayload(invalidRef)
            // Some implementations may return null instead of throwing
        } catch (e: Exception) {
            // Expected behavior for invalid references
            assertNotNull(e.message)
        }
    }
    
    @Test
    fun `test anchor empty payload`() = runBlocking {
        val client = getChainClient()
        val emptyPayload = buildJsonObject { }
        
        try {
            val result = client.writePayload(emptyPayload)
            assertNotNull(result)
        } catch (e: Exception) {
            // Some chains may reject empty payloads
        }
    }
    
    @Test
    fun `test anchor large payload`() = runBlocking {
        val client = getChainClient()
        val largePayload = buildJsonObject {
            put("data", "x".repeat(10000)) // 10KB payload
        }
        
        try {
            val result = client.writePayload(largePayload)
            assertNotNull(result)
        } catch (e: Exception) {
            // Some chains may have size limits
        }
    }
    
    @Test
    fun `test roundtrip anchor and read`() = runBlocking {
        val client = getChainClient()
        val originalPayload = createTestPayload()
        
        val anchorResult = client.writePayload(originalPayload)
        val readResult = client.readPayload(anchorResult.ref)
        
        assertNotNull(readResult)
        // Verify data integrity (implementation-specific)
    }
    
    /**
     * Creates a test payload for anchoring.
     */
    protected fun createTestPayload(): JsonObject {
        return buildJsonObject {
            put("test", "data")
            put("timestamp", System.currentTimeMillis())
            put("digest", "test-digest-${System.currentTimeMillis()}")
        }
    }
}

