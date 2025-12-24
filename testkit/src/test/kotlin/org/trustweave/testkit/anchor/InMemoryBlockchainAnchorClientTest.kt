package org.trustweave.testkit.anchor

import org.trustweave.anchor.*
import org.trustweave.core.exception.TrustWeaveException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class InMemoryBlockchainAnchorClientTest {

    @Test
    fun `writePayload should store payload and return anchor result`() = runBlocking {
        val client = InMemoryBlockchainAnchorClient("test:chain")
        val payload = buildJsonObject {
            put("test", "value")
        }

        val result = client.writePayload(payload)

        assertNotNull(result.ref)
        assertEquals("test:chain", result.ref.chainId)
        assertNotNull(result.ref.txHash)
        assertEquals(payload, result.payload)
        assertEquals(1, client.size())
    }

    @Test
    fun `readPayload should retrieve stored payload`() = runBlocking {
        val client = InMemoryBlockchainAnchorClient("test:chain")
        val payload = buildJsonObject {
            put("key", "value")
        }

        val writeResult = client.writePayload(payload)
        val readResult = client.readPayload(writeResult.ref)

        assertEquals(writeResult.payload, readResult.payload)
        assertEquals(writeResult.ref, readResult.ref)
    }

    @Test
    fun `readPayload should throw NotFoundException for non-existent anchor`() = runBlocking {
        val client = InMemoryBlockchainAnchorClient("test:chain")
        val ref = AnchorRef("test:chain", "nonexistent")

        assertThrows<TrustWeaveException.NotFound> {
            client.readPayload(ref)
        }
    }

    @Test
    fun `round-trip writePayload and readPayload should work`() = runBlocking {
        val client = InMemoryBlockchainAnchorClient("algorand:mainnet", "app-123")
        val payload = buildJsonObject {
            put("vcId", "vc-123")
            put("vcDigest", "uABC123")
        }

        val result = client.writePayload(payload)
        val retrieved = client.readPayload(result.ref)

        assertEquals(payload, retrieved.payload)
        assertEquals("algorand:mainnet", retrieved.ref.chainId)
        assertEquals("app-123", retrieved.ref.contract)
    }
}

