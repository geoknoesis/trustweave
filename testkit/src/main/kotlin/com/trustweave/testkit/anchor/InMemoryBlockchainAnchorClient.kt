package com.trustweave.testkit.anchor

import com.trustweave.anchor.*
import com.trustweave.core.exception.NotFoundException
import kotlinx.serialization.json.JsonElement
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory implementation of BlockchainAnchorClient for testing.
 * Stores anchored payloads in memory keyed by AnchorRef.
 */
class InMemoryBlockchainAnchorClient(
    private val chainId: String,
    private val contract: String? = null
) : BlockchainAnchorClient {

    private val storage = ConcurrentHashMap<String, AnchorResult>()
    private val txCounter = AtomicLong(0)

    override suspend fun writePayload(
        payload: JsonElement,
        mediaType: String
    ): AnchorResult {
        val txHash = "tx_${txCounter.incrementAndGet()}_${System.currentTimeMillis()}"
        val ref = AnchorRef(
            chainId = chainId,
            txHash = txHash,
            contract = contract
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
        if (ref.chainId != chainId) {
            throw IllegalArgumentException("Chain ID mismatch: expected $chainId, got ${ref.chainId}")
        }
        return storage[ref.txHash]
            ?: throw NotFoundException("Anchor not found: ${ref.txHash}")
    }

    /**
     * Clears all stored anchors (useful for test cleanup).
     */
    fun clear() {
        storage.clear()
        txCounter.set(0)
    }

    /**
     * Gets the number of stored anchors.
     */
    fun size(): Int = storage.size
}

