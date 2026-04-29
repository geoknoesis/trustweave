package org.trustweave.did.registrar.util

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.registrar.DidRegistrar
import org.trustweave.did.registrar.PollableRegistrar
import org.trustweave.did.registrar.model.DidRegistrationResponse
import org.trustweave.did.registrar.model.OperationState
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility for tracking and polling long-running DID registration operations.
 *
 * According to the DID Registration specification, some operations may return
 * a `jobId` indicating that the operation is long-running. This utility helps
 * poll for completion.
 *
 * Polling uses [PollableRegistrar.getOperationStatus] when the registrar implements
 * [PollableRegistrar] (for example [org.trustweave.did.registrar.client.DefaultUniversalRegistrar]).
 * [pollInterval] and [maxAttempts] apply to that polling loop.
 *
 * **Example Usage:**
 * ```kotlin
 * val registrar: DidRegistrar = // ...
 * val response = registrar.createDid("web", options)
 *
 * val tracker = JobTracker(registrar)
 * val finalResponse = tracker.waitForCompletion(response)
 * ```
 */
class JobTracker(
    private val registrar: DidRegistrar,
    private val pollInterval: Long = 1000,
    private val maxAttempts: Int = 60,
) {

    /**
     * Waits for a long-running operation to complete by polling status.
     *
     * @param response Initial registration response (may contain jobId)
     * @return Final registration response when operation is complete
     * @throws TrustWeaveException if operation fails or times out
     */
    suspend fun waitForCompletion(response: DidRegistrationResponse): DidRegistrationResponse {
        return withContext(Dispatchers.IO) {
            if (response.isComplete()) {
                throwIfFailed(response)
                return@withContext response
            }

            val jobId = response.jobId
                ?: throw TrustWeaveException.InvalidState(
                    message = "Cannot wait for completion: operation is not complete but no jobId provided",
                    context = mapOf("operation" to "waitForCompletion"),
                )

            val pollable = registrar as? PollableRegistrar
                ?: throw TrustWeaveException.InvalidOperation(
                    message = "Long-running DID registration (jobId=$jobId) requires a registrar that implements " +
                        "PollableRegistrar — for example DefaultUniversalRegistrar. " +
                        "Registrar: ${registrar::class.qualifiedName}",
                    context = mapOf("jobId" to jobId, "operation" to "waitForCompletion"),
                )

            var currentResponse = response
            var attempts = 0

            // Poll immediately on first iteration (no unnecessary wait before first status fetch)
            while (!currentResponse.isComplete() && attempts < maxAttempts) {
                if (attempts > 0) {
                    delay(pollInterval)
                }
                currentResponse = pollable.getOperationStatus(jobId)
                attempts++
            }

            if (!currentResponse.isComplete()) {
                val approxWaitMs = (maxAttempts - 1).coerceAtLeast(0) * pollInterval
                throw TrustWeaveException.Unknown(
                    message = "Operation did not complete after $maxAttempts status polls " +
                        "(~${approxWaitMs}ms polling delay after the first immediate poll; interval ${pollInterval}ms)",
                    context = mapOf(
                        "maxAttempts" to maxAttempts,
                        "pollInterval" to pollInterval,
                        "jobId" to jobId,
                    ),
                )
            }

            throwIfFailed(currentResponse)
            currentResponse
        }
    }

    private fun throwIfFailed(response: DidRegistrationResponse) {
        if (response.didState.state == OperationState.FAILED) {
            val errorMsg = response.didState.action?.description
                ?: response.didState.reason
                ?: "Operation failed"
            throw TrustWeaveException.Unknown(
                message = errorMsg,
                context = mapOf("operation" to "waitForCompletion", "state" to "FAILED"),
            )
        }
    }
}
