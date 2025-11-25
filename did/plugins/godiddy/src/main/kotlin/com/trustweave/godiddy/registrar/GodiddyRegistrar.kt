package com.trustweave.godiddy.registrar

import com.trustweave.core.exception.TrustWeaveException
import com.trustweave.did.DidDocument
import com.trustweave.did.registrar.DidRegistrar
import com.trustweave.did.registrar.model.*
import com.trustweave.godiddy.GodiddyClient
import com.trustweave.godiddy.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/**
 * Client for godiddy Universal Registrar service.
 * 
 * Implements the [DidRegistrar] interface to provide DID lifecycle operations
 * via the Universal Registrar protocol according to the DID Registration specification.
 */
class GodiddyRegistrar(
    private val client: GodiddyClient
) : DidRegistrar {
    /**
     * Creates a new DID using Universal Registrar according to DID Registration spec.
     *
     * @param method The DID method name (e.g., "web", "key")
     * @param options Creation options according to DID Registration spec
     * @return Registration response with jobId and didState
     */
    override suspend fun createDid(method: String, options: CreateDidOptions): DidRegistrationResponse = withContext(Dispatchers.IO) {
        try {
            // Universal Registrar endpoint: POST /1.0/operations
            val path = "/1.0/operations"
            
            // Convert CreateDidOptions to JsonElement map for Godiddy API
            val jsonOptions = buildMap<String, JsonElement> {
                put("keyManagementMode", JsonPrimitive(options.keyManagementMode.name.lowercase()))
                if (options.keyManagementMode == KeyManagementMode.INTERNAL_SECRET) {
                    put("storeSecrets", JsonPrimitive(options.storeSecrets))
                    put("returnSecrets", JsonPrimitive(options.returnSecrets))
                }
                options.secret?.let { secret ->
                    put("secret", convertSecretToJson(secret))
                }
                options.didDocument?.let { doc ->
                    put("didDocument", convertDidDocumentToJson(doc))
                }
                options.methodSpecificOptions.forEach { (key, value) ->
                    put(key, value)
                }
            }
            
            val request = GodiddyCreateDidRequest(
                method = method,
                options = jsonOptions
            )
            
            val response: GodiddyCreateDidResponse = client.post(path, request)
            
            // Convert Godiddy response to spec-compliant DidRegistrationResponse
            val didState = if (response.did != null && response.didDocument != null) {
                val docJson = response.didDocument.jsonObject
                val didDocument = convertToDidDocument(docJson, response.did)
                DidState(
                    state = OperationState.FINISHED,
                    did = response.did,
                    didDocument = didDocument
                )
            } else if (response.jobId != null) {
                // Long-running operation
                DidState(
                    state = OperationState.WAIT,
                    did = null,
                    didDocument = null
                )
            } else {
                DidState(
                    state = OperationState.FAILED,
                    did = null,
                    didDocument = null,
                    reason = response.error ?: "DID creation failed"
                )
            }
            
            DidRegistrationResponse(
                jobId = response.jobId,
                didState = didState
            )
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: Exception) {
            throw com.trustweave.core.exception.TrustWeaveException.Unknown(
                message = "Failed to create DID with method $method: ${e.message ?: "Unknown error"}",
                context = mapOf("method" to method),
                cause = e
            )
        }
    }
    
    /**
     * Updates a DID Document using Universal Registrar according to DID Registration spec.
     *
     * @param did The DID to update
     * @param document The updated DID Document
     * @param options Update options according to DID Registration spec
     * @return Registration response with jobId and didState
     */
    override suspend fun updateDid(
        did: String,
        document: DidDocument,
        options: UpdateDidOptions
    ): DidRegistrationResponse = withContext(Dispatchers.IO) {
        try {
            // Universal Registrar endpoint: POST /1.0/operations (with update operation)
            val path = "/1.0/operations"
            
            // Convert DidDocument to JsonObject
            val docJson = convertDidDocumentToJson(document)
            
            // Convert UpdateDidOptions to JsonElement map
            val jsonOptions = buildMap<String, JsonElement> {
                options.secret?.let { secret ->
                    put("secret", convertSecretToJson(secret))
                }
                options.methodSpecificOptions.forEach { (key, value) ->
                    put(key, value)
                }
            }
            
            val request = GodiddyUpdateDidRequest(
                did = did,
                didDocument = docJson,
                options = jsonOptions
            )
            
            val response: GodiddyOperationResponse = client.post(path, request)
            
            // Convert Godiddy response to spec-compliant DidRegistrationResponse
            val didState = if (response.success && response.didDocument != null) {
                val updatedDocJson = response.didDocument.jsonObject
                val updatedDocument = convertToDidDocument(updatedDocJson, did)
                DidState(
                    state = OperationState.FINISHED,
                    did = did,
                    didDocument = updatedDocument
                )
            } else if (response.jobId != null) {
                DidState(
                    state = OperationState.WAIT,
                    did = did,
                    didDocument = null
                )
            } else {
                DidState(
                    state = OperationState.FAILED,
                    did = did,
                    didDocument = null
                )
            }
            
            DidRegistrationResponse(
                jobId = response.jobId,
                didState = didState
            )
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: Exception) {
            throw com.trustweave.core.exception.TrustWeaveException.Unknown(
                message = "Failed to update DID $did: ${e.message ?: "Unknown error"}",
                context = mapOf("did" to did),
                cause = e
            )
        }
    }
    
    /**
     * Deactivates a DID using Universal Registrar according to DID Registration spec.
     *
     * @param did The DID to deactivate
     * @param options Deactivation options according to DID Registration spec
     * @return Registration response with jobId and didState
     */
    override suspend fun deactivateDid(
        did: String,
        options: DeactivateDidOptions
    ): DidRegistrationResponse = withContext(Dispatchers.IO) {
        try {
            // Universal Registrar endpoint: POST /1.0/operations (with deactivate operation)
            val path = "/1.0/operations"
            
            // Convert DeactivateDidOptions to JsonElement map
            val jsonOptions = buildMap<String, JsonElement> {
                options.secret?.let { secret ->
                    put("secret", convertSecretToJson(secret))
                }
                options.methodSpecificOptions.forEach { (key, value) ->
                    put(key, value)
                }
            }
            
            val request = GodiddyDeactivateDidRequest(
                did = did,
                options = jsonOptions
            )
            
            val response: GodiddyOperationResponse = client.post(path, request)
            
            // Convert Godiddy response to spec-compliant DidRegistrationResponse
            val didState = if (response.success) {
                DidState(
                    state = OperationState.FINISHED,
                    did = did,
                    didDocument = null
                )
            } else if (response.jobId != null) {
                DidState(
                    state = OperationState.WAIT,
                    did = did,
                    didDocument = null
                )
            } else {
                DidState(
                    state = OperationState.FAILED,
                    did = did,
                    didDocument = null
                )
            }
            
            DidRegistrationResponse(
                jobId = response.jobId,
                didState = didState
            )
        } catch (e: Exception) {
            throw com.trustweave.core.exception.TrustWeaveException.Unknown(
                message = "Failed to deactivate DID $did: ${e.message ?: "Unknown error"}",
                context = mapOf("did" to did),
                cause = e
            )
        }
    }
    
    /**
     * Converts Secret to JsonObject for API requests.
     */
    private fun convertSecretToJson(secret: Secret): JsonObject {
        return buildJsonObject {
            secret.keys?.let { keys ->
                putJsonArray("keys") {
                    keys.forEach { key ->
                        add(buildJsonObject {
                            key.id?.let { put("id", it) }
                            key.type?.let { put("type", it) }
                            key.privateKeyJwk?.let { jwk ->
                                putJsonObject("privateKeyJwk") {
                                    jwk.forEach { (k, v) ->
                                        put(k, convertToJsonElement(v))
                                    }
                                }
                            }
                            key.privateKeyMultibase?.let { put("privateKeyMultibase", it) }
                            key.additionalProperties?.forEach { (k, v) ->
                                put(k, v)
                            }
                        })
                    }
                }
            }
            secret.recoveryKey?.let { put("recoveryKey", it) }
            secret.updateKey?.let { put("updateKey", it) }
            secret.methodSpecificSecrets?.forEach { (k, v) ->
                put(k, v)
            }
        }
    }
    
    /**
     * Converts Any to JsonElement.
     */
    private fun convertToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value.toDouble())
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> -> JsonObject(value.mapKeys { it.key.toString() }
                .mapValues { convertToJsonElement(it.value) })
            is List<*> -> JsonArray(value.map { convertToJsonElement(it) })
            else -> JsonPrimitive(value.toString())
        }
    }
    
    /**
     * Converts DidDocument to JsonObject.
     */
    private fun convertDidDocumentToJson(document: DidDocument): JsonObject {
        return buildJsonObject {
            // Add @context (JSON-LD context)
            if (document.context.isNotEmpty()) {
                if (document.context.size == 1) {
                    put("@context", document.context[0])
                } else {
                    put("@context", buildJsonArray {
                        document.context.forEach { add(it) }
                    })
                }
            }
            
            put("id", document.id)
            
            if (document.verificationMethod.isNotEmpty()) {
                put("verificationMethod", buildJsonArray {
                    document.verificationMethod.forEach { vm ->
                        add(buildJsonObject {
                            put("id", vm.id)
                            put("type", vm.type)
                            put("controller", vm.controller)
                            vm.publicKeyJwk?.let { put("publicKeyJwk", JsonObject(it.mapValues { convertToJsonElement(it.value) })) }
                            vm.publicKeyMultibase?.let { put("publicKeyMultibase", it) }
                        })
                    }
                })
            }
            if (document.authentication.isNotEmpty()) {
                put("authentication", buildJsonArray {
                    document.authentication.forEach { add(it) }
                })
            }
            if (document.assertionMethod.isNotEmpty()) {
                put("assertionMethod", buildJsonArray {
                    document.assertionMethod.forEach { add(it) }
                })
            }
            if (document.keyAgreement.isNotEmpty()) {
                put("keyAgreement", buildJsonArray {
                    document.keyAgreement.forEach { add(it) }
                })
            }
            if (document.capabilityInvocation.isNotEmpty()) {
                put("capabilityInvocation", buildJsonArray {
                    document.capabilityInvocation.forEach { add(it) }
                })
            }
            if (document.capabilityDelegation.isNotEmpty()) {
                put("capabilityDelegation", buildJsonArray {
                    document.capabilityDelegation.forEach { add(it) }
                })
            }
            if (document.service.isNotEmpty()) {
                put("service", buildJsonArray {
                    document.service.forEach { s ->
                        add(buildJsonObject {
                            put("id", s.id)
                            put("type", s.type)
                            put("serviceEndpoint", convertToJsonElement(s.serviceEndpoint))
                        })
                    }
                })
            }
        }
    }
    
    /**
     * Converts JsonObject to DidDocument.
     */
    private fun convertToDidDocument(json: JsonObject, did: String): DidDocument {
        val id = json["id"]?.jsonPrimitive?.content ?: did
        
        // Extract @context (can be string or array in JSON-LD)
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
                jwk.entries.associate { it.key to convertJsonElement(it.value) }
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
                val endpoint = convertJsonElement(sEndpoint) ?: return@mapNotNull null
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
     * Converts JsonElement to Any.
     */
    private fun convertJsonElement(element: JsonElement): Any? {
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
            is JsonArray -> element.map { convertJsonElement(it) }
            is JsonObject -> element.entries.associate { it.key to convertJsonElement(it.value) }
            is JsonNull -> null
        }
    }
}

