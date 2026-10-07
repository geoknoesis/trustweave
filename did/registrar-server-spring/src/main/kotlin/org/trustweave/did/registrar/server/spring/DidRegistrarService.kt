package org.trustweave.did.registrar.server.spring

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.trustweave.did.model.DidDocument
import org.trustweave.did.registrar.DidRegistrar
import org.trustweave.did.registrar.model.CreateDidOptions
import org.trustweave.did.registrar.model.DeactivateDidOptions
import org.trustweave.did.registrar.model.DidRegistrationResponse
import org.trustweave.did.registrar.model.UpdateDidOptions
import org.trustweave.did.registrar.storage.JobStorage
import java.util.UUID

/**
 * Service class that handles DID Registrar operations.
 *
 * This service processes DID creation, update, and deactivation requests
 * for the RESTful endpoints.
 */
class DidRegistrarService(
    private val registrar: DidRegistrar,
    private val jobStorage: JobStorage,
) {
    /**
     * Creates a new DID.
     *
     * Used by RESTful endpoint: POST /1.0/dids
     *
     * @param method DID method name (e.g., "web", "key", "ion")
     * @param options Creation options
     * @return Registration response
     */
    suspend fun createDid(
        method: String,
        options: CreateDidOptions,
    ): DidRegistrationResponse = handleCreateOperation(method, options)

    /**
     * Updates an existing DID.
     *
     * Used by RESTful endpoint: PUT /1.0/dids/{did}
     *
     * @param did DID to update
     * @param document Updated DID Document
     * @param options Update options
     * @return Registration response
     */
    suspend fun updateDid(
        did: String,
        document: DidDocument,
        options: UpdateDidOptions,
    ): DidRegistrationResponse = handleUpdateOperation(did, document, options)

    /**
     * Deactivates a DID.
     *
     * Used by RESTful endpoint: DELETE /1.0/dids/{did}
     *
     * @param did DID to deactivate
     * @param options Deactivation options
     * @return Registration response
     */
    suspend fun deactivateDid(
        did: String,
        options: DeactivateDidOptions,
    ): DidRegistrationResponse = handleDeactivateOperation(did, options)

    /**
     * Gets the status of a job.
     */
    fun getJobStatus(jobId: String): DidRegistrationResponse? = jobStorage.get(jobId)

    /**
     * Handles DID creation operation.
     */
    private suspend fun handleCreateOperation(
        method: String,
        options: CreateDidOptions,
    ): DidRegistrationResponse {
        val response = registrar.createDid(method, options)

        // If operation is long-running, store it and return jobId
        if (response.jobId != null || !response.isComplete()) {
            val jobId = response.jobId ?: UUID.randomUUID().toString()
            withContext(Dispatchers.IO) { jobStorage.store(jobId, response.copy(jobId = jobId)) }
            return response.copy(jobId = jobId)
        }

        return response
    }

    /**
     * Handles DID update operation.
     */
    private suspend fun handleUpdateOperation(
        did: String,
        document: DidDocument,
        options: UpdateDidOptions,
    ): DidRegistrationResponse {
        val response = registrar.updateDid(did, document, options)

        // If operation is long-running, store it and return jobId
        if (response.jobId != null || !response.isComplete()) {
            val jobId = response.jobId ?: UUID.randomUUID().toString()
            withContext(Dispatchers.IO) { jobStorage.store(jobId, response.copy(jobId = jobId)) }
            return response.copy(jobId = jobId)
        }

        return response
    }

    /**
     * Handles DID deactivation operation.
     */
    private suspend fun handleDeactivateOperation(
        did: String,
        options: DeactivateDidOptions,
    ): DidRegistrationResponse {
        val response = registrar.deactivateDid(did, options)

        // If operation is long-running, store it and return jobId
        if (response.jobId != null || !response.isComplete()) {
            val jobId = response.jobId ?: UUID.randomUUID().toString()
            withContext(Dispatchers.IO) { jobStorage.store(jobId, response.copy(jobId = jobId)) }
            return response.copy(jobId = jobId)
        }

        return response
    }
}
