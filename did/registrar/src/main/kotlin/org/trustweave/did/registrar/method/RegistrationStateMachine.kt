package org.trustweave.did.registrar.method

import org.trustweave.did.exception.DidException
import org.trustweave.did.identifiers.Did
import org.trustweave.did.registrar.DidRegistrar
import org.trustweave.did.registrar.PollableRegistrar
import org.trustweave.did.registrar.model.DidRegistrationResponse
import org.trustweave.did.registrar.model.OperationState
import kotlinx.coroutines.delay

/**
 * Handles the multi-step lifecycle of a DID registration operation.
 *
 * DID registrars (e.g. Universal Registrar) may return responses that require
 * further polling (WAIT state) or a long-running job (jobId-based polling).
 * This class encapsulates those state transitions so [HttpDidMethod] stays focused
 * on the HTTP protocol layer.
 *
 * State transitions:
 * ```
 * PENDING / COMPLETE → return immediately
 * FAILED             → throw exception
 * ACTION             → throw RequiresAction (caller must react)
 * WAIT               → poll via PollableRegistrar.getOperationStatus() → loop
 * has jobId          → delegate to PollableRegistrar.waitForCompletion()
 * ```
 */
internal class RegistrationStateMachine(
    private val registrar: DidRegistrar?
) {
    /**
     * Drive [response] to a terminal state (finished or failed).
     *
     * @param response The initial response from the registrar
     * @param operation Human-readable operation name ("create", "update", "deactivate")
     * @param did The DID being operated on (used in exception messages)
     * @return The final [DidRegistrationResponse] in a finished state
     * @throws DidException on failure, action-required, or timeout
     */
    suspend fun advance(
        response: DidRegistrationResponse,
        operation: String,
        did: Did?
    ): DidRegistrationResponse {
        if (response.isComplete()) {
            if (response.didState.state == OperationState.FAILED) throw buildException(
                operation, did, response.didState.reason ?: "Operation failed"
            )
            return response
        }
        if (response.requiresAction()) {
            val action = response.didState.action ?: throw buildException(
                operation, did, "Operation requires action but no details provided"
            )
            throw DidException.RequiresAction(
                did = did, action = action, reason = action.description ?: "ACTION required"
            )
        }
        if (response.isWaiting()) return pollUntilComplete(response, operation, did)
        if (response.jobId != null) return pollLongRunning(response, operation, did)
        throw buildException(operation, did, "Operation in unexpected state: ${response.didState.state}")
    }

    private suspend fun pollLongRunning(
        response: DidRegistrationResponse,
        operation: String,
        did: Did?
    ): DidRegistrationResponse {
        val pollable = registrar as? PollableRegistrar
            ?: throw buildException(
                operation, did,
                "Long-running operation requires PollableRegistrar. " +
                "Current registrar: ${registrar?.javaClass?.name}"
            )
        return try {
            pollable.waitForCompletion(response)
        } catch (e: Exception) {
            throw buildException(operation, did, "Failed to poll long-running operation: ${e.message}", e)
        }
    }

    private suspend fun pollUntilComplete(
        response: DidRegistrationResponse,
        operation: String,
        did: Did?
    ): DidRegistrationResponse {
        val jobId = response.jobId ?: throw buildException(operation, did, "WAIT state requires jobId")
        val pollable = registrar as? PollableRegistrar
            ?: throw buildException(operation, did, "WAIT state requires PollableRegistrar")
        var current = response
        var attempts = 0
        while (current.isWaiting() && attempts < 60) {
            delay(1000)
            current = try {
                pollable.getOperationStatus(jobId)
            } catch (e: Exception) {
                throw buildException(operation, did, "Failed to poll WAIT state: ${e.message}", e)
            }
            attempts++
        }
        if (current.isWaiting()) throw buildException(
            operation, did, "Still in WAIT state after 60 polling attempts"
        )
        return advance(current, operation, did)
    }

    private fun buildException(
        operation: String,
        did: Did?,
        reason: String,
        cause: Throwable? = null
    ): DidException = when (operation) {
        "create" -> DidException.DidCreationFailed(did = did, reason = reason, cause = cause)
        "update" -> DidException.DidUpdateFailed(
            did = did ?: throw IllegalArgumentException("DID required for update"),
            reason = reason, cause = cause
        )
        "deactivate" -> DidException.DidDeactivationFailed(
            did = did ?: throw IllegalArgumentException("DID required for deactivate"),
            reason = reason, cause = cause
        )
        else -> DidException.DidCreationFailed(did = did, reason = reason, cause = cause)
    }
}
