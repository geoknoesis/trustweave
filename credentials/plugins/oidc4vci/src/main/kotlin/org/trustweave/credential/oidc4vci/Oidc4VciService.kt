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
    private val offers = mutableMapOf<String, Oidc4VciOffer>()
    private val requests = mutableMapOf<String, Oidc4VciCredentialRequest>()
    private val accessTokens = mutableMapOf<String, String>() // requestId -> accessToken
    private var metadata: CredentialIssuerMetadata? = null

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
     * @return OIDC4VCI offer
     */
    suspend fun createCredentialOffer(
        issuerDid: String,
        credentialTypes: List<String>,
        credentialIssuer: String,
        grants: Map<String, Any?> = emptyMap()
    ): Oidc4VciOffer = withContext(Dispatchers.IO) {
        val offerId = UUID.randomUUID().toString()

        // Create credential offer URI
        // Format: openid-credential-offer://?credential_offer_uri=<url>
        // or: openid-credential-offer://?credential_issuer=<issuer>&credential_configuration_ids=<ids>
        val offerUri = buildCredentialOfferUri(
            credentialIssuer = credentialIssuer,
            credentialTypes = credentialTypes,
            grants = grants
        )

        val offer = Oidc4VciOffer(
            offerId = offerId,
            credentialIssuer = credentialIssuer,
            credentialTypes = credentialTypes,
            offerUri = offerUri,
            grants = grants
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
     * @return Credential request
     */
    suspend fun createCredentialRequest(
        holderDid: String,
        offerId: String,
        redirectUri: String? = null,
        authorizationCode: String? = null
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
            accessToken = accessToken
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
     * @param issuerDid Issuer DID
     * @param holderDid Holder DID
     * @param credential The credential envelope to issue
     * @param requestId Credential request ID
     * @return Issue result
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

        // The credential from the response is the one issued by the issuer
        // In production, parse credentialResponse and convert to CredentialEnvelope
        Oidc4VciIssueResult(
            issueId = issueId,
            credential = credential,
            credentialResponse = credentialResponse
        )
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
     */
    private fun buildCredentialOfferUri(
        credentialIssuer: String,
        credentialTypes: List<String>,
        grants: Map<String, Any?>
    ): String {
        // Build query parameters
        val params = mutableListOf<String>()
        params.add("credential_issuer=$credentialIssuer")
        params.add("credential_configuration_ids=${credentialTypes.joinToString(",")}")

        grants.forEach { (key, value) ->
            params.add("$key=$value")
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
