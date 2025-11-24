package com.trustweave.did.registrar.util

import com.trustweave.core.exception.TrustWeaveException
import com.trustweave.did.registrar.DidRegistrar
import com.trustweave.did.registrar.model.DidRegistrationResponse
import com.trustweave.did.registrar.model.OperationState
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
    private val maxAttempts: Int = 60
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
            // If already complete, return immediately
            if (response.isComplete()) {
                return@withContext response
            }
            
            // If no jobId, cannot poll
            val jobId = response.jobId
                ?: throw TrustWeaveException(
                    "Cannot wait for completion: operation is not complete but no jobId provided"
                )
            
            // Poll until complete
            var currentResponse = response
            var attempts = 0
            
            while (!currentResponse.isComplete() && attempts < maxAttempts) {
                delay(pollInterval)
                
                // Get operation status
                // Note: This requires the registrar to support getOperationStatus
                // For now, we'll throw an error if the registrar doesn't support it
                currentResponse = getOperationStatus(jobId)
                attempts++
            }
            
            if (!currentResponse.isComplete()) {
                throw TrustWeaveException(
                    "Operation did not complete within ${maxAttempts * pollInterval}ms"
                )
            }
            
            // Check if operation failed
            if (currentResponse.didState.state == OperationState.FAILED) {
                val errorMsg = currentResponse.didState.action?.description
                    ?: "Operation failed"
                throw TrustWeaveException(errorMsg)
            }
            
            currentResponse
        }
    }
    
    /**
     * Gets the status of a long-running operation.
     * 
     * This method needs to be implemented by registrars that support
     * long-running operations. For now, this is a placeholder.
     * 
     * @param jobId Job identifier
     * @return Current registration response
     */
    private suspend fun getOperationStatus(jobId: String): DidRegistrationResponse {
        // TODO: This should be part of the DidRegistrar interface
        // For now, throw an error indicating this feature is not yet supported
        throw TrustWeaveException(
            "Job status polling not yet implemented. " +
            "The registrar must support getOperationStatus(jobId) for this to work."
        )
    }
}

