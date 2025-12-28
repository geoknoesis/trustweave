package org.trustweave.credential.oidc4vp

import org.trustweave.core.identifiers.KeyId
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.credential.exchange.exception.ExchangeException
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.oidc4vp.exception.Oidc4VpException
import org.trustweave.credential.oidc4vp.models.*
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.SignResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.util.*
import java.util.Base64

/**
 * OIDC4VP (OpenID Connect for Verifiable Presentations) service.
 *
 * Implements the OIDC4VP protocol with full HTTP integration for wallet/holder operations.
 *
 * **OIDC4VP Flow (Holder/Wallet Side):**
 * 1. Holder receives authorization request URL (e.g., from QR code)
 * 2. Holder fetches authorization request from request_uri
 * 3. Holder creates PermissionRequest from authorization request
 * 4. User selects credentials and fields to share
 * 5. Holder creates PermissionResponse with VP token
 * 6. Holder submits PermissionResponse to verifier
 *
 * **Example Usage:**
 * ```kotlin
 * val service = Oidc4VpService(
 *     kms = kms,
 *     httpClient = OkHttpClient()
 * )
 *
 * // Parse authorization URL from QR code
 * val permissionRequest = service.parseAuthorizationUrl(authorizationUrl)
 *
 * // User selects credentials
 * val selectedCredentials = listOf(...)
 * val selectedFields = listOf(listOf("name", "email"))
 *
 * // Create and submit response
 * val permissionResponse = service.createPermissionResponse(
 *     permissionRequest = permissionRequest,
 *     selectedCredentials = selectedCredentials,
 *     selectedFields = selectedFields,
 *     holderDid = holderDid,
 *     keyId = keyId
 * )
 * service.submitPermissionResponse(permissionResponse)
 * ```
 */
class Oidc4VpService(
    private val kms: KeyManagementService,
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    private val permissionRequests = mutableMapOf<String, PermissionRequest>()
    private var metadata: VerifierMetadata? = null

    /**
     * Parses an OIDC4VP authorization URL and creates a PermissionRequest.
     *
     * The URL typically comes from a QR code scanned by the wallet app.
     * Format: openid4vp://authorize?client_id=...&request_uri=...
     *
     * @param authorizationUrl The authorization URL to parse
     * @return PermissionRequest for user interaction
     */
    suspend fun parseAuthorizationUrl(authorizationUrl: String): PermissionRequest = withContext(Dispatchers.IO) {
        try {
            // Parse URL manually since openid4vp:// is not a standard scheme
            val queryString = if (authorizationUrl.contains("?")) {
                authorizationUrl.substringAfter("?")
            } else {
                ""
            }
            
            // Parse query parameters
            val queryParams = parseQueryParameters(queryString)
            val clientId = queryParams["client_id"]
            val requestUri = queryParams["request_uri"]
            
            if (requestUri == null) {
                throw Oidc4VpException.UrlParseFailed(
                    url = authorizationUrl,
                    reason = "Missing 'request_uri' parameter"
                )
            }
            
            // Fetch authorization request from request_uri
            val authorizationRequest = fetchAuthorizationRequest(requestUri)
            
            val requestId = UUID.randomUUID().toString()
            
            // Extract requested credential types and claims from presentation_definition
            val requestedCredentialTypes = extractCredentialTypes(authorizationRequest.presentationDefinition)
            val requestedClaims = extractRequestedClaims(authorizationRequest.presentationDefinition)
            
            val permissionRequest = PermissionRequest(
                requestId = requestId,
                authorizationRequest = authorizationRequest.copy(
                    clientId = clientId ?: authorizationRequest.clientId,
                    requestUri = requestUri
                ),
                verifierUrl = extractVerifierUrl(requestUri),
                requestedCredentialTypes = requestedCredentialTypes,
                requestedClaims = requestedClaims
            )
            
            permissionRequests[requestId] = permissionRequest
            permissionRequest
        } catch (e: Oidc4VpException) {
            throw e
        } catch (e: Exception) {
            throw Oidc4VpException.UrlParseFailed(
                url = authorizationUrl,
                reason = "Failed to parse URL: ${e.message ?: "Unknown error"}"
            )
        }
    }

    /**
     * Creates a PermissionResponse from a PermissionRequest with selected credentials.
     *
     * @param permissionRequest The permission request to respond to
     * @param selectedCredentials List of credentials to include in presentation
     * @param selectedFields List of field selections per credential (matching credential order)
     * @param holderDid Holder DID
     * @param keyId Key ID for signing the VP token
     * @param presentation The VerifiablePresentation (if already created)
     * @return PermissionResponse ready for submission
     */
    suspend fun createPermissionResponse(
        permissionRequest: PermissionRequest,
        selectedCredentials: List<PresentableCredential>,
        selectedFields: List<List<String>> = emptyList(),
        holderDid: String,
        keyId: String,
        presentation: VerifiablePresentation? = null
    ): PermissionResponse = withContext(Dispatchers.IO) {
        // If presentation is provided, use it; otherwise, we would need to create one
        // For now, we'll create a simple JWT VP token
        val vpToken = if (presentation != null) {
            createVpTokenJwt(presentation, holderDid, keyId, permissionRequest.authorizationRequest.nonce)
        } else {
            // Create a minimal VP token with selected credentials
            createVpTokenJwtFromCredentials(
                credentials = selectedCredentials.map { it.credential },
                holderDid = holderDid,
                keyId = keyId,
                nonce = permissionRequest.authorizationRequest.nonce
            )
        }
        
        PermissionResponse(
            responseId = UUID.randomUUID().toString(),
            requestId = permissionRequest.requestId,
            vpToken = vpToken,
            state = permissionRequest.authorizationRequest.state
        )
    }

    /**
     * Submits a PermissionResponse to the verifier.
     *
     * @param permissionResponse The permission response to submit
     */
    suspend fun submitPermissionResponse(permissionResponse: PermissionResponse) = withContext(Dispatchers.IO) {
        val request = permissionRequests[permissionResponse.requestId]
            ?: throw ExchangeException.RequestNotFound(requestId = permissionResponse.requestId)
        
        val responseUri = request.authorizationRequest.responseUri
        
        // Create response payload
        val responseBody = buildJsonObject {
            put("vp_token", permissionResponse.vpToken)
            permissionResponse.state?.let { put("state", it) }
            permissionResponse.presentationSubmission?.let { put("presentation_submission", it) }
        }
        
        val json = Json { prettyPrint = false; encodeDefaults = false }
        val requestBody = json.encodeToString(JsonObject.serializer(), responseBody)
            .toRequestBody("application/json".toMediaType())
        
        val httpRequest = Request.Builder()
            .url(responseUri)
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()
        
        val response = httpClient.newCall(httpRequest).execute()
        val body = response.body?.string()
        
        if (!response.isSuccessful) {
            throw Oidc4VpException.PresentationSubmissionFailed(
                reason = "HTTP ${response.code}: $body",
                verifierUrl = responseUri
            )
        }
    }

    /**
     * Fetches authorization request from request_uri.
     */
    private suspend fun fetchAuthorizationRequest(requestUri: String): AuthorizationRequest {
        val request = Request.Builder()
            .url(requestUri)
            .get()
            .build()
        
        val response = httpClient.newCall(request).execute()
        val body = response.body?.string()
            ?: throw Oidc4VpException.AuthorizationRequestFetchFailed(
                requestUri = requestUri,
                reason = "Empty response body"
            )
        
        if (!response.isSuccessful) {
            throw Oidc4VpException.AuthorizationRequestFetchFailed(
                requestUri = requestUri,
                reason = "HTTP ${response.code}: $body"
            )
        }
        
        // Parse JSON manually since AuthorizationRequest.presentationDefinition is JsonObject (not @Serializable)
        val json = Json { ignoreUnknownKeys = true }
        val jsonElement = json.parseToJsonElement(body).jsonObject
        
        return AuthorizationRequest(
            responseUri = jsonElement["response_uri"]?.jsonPrimitive?.content
                ?: throw Oidc4VpException.AuthorizationRequestFetchFailed(
                    requestUri = requestUri,
                    reason = "Missing 'response_uri' in authorization request"
                ),
            clientId = jsonElement["client_id"]?.jsonPrimitive?.content,
            requestUri = jsonElement["request_uri"]?.jsonPrimitive?.content,
            presentationDefinition = jsonElement["presentation_definition"]?.jsonObject,
            nonce = jsonElement["nonce"]?.jsonPrimitive?.content,
            state = jsonElement["state"]?.jsonPrimitive?.content,
            redirectUri = jsonElement["redirect_uri"]?.jsonPrimitive?.content
        )
    }

    /**
     * Fetches verifier metadata.
     *
     * @param verifierUrl Verifier URL
     * @return Verifier metadata
     */
    suspend fun fetchVerifierMetadata(verifierUrl: String): VerifierMetadata = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$verifierUrl/.well-known/openid-credential-verifier")
            .get()
            .build()
        
        val response = httpClient.newCall(request).execute()
        val body = response.body?.string()
            ?: throw Oidc4VpException.MetadataFetchFailed(
                verifierUrl = verifierUrl,
                reason = "Empty response body"
            )
        
        if (!response.isSuccessful) {
            throw Oidc4VpException.MetadataFetchFailed(
                verifierUrl = verifierUrl,
                reason = "HTTP ${response.code}: $body"
            )
        }
        
        val json = Json { ignoreUnknownKeys = true }
        json.decodeFromString<VerifierMetadata>(body)
    }

    /**
     * Creates a VP token JWT from a VerifiablePresentation.
     */
    private suspend fun createVpTokenJwt(
        presentation: VerifiablePresentation,
        holderDid: String,
        keyId: String,
        nonce: String?
    ): String {
        // Create JWT header
        val header = buildJsonObject {
            put("alg", "Ed25519")
            put("typ", "JWT")
            put("kid", keyId)
        }
        
        // Create JWT payload with VP
        val now = System.currentTimeMillis() / 1000
        val payload = buildJsonObject {
            put("iss", holderDid)
            put("aud", "verifier") // Should come from authorization request
            put("iat", now)
            put("exp", now + 3600) // 1 hour expiration
            nonce?.let { put("nonce", it) }
            // Embed VP in payload (simplified - full implementation would serialize properly)
            put("vp", buildJsonObject {
                put("type", buildJsonArray {
                    presentation.type.forEach { type ->
                        add(type.value)
                    }
                })
                presentation.holder?.let { put("holder", it.value) }
                // Note: Full VP serialization would be more complex
            })
        }
        
        // Sign JWT
        return signJwt(header, payload, keyId)
    }

    /**
     * Creates a VP token JWT from credentials.
     */
    private suspend fun createVpTokenJwtFromCredentials(
        credentials: List<VerifiableCredential>,
        holderDid: String,
        keyId: String,
        nonce: String?
    ): String {
        // This is a simplified version - in production, you'd create a proper VP
        val header = buildJsonObject {
            put("alg", "Ed25519")
            put("typ", "JWT")
            put("kid", keyId)
        }
        
        val now = System.currentTimeMillis() / 1000
        val payload = buildJsonObject {
            put("iss", holderDid)
            put("aud", "verifier")
            put("iat", now)
            put("exp", now + 3600)
            nonce?.let { put("nonce", it) }
            put("vp", buildJsonObject {
                put("type", JsonArray(listOf(JsonPrimitive("VerifiablePresentation"))))
                put("verifiableCredential", JsonArray(credentials.map { JsonPrimitive(it.toString()) }))
            })
        }
        
        return signJwt(header, payload, keyId)
    }

    /**
     * Signs a JWT.
     */
    private suspend fun signJwt(header: JsonObject, payload: JsonObject, keyId: String): String {
        val json = Json { prettyPrint = false; encodeDefaults = false }
        val headerBase64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(json.encodeToString(JsonObject.serializer(), header).toByteArray(Charsets.UTF_8))
        val payloadBase64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(json.encodeToString(JsonObject.serializer(), payload).toByteArray(Charsets.UTF_8))
        
        val signingInput = "$headerBase64.$payloadBase64".toByteArray(Charsets.UTF_8)
        val signResult = kms.sign(KeyId(keyId), signingInput)
        val signature = when (signResult) {
            is SignResult.Success -> signResult.signature
            is SignResult.Failure.KeyNotFound -> throw IllegalStateException("KMS signing failed: Key not found: ${signResult.keyId}")
            is SignResult.Failure.UnsupportedAlgorithm -> throw IllegalStateException("KMS signing failed: Unsupported algorithm: ${signResult.reason ?: "Algorithm ${signResult.requestedAlgorithm} not compatible with ${signResult.keyAlgorithm}"}")
            is SignResult.Failure.Error -> throw IllegalStateException("KMS signing failed: ${signResult.reason}")
        }
        val signatureBase64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(signature)
        
        return "$headerBase64.$payloadBase64.$signatureBase64"
    }

    /**
     * Parses query parameters from URL query string.
     */
    private fun parseQueryParameters(query: String?): Map<String, String> {
        if (query.isNullOrBlank()) return emptyMap()
        
        return query.split("&").associate { param ->
            val parts = param.split("=", limit = 2)
            val key = URLDecoder.decode(parts[0], "UTF-8")
            val value = if (parts.size > 1) URLDecoder.decode(parts[1], "UTF-8") else ""
            key to value
        }
    }

    /**
     * Extracts verifier URL from request URI.
     */
    private fun extractVerifierUrl(requestUri: String): String? {
        return try {
            val uri = java.net.URI(requestUri)
            val scheme = uri.scheme
            val authority = uri.authority
            if (scheme != null && authority != null) {
                "$scheme://$authority"
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts requested credential types from presentation definition.
     */
    private fun extractCredentialTypes(presentationDefinition: JsonObject?): List<String> {
        if (presentationDefinition == null) return emptyList()
        
        // Extract from input_descriptors
        val inputDescriptors = presentationDefinition["input_descriptors"]?.jsonArray ?: return emptyList()
        return inputDescriptors.mapNotNull { descriptor ->
            descriptor.jsonObject["constraints"]?.jsonObject
                ?.get("fields")?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("path")?.jsonArray
                ?.firstOrNull()
                ?.jsonPrimitive
                ?.content
        }
    }

    /**
     * Extracts requested claims from presentation definition.
     */
    private fun extractRequestedClaims(presentationDefinition: JsonObject?): Map<String, List<String>> {
        if (presentationDefinition == null) return emptyMap()
        
        // Simplified extraction - full implementation would parse full presentation definition
        return emptyMap()
    }
}

