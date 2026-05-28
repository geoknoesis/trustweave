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
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    private val offers = ConcurrentHashMap<String, Oidc4VciOffer>()
    private val requests = ConcurrentHashMap<String, Oidc4VciCredentialRequest>()
    private val accessTokens = ConcurrentHashMap<String, String>() // requestId -> accessToken
    @Volatile private var metadata: CredentialIssuerMetadata? = null

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

        // Create credential offer URI
        // Format: openid-credential-offer://?credential_offer_uri=<url>
        // or: openid-credential-offer://?credential_issuer=<issuer>&credential_configuration_ids=<ids>
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
        txCodeValue: String? = null,
    ): Oidc4VciCredentialRequest = withContext(Dispatchers.IO) {
        val offer = offers[offerId]
            ?: throw TrustWeaveException.NotFound(resource = "OIDC4VCI offer: $offerId")

        // Fetch credential issuer metadata if not cached
        if (metadata == null) {
            metadata = fetchCredentialIssuerMetadata(offer.credentialIssuer)
        }
        val issuerMetadata = metadata ?: throw Oidc4VciException.MetadataFetchFailed(
            credentialIssuer = offer.credentialIssuer,
            reason = "Metadata fetch returned null"
        )

        val requestId = UUID.randomUUID().toString()

        // Step 1: Exchange authorization code for access token (if using auth code flow)
        val accessToken = if (authorizationCode != null && redirectUri != null) {
            exchangeAuthorizationCodeForToken(
                authorizationCode = authorizationCode,
                redirectUri = redirectUri,
                tokenEndpoint = issuerMetadata.tokenEndpoint
            )
        } else {
            // Pre-authorized code flow or direct token
            null // Will be obtained during credential request
        }

        if (accessToken != null) {
            accessTokens[requestId] = accessToken
        }

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

        // Step 1: Create credential request with proof of possession
        val credentialRequest = createCredentialRequestPayload(
            credentialTypes = request.credentialTypes,
            holderDid = holderDid,
            keyId = "$holderDid#key-1" // In production, get actual key ID
        )

        // Step 2: Send credential request to credential endpoint
        val credentialResponse = requestCredentialFromIssuer(
            credentialEndpoint = issuerMetadata.credentialEndpoint,
            accessToken = accessToken,
            credentialRequest = credentialRequest
        )

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
     * Exchanges authorization code for access token.
     */
    private suspend fun exchangeAuthorizationCodeForToken(
        authorizationCode: String,
        redirectUri: String,
        tokenEndpoint: String
    ): String {
        val requestBody = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", authorizationCode)
            .add("redirect_uri", redirectUri)
            .build()

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

        return tokenResponse["access_token"]?.jsonPrimitive?.content
            ?: throw Oidc4VciException.TokenExchangeFailed(
                reason = "Missing access_token in token response",
                credentialIssuer = tokenEndpoint
            )
    }

    /**
     * Creates credential request payload with proof of possession.
     */
    private suspend fun createCredentialRequestPayload(
        credentialTypes: List<String>,
        holderDid: String,
        keyId: String
    ): JsonObject {
        // Create proof of possession (JWT)
        // In production, this would be a proper JWT signed with holder's key
        val proofJwt = createProofOfPossessionJwt(holderDid, keyId)

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
     * Creates a proof of possession JWT.
     * In production, this should be properly signed with holder's private key.
     */
    private suspend fun createProofOfPossessionJwt(
        holderDid: String,
        keyId: String
    ): String {
        // Create JWT header
        val header = buildJsonObject {
            put("alg", "Ed25519")
            put("typ", "openid4vci-proof+jwt")
            put("kid", keyId)
        }

        // Create JWT payload
        val now = System.currentTimeMillis() / 1000
        val payload = buildJsonObject {
            put("iss", holderDid)
            put("aud", credentialIssuerUrl)
            put("iat", now)
            put("exp", now + 3600) // 1 hour expiration
            put("nonce", UUID.randomUUID().toString())
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

        if (!response.isSuccessful) {
            throw Oidc4VciException.CredentialRequestFailed(
                reason = "HTTP ${response.code}: $body",
                credentialIssuer = credentialEndpoint
            )
        }

        val jsonParser = Json { ignoreUnknownKeys = true }
        val credentialResponse = jsonParser.parseToJsonElement(body).jsonObject

        return credentialResponse.toMap()
    }

    /**
     * Builds a credential offer URI.
     *
     * Includes `tx_code` JSON in the offer when [txCode] is non-null (OID4VCI v1.0 §4.1.1).
     */
    private fun buildCredentialOfferUri(
        credentialIssuer: String,
        credentialTypes: List<String>,
        grants: Map<String, Any?>,
        txCode: TxCode? = null,
    ): String {
        // Build query parameters
        val params = mutableListOf<String>()
        params.add("credential_issuer=$credentialIssuer")
        params.add("credential_configuration_ids=${credentialTypes.joinToString(",")}")

        grants.forEach { (key, value) ->
            params.add("$key=$value")
        }

        if (txCode != null) {
            val json = Json { encodeDefaults = false }
            val txCodeJson = json.encodeToString(TxCode.serializer(), txCode)
            params.add("tx_code=${java.net.URLEncoder.encode(txCodeJson, "UTF-8")}")
        }

        return "openid-credential-offer://?${params.joinToString("&")}"
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
