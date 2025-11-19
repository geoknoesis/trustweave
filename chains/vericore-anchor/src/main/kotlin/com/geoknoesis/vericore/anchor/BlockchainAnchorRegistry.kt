package com.geoknoesis.vericore.anchor

/**
 * Contract for registries that manage [BlockchainAnchorClient] instances.
 */
interface BlockchainAnchorRegistry {
    fun register(chainId: String, client: BlockchainAnchorClient)
    fun registerAll(vararg entries: Pair<String, BlockchainAnchorClient>) {
        entries.forEach { (chainId, client) -> register(chainId, client) }
    }
    fun get(chainId: String): BlockchainAnchorClient?
    fun has(chainId: String): Boolean
    fun getAllChainIds(): List<String>
    fun getAllClients(): Map<String, BlockchainAnchorClient>
    fun unregister(chainId: String): Boolean
    fun clear()
    fun size(): Int
    fun snapshot(): BlockchainAnchorRegistry
}

fun BlockchainAnchorRegistry(): BlockchainAnchorRegistry = DefaultBlockchainAnchorRegistry()

suspend fun <T> BlockchainAnchorRegistry.anchorTyped(
    value: T,
    serializer: kotlinx.serialization.KSerializer<T>,
    targetChainId: String,
    mediaType: String = "application/json"
): AnchorResult {
    val client = get(targetChainId)
        ?: throw IllegalArgumentException("No blockchain client registered for chain: $targetChainId")

    val json = kotlinx.serialization.json.Json.encodeToJsonElement(serializer, value)
    return client.writePayload(json, mediaType)
}

suspend fun <T> BlockchainAnchorRegistry.readTyped(
    ref: AnchorRef,
    serializer: kotlinx.serialization.KSerializer<T>
): T {
    val client = get(ref.chainId)
        ?: throw IllegalArgumentException("No blockchain client registered for chain: ${ref.chainId}")

    val result = client.readPayload(ref)
    return kotlinx.serialization.json.Json.decodeFromJsonElement(serializer, result.payload)
}

