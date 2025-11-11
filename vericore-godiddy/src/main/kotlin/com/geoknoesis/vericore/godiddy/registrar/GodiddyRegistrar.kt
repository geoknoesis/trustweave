package com.geoknoesis.vericore.godiddy.registrar

import com.geoknoesis.vericore.core.VeriCoreException
import com.geoknoesis.vericore.did.DidCreationOptions
import com.geoknoesis.vericore.did.DidDocument
import com.geoknoesis.vericore.godiddy.GodiddyClient
import com.geoknoesis.vericore.godiddy.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/**
 * Client for godiddy Universal Registrar service.
 */
class GodiddyRegistrar(
    private val client: GodiddyClient
) {
    /**
     * Creates a new DID using Universal Registrar.
     *
     * @param method The DID method name (e.g., "web", "key")
     * @param options Method-specific options for DID creation
     * @return The created DID Document
     */
    suspend fun createDid(method: String, options: DidCreationOptions): DidDocument = withContext(Dispatchers.IO) {
        try {
            // Universal Registrar endpoint: POST /1.0/operations
            val path = "/1.0/operations"
            
            // Convert options to JsonElement map
            val jsonOptions = options.toMap().mapValues { (_, value) ->
                when (value) {
                    is String -> JsonPrimitive(value)
                    is Number -> JsonPrimitive(value.toDouble())
                    is Boolean -> JsonPrimitive(value)
                    is Map<*, *> -> JsonObject(value.mapKeys { it.key.toString() }
                        .mapValues { convertToJsonElement(it.value) })
                    is List<*> -> JsonArray(value.map { convertToJsonElement(it) })
                    else -> JsonNull
                }
            }
            
            val request = GodiddyCreateDidRequest(
                method = method,
                options = jsonOptions
            )
            
            val response: GodiddyCreateDidResponse = client.post(path, request)
            
            if (response.did == null || response.didDocument == null) {
                throw VeriCoreException("Failed to create DID: ${response.jobId ?: "unknown error"}")
            }
            
            // Convert response to DidDocument
            val docJson = response.didDocument.jsonObject
            convertToDidDocument(docJson, response.did)
        } catch (e: VeriCoreException) {
            throw e
        } catch (e: Exception) {
            throw VeriCoreException("Failed to create DID with method $method: ${e.message}", e)
        }
    }
    
    /**
     * Updates a DID Document using Universal Registrar.
     *
     * @param did The DID to update
     * @param document The updated DID Document
     * @return The updated DID Document
     */
    suspend fun updateDid(did: String, document: DidDocument): DidDocument = withContext(Dispatchers.IO) {
        try {
            // Universal Registrar endpoint: POST /1.0/operations (with update operation)
            val path = "/1.0/operations"
            
            // Convert DidDocument to JsonObject
            val docJson = convertDidDocumentToJson(document)
            
            val request = GodiddyUpdateDidRequest(
                did = did,
                didDocument = docJson,
                options = emptyMap()
            )
            
            val response: GodiddyOperationResponse = client.post(path, request)
            
            if (!response.success || response.didDocument == null) {
                throw VeriCoreException("Failed to update DID $did: ${response.error ?: "unknown error"}")
            }
            
            // Convert response back to DidDocument
            val updatedDocJson = response.didDocument.jsonObject
            convertToDidDocument(updatedDocJson, did)
        } catch (e: VeriCoreException) {
            throw e
        } catch (e: Exception) {
            throw VeriCoreException("Failed to update DID $did: ${e.message}", e)
        }
    }
    
    /**
     * Deactivates a DID using Universal Registrar.
     *
     * @param did The DID to deactivate
     * @return true if the DID was successfully deactivated
     */
    suspend fun deactivateDid(did: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Universal Registrar endpoint: POST /1.0/operations (with deactivate operation)
            val path = "/1.0/operations"
            
            val request = GodiddyDeactivateDidRequest(
                did = did,
                options = emptyMap()
            )
            
            val response: GodiddyOperationResponse = client.post(path, request)
            response.success
        } catch (e: Exception) {
            throw VeriCoreException("Failed to deactivate DID $did: ${e.message}", e)
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
                com.geoknoesis.vericore.did.VerificationMethodRef(
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
                com.geoknoesis.vericore.did.Service(
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

