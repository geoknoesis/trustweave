package com.trustweave.did.registrar.server.spring

import com.trustweave.core.exception.TrustWeaveException
import com.trustweave.did.registrar.model.DeactivateDidOptions
import com.trustweave.did.registrar.model.DidRegistrationResponse
import com.trustweave.did.registrar.model.UpdateDidOptions
import com.trustweave.did.registrar.server.spring.dto.*
import kotlinx.coroutines.runBlocking
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

/**
 * Spring Boot REST controller for DID Registrar endpoints.
 *
 * RESTful endpoints:
 * - POST /1.0/dids - Create DID
 * - PUT /1.0/dids/{did} - Update DID
 * - DELETE /1.0/dids/{did} - Deactivate DID
 * - GET /1.0/jobs/{jobId} - Get job status
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
 * @param service The service that handles DID Registrar operations
 */
@RestController
@RequestMapping("/1.0")
class DidRegistrarController(
    private val service: DidRegistrarService
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
    fun createDid(@RequestBody request: CreateDidRequest): ResponseEntity<Any> {
        return try {
            val response = runBlocking {
                service.createDid(request.method, request.options)
            }
            ResponseEntity.ok(response)
        } catch (e: TrustWeaveException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.fromException(e, "INVALID_REQUEST"))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
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
    fun updateDid(
        @PathVariable did: String,
        @RequestBody request: UpdateDidRequest
    ): ResponseEntity<Any> {
        return try {
            val response = runBlocking {
                service.updateDid(did, request.didDocument, request.options ?: UpdateDidOptions())
            }
            ResponseEntity.ok(response)
        } catch (e: TrustWeaveException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.fromException(e, "INVALID_REQUEST"))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
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
    fun deactivateDid(
        @PathVariable did: String,
        @RequestBody request: DeactivateDidRequest? = null
    ): ResponseEntity<Any> {
        return try {
            val response = runBlocking {
                service.deactivateDid(did, request?.options ?: DeactivateDidOptions())
            }
            ResponseEntity.ok(response)
        } catch (e: TrustWeaveException) {
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.fromException(e, "INVALID_REQUEST"))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.fromException(e, "INTERNAL_ERROR"))
        }
    }

    /**
     * GET /1.0/jobs/{jobId}
     *
     * Gets the status of a long-running operation.
     */
    @GetMapping("/jobs/{jobId}")
    fun getJobStatus(@PathVariable jobId: String): ResponseEntity<Any> {
        return try {
            val response = service.getJobStatus(jobId)
                ?: throw TrustWeaveException("Job not found: $jobId")
            ResponseEntity.ok(response)
        } catch (e: TrustWeaveException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.fromException(e, "JOB_NOT_FOUND"))
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.fromException(e, "INTERNAL_ERROR"))
        }
    }

}

