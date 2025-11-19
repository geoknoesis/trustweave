package com.geoknoesis.vericore.core

/**
 * Enhanced error types for VeriCore API operations.
 * 
 * These errors provide structured error codes and context for better error handling
 * and debugging. All errors extend VeriCoreException for backward compatibility.
 */

/**
 * Sealed hierarchy for structured API errors with context.
 */
sealed class VeriCoreError(
    open val code: String,
    override val message: String,
    open val context: Map<String, Any?> = emptyMap(),
    override val cause: Throwable? = null
) : VeriCoreException(message, cause) {
    
    // DID-related errors
    data class DidNotFound(
        val did: String,
        val availableMethods: List<String> = emptyList()
    ) : VeriCoreError(
        code = "DID_NOT_FOUND",
        message = "DID not found: $did",
        context = mapOf(
            "did" to did,
            "availableMethods" to availableMethods
        )
    )
    
    data class DidMethodNotRegistered(
        val method: String,
        val availableMethods: List<String>
    ) : VeriCoreError(
        code = "DID_METHOD_NOT_REGISTERED",
        message = "DID method '$method' is not registered. Available methods: $availableMethods",
        context = mapOf(
            "method" to method,
            "availableMethods" to availableMethods
        )
    )
    
    data class InvalidDidFormat(
        val did: String,
        val reason: String
    ) : VeriCoreError(
        code = "INVALID_DID_FORMAT",
        message = "Invalid DID format: $did. Reason: $reason",
        context = mapOf(
            "did" to did,
            "reason" to reason
        )
    )
    
    // Credential-related errors
    data class CredentialInvalid(
        val reason: String,
        val credentialId: String? = null,
        val field: String? = null
    ) : VeriCoreError(
        code = "CREDENTIAL_INVALID",
        message = credentialId?.let { "Credential '$it' is invalid: $reason" } 
            ?: "Credential is invalid: $reason",
        context = mapOf(
            "reason" to reason,
            "credentialId" to credentialId,
            "field" to field
        ).filterValues { it != null }
    )
    
    data class CredentialIssuanceFailed(
        val reason: String,
        val issuerDid: String? = null
    ) : VeriCoreError(
        code = "CREDENTIAL_ISSUANCE_FAILED",
        message = "Credential issuance failed: $reason",
        context = mapOf(
            "reason" to reason,
            "issuerDid" to issuerDid
        ).filterValues { it != null }
    )
    
    // Blockchain-related errors
    data class ChainNotRegistered(
        val chainId: String,
        val availableChains: List<String>
    ) : VeriCoreError(
        code = "CHAIN_NOT_REGISTERED",
        message = "Blockchain chain '$chainId' is not registered. Available chains: $availableChains",
        context = mapOf(
            "chainId" to chainId,
            "availableChains" to availableChains
        )
    )
    
    // Wallet-related errors
    data class WalletCreationFailed(
        val reason: String,
        val provider: String? = null,
        val walletId: String? = null
    ) : VeriCoreError(
        code = "WALLET_CREATION_FAILED",
        message = "Wallet creation failed: $reason",
        context = mapOf(
            "reason" to reason,
            "provider" to provider,
            "walletId" to walletId
        ).filterValues { it != null }
    )
    
    // Plugin-related errors
    data class PluginNotFound(
        val pluginId: String,
        val pluginType: String? = null
    ) : VeriCoreError(
        code = "PLUGIN_NOT_FOUND",
        message = pluginType?.let { "Plugin '$pluginId' of type '$it' not found" }
            ?: "Plugin '$pluginId' not found",
        context = mapOf(
            "pluginId" to pluginId,
            "pluginType" to pluginType
        ).filterValues { it != null }
    )
    
    data class PluginInitializationFailed(
        val pluginId: String,
        val reason: String
    ) : VeriCoreError(
        code = "PLUGIN_INITIALIZATION_FAILED",
        message = "Plugin '$pluginId' failed to initialize: $reason",
        context = mapOf(
            "pluginId" to pluginId,
            "reason" to reason
        )
    )
    
    // Validation errors
    data class ValidationFailed(
        val field: String,
        val reason: String,
        val value: Any? = null
    ) : VeriCoreError(
        code = "VALIDATION_FAILED",
        message = "Validation failed for field '$field': $reason",
        context = mapOf(
            "field" to field,
            "reason" to reason,
            "value" to value
        )
    )
    
    // Generic errors
    data class InvalidOperation(
        override val code: String = "INVALID_OPERATION",
        override val message: String,
        override val context: Map<String, Any?> = emptyMap(),
        override val cause: Throwable? = null
    ) : VeriCoreError(code, message, context, cause)
    
    data class InvalidState(
        override val code: String = "INVALID_STATE",
        override val message: String,
        override val context: Map<String, Any?> = emptyMap(),
        override val cause: Throwable? = null
    ) : VeriCoreError(code, message, context, cause)
    
    data class Unknown(
        override val code: String = "UNKNOWN_ERROR",
        override val message: String,
        override val context: Map<String, Any?> = emptyMap(),
        override val cause: Throwable? = null
    ) : VeriCoreError(code, message, context, cause)
}

/**
 * Extension function to convert any Throwable to a VeriCoreError.
 */
fun Throwable.toVeriCoreError(): VeriCoreError = when (this) {
    is VeriCoreError -> this
    is IllegalArgumentException -> VeriCoreError.InvalidOperation(
        code = "INVALID_ARGUMENT",
        message = message ?: "Invalid argument",
        context = emptyMap(),
        cause = this
    )
    is IllegalStateException -> VeriCoreError.InvalidState(
        code = "INVALID_STATE",
        message = message ?: "Invalid state",
        context = emptyMap(),
        cause = this
    )
    is NotFoundException -> VeriCoreError.DidNotFound(
        did = message?.substringAfter(": ") ?: "unknown",
        availableMethods = emptyList()
    )
    is InvalidOperationException -> VeriCoreError.InvalidOperation(
        code = "INVALID_OPERATION",
        message = message ?: "Invalid operation",
        context = emptyMap(),
        cause = this
    )
    else -> VeriCoreError.Unknown(
        code = "UNKNOWN_ERROR",
        message = message ?: "Unknown error: ${this::class.simpleName}",
        context = emptyMap(),
        cause = this
    )
}

