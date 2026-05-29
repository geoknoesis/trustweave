package org.trustweave.referencewallet.lib

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.trustweave.referencewallet.BuildConfig

/**
 * Thin client for the in-repo Next.js demo backend (issuer + verifier).
 *
 * Phase 2.5: receiveCredential() now returns SD-JWT VC by default with the
 * issuer's selectively-disclosable claim list; the verify() body uses the new
 * `presentation` + `format` field shape (with a `vp` legacy fallback on the
 * server side).
 */
class DemoBackend(
    private val baseUrl: String = BuildConfig.DEMO_BACKEND_BASE_URL,
) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class CredentialOffer(
        val format: String,
        val credential: String,
        val issuer: String,
        val selectivelyDisclosable: List<String> = emptyList(),
    )

    @Serializable
    data class PresentationRequestParams(
        val verifier: String,
        val audience: String,
        val nonce: String,
        val acceptedTypes: List<String>,
    )

    @Serializable
    data class VerificationCheck(val step: String, val passed: Boolean, val detail: String? = null)

    @Serializable
    data class VerifiedCredentialView(
        val type: List<String>,
        val issuer: String,
        val subject: String,
        val disclosedClaims: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
        val withheldClaimNames: List<String>? = null,
    )

    @Serializable
    data class VerificationResponse(
        val valid: Boolean,
        val checks: List<VerificationCheck>,
        val holder: String? = null,
        val credentials: List<VerifiedCredentialView>? = null,
    )

    @Serializable
    private data class VerifyRequest(
        val presentation: String,
        val format: String,
        val expectedNonce: String,
    )

    suspend fun receiveCredential(subjectDid: String): CredentialOffer = withContext(Dispatchers.IO) {
        val encoded = java.net.URLEncoder.encode(subjectDid, "UTF-8")
        val req = Request.Builder().url("$baseUrl/api/demo-issuer/credential?subject=$encoded").get().build()
        http.newCall(req).execute().use { response ->
            require(response.isSuccessful) { "Issuer request failed: HTTP ${response.code}" }
            val body = response.body?.string() ?: error("Empty issuer response")
            json.decodeFromString(CredentialOffer.serializer(), body)
        }
    }

    suspend fun fetchPresentationRequest(): PresentationRequestParams = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$baseUrl/api/demo-verifier/request").get().build()
        http.newCall(req).execute().use { response ->
            require(response.isSuccessful) { "Verifier request fetch failed: HTTP ${response.code}" }
            val body = response.body?.string() ?: error("Empty verifier-request response")
            json.decodeFromString(PresentationRequestParams.serializer(), body)
        }
    }

    suspend fun verify(
        presentation: String,
        format: String,
        expectedNonce: String,
    ): VerificationResponse = withContext(Dispatchers.IO) {
        val payload = json.encodeToString(
            VerifyRequest.serializer(),
            VerifyRequest(presentation = presentation, format = format, expectedNonce = expectedNonce),
        )
        val req = Request.Builder()
            .url("$baseUrl/api/demo-verifier/verify")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { response ->
            val body = response.body?.string() ?: error("Empty verify response")
            json.decodeFromString(VerificationResponse.serializer(), body)
        }
    }
}
