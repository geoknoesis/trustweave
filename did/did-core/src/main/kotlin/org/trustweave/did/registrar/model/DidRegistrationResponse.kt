package org.trustweave.did.registrar.model

import kotlinx.serialization.Serializable

/**
 * Response from DID Registrar operations according to DID Registration specification.
 *
 * The DID Registration spec defines that all operations return a response containing:
 * - `jobId`: For long-running operations (present when operation is ongoing, absent when finished/failed)
 * - `didState`: The current state of the DID operation
 *
 * @see https://identity.foundation/did-registration/
 */
@Serializable
data class DidRegistrationResponse(
    /**
     * Unique identifier for tracking the status of long-running DID operations.
     *
     * - Present when operation is ongoing (state is "action" or "wait")
     * - Absent when operation is finished or failed
     */
    val jobId: String? = null,

    /**
     * Current state of the DID operation.
     */
    val didState: DidState
) {
    /**
     * Returns true if the operation is complete (finished or failed).
     */
    fun isComplete(): Boolean {
        return didState.state == OperationState.FINISHED || didState.state == OperationState.FAILED
    }

    /**
     * Returns true if the operation requires additional action.
     */
    fun requiresAction(): Boolean {
        return didState.state == OperationState.ACTION
    }

    /**
     * Returns true if the operation is waiting/pending.
     */
    fun isWaiting(): Boolean {
        return didState.state == OperationState.WAIT
    }
}

