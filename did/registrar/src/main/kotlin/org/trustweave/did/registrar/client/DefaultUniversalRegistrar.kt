package org.trustweave.did.registrar.client

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.model.DidDocument
import org.trustweave.did.registrar.DidRegistrar
import org.trustweave.did.registrar.PollableRegistrar
import org.trustweave.did.registrar.adapter.UniversalRegistrarProtocolAdapter
import org.trustweave.did.registrar.adapter.StandardUniversalRegistrarAdapter
import org.trustweave.did.registrar.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Duration

/**
 * Default implementation of a Universal Registrar client.
 *
 * This implementation works with any Universal Registrar instance through a pluggable
 * protocol adapter pattern. Different providers can use different endpoint patterns,
 * authentication mechanisms, and response formats.
 *
 * Supports long-running operations with automatic polling when `jobId` is returned.
 *
 * **Example Usage:**
 * ```kotlin
 * // Use public dev.uniregistrar.io instance with standard adapter (default)
 * val registrar = DefaultUniversalRegistrar("https://dev.uniregistrar.io")
 *
 * // Create a DID
 * val response = registrar.createDid(
 *     method = "web",
 *     options = CreateDidOptions(
 *         keyManagementMode = KeyManagementMode.INTERNAL_SECRET,
 *         returnSecrets = true
 *     )
 * )
 *
 * // Wait for completion if long-running
 * val finalResponse = registrar.waitForCompletion(response)
 * val did = finalResponse.didState.did
 * ```
 *
 * @param baseUrl Base URL of the Universal Registrar instance (e.g., "https://dev.uniregistrar.io")
 * @param timeout Request timeout in seconds (default: 60)
 * @param apiKey Optional API key for authentication (if required by the registrar)
 * @param protocolAdapter Protocol adapter for the specific Universal Registrar implementation
 *                        (defaults to [StandardUniversalRegistrarAdapter])
 * @param pollInterval Interval between status checks for long-running operations in milliseconds (default: 1000)
 * @param maxPollAttempts Maximum number of polling attempts before giving up (default: 60)
 */
class DefaultUniversalRegistrar(
    private val baseUrl: String,
    private val timeout: Int = 60,
    private val apiKey: String? = null,
    private val protocolAdapter: UniversalRegistrarProtocolAdapter = StandardUniversalRegistrarAdapter(),
    private val pollInterval: Long = 1000,
    private val maxPollAttempts: Int = 60
) : DidRegistrar, PollableRegistrar {

    /**
     * Creates a new DID.
     *
     * @param method DID method name (e.g., "web", "key", "ion")
     * @param options Creation options (key management mode, secrets, etc.)
     * @return Registration response with jobId and didState
     */
    override suspend fun createDid(
        method: String,
        options: CreateDidOptions
    ): DidRegistrationResponse = withContext(Dispatchers.IO) {
        protocolAdapter.createDid(baseUrl, method, options)
    }

    /**
     * Updates a DID Document.
     *
     * @param did The DID to update
     * @param document The updated DID Document
     * @param options Update options (authorization secrets, etc.)
     * @return Registration response with jobId and didState
     */
    override suspend fun updateDid(
        did: String,
        document: DidDocument,
        options: UpdateDidOptions
    ): DidRegistrationResponse = withContext(Dispatchers.IO) {
        protocolAdapter.updateDid(baseUrl, did, document, options)
    }

    /**
     * Deactivates a DID.
     *
     * @param did The DID to deactivate
     * @param options Deactivation options (authorization secrets, etc.)
     * @return Registration response with jobId and didState
     */
    override suspend fun deactivateDid(
        did: String,
        options: DeactivateDidOptions
    ): DidRegistrationResponse = withContext(Dispatchers.IO) {
        protocolAdapter.deactivateDid(baseUrl, did, options)
    }

    /**
     * Gets the status of a long-running operation.
     *
     * Implements [PollableRegistrar.getOperationStatus] for automatic polling support.
     *
     * @param jobId Job identifier from a previous operation
     * @return Registration response with current didState
     */
    override suspend fun getOperationStatus(jobId: String): DidRegistrationResponse = withContext(Dispatchers.IO) {
        protocolAdapter.getOperationStatus(baseUrl, jobId)
    }

    /**
     * Waits for a long-running operation to complete by polling status.
     *
     * Implements [PollableRegistrar.waitForCompletion] for automatic polling support.
     *
     * This method will:
     * 1. Check if the response indicates a long-running operation (has jobId)
     * 2. Poll the status endpoint at regular intervals
     * 3. Return when the operation is complete (finished or failed)
     *
     * @param response Initial registration response (may contain jobId)
     * @return Final registration response when operation is complete
     */
    override suspend fun waitForCompletion(response: DidRegistrationResponse): DidRegistrationResponse {
        // If already complete, return immediately
        if (response.isComplete()) {
            return response
        }

        // If no jobId, cannot poll
        val jobId = response.jobId
                ?: throw org.trustweave.core.exception.TrustWeaveException.InvalidState(
                message = "Cannot wait for completion: operation is not complete but no jobId provided",
                context = mapOf("operation" to "waitForCompletion")
            )

        // Poll until complete
        var currentResponse = response
        var attempts = 0

        while (!currentResponse.isComplete() && attempts < maxPollAttempts) {
            delay(pollInterval)
            currentResponse = getOperationStatus(jobId)
            attempts++
        }

        if (!currentResponse.isComplete()) {
            throw org.trustweave.core.exception.TrustWeaveException.Unknown(
                message = "Operation did not complete within ${maxPollAttempts * pollInterval}ms",
                context = mapOf("jobId" to jobId, "maxPollAttempts" to maxPollAttempts, "pollInterval" to pollInterval)
            )
        }

        return currentResponse
    }

    /**
     * Creates a DID and waits for completion automatically.
     *
     * Convenience method that combines [createDid] and [waitForCompletion].
     *
     * @param method DID method name
     * @param options Creation options
     * @return Final registration response with completed operation
     */
    suspend fun createDidAndWait(
        method: String,
        options: CreateDidOptions
    ): DidRegistrationResponse {
        val response = createDid(method, options)
        return waitForCompletion(response)
    }

    /**
     * Updates a DID and waits for completion automatically.
     *
     * Convenience method that combines [updateDid] and [waitForCompletion].
     *
     * @param did The DID to update
     * @param document The updated DID Document
     * @param options Update options
     * @return Final registration response with completed operation
     */
    suspend fun updateDidAndWait(
        did: String,
        document: DidDocument,
        options: UpdateDidOptions = UpdateDidOptions()
    ): DidRegistrationResponse {
        val response = updateDid(did, document, options)
        return waitForCompletion(response)
    }

    /**
     * Deactivates a DID and waits for completion automatically.
     *
     * Convenience method that combines [deactivateDid] and [waitForCompletion].
     *
     * @param did The DID to deactivate
     * @param options Deactivation options
     * @return Final registration response with completed operation
     */
    suspend fun deactivateDidAndWait(
        did: String,
        options: DeactivateDidOptions = DeactivateDidOptions()
    ): DidRegistrationResponse {
        val response = deactivateDid(did, options)
        return waitForCompletion(response)
    }
}

