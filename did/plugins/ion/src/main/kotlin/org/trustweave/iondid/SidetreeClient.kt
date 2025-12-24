package org.trustweave.iondid

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidService
import org.trustweave.did.model.VerificationMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Client for interacting with Sidetree protocol (used by ION).
 *
 * Implements Sidetree operations:
 * - Create: Create a new DID
 * - Update: Update an existing DID
 * - Recover: Recover a DID with new keys
 * - Deactivate: Deactivate a DID
 *
 * Operations are submitted to ION nodes which batch and anchor them to Bitcoin.
 */
class SidetreeClient(
    private val httpClient: OkHttpClient,
    private val config: IonDidConfig
) {

    /**
     * Response from submitting a Sidetree operation.
     */
    data class OperationResponse(
        val longFormDid: String?,
        val operationHash: String?,
        val success: Boolean,
        val error: String? = null
    )

    /**
     * Response from resolving a DID.
     */
    data class ResolutionResponse(
        val document: JsonObject?,
        val metadata: Map<String, Any?>?,
        val success: Boolean,
        val error: String? = null
    )

    /**
     * Creates a Sidetree create operation.
     */
    suspend fun createCreateOperation(
        publicKeyJwk: Map<String, Any?>
    ): JsonObject = withContext(Dispatchers.IO) {
        // Build Sidetree create operation payload
        // In a full implementation, this would follow Sidetree protocol spec
        buildJsonObject {
            put("type", "create")
            put("suffixData", buildJsonObject {
                put("deltaHash", "placeholder") // Would compute from delta
                put("recoveryCommitment", "placeholder") // Would compute from recovery key
            })
            put("delta", buildJsonObject {
                put("patches", buildJsonArray {
                    add(buildJsonObject {
                        put("action", "replace")
                        put("document", buildJsonObject {
                            put("publicKeys", buildJsonArray {
                                add(buildJsonObject {
                                    put("id", "key-1")
                                    put("type", "JsonWebKey2020")
                                    put("publicKeyJwk", mapToJsonObject(publicKeyJwk))
                                })
                            })
                        })
                    })
                })
            })
        }
    }

    /**
     * Creates a Sidetree update operation.
     */
    suspend fun createUpdateOperation(
        did: String,
        previousOperationHash: String,
        updatedDocument: DidDocument
    ): JsonObject = withContext(Dispatchers.IO) {
        buildJsonObject {
            put("type", "update")
            put("didSuffix", extractDidSuffix(did))
            put("revealValue", "placeholder") // Would compute from update key
            put("delta", buildJsonObject {
                put("patches", buildJsonArray {
                    add(buildJsonObject {
                        put("action", "replace")
                        put("document", documentToJsonObject(updatedDocument))
                    })
                })
            })
        }
    }

    /**
     * Creates a Sidetree deactivate operation.
     */
    suspend fun createDeactivateOperation(
        did: String,
        previousOperationHash: String
    ): JsonObject = withContext(Dispatchers.IO) {
        buildJsonObject {
            put("type", "deactivate")
            put("didSuffix", extractDidSuffix(did))
            put("revealValue", "placeholder") // Would compute from recovery key
        }
    }

    /**
     * Submits a Sidetree operation to an ION node.
     */
    suspend fun submitOperation(operation: JsonObject): OperationResponse = withContext(Dispatchers.IO) {
        try {
            val url = "${config.ionNodeUrl}/operations"
            val json = Json.encodeToString(JsonObject.serializer(), operation)
            val mediaType = "application/json".toMediaType()
            val body = json.toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                return@withContext OperationResponse(
                    longFormDid = null,
                    operationHash = null,
                    success = false,
                    error = "HTTP ${response.code}: $errorBody"
                )
            }

            val responseBody = response.body?.string() ?: "{}"
            val responseJson = Json.parseToJsonElement(responseBody).jsonObject

            // Extract long-form DID and operation hash from response
            val longFormDid = responseJson["didDocument"]?.jsonObject?.get("id")?.jsonPrimitive?.content
            val operationHash = responseJson["operationHash"]?.jsonPrimitive?.content

            OperationResponse(
                longFormDid = longFormDid,
                operationHash = operationHash,
                success = true
            )
        } catch (e: IOException) {
            OperationResponse(
                longFormDid = null,
                operationHash = null,
                success = false,
                error = "Network error: ${e.message}"
            )
        } catch (e: Exception) {
            OperationResponse(
                longFormDid = null,
                operationHash = null,
                success = false,
                error = "Error submitting operation: ${e.message}"
            )
        }
    }

    /**
     * Resolves a DID through ION node.
     */
    suspend fun resolveDid(did: String): ResolutionResponse = withContext(Dispatchers.IO) {
        try {
            val url = "${config.ionNodeUrl}/identifiers/$did"

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                if (response.code == 404) {
                    return@withContext ResolutionResponse(
                        document = null,
                        metadata = null,
                        success = false,
                        error = "DID not found"
                    )
                }

                val errorBody = response.body?.string() ?: "Unknown error"
                return@withContext ResolutionResponse(
                    document = null,
                    metadata = null,
                    success = false,
                    error = "HTTP ${response.code}: $errorBody"
                )
            }

            val responseBody = response.body?.string() ?: "{}"
            val responseJson = Json.parseToJsonElement(responseBody).jsonObject

            val document = responseJson["didDocument"]?.jsonObject
            val metadata = responseJson["didDocumentMetadata"]?.jsonObject?.let { jsonObjectToMap(it) }

            ResolutionResponse(
                document = document,
                metadata = metadata,
                success = true
            )
        } catch (e: IOException) {
            ResolutionResponse(
                document = null,
                metadata = null,
                success = false,
                error = "Network error: ${e.message}"
            )
        } catch (e: Exception) {
            ResolutionResponse(
                document = null,
                metadata = null,
                success = false,
                error = "Error resolving DID: ${e.message}"
            )
        }
    }

    /**
     * Extracts DID suffix from a did:ion identifier.
     */
    private fun extractDidSuffix(did: String): String {
        // For did:ion:{suffix}, extract the suffix part
        if (!did.startsWith("did:ion:")) {
            throw IllegalArgumentException("Invalid did:ion format: $did")
        }

        val suffix = did.substringAfter("did:ion:")

        // Handle long-form DID (contains additional data)
        val colonIndex = suffix.indexOf(':')
        return if (colonIndex >= 0) {
            // Long-form: extract just the suffix part
            suffix.substringBefore(':')
        } else {
            // Short-form: use whole suffix
            suffix
        }
    }

    /**
     * Converts DidDocument to JsonObject for Sidetree operations.
     */
    private fun documentToJsonObject(document: DidDocument): JsonObject {
        return buildJsonObject {
            put("@context", JsonArray(document.context.map { JsonPrimitive(it) }))
            put("id", JsonPrimitive(document.id.value))

            if (document.verificationMethod.isNotEmpty()) {
                put("verificationMethod", JsonArray(document.verificationMethod.map { vmToJsonObject(it) }))
            }
            if (document.authentication.isNotEmpty()) {
                put("authentication", JsonArray(document.authentication.map { JsonPrimitive(it.value) }))
            }
            if (document.assertionMethod.isNotEmpty()) {
                put("assertionMethod", JsonArray(document.assertionMethod.map { JsonPrimitive(it.value) }))
            }
            if (document.service.isNotEmpty()) {
                put("service", JsonArray(document.service.map { serviceToJsonObject(it) }))
            }
        }
    }

    private fun vmToJsonObject(vm: VerificationMethod): JsonObject {
        return buildJsonObject {
            put("id", JsonPrimitive(vm.id.value))
            put("type", JsonPrimitive(vm.type))
            put("controller", JsonPrimitive(vm.controller.value))
            vm.publicKeyJwk?.let { jwk ->
                put("publicKeyJwk", mapToJsonObject(jwk))
            }
            vm.publicKeyMultibase?.let {
                put("publicKeyMultibase", JsonPrimitive(it))
            }
        }
    }

    private fun serviceToJsonObject(service: DidService): JsonObject {
        return buildJsonObject {
            put("id", service.id)
            put("type", service.type)
            put("serviceEndpoint", when (val endpoint = service.serviceEndpoint) {
                is String -> JsonPrimitive(endpoint)
                else -> Json.parseToJsonElement(endpoint.toString())
            })
        }
    }

    private fun mapToJsonObject(map: Map<String, Any?>): JsonObject {
        return buildJsonObject {
            map.forEach { (key, value) ->
                when (value) {
                    null -> put(key, JsonNull)
                    is String -> put(key, value)
                    is Number -> put(key, value)
                    is Boolean -> put(key, value)
                    is Map<*, *> -> put(key, mapToJsonObject(value as Map<String, Any?>))
                    is List<*> -> put(key, JsonArray(value.map {
                        when (it) {
                            is String -> JsonPrimitive(it)
                            is Number -> JsonPrimitive(it)
                            is Boolean -> JsonPrimitive(it)
                            else -> JsonPrimitive(it.toString())
                        }
                    }))
                    else -> put(key, value.toString())
                }
            }
        }
    }

    private fun jsonObjectToMap(obj: JsonObject): Map<String, Any?> {
        return obj.entries.associate { (key, value) ->
            key to when (value) {
                is JsonPrimitive -> value.contentOrNull ?: value.booleanOrNull ?: value.longOrNull ?: value.doubleOrNull ?: value.toString()
                is JsonObject -> jsonObjectToMap(value)
                is JsonArray -> value.map { (it as? JsonPrimitive)?.content ?: it.toString() }
                else -> value.toString()
            }
        }
    }
}

