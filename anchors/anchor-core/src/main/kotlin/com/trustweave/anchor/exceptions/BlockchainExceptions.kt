package com.trustweave.anchor.exceptions

import com.trustweave.core.exception.TrustWeaveException

/**
 * Base exception for blockchain-related errors.
 * 
 * All blockchain-specific exceptions extend this class to provide
 * consistent error handling and context.
 */
open class BlockchainException(
    message: String,
    val chainId: String? = null,
    val operation: String? = null,
    cause: Throwable? = null
) : TrustWeaveException(message, cause) {
    
    override val message: String
        get() = buildString {
            if (chainId != null) {
                append("[Chain: $chainId] ")
            }
            if (operation != null) {
                append("[Operation: $operation] ")
            }
            append(super.message)
        }
}

/**
 * Exception thrown when a blockchain transaction fails.
 * 
 * @param message Error message
 * @param chainId The chain ID where the transaction was attempted
 * @param txHash The transaction hash (if available)
 * @param operation The operation type (e.g., "writePayload", "readPayload", "submitTransaction")
 * @param payloadSize Optional payload size in bytes (for debugging)
 * @param gasUsed Optional gas used (for Ethereum-compatible chains)
 * @param cause The underlying exception
 */
class BlockchainTransactionException(
    message: String,
    chainId: String? = null,
    val txHash: String? = null,
    operation: String? = null,
    val payloadSize: Long? = null,
    val gasUsed: Long? = null,
    cause: Throwable? = null
) : BlockchainException(message, chainId, operation, cause) {
    
    override val message: String
        get() = buildString {
            if (chainId != null) {
                append("[Chain: $chainId] ")
            }
            if (operation != null) {
                append("[Operation: $operation] ")
            }
            if (txHash != null) {
                append("[TxHash: $txHash] ")
            }
            if (payloadSize != null) {
                append("[PayloadSize: ${payloadSize}B] ")
            }
            if (gasUsed != null) {
                append("[GasUsed: $gasUsed] ")
            }
            append(super.message)
        }
}

/**
 * Exception thrown when connection to blockchain fails.
 * 
 * @param message Error message
 * @param chainId The chain ID where connection was attempted
 * @param endpoint The endpoint URL (if available)
 * @param cause The underlying exception
 */
class BlockchainConnectionException(
    message: String,
    chainId: String? = null,
    val endpoint: String? = null,
    cause: Throwable? = null
) : BlockchainException(message, chainId, "connection", cause) {
    
    override val message: String
        get() = buildString {
            if (chainId != null) {
                append("[Chain: $chainId] ")
            }
            if (endpoint != null) {
                append("[Endpoint: $endpoint] ")
            }
            append(super.message)
        }
}

/**
 * Exception thrown when blockchain client configuration is invalid.
 * 
 * @param message Error message
 * @param chainId The chain ID being configured
 * @param configKey The configuration key that is invalid (if applicable)
 * @param cause The underlying exception
 */
class BlockchainConfigurationException(
    message: String,
    chainId: String? = null,
    val configKey: String? = null,
    cause: Throwable? = null
) : BlockchainException(message, chainId, "configuration", cause) {
    
    override val message: String
        get() = buildString {
            if (chainId != null) {
                append("[Chain: $chainId] ")
            }
            if (configKey != null) {
                append("[Config: $configKey] ")
            }
            append(super.message)
        }
}

/**
 * Exception thrown when a blockchain operation is not supported.
 * 
 * @param message Error message
 * @param chainId The chain ID where operation was attempted
 * @param operation The unsupported operation
 * @param cause The underlying exception
 */
class BlockchainUnsupportedOperationException(
    message: String,
    chainId: String? = null,
    operation: String? = null,
    cause: Throwable? = null
) : BlockchainException(message, chainId, operation, cause)

/**
 * Exception thrown when a blockchain chain is not registered.
 * 
 * @param chainId The chain ID that is not registered
 * @param availableChains List of available chain IDs
 * @param cause The underlying exception
 */
class ChainNotRegisteredException(
    val chainId: String,
    val availableChains: List<String>,
    cause: Throwable? = null
) : BlockchainException(
    message = "Blockchain chain '$chainId' is not registered. Available chains: $availableChains",
    chainId = chainId,
    operation = "registration",
    cause = cause
)

