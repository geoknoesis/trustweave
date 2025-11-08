package io.geoknoesis.vericore.anchor

import io.geoknoesis.vericore.anchor.exceptions.BlockchainTransactionException
import io.geoknoesis.vericore.core.NotFoundException
import io.geoknoesis.vericore.core.VeriCoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * Abstract base class for blockchain anchor client implementations.
 * 
 * Provides common functionality shared across blockchain adapters:
 * - In-memory fallback storage for testing
 * - Common error handling patterns
 * - AnchorRef construction helpers
 * - Transaction hash generation utilities
 * 
 * Subclasses should implement:
 * - [canSubmitTransaction]: Check if credentials are available for real transactions
 * - [submitTransactionToBlockchain]: Submit transaction to actual blockchain
 * - [readTransactionFromBlockchain]: Read transaction from actual blockchain
 * - [getContractAddress]: Get contract address from options (if applicable)
 * - [buildExtraMetadata]: Build extra metadata for AnchorRef
 * - [generateTestTxHash]: Generate test transaction hash for fallback mode
 */
abstract class AbstractBlockchainAnchorClient(
    protected val chainId: String,
    protected val options: Map<String, Any?>
) : BlockchainAnchorClient {

    /**
     * In-memory storage for fallback mode (when credentials are not available).
     * Used for testing without requiring actual blockchain credentials.
     */
    protected val storage = ConcurrentHashMap<String, AnchorResult>()

    /**
     * Checks if this client can submit transactions to the blockchain.
     * Returns true if credentials/account are configured, false otherwise.
     */
    protected abstract fun canSubmitTransaction(): Boolean

    /**
     * Submits a transaction to the blockchain and returns the transaction hash.
     * 
     * @param payloadBytes The payload data as bytes
     * @return The transaction hash
     * @throws VeriCoreException if submission fails
     */
    protected abstract suspend fun submitTransactionToBlockchain(payloadBytes: ByteArray): String

    /**
     * Reads a transaction from the blockchain.
     * 
     * @param txHash The transaction hash
     * @return The AnchorResult containing the payload
     * @throws NotFoundException if transaction is not found
     * @throws VeriCoreException if reading fails
     */
    protected abstract suspend fun readTransactionFromBlockchain(txHash: String): AnchorResult

    /**
     * Gets the contract address from options (if applicable).
     * Returns null if no contract is configured.
     */
    protected open fun getContractAddress(): String? {
        return options["contractAddress"] as? String
            ?: options["appId"] as? String
    }

    /**
     * Builds extra metadata for AnchorRef.
     * Subclasses can override to add chain-specific metadata.
     * 
     * @param mediaType The media type of the payload
     * @return Map of extra metadata
     */
    protected open fun buildExtraMetadata(mediaType: String): Map<String, String> {
        return mapOf("mediaType" to mediaType)
    }

    /**
     * Generates a test transaction hash for fallback mode.
     * Should be unique and identifiable as a test hash.
     */
    protected abstract fun generateTestTxHash(): String

    /**
     * Gets the blockchain name for error messages.
     */
    protected abstract fun getBlockchainName(): String

    override suspend fun writePayload(
        payload: JsonElement,
        mediaType: String
    ): AnchorResult = withContext(Dispatchers.IO) {
        val payloadJson = Json.encodeToString(JsonElement.serializer(), payload)
        val payloadBytes = payloadJson.toByteArray(StandardCharsets.UTF_8)
        
        try {
            // Submit transaction to blockchain or use fallback storage
            val txHash = if (canSubmitTransaction()) {
                submitTransactionToBlockchain(payloadBytes)
            } else {
                // Fallback to in-memory storage for testing without credentials
                val hash = generateTestTxHash()
                val ref = buildAnchorRef(
                    txHash = hash,
                    contract = getContractAddress(),
                    extra = buildExtraMetadata(mediaType)
                )
                val result = AnchorResult(
                    ref = ref,
                    payload = payload,
                    mediaType = mediaType,
                    timestamp = System.currentTimeMillis() / 1000
                )
                storage[hash] = result
                hash
            }

            val ref = buildAnchorRef(
                txHash = txHash,
                contract = getContractAddress(),
                extra = buildExtraMetadata(mediaType)
            )
            
            AnchorResult(
                ref = ref,
                payload = payload,
                mediaType = mediaType,
                timestamp = System.currentTimeMillis() / 1000
            )
        } catch (e: VeriCoreException) {
            throw e
        } catch (e: Exception) {
            throw BlockchainTransactionException(
                message = "Failed to anchor payload to ${getBlockchainName()}: ${e.message}",
                chainId = chainId,
                operation = "writePayload",
                payloadSize = payloadBytes.size.toLong(),
                cause = e
            )
        }
    }

    override suspend fun readPayload(ref: AnchorRef): AnchorResult = withContext(Dispatchers.IO) {
        try {
            validateChainId(ref.chainId)

            // Try to read from blockchain first, fallback to storage
            val result = try {
                readTransactionFromBlockchain(ref.txHash)
            } catch (e: NotFoundException) {
                // Fallback to in-memory storage
                storage[ref.txHash] ?: throw NotFoundException("Transaction not found: ${ref.txHash}")
            } catch (e: Exception) {
                // Try storage fallback for other exceptions too
                storage[ref.txHash] ?: throw BlockchainTransactionException(
                    message = "Failed to read payload from ${getBlockchainName()}: ${e.message}",
                    chainId = chainId,
                    txHash = ref.txHash,
                    operation = "readPayload",
                    cause = e
                )
            }
            
            result
        } catch (e: NotFoundException) {
            throw e
        } catch (e: VeriCoreException) {
            throw e
        } catch (e: Exception) {
            throw BlockchainTransactionException(
                message = "Failed to read payload from ${getBlockchainName()}: ${e.message}",
                chainId = chainId,
                txHash = ref.txHash,
                operation = "readPayload",
                cause = e
            )
        }
    }

    /**
     * Validates that the chain ID matches this client's chain ID.
     */
    protected fun validateChainId(refChainId: String) {
        if (refChainId != chainId) {
            throw IllegalArgumentException("Chain ID mismatch: expected $chainId, got $refChainId")
        }
    }

    /**
     * Builds an AnchorRef with common fields.
     */
    protected fun buildAnchorRef(
        txHash: String,
        contract: String? = null,
        extra: Map<String, String> = emptyMap()
    ): AnchorRef {
        return AnchorRef(
            chainId = chainId,
            txHash = txHash,
            contract = contract,
            extra = extra
        )
    }
}

