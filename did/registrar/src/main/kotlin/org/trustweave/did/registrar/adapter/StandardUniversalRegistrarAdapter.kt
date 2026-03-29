package org.trustweave.did.registrar.adapter

import org.trustweave.core.exception.SerializationException
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.model.DidDocument
import org.trustweave.did.parser.DidDocumentJsonParser
import org.trustweave.did.representation.DidDocumentJsonProducer
import org.trustweave.did.identifiers.Did
import org.trustweave.did.registrar.model.*
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
                throw org.trustweave.core.exception.TrustWeaveException.Unknown(
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
                throw org.trustweave.core.exception.TrustWeaveException.Unknown(
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
                throw org.trustweave.core.exception.TrustWeaveException.Unknown(
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
                throw org.trustweave.core.exception.TrustWeaveException.Unknown(
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
            ?: throw SerializationException.InvalidJson(
                parseError = "Missing didState in response",
                jsonString = jsonString
            )

        val state = when (val stateStr = didStateJson["state"]?.jsonPrimitive?.content?.uppercase()) {
            "FINISHED" -> OperationState.FINISHED
            "FAILED" -> OperationState.FAILED
            "ACTION" -> OperationState.ACTION
            "WAIT" -> OperationState.WAIT
            else -> throw org.trustweave.core.exception.TrustWeaveException.InvalidState(
                message = "Invalid state: $stateStr",
                context = mapOf("state" to (stateStr ?: "null"))
            )
        }

        val did = didStateJson["did"]?.jsonPrimitive?.content
        val reason = didStateJson["reason"]?.jsonPrimitive?.content
        val secretJson = didStateJson["secret"]?.jsonObject
        val secret = secretJson?.let { parseSecret(it) }

        val didDocumentJson = didStateJson["didDocument"]?.jsonObject
        val didDocument: DidDocument? = didDocumentJson?.let { parseDidDocumentFromJson(it) }

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
        val docJson = DidDocumentJsonProducer.toJsonObject(document, useV1_1Context = true)
        docJson.forEach { (key, value) -> builder.put(key, value) }
    }

    private fun parseDidDocumentFromJson(json: JsonObject): DidDocument {
        return DidDocumentJsonParser.parse(json)
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

}

