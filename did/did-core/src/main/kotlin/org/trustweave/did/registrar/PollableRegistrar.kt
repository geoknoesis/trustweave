package org.trustweave.did.registrar

import org.trustweave.did.registrar.model.DidRegistrationResponse

/**
 * Extension interface for registrars that support polling for long-running operations.
 *
 * This interface enables automatic polling for operations that return a `jobId`,
 * allowing HttpDidMethod to automatically wait for completion without manual intervention.
 *
 * **Implementation Note:**
 * Registrars that support polling should implement this interface to enable
 * full DID Registration specification compliance with automatic polling.
 *
 * **Example Implementation:**
 * ```kotlin
 * class DefaultUniversalRegistrar(...) : DidRegistrar, PollableRegistrar {
 *     override suspend fun getOperationStatus(jobId: String): DidRegistrationResponse {
 *         // Poll status endpoint
 *     }
 *
 *     override suspend fun waitForCompletion(response: DidRegistrationResponse): DidRegistrationResponse {
 *         // Poll until complete
 *     }
 * }
 * ```
 */
interface PollableRegistrar {
    /**
     * Gets the current status of a long-running operation.
     *
     * @param jobId The job identifier from a previous operation response
     * @return Current registration response with updated didState
     */
    suspend fun getOperationStatus(jobId: String): DidRegistrationResponse

    /**
     * Waits for a long-running operation to complete by polling status.
     *
     * This method will:
     * 1. Check if the response indicates a long-running operation (has jobId)
     * 2. Poll the status endpoint at regular intervals
     * 3. Return when the operation is complete (finished or failed)
     *
     * @param response Initial registration response (may contain jobId)
     * @return Final registration response when operation is complete
     * @throws org.trustweave.core.exception.TrustWeaveException if polling fails or times out
     */
    suspend fun waitForCompletion(response: DidRegistrationResponse): DidRegistrationResponse
}

