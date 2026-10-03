package org.trustweave.did.registrar.server.spring

import kotlinx.coroutines.CancellationException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.registrar.model.DeactivateDidOptions
import org.trustweave.did.registrar.model.UpdateDidOptions
import org.trustweave.did.registrar.server.spring.dto.CreateDidRequest
import org.trustweave.did.registrar.server.spring.dto.DeactivateDidRequest
import org.trustweave.did.registrar.server.spring.dto.ErrorResponse
import org.trustweave.did.registrar.server.spring.dto.UpdateDidRequest

/**
 * Spring Boot REST controller for DID Registrar endpoints.
 *
 * RESTful endpoints:
 * - POST /1.0/dids - Create DID
 * - PUT /1.0/dids/{did} - Update DID
 * - DELETE /1.0/dids/{did} - Deactivate DID
 * - GET /1.0/jobs/{jobId} - Get job status
 *
 * Uses Spring WebFlux coroutine integration so controller methods are declared `suspend`
 * and run on the reactor scheduler without blocking a thread.
 *
 * **Example Usage:**
 * ```kotlin
 * @Configuration
 * class RegistrarConfig {
 *     @Bean
 *     fun registrarController(service: DidRegistrarService) =
 *         DidRegistrarController(service)
 * }
 * ```
 *
 * **Authentication:** POST, PUT and DELETE are key custody operations and are refused (503)
 * until [authentication] is configured, and refused (401) when the caller's bearer token does not
 * match; see [RegistrarAuthentication]. Job status reads need the same credentials unless
 * `trustweave.registrar.auth.public-job-status=true` ([RegistrarAuthentication.publicJobStatus]).
 *
 * @param service The service that handles DID Registrar operations
 * @param authentication What authenticates callers of the mutating endpoints
 */
@RestController
@RequestMapping("/1.0")
class DidRegistrarController(
    private val service: DidRegistrarService,
    private val authentication: RegistrarAuthentication = RegistrarAuthentication.unconfigured(),
) {
    /**
     * POST /1.0/dids
     *
     * Creates a new DID.
     *
     * **Example Request:**
     * ```json
     * {
     *   "method": "web",
     *   "options": {
     *     "keyManagementMode": "internal-secret",
     *     "returnSecrets": true
     *   }
     * }
     * ```
     */
    @PostMapping("/dids")
    suspend fun createDid(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @RequestBody request: CreateDidRequest,
    ): ResponseEntity<Any> {
        authentication.refusal(authorization)?.let { return it }
        return try {
            val response = service.createDid(request.method, request.options)
            ResponseEntity.ok(response)
        } catch (e: TrustWeaveException) {
            ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.fromException(e, "INVALID_REQUEST"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.fromException(e, "INTERNAL_ERROR"))
        }
    }

    /**
     * PUT /1.0/dids/{did}
     *
     * Updates an existing DID.
     *
     * **Example Request:**
     * ```json
     * {
     *   "didDocument": {
     *     "id": "did:web:example.com",
     *     "verificationMethod": [...]
     *   },
     *   "options": {
     *     "secret": {...}
     *   }
     * }
     * ```
     */
    @PutMapping("/dids/{did}")
    suspend fun updateDid(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable did: String,
        @RequestBody request: UpdateDidRequest,
    ): ResponseEntity<Any> {
        authentication.refusal(authorization)?.let { return it }
        return try {
            val response = service.updateDid(did, request.didDocument, request.options ?: UpdateDidOptions())
            ResponseEntity.ok(response)
        } catch (e: TrustWeaveException) {
            ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.fromException(e, "INVALID_REQUEST"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.fromException(e, "INTERNAL_ERROR"))
        }
    }

    /**
     * DELETE /1.0/dids/{did}
     *
     * Deactivates a DID.
     *
     * **Example Request:**
     * ```json
     * {
     *   "options": {
     *     "secret": {...}
     *   }
     * }
     * ```
     */
    @DeleteMapping("/dids/{did}")
    suspend fun deactivateDid(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable did: String,
        @RequestBody(required = false) request: DeactivateDidRequest? = null,
    ): ResponseEntity<Any> {
        authentication.refusal(authorization)?.let { return it }
        return try {
            val response = service.deactivateDid(did, request?.options ?: DeactivateDidOptions())
            ResponseEntity.ok(response)
        } catch (e: TrustWeaveException) {
            ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.fromException(e, "INVALID_REQUEST"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.fromException(e, "INTERNAL_ERROR"))
        }
    }

    /**
     * GET /1.0/jobs/{jobId}
     *
     * Gets the status of a long-running operation.
     */
    @GetMapping("/jobs/{jobId}")
    fun getJobStatus(
        @RequestHeader(HttpHeaders.AUTHORIZATION, required = false) authorization: String?,
        @PathVariable jobId: String,
    ): ResponseEntity<Any> =
        authentication.jobStatusRefusal(authorization) ?: try {
            val response =
                service.getJobStatus(jobId)
                    ?: throw TrustWeaveException.NotFound(resource = "job:$jobId")
            ResponseEntity.ok(response)
        } catch (e: TrustWeaveException) {
            ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.fromException(e, "JOB_NOT_FOUND"))
        } catch (e: Exception) {
            ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.fromException(e, "INTERNAL_ERROR"))
        }
}
