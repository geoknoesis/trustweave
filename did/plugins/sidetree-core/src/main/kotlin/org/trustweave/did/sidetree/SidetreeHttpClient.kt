package org.trustweave.did.sidetree

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Thin Sidetree REST transport: submits operations and resolves DIDs against a
 * Sidetree-conformant node (ION operator, Orb operator, ...).
 *
 * Paths come from the [SidetreeMethodSpec]; the [baseUrl] is the configured node
 * endpoint without a trailing slash.
 */
class SidetreeHttpClient(
    private val httpClient: OkHttpClient,
    private val baseUrl: String,
    private val methodSpec: SidetreeMethodSpec,
    private val authHeader: Pair<String, String>? = null,
) {

    data class OperationResponse(
        val did: String?,
        val rawBody: String,
        val success: Boolean,
        val httpStatus: Int,
        val error: String? = null,
    )

    data class ResolutionResponse(
        val document: JsonObject?,
        val metadata: JsonObject?,
        val success: Boolean,
        val httpStatus: Int,
        val error: String? = null,
    )

    suspend fun submitOperation(operation: JsonObject): OperationResponse = withContext(Dispatchers.IO) {
        val body = Json.encodeToString(JsonObject.serializer(), operation).toRequestBody(JSON_MEDIA_TYPE)
        val builder = Request.Builder()
            .url("${baseUrl.trimEnd('/')}${methodSpec.operationsPath}")
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
        authHeader?.let { (name, value) -> builder.addHeader(name, value) }

        try {
            httpClient.newCall(builder.build()).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext OperationResponse(
                        did = null,
                        rawBody = responseBody,
                        success = false,
                        httpStatus = response.code,
                        error = "HTTP ${response.code}: $responseBody",
                    )
                }
                val didFromBody = runCatching {
                    val element = if (responseBody.isBlank()) JsonNull else Json.parseToJsonElement(responseBody)
                    (element as? JsonObject)?.get("didDocument")?.jsonObject
                        ?.get("id")?.jsonPrimitive?.contentOrNull
                }.getOrNull()
                OperationResponse(
                    did = didFromBody,
                    rawBody = responseBody,
                    success = true,
                    httpStatus = response.code,
                )
            }
        } catch (e: IOException) {
            OperationResponse(
                did = null,
                rawBody = "",
                success = false,
                httpStatus = -1,
                error = "Network error: ${e.message}",
            )
        }
    }

    suspend fun resolveDid(did: String): ResolutionResponse = withContext(Dispatchers.IO) {
        val builder = Request.Builder()
            .url("${baseUrl.trimEnd('/')}${methodSpec.identifiersPath}/$did")
            .get()
            .addHeader("Accept", "application/json")
        authHeader?.let { (name, value) -> builder.addHeader(name, value) }

        try {
            httpClient.newCall(builder.build()).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.code == 404) {
                    return@withContext ResolutionResponse(
                        document = null,
                        metadata = null,
                        success = false,
                        httpStatus = 404,
                        error = "DID not found",
                    )
                }
                if (!response.isSuccessful) {
                    return@withContext ResolutionResponse(
                        document = null,
                        metadata = null,
                        success = false,
                        httpStatus = response.code,
                        error = "HTTP ${response.code}: $responseBody",
                    )
                }
                val parsed = runCatching { Json.parseToJsonElement(responseBody) as? JsonObject }.getOrNull()
                    ?: return@withContext ResolutionResponse(
                        document = null,
                        metadata = null,
                        success = false,
                        httpStatus = response.code,
                        error = "Invalid JSON response from Sidetree node",
                    )
                ResolutionResponse(
                    document = parsed["didDocument"] as? JsonObject,
                    metadata = parsed["didDocumentMetadata"] as? JsonObject,
                    success = parsed["didDocument"] is JsonObject,
                    httpStatus = response.code,
                )
            }
        } catch (e: IOException) {
            ResolutionResponse(
                document = null,
                metadata = null,
                success = false,
                httpStatus = -1,
                error = "Network error: ${e.message}",
            )
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
