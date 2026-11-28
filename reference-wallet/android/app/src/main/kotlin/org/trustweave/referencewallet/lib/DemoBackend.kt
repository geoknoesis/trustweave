package org.trustweave.referencewallet.lib

import java.net.URLEncoder
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
 * HTTP client supporting both the default demo backend AND dynamic issuer/verifier
 * URLs parsed from scanned QR codes, NFC NDEF tags, and OS deep links.
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

    @Serializable
    private data class Oid4VciRedeemRequest(
        val preAuthorizedCode: String,
        val subjectDid: String,
    )

    @Serializable
    private data class IssuerIdentity(val issuerDids: List<String> = emptyList())

    suspend fun receiveCredential(subjectDid: String): CredentialOffer = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(subjectDid, "UTF-8")
        val req = Request.Builder().url("$baseUrl/api/demo-issuer/credential?subject=$encoded").get().build()
        http.newCall(req).execute().use { response ->
            require(response.isSuccessful) { "Could not reach issuer (HTTP ${response.code})." }
            val body = response.body?.string() ?: error("Empty issuer response")
            json.decodeFromString(CredentialOffer.serializer(), body)
        }
    }

    /** Claim a credential from a scanned QR / NFC / deep-link offer (direct or OpenID4VCI pre-authorized). */
    suspend fun claimIncomingOffer(
        offer: PayloadRouter.IncomingOffer,
        subjectDid: String,
    ): CredentialOffer = withContext(Dispatchers.IO) {
        when (offer) {
            is PayloadRouter.IncomingOffer.TrustWeaveDirect -> {
                val resolvedBase = PayloadRouter.resolveHostUrl(offer.issuerUrl, baseUrl)
                val endpoint = if (offer.credentialEndpoint.startsWith("/")) offer.credentialEndpoint else "/${offer.credentialEndpoint}"
                val encodedSubject = URLEncoder.encode(subjectDid, "UTF-8")
                val studentQuery = offer.studentId?.let { "&studentId=${URLEncoder.encode(it, "UTF-8")}" } ?: ""
                val req = Request.Builder()
                    .url("$resolvedBase$endpoint?subject=$encodedSubject$studentQuery")
                    .get()
                    .build()
                http.newCall(req).execute().use { response ->
                    require(response.isSuccessful) { "Issuer returned HTTP ${response.code}." }
                    val body = response.body?.string() ?: error("Empty issuer response")
                    json.decodeFromString(CredentialOffer.serializer(), body)
                }
            }
            is PayloadRouter.IncomingOffer.Oid4VciPreAuthorized -> {
                val resolvedBase = PayloadRouter.resolveHostUrl(offer.issuerBase, baseUrl)
                val payload = json.encodeToString(
                    Oid4VciRedeemRequest.serializer(),
                    Oid4VciRedeemRequest(
                        preAuthorizedCode = offer.preAuthorizedCode,
                        subjectDid = subjectDid,
                    ),
                )
                val req = Request.Builder()
                    .url("$resolvedBase/api/public/credential-offer/redeem")
                    .post(payload.toRequestBody("application/json".toMediaType()))
                    .build()
                http.newCall(req).execute().use { response ->
                    require(response.isSuccessful) { "Could not redeem offer (HTTP ${response.code})." }
                    val body = response.body?.string() ?: error("Empty issuer response")
                    json.decodeFromString(CredentialOffer.serializer(), body)
                }
            }
        }
    }

    /**
     * The issuer DIDs of the backend this wallet is configured to use (build-time base URL), read
     * from its identity endpoint, never from an offer. Fails closed: any problem yields an empty set.
     */
    suspend fun backendIssuers(): Set<String> =
        try {
            withContext(Dispatchers.IO) {
                val req = Request.Builder().url("$baseUrl/api/demo-issuer/identity").get().build()
                http.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) return@use emptySet<String>()
                    val body = response.body?.string() ?: return@use emptySet<String>()
                    val dids = json.decodeFromString(IssuerIdentity.serializer(), body).issuerDids
                    if (dids.size > 16 || !dids.all(IssuerTrust::isPlausibleDid)) emptySet() else dids.toSet()
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: java.io.IOException) {
            emptySet()
        } catch (_: kotlinx.serialization.SerializationException) {
            emptySet()
        }

    suspend fun fetchPresentationRequest(
        target: PayloadRouter.PresentationRequestPayload? = null,
    ): PresentationRequestParams = withContext(Dispatchers.IO) {
        val resolvedBase = target?.let { PayloadRouter.resolveHostUrl(it.verifierUrl, baseUrl) } ?: baseUrl
        val rawEndpoint = target?.requestEndpoint ?: "/api/demo-verifier/request"
        val endpoint = if (rawEndpoint.startsWith("/")) rawEndpoint else "/$rawEndpoint"
        val req = Request.Builder().url("$resolvedBase$endpoint").get().build()
        http.newCall(req).execute().use { response ->
            require(response.isSuccessful) { "Could not read verifier request (HTTP ${response.code})." }
            val body = response.body?.string() ?: error("Empty verifier-request response")
            json.decodeFromString(PresentationRequestParams.serializer(), body)
        }
    }

    suspend fun verify(
        presentation: String,
        format: String,
        expectedNonce: String,
        target: PayloadRouter.PresentationRequestPayload? = null,
    ): VerificationResponse = withContext(Dispatchers.IO) {
        val resolvedBase = target?.let { PayloadRouter.resolveHostUrl(it.verifierUrl, baseUrl) } ?: baseUrl
        val rawEndpoint = target?.verifyEndpoint ?: "/api/demo-verifier/verify"
        val endpoint = if (rawEndpoint.startsWith("/")) rawEndpoint else "/$rawEndpoint"
        val payload = json.encodeToString(
            VerifyRequest.serializer(),
            VerifyRequest(presentation = presentation, format = format, expectedNonce = expectedNonce),
        )
        val req = Request.Builder()
            .url("$resolvedBase$endpoint")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { response ->
            val body = response.body?.string() ?: error("Empty verify response")
            json.decodeFromString(VerificationResponse.serializer(), body)
        }
    }
}
