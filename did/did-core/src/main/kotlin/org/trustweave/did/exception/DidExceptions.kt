package org.trustweave.did.exception

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.identifiers.Did

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
        val did: Did,
        val availableMethods: List<String> = emptyList()
    ) : DidException(
        code = "DID_NOT_FOUND",
        message = "DID not found: ${did.value}",
        context = mapOf(
            "did" to did.value,
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
        val did: Did,
        val reason: String,
        override val cause: Throwable? = null
    ) : DidException(
        code = "DID_RESOLUTION_FAILED",
        message = "DID resolution failed for '${did.value}': $reason",
        context = mapOf(
            "did" to did.value,
            "reason" to reason
        ),
        cause = cause
    )

    data class DidCreationFailed(
        val did: Did?,
        val reason: String,
        override val cause: Throwable? = null
    ) : DidException(
        code = "DID_CREATION_FAILED",
        message = "DID creation failed: $reason",
        context = mapOf(
            "did" to (did?.value ?: "unknown"),
            "reason" to reason
        ),
        cause = cause
    )

    data class DidUpdateFailed(
        val did: Did,
        val reason: String,
        override val cause: Throwable? = null
    ) : DidException(
        code = "DID_UPDATE_FAILED",
        message = "DID update failed for '${did.value}': $reason",
        context = mapOf(
            "did" to did.value,
            "reason" to reason
        ),
        cause = cause
    )

    data class DidDeactivationFailed(
        val did: Did,
        val reason: String,
        override val cause: Throwable? = null
    ) : DidException(
        code = "DID_DEACTIVATION_FAILED",
        message = "DID deactivation failed for '${did.value}': $reason",
        context = mapOf(
            "did" to did.value,
            "reason" to reason
        ),
        cause = cause
    )

    /**
     * Exception thrown when a DID operation requires additional action (ACTION state).
     * 
     * According to the DID Registration specification, some operations may return
     * an ACTION state requiring user interaction (e.g., redirect, sign, wait).
     * This exception is thrown to allow the caller to handle the action appropriately.
     * 
     * @param did The DID being operated on (if available)
     * @param action The action details from the registration response
     * @param reason Human-readable reason for the action requirement
     */
    data class RequiresAction(
        val did: Did?,
        val action: org.trustweave.did.registrar.model.Action,
        val reason: String
    ) : DidException(
        code = "DID_REQUIRES_ACTION",
        message = "DID operation requires action: $reason",
        context = mapOf(
            "did" to (did?.value ?: "unknown"),
            "actionType" to action.type,
            "actionUrl" to action.url,
            "actionDescription" to action.description,
            "reason" to reason
        )
    )
}

/**
 * Extension function to convert any Throwable to a DidException.
 */
fun Throwable.toDidException(): DidException = when (this) {
    is DidException -> this
    is TrustWeaveException.NotFound -> {
        val didString = this.resource ?: "unknown"
        try {
            DidException.DidNotFound(
                did = Did(didString),
                availableMethods = emptyList()
            )
        } catch (e: IllegalArgumentException) {
            DidException.InvalidDidFormat(
                did = didString,
                reason = message ?: "Unknown error: ${this::class.simpleName}"
            )
        }
    }
    else -> DidException.InvalidDidFormat(
        did = "unknown",
        reason = message ?: "Unknown error: ${this::class.simpleName}"
    )
}

