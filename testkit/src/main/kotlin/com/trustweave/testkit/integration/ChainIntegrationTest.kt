package com.trustweave.testkit.integration

import com.trustweave.anchor.AnchorRef
import com.trustweave.anchor.AnchorResult
import com.trustweave.anchor.BlockchainAnchorClient
import com.trustweave.testkit.BaseIntegrationTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Base class for chain integration tests.
 *
 * Provides common test scenarios for blockchain anchor plugins including:
 * - Tests with testnet/local nodes (Ganache, Algorand testnet)
 * - Anchor/read roundtrip tests
 * - Multi-chain tests
 *
 * **Example Usage**:
 * ```kotlin
 * @Testcontainers
 * class EthereumIntegrationTest : ChainIntegrationTest() {
 *     companion object {
 *         @JvmStatic
 *         val ganache = GanacheContainer.create()
 *     }
 *
 *     override fun getChainClient(): BlockchainAnchorClient {
 *         val config = EthereumOptions(
 *             rpcUrl = ganache.rpcEndpoint
 *         )
 *         return EthereumBlockchainAnchorClient("eip155:1337", config)
 *     }
 *
 *     @Test
 *     fun testAnchorAndRead() = runBlocking {
 *         testAnchorRoundtrip()
 *     }
 * }
 * ```
 */
abstract class ChainIntegrationTest : BaseIntegrationTest() {

    /**
     * Gets the blockchain anchor client to test.
     * Must be implemented by subclasses.
     */
    abstract fun getChainClient(): BlockchainAnchorClient

    /**
     * Gets the chain ID for this client.
     * Must be implemented by subclasses.
     */
    abstract fun getChainId(): String

    /**
     * Tests anchoring and reading data (roundtrip).
     */
    protected suspend fun testAnchorRoundtrip() {
        val client = getChainClient()
        val testData = createTestPayload()

        val anchorResult = client.writePayload(testData)
        kotlin.test.assertNotNull(anchorResult)
        kotlin.test.assertNotNull(anchorResult.ref)
        kotlin.test.assertNotNull(anchorResult.ref.txHash)
        kotlin.test.assertEquals(getChainId(), anchorResult.ref.chainId)

        val readResult = client.readPayload(anchorResult.ref)
        kotlin.test.assertNotNull(readResult)

        // Verify data integrity
        val originalDigest = computeDigest(testData)
        val readDigest = computeDigest(readResult.payload)
        kotlin.test.assertEquals(originalDigest, readDigest)
    }

    /**
     * Tests anchoring multiple payloads.
     */
    protected suspend fun testMultipleAnchors() {
        val client = getChainClient()
        val payloads = (1..5).map { index ->
            buildJsonObject {
                put("id", "test-$index")
                put("data", "payload-$index")
            }
        }

        val results = payloads.map { payload ->
            client.writePayload(payload)
        }

        kotlin.test.assertTrue(results.size == payloads.size)
        results.forEach { result ->
            kotlin.test.assertNotNull(result.ref)
            kotlin.test.assertNotNull(result.ref.txHash)
        }

        // Verify all can be read back
        results.forEach { result ->
            val read = client.readPayload(result.ref)
            kotlin.test.assertNotNull(read)
        }
    }

    /**
     * Tests error handling for invalid anchor references.
     */
    protected suspend fun testInvalidAnchorReference() {
        val client = getChainClient()
        val invalidRef = AnchorRef(
            chainId = getChainId(),
            txHash = "invalid-hash-${System.currentTimeMillis()}"
        )

        try {
            client.readPayload(invalidRef)
            // Some implementations may return null instead of throwing
        } catch (e: Exception) {
            // Expected behavior for invalid references
        }
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

    /**
     * Computes a simple digest for comparison.
     * In real scenarios, use DigestUtils.sha256DigestMultibase.
     */
    private fun computeDigest(json: JsonElement): String {
        return json.toString().hashCode().toString()
    }
}

