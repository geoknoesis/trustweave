package io.geoknoesis.vericore.anchor

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
}

class BlockchainRegistryTest {

    @Test
    fun `BlockchainRegistry should register and retrieve clients`() {
        val mockClient = object : BlockchainAnchorClient {
            override suspend fun writePayload(payload: JsonElement, mediaType: String) = TODO()
            override suspend fun readPayload(ref: AnchorRef) = TODO()
        }

        BlockchainRegistry.register("test:chain", mockClient)
        assertEquals(mockClient, BlockchainRegistry.get("test:chain"))
        assertNull(BlockchainRegistry.get("nonexistent:chain"))

        BlockchainRegistry.clear()
    }
}

