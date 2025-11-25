package com.trustweave.did.registrar.server

import com.trustweave.core.exception.TrustWeaveException
import com.trustweave.did.DidDocument
import com.trustweave.did.registrar.DidRegistrar
import com.trustweave.did.registrar.model.*
import com.trustweave.did.registrar.server.dto.*
import com.trustweave.did.registrar.storage.JobStorage
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

/**
 * Ktor routing configuration for DID Registrar endpoints.
 *
 * RESTful endpoints:
 * - POST /1.0/dids - Create DID
 * - PUT /1.0/dids/{did} - Update DID
 * - DELETE /1.0/dids/{did} - Deactivate DID
 * - GET /1.0/jobs/{jobId} - Get job status
 *
 * **Example Usage:**
 * ```kotlin
 * val registrar: DidRegistrar = KmsBasedRegistrar(kms)
 * val jobStorage: JobStorage = InMemoryJobStorage()
 * routing {
 *     configureDidRegistrarRoutes(registrar, jobStorage)
 * }
 * ```
 *
 * @param registrar The DID Registrar implementation to use for operations
 * @param jobStorage Storage for tracking long-running operations
 */
fun Routing.configureDidRegistrarRoutes(
    registrar: DidRegistrar,
    jobStorage: JobStorage
) {
    
    /**
     * POST /1.0/dids
     *
     * Creates a new DID.
     */
    post("/1.0/dids") {
        try {
            val request = call.receive<CreateDidRequest>()
            val response = handleCreateOperation(registrar, request.method, request.options, jobStorage)
            call.respond(HttpStatusCode.OK, response)
        } catch (e: TrustWeaveException) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse.fromException(e, "INVALID_REQUEST")
            )
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse.fromException(e, "INTERNAL_ERROR")
            )
        }
    }

    /**
     * PUT /1.0/dids/{did}
     *
     * Updates an existing DID.
     */
    put("/1.0/dids/{did}") {
        try {
            val did = call.parameters["did"]
                ?: throw com.trustweave.core.exception.TrustWeaveException.InvalidOperation(
                    message = "Missing did parameter",
                    context = mapOf("parameter" to "did")
                )
            val request = call.receive<UpdateDidRequest>()
            val response = handleUpdateOperation(registrar, did, request.didDocument, request.options ?: UpdateDidOptions(), jobStorage)
            call.respond(HttpStatusCode.OK, response)
        } catch (e: TrustWeaveException) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse.fromException(e, "INVALID_REQUEST")
            )
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse.fromException(e, "INTERNAL_ERROR")
            )
        }
    }

    /**
     * DELETE /1.0/dids/{did}
     *
     * Deactivates a DID.
     */
    delete("/1.0/dids/{did}") {
        try {
            val did = call.parameters["did"]
                ?: throw com.trustweave.core.exception.TrustWeaveException.InvalidOperation(
                    message = "Missing did parameter",
                    context = mapOf("parameter" to "did")
                )
            val request = call.receiveNullable<DeactivateDidRequest>()
            val response = handleDeactivateOperation(registrar, did, request?.options ?: DeactivateDidOptions(), jobStorage)
            call.respond(HttpStatusCode.OK, response)
        } catch (e: TrustWeaveException) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse.fromException(e, "INVALID_REQUEST")
            )
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse.fromException(e, "INTERNAL_ERROR")
            )
        }
    }

    /**
     * GET /1.0/jobs/{jobId}
     *
     * Gets the status of a long-running operation.
     */
    get("/1.0/jobs/{jobId}") {
        try {
            val jobId = call.parameters["jobId"]
                ?: throw com.trustweave.core.exception.TrustWeaveException.InvalidOperation(
                    message = "Missing jobId parameter",
                    context = mapOf("parameter" to "jobId")
                )
            val response = jobStorage.get(jobId)
                ?: throw com.trustweave.core.exception.TrustWeaveException.NotFound(
                    resource = "job:$jobId"
                )
            call.respond(HttpStatusCode.OK, response)
        } catch (e: TrustWeaveException) {
            call.respond(
                HttpStatusCode.NotFound,
                ErrorResponse.fromException(e, "JOB_NOT_FOUND")
            )
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse.fromException(e, "INTERNAL_ERROR")
            )
        }
    }

}

/**
 * Handles DID creation operation.
 */
private suspend fun handleCreateOperation(
    registrar: DidRegistrar,
    method: String,
    options: CreateDidOptions,
    jobStorage: JobStorage
): DidRegistrationResponse {
    val response = registrar.createDid(method, options)

    // If operation is long-running, store it and return jobId
    if (response.jobId != null || !response.isComplete()) {
        val jobId = response.jobId ?: UUID.randomUUID().toString()
        jobStorage.store(jobId, response.copy(jobId = jobId))
        return response.copy(jobId = jobId)
    }

    return response
}

/**
 * Handles DID update operation.
 */
private suspend fun handleUpdateOperation(
    registrar: DidRegistrar,
    did: String,
    document: DidDocument,
    options: UpdateDidOptions,
    jobStorage: JobStorage
): DidRegistrationResponse {
    val response = registrar.updateDid(did, document, options)

    // If operation is long-running, store it and return jobId
    if (response.jobId != null || !response.isComplete()) {
        val jobId = response.jobId ?: UUID.randomUUID().toString()
        jobStorage.store(jobId, response.copy(jobId = jobId))
        return response.copy(jobId = jobId)
    }

    return response
}

/**
 * Handles DID deactivation operation.
 */
private suspend fun handleDeactivateOperation(
    registrar: DidRegistrar,
    did: String,
    options: DeactivateDidOptions,
    jobStorage: JobStorage
): DidRegistrationResponse {
    val response = registrar.deactivateDid(did, options)

    // If operation is long-running, store it and return jobId
    if (response.jobId != null || !response.isComplete()) {
        val jobId = response.jobId ?: UUID.randomUUID().toString()
        jobStorage.store(jobId, response.copy(jobId = jobId))
        return response.copy(jobId = jobId)
    }

    return response
}

