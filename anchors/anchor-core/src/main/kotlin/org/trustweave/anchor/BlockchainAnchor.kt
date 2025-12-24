package org.trustweave.anchor

import org.trustweave.core.exception.TrustWeaveException
import kotlinx.serialization.json.JsonElement

/**
 * Reference to a blockchain anchor (CAIP-2-style chain identifier + transaction reference).
 *
 * @param chainId The chain identifier (e.g., "algorand:mainnet", "eip155:137")
 * @param txHash The transaction hash or operation identifier
 * @param contract Optional registry contract address or app ID
 * @param extra Additional metadata as key-value pairs
 */
data class AnchorRef(
    val chainId: String,
    val txHash: String,
    val contract: String? = null,
    val extra: Map<String, String> = emptyMap()
)

/**
 * Result of anchoring a payload to a blockchain.
 *
 * @param ref The anchor reference pointing to the anchored data
 * @param payload The JSON payload that was anchored
 * @param mediaType The media type of the payload (default: "application/json")
 * @param timestamp Optional timestamp (epoch seconds) when the anchor was created
 */
data class AnchorResult(
    val ref: AnchorRef,
    val payload: JsonElement,
    val mediaType: String = "application/json",
    val timestamp: Long? = null
)

/**
 * Interface for blockchain anchoring operations.
 * This interface is chain-agnostic; specific blockchain implementations
 * will be provided in separate adapter modules.
 */
interface BlockchainAnchorClient {

    /**
     * Writes a payload to the blockchain and returns an anchor reference.
     *
     * @param payload The JSON payload to anchor
     * @param mediaType The media type of the payload
     * @return An AnchorResult containing the reference and payload
     */
    suspend fun writePayload(
        payload: JsonElement,
        mediaType: String = "application/json"
    ): AnchorResult

    /**
     * Reads a payload from the blockchain using an anchor reference.
     *
     * @param ref The anchor reference
     * @return An AnchorResult containing the payload and metadata
     * @throws TrustWeaveException.NotFound if the anchor reference does not exist
     */
    suspend fun readPayload(ref: AnchorRef): AnchorResult
}
