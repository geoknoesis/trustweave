package com.trustweave.anchor.exceptions

import com.trustweave.core.exception.TrustWeaveException

/**
 * Blockchain-related exception types.
 *
 * These exceptions provide structured error codes and context for blockchain operations.
 */
sealed class BlockchainException(
    override val code: String,
    override val message: String,
    override val context: Map<String, Any?> = emptyMap(),
    override val cause: Throwable? = null
) : TrustWeaveException(code, message, context, cause) {

    data class TransactionFailed(
        val chainId: String? = null,
        val txHash: String? = null,
        val operation: String? = null,
        val payloadSize: Long? = null,
        val gasUsed: Long? = null,
        val reason: String
    ) : BlockchainException(
        code = "BLOCKCHAIN_TRANSACTION_FAILED",
        message = buildString {
            if (chainId != null) append("[Chain: $chainId] ")
            if (operation != null) append("[Operation: $operation] ")
            if (txHash != null) append("[TxHash: $txHash] ")
            append(reason)
        },
        context = mapOf(
            "chainId" to chainId,
            "txHash" to txHash,
            "operation" to operation,
            "payloadSize" to payloadSize,
            "gasUsed" to gasUsed,
            "reason" to reason
        ).filterValues { it != null }
    )

    data class ConnectionFailed(
        val chainId: String? = null,
        val endpoint: String? = null,
        val reason: String
    ) : BlockchainException(
        code = "BLOCKCHAIN_CONNECTION_FAILED",
        message = buildString {
            if (chainId != null) append("[Chain: $chainId] ")
            if (endpoint != null) append("[Endpoint: $endpoint] ")
            append(reason)
        },
        context = mapOf(
            "chainId" to chainId,
            "endpoint" to endpoint,
            "reason" to reason
        ).filterValues { it != null }
    )

    data class ConfigurationFailed(
        val chainId: String? = null,
        val configKey: String? = null,
        val reason: String
    ) : BlockchainException(
        code = "BLOCKCHAIN_CONFIGURATION_FAILED",
        message = buildString {
            if (chainId != null) append("[Chain: $chainId] ")
            if (configKey != null) append("[Config: $configKey] ")
            append(reason)
        },
        context = mapOf(
            "chainId" to chainId,
            "configKey" to configKey,
            "reason" to reason
        ).filterValues { it != null }
    )

    data class UnsupportedOperation(
        val chainId: String? = null,
        val operation: String? = null,
        val reason: String? = null
    ) : BlockchainException(
        code = "BLOCKCHAIN_UNSUPPORTED_OPERATION",
        message = buildString {
            if (chainId != null) append("[Chain: $chainId] ")
            if (operation != null) append("[Operation: $operation] ")
            append(reason ?: "Operation not supported")
        },
        context = mapOf(
            "chainId" to chainId,
            "operation" to operation,
            "reason" to reason
        ).filterValues { it != null }
    )

    data class ChainNotRegistered(
        val chainId: String,
        val availableChains: List<String>
    ) : BlockchainException(
        code = "CHAIN_NOT_REGISTERED",
        message = "Blockchain chain '$chainId' is not registered. Available chains: $availableChains",
        context = mapOf(
            "chainId" to chainId,
            "availableChains" to availableChains
        )
    )
}
