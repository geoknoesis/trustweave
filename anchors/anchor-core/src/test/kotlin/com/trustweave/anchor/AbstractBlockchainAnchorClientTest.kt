package com.trustweave.anchor

import com.trustweave.anchor.exceptions.BlockchainException
import com.trustweave.core.exception.TrustWeaveException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Comprehensive tests for AbstractBlockchainAnchorClient API.
 * Tests via a concrete test implementation.
 */
class AbstractBlockchainAnchorClientTest {

    @Test
    fun `test writePayload with fallback mode`() = runBlocking {
        val client = TestBlockchainAnchorClient(
            chainId = "algorand:testnet",
            canSubmit = false
        )

        val payload = buildJsonObject {
            put("data", "test")
        }

        val result = client.writePayload(payload)

        assertNotNull(result)
        assertEquals("algorand:testnet", result.ref.chainId)
        assertNotNull(result.ref.txHash)
        assertTrue(result.ref.txHash.startsWith("test_tx_"))
    }

    @Test
    fun `test writePayload with real mode`() = runBlocking {
        val client = TestBlockchainAnchorClient(
            chainId = "algorand:testnet",
            canSubmit = true
        )

        val payload = buildJsonObject {
            put("data", "test")
        }

        val result = client.writePayload(payload)

        assertNotNull(result)
        assertEquals("algorand:testnet", result.ref.chainId)
        assertNotNull(result.ref.txHash)
        assertTrue(result.ref.txHash.startsWith("real_tx_"))
    }

    @Test
    fun `test writePayload with contract address`() = runBlocking {
        val client = TestBlockchainAnchorClient(
            chainId = "algorand:testnet",
            contractAddress = "app-123"
        )

        val payload = buildJsonObject {
            put("data", "test")
        }

        val result = client.writePayload(payload)

        assertEquals("app-123", result.ref.contract)
    }

    @Test
    fun `test writePayload with custom media type`() = runBlocking {
        val client = TestBlockchainAnchorClient("algorand:testnet")

        val payload = buildJsonObject {
            put("data", "test")
        }

        val result = client.writePayload(payload, "application/cbor")

        assertEquals("application/cbor", result.mediaType)
        assertTrue(result.ref.extra.containsKey("mediaType"))
        assertEquals("application/cbor", result.ref.extra["mediaType"])
    }

    @Test
    fun `test readPayload from fallback storage`() = runBlocking {
        val client = TestBlockchainAnchorClient("algorand:testnet")

        val payload = buildJsonObject {
            put("data", "test")
        }

        val writeResult = client.writePayload(payload)
        val readResult = client.readPayload(writeResult.ref)

        assertEquals(writeResult.payload, readResult.payload)
        assertEquals(writeResult.ref.txHash, readResult.ref.txHash)
    }

    @Test
    fun `test readPayload validates chain ID`() = runBlocking {
        val client = TestBlockchainAnchorClient("algorand:testnet")

        val ref = AnchorRef(
            chainId = "eip155:137",
            txHash = "tx-123"
        )

        assertFailsWith<IllegalArgumentException> {
            client.readPayload(ref)
        }
    }

    @Test
    fun `test readPayload throws NotFoundException when not found`() = runBlocking {
        val client = TestBlockchainAnchorClient("algorand:testnet")

        val ref = AnchorRef(
            chainId = "algorand:testnet",
            txHash = "nonexistent-tx"
        )

        assertFailsWith<TrustWeaveException.NotFound> {
            client.readPayload(ref)
        }
    }

    @Test
    fun `test writePayload handles exceptions`() = runBlocking {
        val client = TestBlockchainAnchorClient(
            chainId = "algorand:testnet",
            throwOnSubmit = true
        )

        val payload = buildJsonObject {
            put("data", "test")
        }

        assertFailsWith<BlockchainException.TransactionFailed> {
            client.writePayload(payload)
        }
    }

    @Test
    fun `test buildAnchorRef with extra metadata`() = runBlocking {
        val client = TestBlockchainAnchorClient("algorand:testnet")

        val payload = buildJsonObject {
            put("data", "test")
        }

        val result = client.writePayload(payload, "application/json")

        assertTrue(result.ref.extra.containsKey("mediaType"))
    }

    /**
     * Test implementation of AbstractBlockchainAnchorClient.
     */
    private class TestBlockchainAnchorClient(
        chainId: String,
        private val canSubmit: Boolean = false,
        private val contractAddress: String? = null,
        private val throwOnSubmit: Boolean = false
    ) : AbstractBlockchainAnchorClient(
        chainId = chainId,
        options = mapOf("contractAddress" to contractAddress, "appId" to contractAddress)
    ) {
        private val realStorage = ConcurrentHashMap<String, AnchorResult>()
        private var realTxCounter = 0

        override fun canSubmitTransaction(): Boolean = canSubmit

        override suspend fun submitTransactionToBlockchain(payloadBytes: ByteArray): String {
            if (throwOnSubmit) {
                throw RuntimeException("Simulated blockchain error")
            }
            val txHash = "real_tx_${realTxCounter++}_${System.currentTimeMillis()}"
            val ref = buildAnchorRef(txHash, getContractAddress(), buildExtraMetadata("application/json"))
            val payload = Json.parseToJsonElement(String(payloadBytes))
            val result = AnchorResult(
                ref = ref,
                payload = payload,
                mediaType = "application/json",
                timestamp = System.currentTimeMillis() / 1000
            )
            realStorage[txHash] = result
            return txHash
        }

        override suspend fun readTransactionFromBlockchain(txHash: String): AnchorResult {
            return realStorage[txHash] ?: throw TrustWeaveException.NotFound("Not found: $txHash")
        }

        override fun generateTestTxHash(): String {
            return "test_tx_${System.currentTimeMillis()}_${Math.random()}"
        }

        override fun getBlockchainName(): String = "Test Blockchain"
    }
}


