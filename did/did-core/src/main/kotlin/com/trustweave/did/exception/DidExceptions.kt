package com.trustweave.did.exception

import com.trustweave.core.exception.TrustWeaveException

/**
 * DID-related exception types.
 *
 * These exceptions provide structured error codes and context for DID operations.
 */
sealed class DidException(
    override val code: String,
    override val message: String,
    override val context: Map<String, Any?> = emptyMap(),
    override val cause: Throwable? = null
) : TrustWeaveException(code, message, context, cause) {

    data class DidNotFound(
        val did: String,
        val availableMethods: List<String> = emptyList()
    ) : DidException(
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
    ) : DidException(
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
    ) : DidException(
        code = "INVALID_DID_FORMAT",
        message = "Invalid DID format: $did. Reason: $reason",
        context = mapOf(
            "did" to did,
            "reason" to reason
        )
    )

    data class DidResolutionFailed(
        val did: String,
        val reason: String,
        override val cause: Throwable? = null
    ) : DidException(
        code = "DID_RESOLUTION_FAILED",
        message = "DID resolution failed for '$did': $reason",
        context = mapOf(
            "did" to did,
            "reason" to reason
        ),
        cause = cause
    )
}

/**
 * Extension function to convert any Throwable to a DidException.
 */
fun Throwable.toDidException(): DidException = when (this) {
    is DidException -> this
    is TrustWeaveException.NotFound -> DidException.DidNotFound(
        did = this.resource ?: "unknown",
        availableMethods = emptyList()
    )
    else -> DidException.InvalidDidFormat(
        did = "unknown",
        reason = message ?: "Unknown error: ${this::class.simpleName}"
    )
}

