package org.trustweave.integrations.entra

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Client for the Verified ID `createPresentationRequest` endpoint and the asynchronous
 * webhook payloads it delivers back to the verifier.
 *
 * Spec: https://learn.microsoft.com/entra/verified-id/presentation-request-api
 */
class EntraPresentationClient(
    private val config: EntraConfig,
    private val tokenClient: EntraTokenClient,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    /**
     * Build the wire-format request body for a presentation request.
     */
    fun buildRequestBody(
        requestedCredentials: List<EntraRequestedCredential>,
        callbackUrl: String,
        state: String,
        clientName: String,
        purpose: String? = null,
        callbackHeaders: Map<String, String>? = null,
        includeQrCode: Boolean = true,
        includeReceipt: Boolean = false,
    ): EntraPresentationRequestBody {
        require(requestedCredentials.isNotEmpty()) {
            "At least one requestedCredential is required for presentation request"
        }
        return EntraPresentationRequestBody(
            authority = config.authorityDid,
            callback = EntraCallback(url = callbackUrl, state = state, headers = callbackHeaders),
            registration = EntraRegistration(clientName = clientName, purpose = purpose),
            includeQRCode = includeQrCode,
            includeReceipt = includeReceipt,
            requestedCredentials = requestedCredentials,
        )
    }

    fun encodeRequestBody(body: EntraPresentationRequestBody): String = json.encodeToString(
        EntraPresentationRequestBody.serializer(),
        body,
    )

    /**
     * Submit a `createPresentationRequest` to the Verified ID Request Service.
     */
    suspend fun createRequest(body: EntraPresentationRequestBody): EntraPresentationRequestResponse {
        val token = tokenClient.getAccessToken()
        return withContext(Dispatchers.IO) {
            val payload = encodeRequestBody(body).toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(config.presentationRequestUrl)
                .post(payload)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build()
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw EntraException.RequestServiceError(
                        httpStatus = response.code,
                        responseBody = responseBody,
                        message = "createPresentationRequest failed: HTTP ${response.code}",
                    )
                }
                runCatching {
                    json.decodeFromString(EntraPresentationRequestResponse.serializer(), responseBody)
                }.getOrElse {
                    throw EntraException.MalformedResponse(
                        message = "Could not parse createPresentationRequest response: ${it.message}",
                        cause = it,
                    )
                }
            }
        }
    }

    /**
     * Parse a webhook callback payload delivered by Entra Verified ID to the caller's webhook.
     *
     * Callers should validate transport security (mTLS, signature header, etc.) before invoking
     * this method; payload parsing alone does not authenticate the source.
     */
    fun parseCallbackPayload(rawJson: String): EntraCallbackPayload {
        val payload = runCatching {
            json.decodeFromString(EntraCallbackPayload.serializer(), rawJson)
        }.getOrElse {
            throw EntraException.MalformedResponse(
                message = "Could not parse Entra callback payload: ${it.message}",
                cause = it,
            )
        }
        payload.error?.let { err ->
            throw EntraException.CallbackError(
                errorCode = err.code,
                message = "Entra callback reported error '${err.code}': ${err.message}",
                context = mapOf(
                    "requestId" to payload.requestId,
                    "requestStatus" to payload.requestStatus,
                    "state" to (payload.state ?: ""),
                ),
            )
        }
        return payload
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
