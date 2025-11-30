package com.trustweave.anchor.services

import com.trustweave.anchor.AnchorResult
import com.trustweave.anchor.AnchorRef
import com.trustweave.anchor.anchorTyped
import com.trustweave.anchor.validation.ChainIdValidator
import com.trustweave.anchor.exceptions.BlockchainException
import com.trustweave.anchor.BlockchainAnchorClient
import com.trustweave.anchor.BlockchainAnchorRegistry
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * Focused service for blockchain anchoring operations.
 *
 * Provides a clean API for anchoring data to blockchains and reading anchored data.
 *
 * **Example:**
 * ```kotlin
 * val blockchainService = BlockchainService(blockchainRegistry)
 * val anchor = blockchainService.anchor(
 *     data = myData,
 *     serializer = MyData.serializer(),
 *     chainId = "algorand:testnet"
 * )
 * ```
 */
class BlockchainService(
    private val blockchainRegistry: BlockchainAnchorRegistry
) {
    /**
     * Anchors data to a blockchain.
     *
     * **Example:**
     * ```kotlin
     * val anchor = blockchainService.anchor(
     *     data = myData,
     *     serializer = MyData.serializer(),
     *     chainId = "algorand:testnet"
     * )
     * println("Anchored at: ${anchor.ref.txHash}")
     * ```
     *
     * @param data The data to anchor
     * @param serializer Kotlinx Serialization serializer for the data type
     * @param chainId Blockchain chain identifier (CAIP-2 format)
     * @return Anchor result with transaction reference
     * @throws BlockchainException.ChainNotRegistered if chain is not registered
     */
    suspend fun <T : Any> anchor(
        data: T,
        serializer: KSerializer<T>,
        chainId: String
    ): AnchorResult {
        val availableChains = blockchainRegistry.getAllChainIds()
        
        // Validate chain ID format
        ChainIdValidator.validateFormat(chainId).let {
            if (!it.isValid()) {
                throw BlockchainException.ChainNotRegistered(
                    chainId = chainId,
                    availableChains = availableChains
                )
            }
        }

        // Validate chain is registered
        ChainIdValidator.validateRegistered(chainId, availableChains).let {
            if (!it.isValid()) {
                throw BlockchainException.ChainNotRegistered(
                    chainId = chainId,
                    availableChains = availableChains
                )
            }
        }

        val client = blockchainRegistry.get(chainId) as? BlockchainAnchorClient
            ?: throw BlockchainException.ChainNotRegistered(
                chainId = chainId,
                availableChains = availableChains
            )

        val json = Json.encodeToJsonElement(serializer, data)
        return client.writePayload(json)
    }

    /**
     * Reads anchored data from a blockchain.
     *
     * **Example:**
     * ```kotlin
     * val anchorRef = AnchorRef(
     *     chainId = "algorand:testnet",
     *     txHash = "abc123..."
     * )
     * val data = blockchainService.read<MyData>(
     *     ref = anchorRef,
     *     serializer = MyData.serializer()
     * )
     * ```
     *
     * @param ref Anchor reference containing chain ID and transaction hash
     * @param serializer Kotlinx Serialization serializer for the data type
     * @return The anchored data
     * @throws BlockchainException.ChainNotRegistered if chain is not registered
     */
    suspend fun <T : Any> read(
        ref: AnchorRef,
        serializer: KSerializer<T>
    ): T {
        val chainId = ref.chainId
        val availableChains = blockchainRegistry.getAllChainIds()
        
        if (chainId !in availableChains) {
            throw BlockchainException.ChainNotRegistered(
                chainId = chainId,
                availableChains = availableChains
            )
        }

        val client = blockchainRegistry.get(chainId) as? BlockchainAnchorClient
            ?: throw BlockchainException.ChainNotRegistered(
                chainId = chainId,
                availableChains = availableChains
            )

        val result = client.readPayload(ref)
        return Json.decodeFromJsonElement(serializer, result.payload)
    }

    /**
     * Gets available blockchain chains.
     *
     * @return List of registered blockchain chain IDs
     */
    fun availableChains(): List<String> = blockchainRegistry.getAllChainIds()
}


