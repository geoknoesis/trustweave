package org.trustweave.did.registrar.model

import org.trustweave.did.model.DidDocument
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * Represents the current state of a DID operation according to DID Registration specification.
 *
 * The `didState` object contains:
 * - `state`: The operation's status (finished, failed, action, wait)
 * - `did`: The DID resulting from the operation (when available)
 * - `secret`: Sensitive information like controller keys (only in Internal Secret Mode with returnSecrets=true)
 * - `didDocument`: The DID Document post-operation
 * - `action`: Additional steps required (when state is "action")
 *
 * @see https://identity.foundation/did-registration/
 */
@Serializable
data class DidState(
    /**
     * Indicates the operation's status.
     *
     * - `finished`: Operation completed successfully
     * - `failed`: Operation encountered an error
     * - `action`: Additional steps are required to complete the operation
     * - `wait`: Operation is pending and awaiting further input or processing
     */
    val state: OperationState,

    /**
     * The DID resulting from the operation.
     * Present when the DID has been created or when referencing an existing DID.
     */
    val did: String? = null,

    /**
     * Contains sensitive information like controller keys.
     *
     * Only present when:
     * - Registrar operates in Internal Secret Mode
     * - `returnSecrets` option is set to `true`
     *
     * Contains:
     * - `keys`: List of key material (private keys, recovery keys, etc.)
     * - Method-specific secret fields
     */
    val secret: Secret? = null,

    /**
     * The DID Document resulting from the operation.
     * Present when the operation successfully creates, updates, or resolves a DID Document.
     */
    @Contextual
    val didDocument: DidDocument? = null,

    /**
     * Additional steps required to complete the operation.
     *
     * Required when `state` is `ACTION`.
     * Contains:
     * - `type`: Type of action (e.g., "redirect", "sign", "wait")
     * - `url`: URL to redirect to or endpoint to call
     * - `data`: Additional data required for the action
     */
    val action: Action? = null,

    /**
     * Error reason or message when the operation has failed.
     *
     * Present when `state` is `FAILED`.
     * Contains a human-readable description of why the operation failed.
     */
    val reason: String? = null
)

/**
 * Operational states for DID operations.
 */
@Serializable
enum class OperationState {
    /**
     * Operation completed successfully.
     */
    FINISHED,

    /**
     * Operation encountered an error and did not complete.
     */
    FAILED,

    /**
     * Additional steps are required to complete the operation.
     * The `action` field in `DidState` will contain details.
     */
    ACTION,

    /**
     * Operation is pending and awaiting further input or processing.
     */
    WAIT
}

