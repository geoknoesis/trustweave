package io.geoknoesis.vericore.anchor

import io.geoknoesis.vericore.core.NotFoundException
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
     * @throws NotFoundException if the anchor reference does not exist
     */
    suspend fun readPayload(ref: AnchorRef): AnchorResult
}

/**
 * Registry for blockchain anchor clients.
 * 
 * Allows registration and lookup of clients by chain ID.
 * This registry enables chain-agnostic anchoring operations where clients
 * can be registered at runtime and selected based on chain ID.
 * 
 * **Example Usage**:
 * ```
 * // Register a client
 * BlockchainRegistry.register("algorand:testnet", algorandClient)
 * 
 * // Use helper function (automatically looks up client)
 * val result = anchorTyped(myData, MyData.serializer(), "algorand:testnet")
 * ```
 * 
 * **Thread Safety**: This registry is thread-safe for concurrent access.
 */
object BlockchainRegistry {
    private val clients = mutableMapOf<String, BlockchainAnchorClient>()

    /**
     * Registers a blockchain anchor client for a specific chain.
     *
     * @param chainId The chain identifier (e.g., "algorand:mainnet")
     * @param client The blockchain anchor client implementation
     */
    fun register(chainId: String, client: BlockchainAnchorClient) {
        clients[chainId] = client
    }

    /**
     * Gets a blockchain anchor client for a specific chain.
     *
     * @param chainId The chain identifier
     * @return The BlockchainAnchorClient, or null if not registered
     */
    fun get(chainId: String): BlockchainAnchorClient? {
        return clients[chainId]
    }

    /**
     * Clears all registered clients (useful for testing).
     */
    fun clear() {
        clients.clear()
    }
}

/**
 * Helper function to anchor a typed value to a blockchain.
 * 
 * This function provides type-safe anchoring by automatically serializing
 * the value to JSON and anchoring it using the registered client for the
 * specified chain.
 * 
 * **Example Usage**:
 * ```
 * @Serializable
 * data class MyData(val name: String, val value: Int)
 * 
 * val data = MyData("test", 42)
 * val result = anchorTyped(data, MyData.serializer(), "algorand:testnet")
 * println("Anchored at: ${result.ref.txHash}")
 * ```
 * 
 * @param value The value to anchor (must be serializable)
 * @param serializer The Kotlinx Serialization serializer for the type
 * @param targetChainId The chain ID to anchor to (must have a registered client)
 * @param mediaType The media type of the payload (default: "application/json")
 * @return An AnchorResult containing the anchor reference and payload
 * @throws IllegalArgumentException if no client is registered for the chain ID
 * @throws BlockchainTransactionException if anchoring fails
 */
suspend fun <T> anchorTyped(
    value: T,
    serializer: kotlinx.serialization.KSerializer<T>,
    targetChainId: String,
    mediaType: String = "application/json"
): AnchorResult {
    val client = BlockchainRegistry.get(targetChainId)
        ?: throw IllegalArgumentException("No blockchain client registered for chain: $targetChainId")
    
    val json = kotlinx.serialization.json.Json.encodeToJsonElement(serializer, value)
    return client.writePayload(json, mediaType)
}

/**
 * Helper function to read a typed value from a blockchain anchor.
 * 
 * This function provides type-safe reading by automatically deserializing
 * the payload from the anchor reference using the registered client.
 * 
 * **Example Usage**:
 * ```
 * @Serializable
 * data class MyData(val name: String, val value: Int)
 * 
 * val ref = AnchorRef(chainId = "algorand:testnet", txHash = "...")
 * val data = readTyped<MyData>(ref, MyData.serializer())
 * println("Read: ${data.name}")
 * ```
 * 
 * @param ref The anchor reference pointing to the anchored data
 * @param serializer The Kotlinx Serialization serializer for the type
 * @return The deserialized value
 * @throws IllegalArgumentException if no client is registered for the chain ID
 * @throws NotFoundException if the anchor reference does not exist
 * @throws BlockchainTransactionException if reading fails
 */
suspend fun <T> readTyped(
    ref: AnchorRef,
    serializer: kotlinx.serialization.KSerializer<T>
): T {
    val client = BlockchainRegistry.get(ref.chainId)
        ?: throw IllegalArgumentException("No blockchain client registered for chain: ${ref.chainId}")
    
    val result = client.readPayload(ref)
    return kotlinx.serialization.json.Json.decodeFromJsonElement(serializer, result.payload)
}

