package com.trustweave.anchor

import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AnchorRefTest {

    @Test
    fun `AnchorRef should store chain and transaction information`() {
        val ref = AnchorRef(
            chainId = "algorand:mainnet",
            txHash = "ABC123",
            contract = "app-123"
        )

        assertEquals("algorand:mainnet", ref.chainId)
        assertEquals("ABC123", ref.txHash)
        assertEquals("app-123", ref.contract)
    }
    
    @Test
    fun `AnchorRef with extra metadata`() {
        val ref = AnchorRef(
            chainId = "algorand:testnet",
            txHash = "tx-123",
            contract = "app-456",
            extra = mapOf("mediaType" to "application/json", "custom" to "value")
        )
        
        assertEquals(2, ref.extra.size)
        assertEquals("application/json", ref.extra["mediaType"])
        assertEquals("value", ref.extra["custom"])
    }
    
    @Test
    fun `AnchorResult with all fields`() {
        val ref = AnchorRef(chainId = "algorand:testnet", txHash = "tx-123")
        val payload = buildJsonObject { put("data", "test") }
        
        val result = AnchorResult(
            ref = ref,
            payload = payload,
            mediaType = "application/json",
            timestamp = 1234567890L
        )
        
        assertEquals(ref, result.ref)
        assertEquals(payload, result.payload)
        assertEquals("application/json", result.mediaType)
        assertEquals(1234567890L, result.timestamp)
    }
}


