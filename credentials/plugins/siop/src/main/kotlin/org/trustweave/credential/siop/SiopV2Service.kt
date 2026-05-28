package org.trustweave.credential.siop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.trustweave.core.identifiers.KeyId
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.pex.PresentationDefinition
import org.trustweave.credential.pex.PresentationSubmission
import org.trustweave.credential.siop.models.SiopV2AuthorizationRequest
import org.trustweave.credential.siop.models.SiopV2AuthorizationResponse
import org.trustweave.credential.siop.models.SiopV2Session
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.SignResult
import java.net.URLDecoder
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SiopV2Service(
    private val kms: KeyManagementService,
    private val config: SiopV2Config = SiopV2Config(),
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val sessions = ConcurrentHashMap<String, SiopV2Session>()

    /** Creates a SIOPv2 authorization request. Returns the session ID and request object. */
    suspend fun createAuthorizationRequest(
        clientId: String,
        nonce: String = UUID.randomUUID().toString(),
        state: String? = UUID.randomUUID().toString(),
        responseUri: String,
        presentationDefinition: PresentationDefinition? = null,
        responseType: String = "vp_token",
    ): SiopV2Session {
        val request = SiopV2AuthorizationRequest(
            responseType = responseType,
            clientId = clientId,
            clientIdScheme = config.defaultClientIdScheme,
            responseUri = responseUri,
            nonce = nonce,
            state = state,
            presentationDefinition = presentationDefinition,
        )
        val session = SiopV2Session(sessionId = UUID.randomUUID().toString(), request = request)
        sessions[session.sessionId] = session
        return session
    }

    /** Parses a SIOPv2 authorization request from a URL or fetches from request_uri. */
    suspend fun parseAuthorizationRequest(authorizationUrl: String): SiopV2Session = withContext(Dispatchers.IO) {
        val queryString = authorizationUrl.substringAfter("?", "")
        val params = queryString.split("&").associate { param ->
            val parts = param.split("=", limit = 2)
            URLDecoder.decode(parts[0], "UTF-8") to
                (if (parts.size > 1) URLDecoder.decode(parts[1], "UTF-8") else "")
        }
        val requestUri = params["request_uri"]
        val json = Json { ignoreUnknownKeys = true }
        val requestJson: JsonObject = if (requestUri != null) {
            val response = httpClient.newCall(Request.Builder().url(requestUri).get().build()).execute()
            val body = response.body?.string()
                ?: throw SiopV2Exception("FETCH_FAILED", "Empty response from request_uri")
            json.parseToJsonElement(body).jsonObject
        } else {
            buildJsonObject {
                params.forEach { (k, v) -> put(k, v) }
            }
        }
        val request = json.decodeFromJsonElement<SiopV2AuthorizationRequest>(requestJson)
        val session = SiopV2Session(sessionId = UUID.randomUUID().toString(), request = request)
        sessions[session.sessionId] = session
        session
    }

    /** Gets a stored session by ID. */
    fun getSession(sessionId: String): SiopV2Session? = sessions[sessionId]

    /**
     * Builds a SIOPv2 authorization response (ID Token and/or VP Token).
     * The ID Token is a JWT where iss = sub = holderDid, signed by the holder's key.
     */
    suspend fun buildAuthorizationResponse(
        session: SiopV2Session,
        holderDid: String,
        keyId: String,
        presentation: VerifiablePresentation? = null,
        presentationSubmission: PresentationSubmission? = null,
    ): SiopV2AuthorizationResponse = withContext(Dispatchers.IO) {
        val request = session.request
        val now = System.currentTimeMillis() / 1000

        val idToken: String? = if (request.responseType.contains("id_token")) {
            val header = buildJsonObject {
                put("alg", "EdDSA")
                put("typ", "JWT")
                put("kid", keyId)
            }
            val payload = buildJsonObject {
                put("iss", holderDid)
                put("sub", holderDid)
                put("aud", request.clientId)
                put("iat", now)
                put("exp", now + 600)
                put("nonce", request.nonce)
            }
            signJwt(header, payload, keyId)
        } else {
            null
        }

        val vpToken: String? = if (request.responseType.contains("vp_token") && presentation != null) {
            val header = buildJsonObject {
                put("alg", "EdDSA")
                put("typ", "JWT")
                put("kid", keyId)
            }
            val payload = buildJsonObject {
                put("iss", holderDid)
                put("aud", request.clientId)
                put("iat", now)
                put("exp", now + 600)
                put("nonce", request.nonce)
                put(
                    "vp",
                    buildJsonObject {
                        put("@context", JsonArray(presentation.context.map { JsonPrimitive(it) }))
                        put("type", JsonArray(presentation.type.map { JsonPrimitive(it.value) }))
                        put("holder", presentation.holder.value)
                    },
                )
            }
            signJwt(header, payload, keyId)
        } else {
            null
        }

        SiopV2AuthorizationResponse(
            idToken = idToken,
            vpToken = vpToken,
            presentationSubmission = presentationSubmission,
            state = request.state,
        )
    }

    /** Submits the authorization response to the verifier's response_uri via direct_post. */
    suspend fun submitResponse(
        session: SiopV2Session,
        response: SiopV2AuthorizationResponse,
    ) = withContext(Dispatchers.IO) {
        val responseUri = session.request.responseUri
            ?: throw SiopV2Exception("NO_RESPONSE_URI", "No response_uri in authorization request")
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
        val body = json.encodeToString(SiopV2AuthorizationResponse.serializer(), response)
            .toRequestBody("application/json".toMediaType())
        val httpRequest = Request.Builder()
            .url(responseUri)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()
        val httpResponse = httpClient.newCall(httpRequest).execute()
        if (!httpResponse.isSuccessful) {
            throw SiopV2Exception(
                "SUBMISSION_FAILED",
                "HTTP ${httpResponse.code}: ${httpResponse.body?.string()}",
            )
        }
    }

    private suspend fun signJwt(header: JsonObject, payload: JsonObject, keyId: String): String {
        val json = Json { prettyPrint = false; encodeDefaults = false }
        val enc = Base64.getUrlEncoder().withoutPadding()
        val headerB64 = enc.encodeToString(
            json.encodeToString(JsonObject.serializer(), header).toByteArray(),
        )
        val payloadB64 = enc.encodeToString(
            json.encodeToString(JsonObject.serializer(), payload).toByteArray(),
        )
        val signingInput = "$headerB64.$payloadB64".toByteArray()
        val signResult = kms.sign(KeyId(keyId), signingInput)
        val sig = when (signResult) {
            is SignResult.Success -> signResult.signature
            is SignResult.Failure.KeyNotFound ->
                throw SiopV2Exception("SIGN_FAILED", "Key not found: ${signResult.keyId}")
            is SignResult.Failure.UnsupportedAlgorithm ->
                throw SiopV2Exception("SIGN_FAILED", "Unsupported algorithm")
            is SignResult.Failure.Error ->
                throw SiopV2Exception("SIGN_FAILED", signResult.reason)
        }
        return "$headerB64.$payloadB64.${enc.encodeToString(sig)}"
    }
}
