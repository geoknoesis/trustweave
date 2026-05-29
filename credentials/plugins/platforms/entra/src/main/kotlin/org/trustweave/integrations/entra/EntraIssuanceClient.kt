package org.trustweave.integrations.entra

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Client for the Verified ID `createIssuanceRequest` endpoint.
 *
 * Submits a request describing the credential type, manifest URL, callback URL, and any
 * claims to embed; returns the request URL (deep link) and QR-code payload that the
 * wallet uses to retrieve the issuance request.
 *
 * Spec: https://learn.microsoft.com/entra/verified-id/issuance-request-api
 */
class EntraIssuanceClient(
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
     * Build the wire-format request body for a credential issuance request.
     *
     * Public so callers can preview/serialize it; the [createRequest] method also accepts
     * a pre-built body for advanced use cases.
     */
    fun buildRequestBody(
        credentialType: String,
        manifestUrl: String,
        callbackUrl: String,
        state: String,
        clientName: String,
        claims: Map<String, String>? = null,
        purpose: String? = null,
        pin: EntraPin? = null,
        callbackHeaders: Map<String, String>? = null,
        includeQrCode: Boolean = true,
    ): EntraIssuanceRequestBody = EntraIssuanceRequestBody(
        authority = config.authorityDid,
        callback = EntraCallback(url = callbackUrl, state = state, headers = callbackHeaders),
        registration = EntraRegistration(clientName = clientName, purpose = purpose),
        type = credentialType,
        manifest = manifestUrl,
        claims = claims,
        pin = pin,
        includeQRCode = includeQrCode,
    )

    /**
     * Serialize [body] to JSON in the exact wire format expected by Entra Verified ID.
     */
    fun encodeRequestBody(body: EntraIssuanceRequestBody): String = json.encodeToString(
        EntraIssuanceRequestBody.serializer(),
        body,
    )

    /**
     * Submit a `createIssuanceRequest` to the Verified ID Request Service and return the
     * issuance request URL plus QR-code payload.
     */
    suspend fun createRequest(body: EntraIssuanceRequestBody): EntraIssuanceRequestResponse {
        val token = tokenClient.getAccessToken()
        return executeRequest(body, token)
    }

    private suspend fun executeRequest(
        body: EntraIssuanceRequestBody,
        token: String,
    ): EntraIssuanceRequestResponse = withContext(Dispatchers.IO) {
        val payload = encodeRequestBody(body).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(config.issuanceRequestUrl)
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
                    message = "createIssuanceRequest failed: HTTP ${response.code}",
                )
            }
            runCatching { json.decodeFromString(EntraIssuanceRequestResponse.serializer(), responseBody) }
                .getOrElse {
                    throw EntraException.MalformedResponse(
                        message = "Could not parse createIssuanceRequest response: ${it.message}",
                        cause = it,
                    )
                }
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
