package org.trustweave.credential.oidc4vci

import org.trustweave.core.identifiers.KeyId
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.credential.exchange.exception.ExchangeException
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.oidc4vci.exception.Oidc4VciException
import org.trustweave.credential.oidc4vci.models.*
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.SignResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.*
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * OIDC4VCI (OpenID Connect for Verifiable Credential Issuance) service.
 *
 * Implements the OIDC4VCI protocol with full HTTP integration.
 *
 * **OIDC4VCI Flow:**
 * 1. Issuer creates credential offer
 * 2. Holder requests credential using offer
 * 3. Holder exchanges authorization code for access token
 * 4. Holder requests credential with proof of possession
 * 5. Issuer issues credential via credential endpoint
 * 6. If deferred, poll via [pollDeferredCredential]
 * 7. Optionally send [sendNotification] to the issuer's notification endpoint
 *
 * **Example Usage:**
 * ```kotlin
 * val service = Oidc4VciService(
 *     credentialIssuerUrl = "https://issuer.example.com",
 *     kms = kms,
 *     httpClient = OkHttpClient()
 * )
 *
 * val offer = service.createCredentialOffer(
 *     issuerDid = "did:key:issuer",
 *     credentialTypes = listOf("PersonCredential"),
 *     credentialIssuer = "https://issuer.example.com"
 * )
 * ```
 */
class Oidc4VciService(
    private val credentialIssuerUrl: String,
    private val kms: KeyManagementService,
    private val httpClient: OkHttpClient = org.trustweave.core.net.ssrfGuardedOkHttpClient()
) {
    companion object {
        /** Pre-authorized code grant type — OID4VCI v1.0 §4.1.1. */
        const val PRE_AUTHORIZED_CODE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:pre-authorized_code"
    }

    private val offers = ConcurrentHashMap<String, Oidc4VciOffer>()
    private val requests = ConcurrentHashMap<String, Oidc4VciCredentialRequest>()
    private val accessTokens = ConcurrentHashMap<String, String>() // requestId -> accessToken
    private val cNonces = ConcurrentHashMap<String, String>() // requestId -> c_nonce from token/credential endpoint
    @Volatile private var metadata: CredentialIssuerMetadata? = null

    /** Token endpoint resolved from the authorization server's RFC 8414 metadata (cached). */
    @Volatile private var resolvedTokenEndpoint: String? = null

    /** Access token + proof-of-possession nonce returned by the token endpoint (OID4VCI v1.0 §6.2). */
    private data class TokenResponse(val accessToken: String, val cNonce: String?)

    /**
     * Internal signal: credential endpoint rejected the proof and supplied a fresh `c_nonce`
     * (OID4VCI v1.0 §7.3.1 `invalid_proof`). The caller retries once with the new nonce.
     */
    private class FreshNonceRequired(val cNonce: String, reason: String) : RuntimeException(reason)

    init {
        // Fetch metadata on initialization
        // In production, this could be cached with TTL
    }

    /**
     * Creates a credential offer URI.
     *
     * The offer URI can be shared with the holder (e.g., via QR code, deep link).
     *
     * @param issuerDid Issuer DID
     * @param credentialTypes List of credential types to offer
     * @param credentialIssuer Credential issuer URL
     * @param grants Optional grants (e.g., authorization code)
     * @param txCode Optional transaction code (PIN) for pre-authorized code grant (OID4VCI v1.0 §4.1.1)
     * @return OIDC4VCI offer
     */
    suspend fun createCredentialOffer(
        issuerDid: String,
        credentialTypes: List<String>,
        credentialIssuer: String,
        grants: Map<String, Any?> = emptyMap(),
        txCode: TxCode? = null,
    ): Oidc4VciOffer = withContext(Dispatchers.IO) {
        val offerId = UUID.randomUUID().toString()

        // Create credential offer URI (OID4VCI v1.0 §4.1)
        // Format: openid-credential-offer://?credential_offer=<url-encoded JSON>
        val offerUri = buildCredentialOfferUri(
            credentialIssuer = credentialIssuer,
            credentialTypes = credentialTypes,
            grants = grants,
            txCode = txCode,
        )

        val offer = Oidc4VciOffer(
            offerId = offerId,
            credentialIssuer = credentialIssuer,
            credentialTypes = credentialTypes,
            offerUri = offerUri,
            grants = grants,
            txCode = txCode,
        )

        offers[offerId] = offer
        offer
    }

    /**
     * Creates a credential request from an offer.
     *
     * The holder uses this to request the credential from the issuer.
     * This performs the full OIDC4VCI flow:
     * 1. Exchange authorization code for access token (if using auth code flow)
     * 2. Create credential request with proof of possession
     * 3. Send request to credential endpoint
     *
     * @param holderDid Holder DID
     * @param offerId Offer ID
     * @param redirectUri Optional redirect URI for authorization code flow
     * @param authorizationCode Optional authorization code (if using auth code flow)
     * @param txCodeValue Optional transaction code value supplied by the holder (OID4VCI v1.0 §4.1.1)
     * @return Credential request
     */
    suspend fun createCredentialRequest(
        holderDid: String,
        offerId: String,
        redirectUri: String? = null,
        authorizationCode: String? = null,
        codeVerifier: String? = null,
        txCodeValue: String? = null,
    ): Oidc4VciCredentialRequest = withContext(Dispatchers.IO) {
        val offer = offers[offerId]
            ?: throw TrustWeaveException.NotFound(resource = "OIDC4VCI offer: $offerId")

        // PKCE is mandatory for the authorization_code flow (OAuth 2.1 / OID4VCI §3.4): the caller
        // must supply the code_verifier whose code_challenge bound the authorization request, so an
        // intercepted authorization code cannot be replayed by another client. Fail closed.
        if (authorizationCode != null && redirectUri != null && codeVerifier.isNullOrBlank()) {
            throw IllegalArgumentException(
                "PKCE code_verifier is required for the authorization_code flow (OAuth 2.1 / OID4VCI). " +
                    "Generate it with Pkce.generateCodeVerifier(), send Pkce.codeChallengeS256(verifier) " +
                    "as code_challenge (code_challenge_method=S256) on the authorization request, then " +
                    "pass the same verifier here.",
            )
        }

        // Fetch credential issuer metadata if not cached
        if (metadata == null) {
            metadata = fetchCredentialIssuerMetadata(offer.credentialIssuer)
        }
        val issuerMetadata = metadata ?: throw Oidc4VciException.MetadataFetchFailed(
            credentialIssuer = offer.credentialIssuer,
            reason = "Metadata fetch returned null"
        )

        val requestId = UUID.randomUUID().toString()

        // Step 1: Exchange the grant for an access token.
        // - Authorization code flow: requires authorizationCode + redirectUri
        // - Pre-authorized code flow: the pre-authorized code comes from the offer's grants
        //   (OID4VCI v1.0 §6.1), optionally accompanied by the holder-supplied tx_code value.
        val preAuthorizedCode = extractPreAuthorizedCode(offer.grants)
        val tokenResponse = when {
            authorizationCode != null && redirectUri != null -> exchangeAuthorizationCodeForToken(
                authorizationCode = authorizationCode,
                redirectUri = redirectUri,
                codeVerifier = codeVerifier,
                tokenEndpoint = resolveTokenEndpoint(issuerMetadata)
            )
            preAuthorizedCode != null -> exchangePreAuthorizedCodeForToken(
                preAuthorizedCode = preAuthorizedCode,
                txCodeValue = txCodeValue,
                tokenEndpoint = resolveTokenEndpoint(issuerMetadata)
            )
            else -> null
        }

        if (tokenResponse != null) {
            accessTokens[requestId] = tokenResponse.accessToken
            tokenResponse.cNonce?.let { cNonces[requestId] = it }
        }
        val accessToken = tokenResponse?.accessToken

        val request = Oidc4VciCredentialRequest(
            requestId = requestId,
            holderDid = holderDid,
            offerId = offerId,
            credentialIssuer = offer.credentialIssuer,
            credentialTypes = offer.credentialTypes,
            redirectUri = redirectUri,
            accessToken = accessToken,
            txCodeValue = txCodeValue,
        )

        requests[requestId] = request
        request
    }

    /**
     * Issues a credential via OIDC4VCI.
     *
     * This performs the actual credential issuance after the holder has
     * requested the credential. It makes HTTP calls to the credential issuer endpoint.
     *
     * If the issuer responds with a `transaction_id` the issuance is deferred — the
     * returned [Oidc4VciIssueResult] will have `credential = null` and `transactionId`
     * set. Call [pollDeferredCredential] to retrieve the credential once it is ready.
     *
     * @param issuerDid Issuer DID
     * @param holderDid Holder DID
     * @param credential The credential envelope to issue
     * @param requestId Credential request ID
     * @return Issue result (may be deferred)
     */
    suspend fun issueCredential(
        issuerDid: String,
        holderDid: String,
        credential: VerifiableCredential,
        requestId: String
    ): Oidc4VciIssueResult = withContext(Dispatchers.IO) {
        val request = requests[requestId]
            ?: throw ExchangeException.RequestNotFound(requestId = requestId)

        // Fetch metadata if not cached
        if (metadata == null) {
            metadata = fetchCredentialIssuerMetadata(request.credentialIssuer)
        }
        val issuerMetadata = metadata ?: throw Oidc4VciException.MetadataFetchFailed(
            credentialIssuer = request.credentialIssuer,
            reason = "Metadata fetch returned null"
        )

        // Get or obtain access token
        val accessToken = accessTokens[requestId]
            ?: throw Oidc4VciException.TokenExchangeFailed(
                reason = "Access token not available for request: $requestId",
                credentialIssuer = request.credentialIssuer
            )

        val keyId = "$holderDid#key-1" // In production, get actual key ID
        // aud of the proof-of-possession JWT is the credential issuer identifier (OID4VCI v1.0 §7.2.1.1)
        val proofAudience = issuerMetadata.credentialIssuer

        // Step 1: Create credential request with proof of possession using the c_nonce
        // returned by the token endpoint (OID4VCI v1.0 §7.2.1.1).
        // Step 2: Send credential request to credential endpoint. If the issuer rejects the
        // proof and supplies a fresh c_nonce (§7.3.1 invalid_proof), retry once with it.
        val credentialResponse = try {
            requestCredentialFromIssuer(
                credentialEndpoint = issuerMetadata.credentialEndpoint,
                accessToken = accessToken,
                credentialRequest = createCredentialRequestPayload(
                    credentialTypes = request.credentialTypes,
                    holderDid = holderDid,
                    keyId = keyId,
                    audience = proofAudience,
                    cNonce = cNonces[requestId]
                )
            )
        } catch (e: FreshNonceRequired) {
            cNonces[requestId] = e.cNonce
            try {
                requestCredentialFromIssuer(
                    credentialEndpoint = issuerMetadata.credentialEndpoint,
                    accessToken = accessToken,
                    credentialRequest = createCredentialRequestPayload(
                        credentialTypes = request.credentialTypes,
                        holderDid = holderDid,
                        keyId = keyId,
                        audience = proofAudience,
                        cNonce = e.cNonce
                    )
                )
            } catch (retry: FreshNonceRequired) {
                // The retry is attempted exactly once. A second invalid_proof must surface as
                // a public typed exception — FreshNonceRequired is a private signal and must
                // never escape issueCredential.
                throw Oidc4VciException.CredentialRequestFailed(
                    reason = "Credential endpoint rejected the proof of possession again " +
                        "after retrying with a fresh c_nonce: ${retry.message}",
                    credentialIssuer = issuerMetadata.credentialEndpoint,
                    cause = retry
                )
            }
        }

        // Issuer may rotate the c_nonce in a successful response (OID4VCI v1.0 §7.3)
        (credentialResponse["c_nonce"] as? String)?.let { cNonces[requestId] = it }

        val issueId = UUID.randomUUID().toString()

        // Deferred issuance: issuer returns transaction_id instead of credential (OID4VCI v1.0 §9)
        val transactionId = credentialResponse["transaction_id"] as? String
        return@withContext if (transactionId != null) {
            Oidc4VciIssueResult(
                issueId = issueId,
                credential = null,
                transactionId = transactionId,
                credentialResponse = credentialResponse,
            )
        } else {
            // Immediate issuance — use the credential supplied by the caller
            // In production, parse credentialResponse and convert to VerifiableCredential
            Oidc4VciIssueResult(
                issueId = issueId,
                credential = credential,
                transactionId = null,
                credentialResponse = credentialResponse,
            )
        }
    }

    /**
     * Polls the deferred credential endpoint to retrieve a credential whose issuance
     * was deferred by the issuer.
     *
     * OID4VCI v1.0 §9 — POST `{"transaction_id": "..."}` to `deferredCredentialEndpoint`.
     *
     * @param request Deferred credential request containing the transaction ID and access token
     * @return Issue result (credential will be non-null if the issuer has completed issuance)
     */
    suspend fun pollDeferredCredential(
        request: DeferredCredentialRequest
    ): Oidc4VciIssueResult = withContext(Dispatchers.IO) {
        val issuerMetadata = metadata ?: throw Oidc4VciException.MetadataFetchFailed(
            credentialIssuer = credentialIssuerUrl,
            reason = "Issuer metadata not loaded — call fetchCredentialIssuerMetadata first"
        )

        val deferredEndpoint = issuerMetadata.deferredCredentialEndpoint
            ?: throw Oidc4VciException.CredentialRequestFailed(
                reason = "Issuer does not support deferred credential endpoint",
                credentialIssuer = credentialIssuerUrl
            )

        val json = Json { prettyPrint = false; encodeDefaults = false }
        val requestBody = buildJsonObject {
            put("transaction_id", request.transactionId)
        }
        val body = json.encodeToString(JsonObject.serializer(), requestBody)
            .toRequestBody("application/json".toMediaType())

        val httpRequest = Request.Builder()
            .url(deferredEndpoint)
            .post(body)
            .addHeader("Authorization", "Bearer ${request.accessToken}")
            .addHeader("Content-Type", "application/json")
            .build()

        val response = httpClient.newCall(httpRequest).execute()
        val responseBody = response.body?.string()
            ?: throw Oidc4VciException.CredentialRequestFailed(
                reason = "Empty response body from deferred credential endpoint",
                credentialIssuer = deferredEndpoint
            )

        if (!response.isSuccessful) {
            throw Oidc4VciException.CredentialRequestFailed(
                reason = "HTTP ${response.code}: $responseBody",
                credentialIssuer = deferredEndpoint
            )
        }

        val jsonParser = Json { ignoreUnknownKeys = true }
        val credentialResponse = jsonParser.parseToJsonElement(responseBody).jsonObject.toMap()
        val newTransactionId = credentialResponse["transaction_id"] as? String

        Oidc4VciIssueResult(
            issueId = UUID.randomUUID().toString(),
            credential = null, // Caller is responsible for parsing credential from credentialResponse
            transactionId = newTransactionId,
            credentialResponse = credentialResponse,
        )
    }

    /**
     * Sends a credential lifecycle notification to the issuer's notification endpoint.
     *
     * OID4VCI v1.0 §10 — POST notification JSON with `Authorization: Bearer <token>`.
     *
     * @param accessToken Bearer token for the notification request
     * @param notification Notification payload
     */
    suspend fun sendNotification(
        accessToken: String,
        notification: Oidc4VciNotification,
    ): Unit = withContext(Dispatchers.IO) {
        val issuerMetadata = metadata ?: throw Oidc4VciException.MetadataFetchFailed(
            credentialIssuer = credentialIssuerUrl,
            reason = "Issuer metadata not loaded — call fetchCredentialIssuerMetadata first"
        )

        val notificationEndpoint = issuerMetadata.notificationEndpoint
            ?: throw Oidc4VciException.CredentialRequestFailed(
                reason = "Issuer does not support notification endpoint",
                credentialIssuer = credentialIssuerUrl
            )

        val json = Json { prettyPrint = false; encodeDefaults = false }
        val notificationJson = json.encodeToString(Oidc4VciNotification.serializer(), notification)
        val body = notificationJson.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(notificationEndpoint)
            .post(body)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val responseBody = response.body?.string() ?: ""
            throw Oidc4VciException.CredentialRequestFailed(
                reason = "Notification endpoint returned HTTP ${response.code}: $responseBody",
                credentialIssuer = notificationEndpoint
            )
        }
    }

    /**
     * Issues multiple credentials in a single request via the batch credential endpoint.
     *
     * OID4VCI v1.0 §8 — POST `{"credential_requests": [...]}` to `batchCredentialEndpoint`.
     * Each entry in the response is either an immediate credential or a deferred `transaction_id`.
     *
     * @param request Batch credential request with per-credential request payloads and an access token
     * @return Batch credential response; inspect each item to determine immediate vs deferred
     */
    suspend fun issueBatchCredentials(
        request: BatchCredentialRequest,
    ): BatchCredentialResponse = withContext(Dispatchers.IO) {
        val issuerMetadata = metadata ?: throw Oidc4VciException.MetadataFetchFailed(
            credentialIssuer = credentialIssuerUrl,
            reason = "Issuer metadata not loaded — call fetchCredentialIssuerMetadata first"
        )

        val batchEndpoint = issuerMetadata.batchCredentialEndpoint
            ?: throw Oidc4VciException.CredentialRequestFailed(
                reason = "Issuer does not advertise a batch_credential_endpoint",
                credentialIssuer = credentialIssuerUrl
            )

        // Build the batch request body: {"credential_requests": [...]}
        val credentialRequestsJson = JsonArray(
            request.credentialRequests.map { credRequest ->
                buildJsonObject {
                    put("format", "vc+sd-jwt")
                    put("credential_definition", buildJsonObject {
                        put("type", JsonArray(credRequest.credentialTypes.map { JsonPrimitive(it) }))
                    })
                }
            }
        )
        val batchBody = buildJsonObject {
            put("credential_requests", credentialRequestsJson)
        }

        val json = Json { prettyPrint = false; encodeDefaults = false }
        val requestBody = json.encodeToString(JsonObject.serializer(), batchBody)
            .toRequestBody("application/json".toMediaType())

        val httpRequest = Request.Builder()
            .url(batchEndpoint)
            .post(requestBody)
            .addHeader("Authorization", "Bearer ${request.accessToken}")
            .addHeader("Content-Type", "application/json")
            .build()

        val response = httpClient.newCall(httpRequest).execute()
        val responseBody = response.body?.string()
            ?: throw Oidc4VciException.CredentialRequestFailed(
                reason = "Empty response body from batch credential endpoint",
                credentialIssuer = batchEndpoint
            )

        if (!response.isSuccessful) {
            throw Oidc4VciException.CredentialRequestFailed(
                reason = "HTTP ${response.code}: $responseBody",
                credentialIssuer = batchEndpoint
            )
        }

        val jsonParser = Json { ignoreUnknownKeys = true }
        val batchResponse = jsonParser.parseToJsonElement(responseBody).jsonObject
        val credentialResponsesArray = batchResponse["credential_responses"]?.jsonArray
            ?: throw Oidc4VciException.CredentialRequestFailed(
                reason = "Missing credential_responses in batch response",
                credentialIssuer = batchEndpoint
            )

        val items = credentialResponsesArray.map { element ->
            val obj = element.jsonObject
            val transactionId = obj["transaction_id"]?.jsonPrimitive?.contentOrNull
            BatchCredentialResponseItem(
                credential = null, // caller is responsible for parsing the credential JWT/JSON
                transactionId = transactionId,
            )
        }

        BatchCredentialResponse(credentialResponses = items)
    }

    /**
     * Resolves the token endpoint for a credential issuer (OID4VCI v1.0 §6).
     *
     * Resolution order:
     * 1. Inline `token_endpoint` in the credential issuer metadata — non-standard but kept
     *    for backward compatibility with issuers that advertise it directly.
     * 2. The first entry of `authorization_servers` (falling back to the legacy singular
     *    `authorization_server`): fetch its RFC 8414 metadata from
     *    `{as}/.well-known/oauth-authorization-server` and use its `token_endpoint`.
     * 3. Neither available → [Oidc4VciException.TokenEndpointResolutionFailed].
     *
     * **Security:** both the authorization-server URL and any token endpoint (inline or
     * AS-resolved) must satisfy the https-or-loopback policy — the token request carries
     * the pre-authorized code and the holder's `tx_code` PIN, so it must never be steered
     * to a cleartext or link-local/metadata endpoint by attacker-supplied metadata.
     * The AS-resolved endpoint is validated *before* it is cached.
     */
    private fun resolveTokenEndpoint(issuerMetadata: CredentialIssuerMetadata): String {
        issuerMetadata.tokenEndpoint?.let { inlineEndpoint ->
            requireHttpsOrLoopback(inlineEndpoint) { reason ->
                Oidc4VciException.TokenEndpointResolutionFailed(
                    credentialIssuer = issuerMetadata.credentialIssuer,
                    reason = "Inline token_endpoint '$inlineEndpoint' $reason"
                )
            }
            return inlineEndpoint
        }
        resolvedTokenEndpoint?.let { return it }

        val authorizationServer = issuerMetadata.authorizationServers?.firstOrNull()
            ?: issuerMetadata.authorizationServer
            ?: throw Oidc4VciException.TokenEndpointResolutionFailed(
                credentialIssuer = issuerMetadata.credentialIssuer,
                reason = "Issuer metadata carries neither 'token_endpoint' nor 'authorization_servers'"
            )

        val asMetadata = fetchAuthorizationServerMetadata(authorizationServer)
        val tokenEndpoint = asMetadata.tokenEndpoint
            ?: throw Oidc4VciException.TokenEndpointResolutionFailed(
                credentialIssuer = issuerMetadata.credentialIssuer,
                reason = "Authorization server metadata at '$authorizationServer' has no 'token_endpoint'"
            )

        requireHttpsOrLoopback(tokenEndpoint) { reason ->
            Oidc4VciException.TokenEndpointResolutionFailed(
                credentialIssuer = issuerMetadata.credentialIssuer,
                reason = "token_endpoint '$tokenEndpoint' resolved from authorization server " +
                    "'$authorizationServer' $reason"
            )
        }

        resolvedTokenEndpoint = tokenEndpoint
        return tokenEndpoint
    }

    /**
     * Fetches OAuth 2.0 Authorization Server Metadata (RFC 8414) for [authorizationServer].
     *
     * **Security:**
     * - The authorization-server URL must satisfy the https-or-loopback policy *before*
     *   any network call — `authorization_servers` is attacker-suppliable via offer QR codes.
     * - The returned metadata's `issuer` MUST equal the URL the metadata was requested for
     *   (RFC 8414 §3.3, modulo trailing-slash normalization) — the standard defense against
     *   authorization-server mix-up attacks.
     */
    private fun fetchAuthorizationServerMetadata(authorizationServer: String): AuthorizationServerMetadata {
        requireHttpsOrLoopback(authorizationServer) { reason ->
            Oidc4VciException.TokenEndpointResolutionFailed(
                credentialIssuer = authorizationServer,
                reason = "Authorization server URL $reason"
            )
        }

        val request = Request.Builder()
            .url(authorizationServerMetadataUrl(authorizationServer))
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string()
            ?: throw Oidc4VciException.TokenEndpointResolutionFailed(
                credentialIssuer = authorizationServer,
                reason = "Empty response body from authorization server metadata endpoint"
            )

        if (!response.isSuccessful) {
            throw Oidc4VciException.TokenEndpointResolutionFailed(
                credentialIssuer = authorizationServer,
                reason = "Authorization server metadata fetch failed: HTTP ${response.code}: $body"
            )
        }

        val json = Json { ignoreUnknownKeys = true }
        val asMetadata = try {
            json.decodeFromString<AuthorizationServerMetadata>(body)
        } catch (e: Exception) {
            throw Oidc4VciException.TokenEndpointResolutionFailed(
                credentialIssuer = authorizationServer,
                reason = "Authorization server metadata is not valid JSON: ${e.message}",
                cause = e
            )
        }

        // RFC 8414 §3.3: the metadata's issuer must be identical to the authorization
        // server URL the metadata was retrieved for (mix-up attack defense).
        if (asMetadata.issuer?.trimEnd('/') != authorizationServer.trimEnd('/')) {
            throw Oidc4VciException.TokenEndpointResolutionFailed(
                credentialIssuer = authorizationServer,
                reason = "RFC 8414 issuer mismatch: metadata 'issuer' is " +
                    "'${asMetadata.issuer ?: "absent"}' but the metadata was requested for " +
                    "'$authorizationServer'"
            )
        }

        return asMetadata
    }

    /**
     * Builds the RFC 8414 §3.1 well-known metadata URL for [authorizationServer]:
     * the well-known segment is inserted *between host and path*, e.g. issuer
     * `https://host/tenant` → `https://host/.well-known/oauth-authorization-server/tenant`
     * (NOT appended after the path).
     */
    private fun authorizationServerMetadataUrl(authorizationServer: String): String {
        val uri = java.net.URI(authorizationServer)
        val pathSuffix = uri.rawPath?.trimEnd('/').orEmpty()
        return "${uri.scheme}://${uri.rawAuthority}/.well-known/oauth-authorization-server$pathSuffix"
    }

    /**
     * Exchanges authorization code for access token.
     */
    private suspend fun exchangeAuthorizationCodeForToken(
        authorizationCode: String,
        redirectUri: String,
        codeVerifier: String?,
        tokenEndpoint: String
    ): TokenResponse {
        val requestBody = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", authorizationCode)
            .add("redirect_uri", redirectUri)
            .apply { codeVerifier?.let { add("code_verifier", it) } }
            .build()

        return executeTokenRequest(tokenEndpoint, requestBody)
    }

    /**
     * Exchanges a pre-authorized code for an access token (OID4VCI v1.0 §6.1).
     *
     * Sends `grant_type=urn:ietf:params:oauth:grant-type:pre-authorized_code` with the
     * `pre-authorized_code` from the credential offer and the holder-supplied `tx_code`
     * value when the offer required one (§4.1.1).
     */
    private suspend fun exchangePreAuthorizedCodeForToken(
        preAuthorizedCode: String,
        txCodeValue: String?,
        tokenEndpoint: String
    ): TokenResponse {
        val requestBody = FormBody.Builder()
            .add("grant_type", PRE_AUTHORIZED_CODE_GRANT_TYPE)
            .add("pre-authorized_code", preAuthorizedCode)
            .apply { txCodeValue?.let { add("tx_code", it) } }
            .build()

        return executeTokenRequest(tokenEndpoint, requestBody)
    }

    /**
     * POSTs a token request and parses `access_token` + optional `c_nonce` from the response.
     */
    private fun executeTokenRequest(tokenEndpoint: String, requestBody: FormBody): TokenResponse {
        val request = Request.Builder()
            .url(tokenEndpoint)
            .post(requestBody)
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string()
            ?: throw Oidc4VciException.TokenExchangeFailed(
                reason = "Empty response body",
                credentialIssuer = tokenEndpoint
            )

        if (!response.isSuccessful) {
            throw Oidc4VciException.TokenExchangeFailed(
                reason = "HTTP ${response.code}: $body",
                credentialIssuer = tokenEndpoint,
                cause = null
            )
        }

        val json = Json { ignoreUnknownKeys = true }
        val tokenResponse = json.parseToJsonElement(body).jsonObject

        val accessToken = tokenResponse["access_token"]?.jsonPrimitive?.content
            ?: throw Oidc4VciException.TokenExchangeFailed(
                reason = "Missing access_token in token response",
                credentialIssuer = tokenEndpoint
            )

        return TokenResponse(
            accessToken = accessToken,
            cNonce = tokenResponse["c_nonce"]?.jsonPrimitive?.contentOrNull
        )
    }

    /**
     * Extracts the pre-authorized code from a credential offer's grants map
     * (`grants["urn:ietf:params:oauth:grant-type:pre-authorized_code"]["pre-authorized_code"]`).
     */
    private fun extractPreAuthorizedCode(grants: Map<String, Any?>): String? =
        when (val grant = grants[PRE_AUTHORIZED_CODE_GRANT_TYPE]) {
            is String -> grant
            is Map<*, *> -> grant["pre-authorized_code"] as? String
            is JsonObject -> (grant["pre-authorized_code"] as? JsonPrimitive)?.contentOrNull
            is JsonPrimitive -> grant.contentOrNull
            else -> null
        }

    /**
     * Creates credential request payload with proof of possession.
     */
    private suspend fun createCredentialRequestPayload(
        credentialTypes: List<String>,
        holderDid: String,
        keyId: String,
        audience: String,
        cNonce: String?
    ): JsonObject {
        // Create proof of possession (JWT) signed with the holder's key
        val proofJwt = createProofOfPossessionJwt(holderDid, keyId, audience, cNonce)

        return buildJsonObject {
            put("format", "vc+sd-jwt") // or "jwt_vc_json" or "ldp_vc"
            put("credential_definition", buildJsonObject {
                put("type", JsonArray(credentialTypes.map { JsonPrimitive(it) }))
            })
            put("proof", buildJsonObject {
                put("proof_type", "jwt")
                put("jwt", proofJwt)
            })
        }
    }

    /**
     * Creates a proof of possession JWT (OID4VCI v1.0 §7.2.1.1).
     *
     * The `nonce` claim carries the `c_nonce` issued by the token endpoint (or the most recent
     * one returned by the credential endpoint); the `aud` claim is the credential issuer
     * identifier. The JOSE `alg` is `EdDSA` (RFC 8037) for Ed25519 keys.
     */
    private suspend fun createProofOfPossessionJwt(
        holderDid: String,
        keyId: String,
        audience: String,
        cNonce: String?
    ): String {
        // Create JWT header
        val header = buildJsonObject {
            put("alg", "EdDSA")
            put("typ", "openid4vci-proof+jwt")
            put("kid", keyId)
        }

        // Create JWT payload
        val now = System.currentTimeMillis() / 1000
        val payload = buildJsonObject {
            put("iss", holderDid)
            put("aud", audience)
            put("iat", now)
            put("exp", now + 3600) // 1 hour expiration
            cNonce?.let { put("nonce", it) }
        }

        // Encode header and payload
        val json = Json { prettyPrint = false; encodeDefaults = false }
        val headerBase64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(json.encodeToString(JsonObject.serializer(), header).toByteArray(Charsets.UTF_8))
        val payloadBase64 = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(json.encodeToString(JsonObject.serializer(), payload).toByteArray(Charsets.UTF_8))

        // Sign JWT (in production, use KMS to sign)
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
     * Requests credential from issuer endpoint.
     */
    private suspend fun requestCredentialFromIssuer(
        credentialEndpoint: String,
        accessToken: String,
        credentialRequest: JsonObject
    ): Map<String, Any?> {
        val json = Json { prettyPrint = false; encodeDefaults = false }
        val requestBody = json.encodeToString(JsonObject.serializer(), credentialRequest)
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(credentialEndpoint)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $accessToken")
            .addHeader("Content-Type", "application/json")
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string()
            ?: throw Oidc4VciException.CredentialRequestFailed(
                reason = "Empty response body",
                credentialIssuer = credentialEndpoint
            )

        val jsonParser = Json { ignoreUnknownKeys = true }

        if (!response.isSuccessful) {
            // OID4VCI v1.0 §7.3.1: an invalid_proof error may carry a fresh c_nonce the
            // wallet must use for its next proof of possession. Only that specific error
            // code triggers a retry — a stray c_nonce in an unrelated error response
            // (e.g. HTTP 500) must not.
            val errorJson = runCatching { jsonParser.parseToJsonElement(body).jsonObject }.getOrNull()
            val errorCode = errorJson?.get("error")?.jsonPrimitive?.contentOrNull
            val freshNonce = errorJson?.get("c_nonce")?.jsonPrimitive?.contentOrNull
            if (errorCode == "invalid_proof" && freshNonce != null) {
                throw FreshNonceRequired(freshNonce, "HTTP ${response.code}: $body")
            }
            throw Oidc4VciException.CredentialRequestFailed(
                reason = "HTTP ${response.code}: $body",
                credentialIssuer = credentialEndpoint
            )
        }

        val credentialResponse = jsonParser.parseToJsonElement(body).jsonObject

        return credentialResponse.toMap()
    }

    /**
     * Builds a credential offer URI (OID4VCI v1.0 §4.1).
     *
     * The offer is a single URL-encoded JSON object carried in the `credential_offer`
     * query parameter:
     * `openid-credential-offer://?credential_offer=%7B%22credential_issuer%22...%7D`
     *
     * When [txCode] is non-null it is embedded in the pre-authorized code grant object
     * (`grants["urn:ietf:params:oauth:grant-type:pre-authorized_code"].tx_code`, §4.1.1).
     */
    private fun buildCredentialOfferUri(
        credentialIssuer: String,
        credentialTypes: List<String>,
        grants: Map<String, Any?>,
        txCode: TxCode? = null,
    ): String {
        val grantsJson = buildMap<String, JsonElement> {
            grants.forEach { (key, value) -> put(key, anyToJsonElement(value)) }
            if (txCode != null) {
                val json = Json { encodeDefaults = false }
                val txCodeJson = json.encodeToJsonElement(TxCode.serializer(), txCode)
                val preAuthGrant = (get(PRE_AUTHORIZED_CODE_GRANT_TYPE) as? JsonObject) ?: JsonObject(emptyMap())
                put(PRE_AUTHORIZED_CODE_GRANT_TYPE, JsonObject(preAuthGrant + ("tx_code" to txCodeJson)))
            }
        }

        val offerJson = buildJsonObject {
            put("credential_issuer", credentialIssuer)
            put("credential_configuration_ids", JsonArray(credentialTypes.map { JsonPrimitive(it) }))
            if (grantsJson.isNotEmpty()) {
                put("grants", JsonObject(grantsJson))
            }
        }

        val encodedOffer = URLEncoder.encode(
            Json.encodeToString(JsonObject.serializer(), offerJson),
            "UTF-8"
        )
        return "openid-credential-offer://?credential_offer=$encodedOffer"
    }

    /**
     * Parses a credential offer URI (OID4VCI v1.0 §4.1) — the symmetric counterpart of
     * [createCredentialOffer]'s URI generation.
     *
     * Supports both offer-by-value (`credential_offer=<url-encoded JSON>`) and
     * offer-by-reference (`credential_offer_uri=<https URL>`, fetched via HTTP GET).
     *
     * The parsed offer is registered so it can be used with [createCredentialRequest].
     *
     * **Security:** one service instance is bound to one credential issuer
     * ([credentialIssuerUrl]). An offer whose `credential_issuer` names a *different*
     * issuer is rejected (modulo trailing-slash normalization) — otherwise an
     * attacker-supplied QR code could poison this instance's pinned metadata and
     * token-endpoint caches with endpoints belonging to another issuer.
     *
     * @param offerUri Offer URI, e.g. from a scanned QR code
     * @return The parsed (and registered) [Oidc4VciOffer]
     */
    suspend fun parseCredentialOfferUri(offerUri: String): Oidc4VciOffer = withContext(Dispatchers.IO) {
        val queryParams = parseQueryParameters(offerUri.substringAfter("?", ""))

        val offerJsonString = queryParams["credential_offer"]
            ?: queryParams["credential_offer_uri"]?.let { fetchCredentialOfferByReference(it) }
            ?: throw Oidc4VciException.OfferParseFailed(
                offerUri = offerUri,
                reason = "Missing 'credential_offer' or 'credential_offer_uri' query parameter"
            )

        val offerJson = runCatching {
            Json.parseToJsonElement(offerJsonString).jsonObject
        }.getOrElse {
            throw Oidc4VciException.OfferParseFailed(
                offerUri = offerUri,
                reason = "credential_offer is not a valid JSON object: ${it.message}"
            )
        }

        val credentialIssuer = offerJson["credential_issuer"]?.jsonPrimitive?.contentOrNull
            ?: throw Oidc4VciException.OfferParseFailed(
                offerUri = offerUri,
                reason = "Missing 'credential_issuer' in credential_offer"
            )

        // Cross-issuer cache guard: this instance's metadata / resolvedTokenEndpoint
        // caches are pinned to credentialIssuerUrl. Registering an offer for another
        // issuer would let those pinned caches serve the wrong issuer's endpoints
        // (token-endpoint confusion). One service instance == one issuer.
        if (credentialIssuer.trimEnd('/') != credentialIssuerUrl.trimEnd('/')) {
            throw Oidc4VciException.OfferParseFailed(
                offerUri = offerUri,
                reason = "credential_offer is for issuer '$credentialIssuer' but this service " +
                    "instance is configured for '$credentialIssuerUrl'; cross-issuer offers " +
                    "are rejected"
            )
        }

        val credentialConfigurationIds = offerJson["credential_configuration_ids"]?.jsonArray
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?: emptyList()

        val grantsJson = offerJson["grants"] as? JsonObject
        val txCode = (grantsJson?.get(PRE_AUTHORIZED_CODE_GRANT_TYPE) as? JsonObject)
            ?.get("tx_code")
            ?.let { element ->
                runCatching {
                    Json { ignoreUnknownKeys = true }.decodeFromJsonElement(TxCode.serializer(), element)
                }.getOrNull()
            }

        val offer = Oidc4VciOffer(
            offerId = UUID.randomUUID().toString(),
            credentialIssuer = credentialIssuer,
            credentialTypes = credentialConfigurationIds,
            offerUri = offerUri,
            grants = grantsJson?.toMap() ?: emptyMap(),
            txCode = txCode,
        )

        offers[offer.offerId] = offer
        offer
    }

    /**
     * Fetches a credential offer JSON document by reference (`credential_offer_uri`).
     *
     * Only `https` URIs are accepted; plain `http` is allowed exclusively for loopback
     * hosts (`localhost`, `127.0.0.1`, `::1`) to support local development and tests.
     * This prevents a malicious offer from steering the wallet to cleartext or
     * link-local/metadata endpoints (e.g. `http://169.254.169.254/...`).
     */
    private fun fetchCredentialOfferByReference(credentialOfferUri: String): String {
        requireHttpsOfferUri(credentialOfferUri)

        val request = try {
            Request.Builder()
                .url(credentialOfferUri)
                .get()
                .build()
        } catch (e: IllegalArgumentException) {
            // OkHttp rejects non-HTTP(S) or otherwise malformed URLs with an IAE —
            // surface it as a typed parse failure instead of a raw runtime exception.
            throw Oidc4VciException.OfferParseFailed(
                offerUri = credentialOfferUri,
                reason = "Invalid credential_offer_uri: ${e.message}",
                cause = e
            )
        }

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string()
            ?: throw Oidc4VciException.OfferParseFailed(
                offerUri = credentialOfferUri,
                reason = "Empty response body from credential_offer_uri"
            )

        if (!response.isSuccessful) {
            throw Oidc4VciException.OfferParseFailed(
                offerUri = credentialOfferUri,
                reason = "HTTP ${response.code}: $body"
            )
        }

        return body
    }

    /**
     * Enforces the https-only policy for `credential_offer_uri` (http allowed for loopback).
     *
     * @throws Oidc4VciException.OfferParseFailed when the URI is malformed, uses a
     *   non-http(s) scheme, or uses http against a non-loopback host
     */
    private fun requireHttpsOfferUri(credentialOfferUri: String) =
        requireHttpsOrLoopback(credentialOfferUri) { reason ->
            Oidc4VciException.OfferParseFailed(
                offerUri = credentialOfferUri,
                reason = "credential_offer_uri $reason"
            )
        }

    /**
     * Enforces the https-or-loopback URL policy: `https` is always allowed; plain `http`
     * is allowed exclusively for loopback hosts (`localhost`, `127.0.0.1`, `::1`) to
     * support local development and tests. Everything else — including non-http(s)
     * schemes and malformed URIs — is a violation.
     *
     * This protects every URL an attacker can steer via offer QR codes or fetched
     * metadata (credential_offer_uri, authorization server URLs, token endpoints) from
     * pointing at cleartext or link-local/metadata endpoints (e.g. `http://169.254.169.254/`).
     *
     * @param onViolation builds the *typed* exception to throw; receives a human-readable
     *   reason fragment (e.g. "must use https (got scheme 'http'); ...")
     */
    private inline fun requireHttpsOrLoopback(
        url: String,
        onViolation: (reason: String) -> Oidc4VciException,
    ) {
        val violation = httpsOrLoopbackViolation(url) ?: return
        throw onViolation(violation)
    }

    /**
     * Returns the policy-violation reason for [url], or `null` when the URL satisfies
     * the https-or-loopback policy. Performs no network I/O (IP literals are checked
     * without DNS resolution).
     */
    private fun httpsOrLoopbackViolation(url: String): String? {
        val uri = runCatching { java.net.URI(url) }.getOrElse {
            return "is not a valid URI: ${it.message}"
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme == "https") return null
        if (scheme == "http" && isLoopbackHost(uri.host?.lowercase())) return null
        return "must use https (got scheme '${scheme ?: "none"}'); " +
            "plain http is only allowed for localhost/127.0.0.1"
    }

    /**
     * `true` when [host] is loopback (localhost / 127.0.0.1 / ::1). Falls back to address
     * resolution for other loopback-mapped names; IP literals resolve without a DNS lookup.
     */
    private fun isLoopbackHost(host: String?): Boolean {
        if (host == null) return false
        if (host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "[::1]") return true
        return runCatching { java.net.InetAddress.getByName(host).isLoopbackAddress }.getOrDefault(false)
    }

    /**
     * Parses query parameters from a URL query string.
     */
    private fun parseQueryParameters(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()

        return query.split("&").associate { param ->
            val parts = param.split("=", limit = 2)
            val key = URLDecoder.decode(parts[0], "UTF-8")
            val value = if (parts.size > 1) URLDecoder.decode(parts[1], "UTF-8") else ""
            key to value
        }
    }

    /**
     * Converts an arbitrary grants value into a [JsonElement] for offer serialization.
     */
    private fun anyToJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is JsonElement -> value
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to anyToJsonElement(v) })
        is List<*> -> JsonArray(value.map { anyToJsonElement(it) })
        else -> JsonPrimitive(value.toString())
    }

    /**
     * Fetches credential issuer metadata.
     *
     * @param credentialIssuerUrl Credential issuer URL
     * @return Credential issuer metadata
     */
    suspend fun fetchCredentialIssuerMetadata(
        credentialIssuerUrl: String
    ): CredentialIssuerMetadata = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$credentialIssuerUrl/.well-known/openid-credential-issuer")
            .get()
            .build()

        val response = httpClient.newCall(request).execute()
        val body = response.body?.string()
        ?: throw Oidc4VciException.MetadataFetchFailed(
            credentialIssuer = credentialIssuerUrl,
            reason = "Empty response body"
        )

        if (!response.isSuccessful) {
            throw Oidc4VciException.MetadataFetchFailed(
                credentialIssuer = credentialIssuerUrl,
                reason = "HTTP ${response.code}: $body"
            )
        }

        val json = Json { ignoreUnknownKeys = true }
        json.decodeFromString<CredentialIssuerMetadata>(body)
    }

    /**
     * Converts JsonObject to Map for credential response.
     */
    private fun JsonObject.toMap(): Map<String, Any?> {
        return entries.associate { (key, value) ->
            key to when (value) {
                is JsonPrimitive -> {
                    when {
                        value.isString -> value.content
                        value.booleanOrNull != null -> value.boolean
                        value.longOrNull != null -> value.long
                        value.doubleOrNull != null -> value.double
                        else -> value.content
                    }
                }
                is JsonObject -> value.toMap()
                is JsonArray -> value.map { element ->
                    when (element) {
                        is JsonPrimitive -> element.content
                        is JsonObject -> element.toMap()
                        else -> element.toString()
                    }
                }
                else -> value.toString()
            }
        }
    }
}
