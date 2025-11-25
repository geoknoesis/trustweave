package com.trustweave.did.registrar.adapter

import com.trustweave.core.exception.TrustWeaveException
import com.trustweave.did.DidDocument
import com.trustweave.did.registrar.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Protocol adapter for standard Universal Registrar implementations.
 * 
 * Uses the standard endpoint pattern as defined by the Universal Registrar reference implementation:
 * - Operations: `POST /1.0/operations`
 * - Status: `GET /1.0/operations/{jobId}`
 * 
 * This is the pattern used by:
 * - dev.uniregistrar.io (public instance)
 * - Reference Universal Registrar implementation
 * - Most self-hosted Universal Registrar instances
 * 
 * **Example Usage:**
 * ```kotlin
 * val registrar = DefaultUniversalRegistrar(
 *     baseUrl = "https://dev.uniregistrar.io",
 *     protocolAdapter = StandardUniversalRegistrarAdapter()
 * )
 * ```
 */
class StandardUniversalRegistrarAdapter : UniversalRegistrarProtocolAdapter {
    
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    
    override suspend fun createDid(
        baseUrl: String,
        method: String,
        options: CreateDidOptions
    ): DidRegistrationResponse = withContext(Dispatchers.IO) {
        val url = "$baseUrl/1.0/operations"
        
        // Build request body according to spec
        val requestBody = buildJsonObject {
            put("method", method)
            putJsonObject("options") {
                // Key management mode
                put("keyManagementMode", options.keyManagementMode.name.lowercase())
                
                // Internal Secret Mode options
                if (options.keyManagementMode == KeyManagementMode.INTERNAL_SECRET) {
                    put("storeSecrets", options.storeSecrets)
                    put("returnSecrets", options.returnSecrets)
                }
                
                // External Secret Mode: provide secret
                val secret = options.secret
                if (options.keyManagementMode == KeyManagementMode.EXTERNAL_SECRET && secret != null) {
                    putJsonObject("secret") {
                        putSecret(this, secret)
                    }
                }
                
                // Pre-created document
                options.didDocument?.let { doc ->
                    putJsonObject("didDocument") {
                        putDidDocument(this, doc)
                    }
                }
                
                // Method-specific options
                options.methodSpecificOptions.forEach { (key, value) ->
                    put(key, value)
                }
            }
        }
        
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
            .timeout(Duration.ofSeconds(60))
            .build()
        
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        when (response.statusCode()) {
            200, 201, 202 -> {
                parseRegistrationResponse(response.body())
            }
            else -> {
                throw com.trustweave.core.exception.TrustWeaveException.Unknown(
                    message = "Failed to create DID: HTTP ${response.statusCode()}: ${response.body()}",
                    context = mapOf("statusCode" to response.statusCode(), "operation" to "create")
                )
            }
        }
    }
    
    override suspend fun updateDid(
        baseUrl: String,
        did: String,
        document: DidDocument,
        options: UpdateDidOptions
    ): DidRegistrationResponse = withContext(Dispatchers.IO) {
        val url = "$baseUrl/1.0/operations"
        
        val requestBody = buildJsonObject {
            put("did", did)
            putJsonObject("didDocument") {
                putDidDocument(this, document)
            }
            putJsonObject("options") {
                options.secret?.let { secret ->
                    putJsonObject("secret") {
                        putSecret(this, secret)
                    }
                }
                options.methodSpecificOptions.forEach { (key, value) ->
                    put(key, value)
                }
            }
        }
        
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
            .timeout(Duration.ofSeconds(60))
            .build()
        
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        when (response.statusCode()) {
            200, 201, 202 -> {
                parseRegistrationResponse(response.body())
            }
            else -> {
                throw com.trustweave.core.exception.TrustWeaveException.Unknown(
                    message = "Failed to update DID: HTTP ${response.statusCode()}: ${response.body()}",
                    context = mapOf("statusCode" to response.statusCode(), "operation" to "update")
                )
            }
        }
    }
    
    override suspend fun deactivateDid(
        baseUrl: String,
        did: String,
        options: DeactivateDidOptions
    ): DidRegistrationResponse = withContext(Dispatchers.IO) {
        val url = "$baseUrl/1.0/operations"
        
        val requestBody = buildJsonObject {
            put("did", did)
            put("operation", "deactivate")
            putJsonObject("options") {
                options.secret?.let { secret ->
                    putJsonObject("secret") {
                        putSecret(this, secret)
                    }
                }
                options.methodSpecificOptions.forEach { (key, value) ->
                    put(key, value)
                }
            }
        }
        
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
            .timeout(Duration.ofSeconds(60))
            .build()
        
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        when (response.statusCode()) {
            200, 201, 202 -> {
                parseRegistrationResponse(response.body())
            }
            else -> {
                throw com.trustweave.core.exception.TrustWeaveException.Unknown(
                    message = "Failed to deactivate DID: HTTP ${response.statusCode()}: ${response.body()}",
                    context = mapOf("statusCode" to response.statusCode(), "operation" to "deactivate")
                )
            }
        }
    }
    
    override suspend fun getOperationStatus(
        baseUrl: String,
        jobId: String
    ): DidRegistrationResponse = withContext(Dispatchers.IO) {
        val encodedJobId = URLEncoder.encode(jobId, "UTF-8")
        val url = "$baseUrl/1.0/operations/$encodedJobId"
        
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/json")
            .GET()
            .timeout(Duration.ofSeconds(30))
            .build()
        
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        when (response.statusCode()) {
            200 -> {
                parseRegistrationResponse(response.body())
            }
            else -> {
                throw com.trustweave.core.exception.TrustWeaveException.Unknown(
                    message = "Failed to get operation status: HTTP ${response.statusCode()}: ${response.body()}",
                    context = mapOf("statusCode" to response.statusCode(), "operation" to "getStatus", "jobId" to jobId)
                )
            }
        }
    }
    
    private fun parseRegistrationResponse(jsonString: String): DidRegistrationResponse {
        val json = Json { ignoreUnknownKeys = true }
        val jsonObject = json.parseToJsonElement(jsonString).jsonObject
        
        val jobId = jsonObject["jobId"]?.jsonPrimitive?.content
        val didStateJson = jsonObject["didState"]?.jsonObject
            ?: throw com.trustweave.core.exception.TrustWeaveException.InvalidJson(
                parseError = "Missing didState in response",
                jsonString = jsonString
            )
        
        val state = when (val stateStr = didStateJson["state"]?.jsonPrimitive?.content?.uppercase()) {
            "FINISHED" -> OperationState.FINISHED
            "FAILED" -> OperationState.FAILED
            "ACTION" -> OperationState.ACTION
            "WAIT" -> OperationState.WAIT
            else -> throw com.trustweave.core.exception.TrustWeaveException.InvalidState(
                message = "Invalid state: $stateStr",
                context = mapOf("state" to (stateStr ?: "null"))
            )
        }
        
        val did = didStateJson["did"]?.jsonPrimitive?.content
        val reason = didStateJson["reason"]?.jsonPrimitive?.content
        val secretJson = didStateJson["secret"]?.jsonObject
        val secret = secretJson?.let { parseSecret(it) }
        
        val didDocumentJson = didStateJson["didDocument"]?.jsonObject
        val didDocument = didDocumentJson?.let { parseDidDocumentFromJson(it) }
        
        val actionJson = didStateJson["action"]?.jsonObject
        val action = actionJson?.let { parseAction(it) }
        
        val didState = DidState(
            state = state,
            did = did,
            secret = secret,
            didDocument = didDocument,
            action = action,
            reason = reason
        )
        
        return DidRegistrationResponse(
            jobId = jobId,
            didState = didState
        )
    }
    
    private fun putSecret(builder: JsonObjectBuilder, secret: Secret) {
        secret.keys?.let { keys ->
            builder.putJsonArray("keys") {
                keys.forEach { key ->
                    add(buildJsonObject {
                        key.id?.let { put("id", it) }
                        key.type?.let { put("type", it) }
                        key.privateKeyJwk?.let { jwk ->
                            put("privateKeyJwk", jwk)
                        }
                        key.privateKeyMultibase?.let { put("privateKeyMultibase", it) }
                        key.additionalProperties?.forEach { entry ->
                            put(entry.key, entry.value)
                        }
                    })
                }
            }
        }
        secret.recoveryKey?.let { builder.put("recoveryKey", it) }
        secret.updateKey?.let { builder.put("updateKey", it) }
        secret.methodSpecificSecrets?.forEach { entry ->
            builder.put(entry.key, entry.value)
        }
    }
    
    private fun parseSecret(json: JsonObject): Secret {
        val keys = json["keys"]?.jsonArray?.mapNotNull { keyJson ->
            val keyObj = keyJson.jsonObject
            KeyMaterial(
                id = keyObj["id"]?.jsonPrimitive?.content,
                type = keyObj["type"]?.jsonPrimitive?.content,
                privateKeyJwk = keyObj["privateKeyJwk"]?.jsonObject,
                privateKeyMultibase = keyObj["privateKeyMultibase"]?.jsonPrimitive?.content,
                additionalProperties = keyObj.entries
                    .filter { it.key !in setOf("id", "type", "privateKeyJwk", "privateKeyMultibase") }
                    .associate { it.key to (it.value.jsonPrimitive?.content ?: "") }
            )
        }
        
        return Secret(
            keys = keys,
            recoveryKey = json["recoveryKey"]?.jsonPrimitive?.content,
            updateKey = json["updateKey"]?.jsonPrimitive?.content,
            methodSpecificSecrets = json.entries
                .filter { it.key !in setOf("keys", "recoveryKey", "updateKey") }
                .associate { it.key to (it.value.jsonPrimitive?.content ?: "") }
        )
    }
    
    private fun parseAction(json: JsonObject): Action {
        return Action(
            type = json["type"]?.jsonPrimitive?.content ?: "",
            url = json["url"]?.jsonPrimitive?.content,
            data = json["data"]?.jsonObject,
            description = json["description"]?.jsonPrimitive?.content
        )
    }
    
    private fun putDidDocument(builder: JsonObjectBuilder, document: DidDocument) {
        if (document.context.isNotEmpty()) {
            if (document.context.size == 1) {
                builder.put("@context", document.context[0])
            } else {
                builder.putJsonArray("@context") {
                    document.context.forEach { add(it) }
                }
            }
        }
        builder.put("id", document.id)
        
        if (document.verificationMethod.isNotEmpty()) {
            builder.putJsonArray("verificationMethod") {
                document.verificationMethod.forEach { vm ->
                    add(buildJsonObject {
                        put("id", vm.id)
                        put("type", vm.type)
                        put("controller", vm.controller)
                        vm.publicKeyJwk?.let { jwk ->
                            putJsonObject("publicKeyJwk") {
                                jwk.forEach { entry ->
                                    put(entry.key, convertToJsonElement(entry.value))
                                }
                            }
                        }
                        vm.publicKeyMultibase?.let { put("publicKeyMultibase", it) }
                    })
                }
            }
        }
        
        if (document.authentication.isNotEmpty()) {
            builder.putJsonArray("authentication") {
                document.authentication.forEach { add(it) }
            }
        }
        
        if (document.assertionMethod.isNotEmpty()) {
            builder.putJsonArray("assertionMethod") {
                document.assertionMethod.forEach { add(it) }
            }
        }
        
        if (document.keyAgreement.isNotEmpty()) {
            builder.putJsonArray("keyAgreement") {
                document.keyAgreement.forEach { add(it) }
            }
        }
        
        if (document.capabilityInvocation.isNotEmpty()) {
            builder.putJsonArray("capabilityInvocation") {
                document.capabilityInvocation.forEach { add(it) }
            }
        }
        
        if (document.capabilityDelegation.isNotEmpty()) {
            builder.putJsonArray("capabilityDelegation") {
                document.capabilityDelegation.forEach { add(it) }
            }
        }
        
        if (document.service.isNotEmpty()) {
            builder.putJsonArray("service") {
                document.service.forEach { s ->
                    add(buildJsonObject {
                        put("id", s.id)
                        put("type", s.type)
                        put("serviceEndpoint", convertToJsonElement(s.serviceEndpoint))
                    })
                }
            }
        }
    }
    
    private fun parseDidDocumentFromJson(json: JsonObject): DidDocument {
        val id = json["id"]?.jsonPrimitive?.content ?: throw com.trustweave.did.exception.DidException.InvalidDidFormat(
            did = "unknown",
            reason = "Missing DID id in document"
        )
        
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
}

