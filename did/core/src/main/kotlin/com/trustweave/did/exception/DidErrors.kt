package com.trustweave.did.exception

import com.trustweave.core.exception.NotFoundException
import com.trustweave.core.exception.TrustWeaveException

/**
 * DID-related error types.
 * 
 * These errors provide structured error codes and context for DID operations.
 */
sealed class DidError(
    open val code: String,
    override val message: String,
    open val context: Map<String, Any?> = emptyMap(),
    override val cause: Throwable? = null
) : TrustWeaveException(message, cause) {
    
    data class DidNotFound(
        val did: String,
        val availableMethods: List<String> = emptyList()
    ) : DidError(
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
    ) : DidError(
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
    ) : DidError(
        code = "INVALID_DID_FORMAT",
        message = "Invalid DID format: $did. Reason: $reason",
        context = mapOf(
            "did" to did,
            "reason" to reason
        )
    )
}

/**
 * Extension function to convert any Throwable to a DidError.
 */
fun Throwable.toDidError(): DidError = when (this) {
    is DidError -> this
    is NotFoundException -> {
        // Try to extract DID from message, but don't rely on message format
        val did = message?.substringAfter(": ")?.takeIf { it.isNotBlank() } 
            ?: message?.takeIf { it.isNotBlank() }
            ?: "unknown"
        DidError.DidNotFound(
            did = did,
            availableMethods = emptyList()
        )
    }
    else -> DidError.InvalidDidFormat(
        did = "unknown",
        reason = message ?: "Unknown error: ${this::class.simpleName}"
    )
}

