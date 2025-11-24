package com.trustweave.did.registrar.server

import com.trustweave.core.exception.TrustWeaveException
import com.trustweave.did.DidDocument
import com.trustweave.did.DidService
import com.trustweave.did.VerificationMethod
import com.trustweave.did.registrar.DidRegistrar
import com.trustweave.did.registrar.model.*
import com.trustweave.did.registrar.storage.JobStorage
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.util.UUID

/**
 * Ktor routing configuration for Universal Registrar endpoints.
 *
 * Implements the Universal Registrar protocol as defined by the DID Registration specification:
 * - POST /1.0/operations - Create, update, or deactivate DIDs
 * - GET /1.0/operations/{jobId} - Get status of long-running operations
 *
 * **Example Usage:**
 * ```kotlin
 * val registrar: DidRegistrar = KmsBasedRegistrar(kms)
 * val jobStorage: JobStorage = InMemoryJobStorage()
 * routing {
 *     configureUniversalRegistrarRoutes(registrar, jobStorage)
 * }
 * ```
 *
 * @param registrar The DID Registrar implementation to use for operations
 * @param jobStorage Storage for tracking long-running operations
 */
fun Routing.configureUniversalRegistrarRoutes(
    registrar: DidRegistrar,
    jobStorage: JobStorage
) {
    /**
     * POST /1.0/operations
     *
     * Handles DID creation, update, and deactivation operations.
     *
     * Request body format:
     * - Create: `{ "method": "web", "options": {...} }`
     * - Update: `{ "did": "did:web:example.com", "didDocument": {...}, "options": {...} }`
     * - Deactivate: `{ "did": "did:web:example.com", "operation": "deactivate", "options": {...} }`
     */
    post("/1.0/operations") {
        try {
            val requestBody = call.receive<JsonObject>()

            // Determine operation type
            val operation = requestBody["operation"]?.jsonPrimitive?.content
            val did = requestBody["did"]?.jsonPrimitive?.content
            val method = requestBody["method"]?.jsonPrimitive?.content

            val response = when {
                // Create operation
                method != null -> {
                    val optionsJson = requestBody["options"]?.jsonObject
                        ?: throw TrustWeaveException("Missing 'options' field for create operation")
                    val options = parseCreateDidOptions(optionsJson)
                    handleCreateOperation(registrar, method, options, jobStorage)
                }
                // Update operation
                did != null && requestBody["didDocument"] != null -> {
                    val didDocumentJson = requestBody["didDocument"]?.jsonObject
                        ?: throw TrustWeaveException("Missing 'didDocument' field for update operation")
                    val didDocument = parseDidDocument(didDocumentJson)
                    val optionsJson = requestBody["options"]?.jsonObject
                    val options = optionsJson?.let { parseUpdateDidOptions(it) } ?: UpdateDidOptions()
                    handleUpdateOperation(registrar, did, didDocument, options, jobStorage)
                }
                // Deactivate operation
                did != null && operation == "deactivate" -> {
                    val optionsJson = requestBody["options"]?.jsonObject
                    val options = optionsJson?.let { parseDeactivateDidOptions(it) } ?: DeactivateDidOptions()
                    handleDeactivateOperation(registrar, did, options, jobStorage)
                }
                else -> {
                    throw TrustWeaveException("Invalid request: must specify 'method' for create, 'did' and 'didDocument' for update, or 'did' and 'operation: deactivate' for deactivate")
                }
            }

            call.respond(HttpStatusCode.OK, response)
        } catch (e: TrustWeaveException) {
            call.respond(
                HttpStatusCode.BadRequest,
                createErrorResponse(e.message ?: "Invalid request")
            )
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                createErrorResponse("Internal server error: ${e.message}")
            )
        }
    }

    /**
     * GET /1.0/operations/{jobId}
     *
     * Gets the status of a long-running operation.
     */
    get("/1.0/operations/{jobId}") {
        try {
            val jobId = call.parameters["jobId"]
                ?: throw TrustWeaveException("Missing jobId parameter")

            val response = jobStorage.get(jobId)
                ?: throw TrustWeaveException("Job not found: $jobId")

            call.respond(HttpStatusCode.OK, response)
        } catch (e: TrustWeaveException) {
            call.respond(
                HttpStatusCode.NotFound,
                createErrorResponse(e.message ?: "Job not found")
            )
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                createErrorResponse("Internal server error: ${e.message}")
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

/**
 * Parses CreateDidOptions from JSON.
 */
private fun parseCreateDidOptions(json: JsonObject): CreateDidOptions {
    val jsonParser = Json { ignoreUnknownKeys = true }
    return jsonParser.decodeFromJsonElement(json)
}

/**
 * Parses UpdateDidOptions from JSON.
 */
private fun parseUpdateDidOptions(json: JsonObject): UpdateDidOptions {
    val jsonParser = Json { ignoreUnknownKeys = true }
    return jsonParser.decodeFromJsonElement(json)
}

/**
 * Parses DeactivateDidOptions from JSON.
 */
private fun parseDeactivateDidOptions(json: JsonObject): DeactivateDidOptions {
    val jsonParser = Json { ignoreUnknownKeys = true }
    return jsonParser.decodeFromJsonElement(json)
}

/**
 * Parses DidDocument from JSON.
 */
private fun parseDidDocument(json: JsonObject): DidDocument {
    val id = json["id"]?.jsonPrimitive?.content
        ?: throw TrustWeaveException("Missing DID id")

    val context = when {
        json["@context"] != null -> {
            when (val ctx = json["@context"]) {
                is JsonPrimitive -> listOf(ctx.content)
                is JsonArray -> ctx.mapNotNull { it.jsonPrimitive?.content }
                else -> listOf("https://www.w3.org/ns/did/v1")
            }
        }
        else -> listOf("https://www.w3.org/ns/did/v1")
    }

    val verificationMethod = json["verificationMethod"]?.jsonArray?.mapNotNull { vmJson ->
        val vmObj = vmJson.jsonObject
        val vmId = vmObj["id"]?.jsonPrimitive?.content
        val vmType = vmObj["type"]?.jsonPrimitive?.content
        val controller = vmObj["controller"]?.jsonPrimitive?.content ?: id
        val publicKeyJwk = vmObj["publicKeyJwk"]?.jsonObject?.let { jwk ->
            jwk.entries.associate { it.key to convertFromJsonElement(it.value) }
        }
        val publicKeyMultibase = vmObj["publicKeyMultibase"]?.jsonPrimitive?.content

        if (vmId != null && vmType != null) {
            com.trustweave.did.VerificationMethod(
                id = vmId,
                type = vmType,
                controller = controller,
                publicKeyJwk = publicKeyJwk,
                publicKeyMultibase = publicKeyMultibase
            )
        } else null
    } ?: emptyList()

    val authentication = json["authentication"]?.jsonArray?.mapNotNull { it.jsonPrimitive?.content }
        ?: json["authentication"]?.jsonPrimitive?.content?.let { listOf(it) }
        ?: emptyList()

    val assertionMethod = json["assertionMethod"]?.jsonArray?.mapNotNull { it.jsonPrimitive?.content }
        ?: json["assertionMethod"]?.jsonPrimitive?.content?.let { listOf(it) }
        ?: emptyList()

    val keyAgreement = json["keyAgreement"]?.jsonArray?.mapNotNull { it.jsonPrimitive?.content }
        ?: json["keyAgreement"]?.jsonPrimitive?.content?.let { listOf(it) }
        ?: emptyList()

    val capabilityInvocation = json["capabilityInvocation"]?.jsonArray?.mapNotNull { it.jsonPrimitive?.content }
        ?: json["capabilityInvocation"]?.jsonPrimitive?.content?.let { listOf(it) }
        ?: emptyList()

    val capabilityDelegation = json["capabilityDelegation"]?.jsonArray?.mapNotNull { it.jsonPrimitive?.content }
        ?: json["capabilityDelegation"]?.jsonPrimitive?.content?.let { listOf(it) }
        ?: emptyList()

    val service = json["service"]?.jsonArray?.mapNotNull { sJson ->
        val sObj = sJson.jsonObject
        val sId = sObj["id"]?.jsonPrimitive?.content
        val sType = sObj["type"]?.jsonPrimitive?.content
        val sEndpoint = sObj["serviceEndpoint"]

        if (sId != null && sType != null && sEndpoint != null) {
            val endpoint = convertFromJsonElement(sEndpoint) ?: return@mapNotNull null
            com.trustweave.did.DidService(
                id = sId,
                type = sType,
                serviceEndpoint = endpoint
            )
        } else null
    } ?: emptyList()

    return DidDocument(
        id = id,
        context = context,
        verificationMethod = verificationMethod,
        authentication = authentication,
        assertionMethod = assertionMethod,
        keyAgreement = keyAgreement,
        capabilityInvocation = capabilityInvocation,
        capabilityDelegation = capabilityDelegation,
        service = service
    )
}

/**
 * Converts JsonElement to Any? for DID Document parsing.
 */
private fun convertFromJsonElement(element: JsonElement): Any? {
    return when (element) {
        is JsonPrimitive -> {
            when {
                element.isString -> element.content
                element.booleanOrNull != null -> element.boolean
                element.longOrNull != null -> element.long
                element.doubleOrNull != null -> element.double
                else -> element.content
            }
        }
        is JsonArray -> element.map { convertFromJsonElement(it) }
        is JsonObject -> element.entries.associate { it.key to convertFromJsonElement(it.value) }
        is JsonNull -> null
    }
}

/**
 * Creates an error response.
 */
private fun createErrorResponse(message: String): JsonObject {
    return buildJsonObject {
        put("error", message)
        putJsonObject("didState") {
            put("state", "failed")
            put("reason", message)
        }
    }
}

