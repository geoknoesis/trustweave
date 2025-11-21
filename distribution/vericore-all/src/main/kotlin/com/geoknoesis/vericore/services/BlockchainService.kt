package com.geoknoesis.vericore.services

import com.geoknoesis.vericore.anchor.AnchorResult
import com.geoknoesis.vericore.anchor.AnchorRef
import com.geoknoesis.vericore.core.*
import com.geoknoesis.vericore.anchor.BlockchainAnchorClient
import kotlinx.serialization.KSerializer

/**
 * Focused service for blockchain anchoring operations.
 * 
 * Provides a clean API for anchoring data to blockchains and reading anchored data.
 * 
 * **Example:**
 * ```kotlin
 * val vericore = VeriCore.create()
 * val anchor = vericore.blockchains.anchor(
 *     data = myData,
 *     serializer = MyData.serializer(),
 *     chainId = "algorand:testnet"
 * )
 * ```
 */
class BlockchainService(
    private val context: VeriCoreContext
) {
    /**
     * Anchors data to a blockchain.
     * 
     * **Example:**
     * ```kotlin
     * val anchor = vericore.blockchains.anchor(
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
     * @throws VeriCoreError.ChainNotRegistered if chain is not registered
     */
    suspend fun <T : Any> anchor(
        data: T,
        serializer: KSerializer<T>,
        chainId: String
    ): AnchorResult {
        // Validate chain ID format
        ChainIdValidator.validateFormat(chainId).let {
            if (!it.isValid()) {
                throw VeriCoreError.ChainNotRegistered(
                    chainId = chainId,
                    availableChains = context.getAvailableChains()
                )
            }
        }
        
        // Validate chain is registered
        val availableChains = context.getAvailableChains()
        ChainIdValidator.validateRegistered(chainId, availableChains).let {
            if (!it.isValid()) {
                throw VeriCoreError.ChainNotRegistered(
                    chainId = chainId,
                    availableChains = availableChains
                )
            }
        }
        
        val client = context.getBlockchainClient(chainId)
            ?: throw VeriCoreError.ChainNotRegistered(
                chainId = chainId,
                availableChains = availableChains
            )
        
        return client.anchor(data, serializer)
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
     * val data = vericore.blockchains.read<MyData>(
     *     ref = anchorRef,
     *     serializer = MyData.serializer()
     * )
     * ```
     * 
     * @param ref Anchor reference containing chain ID and transaction hash
     * @param serializer Kotlinx Serialization serializer for the data type
     * @return The anchored data
     * @throws VeriCoreError.ChainNotRegistered if chain is not registered
     */
    suspend fun <T : Any> read(
        ref: AnchorRef,
        serializer: KSerializer<T>
    ): T {
        val chainId = ref.chainId
        
        val availableChains = context.getAvailableChains()
        if (chainId !in availableChains) {
            throw VeriCoreError.ChainNotRegistered(
                chainId = chainId,
                availableChains = availableChains
            )
        }
        
        val client = context.getBlockchainClient(chainId)
            ?: throw VeriCoreError.ChainNotRegistered(
                chainId = chainId,
                availableChains = availableChains
            )
        
        return client.readPayload(ref, serializer)
    }
    
    /**
     * Gets available blockchain chains.
     * 
     * @return List of registered blockchain chain IDs
     */
    fun availableChains(): List<String> = context.getAvailableChains()
}

