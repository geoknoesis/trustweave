package org.trustweave.credential.oidc4vp

import org.trustweave.core.identifiers.KeyId
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.credential.exchange.exception.ExchangeException
import org.trustweave.credential.model.vc.CredentialProof
import org.trustweave.credential.model.vc.Issuer
import org.trustweave.credential.model.vc.VerifiableCredential
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.oidc4vp.exception.Oidc4VpException
import org.trustweave.credential.oidc4vp.haip.HaipProfileValidator
import org.trustweave.credential.oidc4vp.models.*
import org.trustweave.credential.oidc4vp.models.ClientIdScheme
import org.trustweave.credential.oidc4vp.session.InMemorySessionStore
import org.trustweave.credential.oidc4vp.session.SessionStore
import org.trustweave.credential.pex.DescriptorMap
import org.trustweave.credential.pex.PresentationDefinition
import org.trustweave.credential.pex.PresentationDefinitionMatcher
import org.trustweave.credential.pex.PresentationSubmission
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.SignResult
import com.nimbusds.jose.JWSVerifier
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.crypto.Ed25519Verifier
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.OctetKeyPair
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTParser
import com.nimbusds.jwt.PlainJWT
import com.nimbusds.jwt.SignedJWT
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
private val lenientJson = Json { ignoreUnknownKeys = true }

private const val W3C_CREDENTIALS_V1_CONTEXT = "https://www.w3.org/2018/credentials/v1"

/**
 * Json configuration for serializing credential/presentation models into vp_token JSON.
 *
 * `encodeDefaults = true` keeps required W3C fields with default values (e.g. `@context`);
 * `explicitNulls = false` omits absent optional fields.
 */
private val vpJson = Json {
    prettyPrint = false
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
    classDiscriminator = "@type" // avoid conflict with LinkedDataProof.type
}

class Oidc4VpService(
    private val kms: KeyManagementService,
    private val httpClient: OkHttpClient = OkHttpClient(),
    /**
     * When `true`, [parseAuthorizationUrl] enforces HAIP constraints:
     * - `client_id_scheme` must be `did`, `x509_san_dns`, or `verifier_attestation`
     * - `response_mode` must be `direct_post` or `direct_post.jwt`
     * - `response_uri` is required for direct_post modes
     * - `nonce` is required
     * - Either `presentation_definition` or `dcql_query` must be present
     *
     * Throws [org.trustweave.credential.oidc4vp.exception.Oidc4VpException.HaipViolationException]
     * on violation.
     */
    val haipMode: Boolean = false,
    /**
     * Store for in-flight [PermissionRequest] objects.
     *
     * Defaults to [InMemorySessionStore]. Inject a DB-backed implementation to survive
     * restarts or share state across nodes.
     */
    val sessionStore: SessionStore = InMemorySessionStore(),
) {
    @Volatile private var metadata: VerifierMetadata? = null

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
            val clientIdScheme = queryParams["client_id_scheme"]
                ?.let { ClientIdScheme.fromString(it) }
                ?: ClientIdScheme.PRE_REGISTERED
            val requestUri = queryParams["request_uri"]
            val responseUri = queryParams["response_uri"]
            val redirectUri = queryParams["redirect_uri"]
            val responseMode = queryParams["response_mode"]
            val nonce = queryParams["nonce"]
            val state = queryParams["state"]

            // Parse dcql_query into typed DcqlQuery if present
            val dcqlQuery = queryParams["dcql_query"]?.let { raw ->
                try {
                    lenientJson.decodeFromString<DcqlQuery>(raw)
                } catch (_: Exception) {
                    null
                }
            }

            // presentation_definition may be a JSON string embedded directly in the URL param
            val urlPresentationDefinition = queryParams["presentation_definition"]?.let { raw ->
                try {
                    Json.parseToJsonElement(raw).jsonObject
                } catch (_: Exception) {
                    null
                }
            }

            // client_metadata may be a JSON string embedded directly in the URL param
            val urlClientMetadata = queryParams["client_metadata"]?.let { raw ->
                try {
                    Json.parseToJsonElement(raw).jsonObject
                } catch (_: Exception) {
                    null
                }
            }

            // Build base AuthorizationRequest from URL params
            val urlRequest = AuthorizationRequest(
                responseUri = responseUri,
                redirectUri = redirectUri,
                clientId = clientId,
                clientIdScheme = clientIdScheme,
                requestUri = requestUri,
                presentationDefinition = urlPresentationDefinition,
                nonce = nonce,
                state = state,
                responseMode = responseMode,
                dcqlQuery = dcqlQuery,
                clientMetadata = urlClientMetadata,
            )

            // If request_uri is present, fetch the request object.
            // - Signed JWT request object (JAR / OID4VP §5.10): its claims are authoritative —
            //   URL query parameters MUST NOT override or fill in request parameters.
            // - Plain JSON request: fetched values take precedence, URL params fill gaps.
            val authorizationRequest = if (requestUri != null) {
                val (fetched, fromSignedRequestObject) = fetchAuthorizationRequest(requestUri)
                if (fromSignedRequestObject) {
                    fetched.copy(requestUri = requestUri)
                } else {
                    AuthorizationRequest(
                        responseUri = fetched.responseUri ?: urlRequest.responseUri,
                        redirectUri = fetched.redirectUri ?: urlRequest.redirectUri,
                        clientId = fetched.clientId ?: urlRequest.clientId,
                        clientIdScheme = if (fetched.clientIdScheme != ClientIdScheme.PRE_REGISTERED)
                            fetched.clientIdScheme else urlRequest.clientIdScheme,
                        requestUri = requestUri,
                        presentationDefinition = fetched.presentationDefinition ?: urlRequest.presentationDefinition,
                        nonce = fetched.nonce ?: urlRequest.nonce,
                        state = fetched.state ?: urlRequest.state,
                        responseMode = fetched.responseMode ?: urlRequest.responseMode,
                        dcqlQuery = fetched.dcqlQuery ?: urlRequest.dcqlQuery,
                        clientMetadata = fetched.clientMetadata ?: urlRequest.clientMetadata,
                    )
                }
            } else {
                // No request_uri: all data must come from URL params directly
                if (urlRequest.responseUri == null && urlRequest.redirectUri == null) {
                    throw Oidc4VpException.UrlParseFailed(
                        url = authorizationUrl,
                        reason = "Missing both 'request_uri' and response endpoint ('response_uri' / 'redirect_uri')"
                    )
                }
                urlRequest
            }

            // HAIP compliance check — runs before any further processing
            if (haipMode) {
                val violations = HaipProfileValidator.validateAuthorizationRequest(authorizationRequest)
                if (violations.isNotEmpty()) {
                    throw Oidc4VpException.HaipViolationException(violations)
                }
            }

            val requestId = UUID.randomUUID().toString()

            // Extract requested credential types and claims from presentation_definition
            val requestedCredentialTypes = extractCredentialTypes(authorizationRequest.presentationDefinition)
            val requestedClaims = extractRequestedClaims(authorizationRequest.presentationDefinition)

            val verifierUrl = requestUri?.let { extractVerifierUrl(it) }
                ?: authorizationRequest.responseUri?.let { extractVerifierUrl(it) }
                ?: authorizationRequest.redirectUri?.let { extractVerifierUrl(it) }

            val permissionRequest = PermissionRequest(
                requestId = requestId,
                authorizationRequest = authorizationRequest,
                verifierUrl = verifierUrl,
                requestedCredentialTypes = requestedCredentialTypes,
                requestedClaims = requestedClaims
            )

            sessionStore.put(requestId, permissionRequest)
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
        val authorizationRequest = permissionRequest.authorizationRequest

        // Credentials embedded in the vp_token, in array order — presentation_submission
        // descriptor paths are aligned with this order.
        val vpCredentials = presentation?.verifiableCredential
            ?: selectedCredentials.map { it.credential }

        val vpToken = if (presentation != null) {
            createVpTokenJwt(presentation, holderDid, keyId, authorizationRequest)
        } else {
            createVpTokenJwtFromCredentials(
                credentials = vpCredentials,
                holderDid = holderDid,
                keyId = keyId,
                authorizationRequest = authorizationRequest
            )
        }

        PermissionResponse(
            responseId = UUID.randomUUID().toString(),
            requestId = permissionRequest.requestId,
            vpToken = vpToken,
            presentationSubmission = buildPresentationSubmission(
                presentationDefinition = authorizationRequest.presentationDefinition,
                credentials = vpCredentials,
            ),
            state = authorizationRequest.state
        )
    }

    /**
     * Submits a PermissionResponse to the verifier.
     *
     * @param permissionResponse The permission response to submit
     */
    suspend fun submitPermissionResponse(permissionResponse: PermissionResponse) = withContext(Dispatchers.IO) {
        val request = sessionStore.get(permissionResponse.requestId)
            ?: throw ExchangeException.RequestNotFound(requestId = permissionResponse.requestId)
        
        val responseUri = request.authorizationRequest.effectiveResponseEndpoint
            ?: throw Oidc4VpException.PresentationSubmissionFailed(
                reason = "No response endpoint available (neither response_uri nor redirect_uri)",
                verifierUrl = request.verifierUrl ?: "unknown"
            )
        
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

    /** Result of fetching a request_uri: the parsed request and whether it was a signed JWT request object. */
    private data class FetchedAuthorizationRequest(
        val request: AuthorizationRequest,
        val fromSignedRequestObject: Boolean,
    )

    /**
     * Fetches the authorization request from request_uri.
     *
     * The response may be a plain JSON document or a JWT request object
     * (`application/oauth-authz-req+jwt`). JWT request objects must be signed —
     * unsigned (`alg: none`) request objects are rejected, and the signature is
     * verified against the verifier keys in `client_metadata.jwks` when present.
     */
    private suspend fun fetchAuthorizationRequest(requestUri: String): FetchedAuthorizationRequest {
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

        val trimmed = body.trim()
        return if (trimmed.startsWith("{")) {
            val jsonElement = lenientJson.parseToJsonElement(trimmed).jsonObject
            FetchedAuthorizationRequest(
                request = buildAuthorizationRequestFromJson(jsonElement),
                fromSignedRequestObject = false,
            )
        } else {
            FetchedAuthorizationRequest(
                request = parseRequestObjectJwt(trimmed, requestUri),
                fromSignedRequestObject = true,
            )
        }
    }

    /**
     * Parses (and verifies) a JWT request object fetched from request_uri.
     *
     * Security:
     * - `alg: none` (unsigned) request objects are refused.
     * - Encrypted request objects are not supported and refused.
     * - When the request object carries verifier keys (`client_metadata.jwks`), the JWS
     *   signature MUST verify against one of them; otherwise the request is rejected.
     * - Without resolvable keys the claims are accepted unverified (best effort) —
     *   trust then rests on the TLS channel to the request_uri host.
     *
     * **Known limitation — `client_metadata.jwks` is self-attested.** The JWK Set used for
     * signature verification is carried inside the request object itself, so a successful
     * verification only proves internal consistency: whoever minted the request object
     * controls both the keys and the signature. It does NOT authenticate the verifier.
     * Real trust requires resolving signing keys pinned by the `client_id` scheme — e.g.
     * DID resolution of the `client_id` for `client_id_scheme=did`, or X.509 SAN/chain
     * validation for `x509_san_dns` / `verifier_attestation` — which is not implemented
     * here yet. Until then, trust in the request content ultimately rests on the TLS
     * channel to the request_uri host.
     */
    private fun parseRequestObjectJwt(jwtString: String, requestUri: String): AuthorizationRequest {
        val jwt = try {
            JWTParser.parse(jwtString)
        } catch (e: Exception) {
            throw Oidc4VpException.AuthorizationRequestFetchFailed(
                requestUri = requestUri,
                reason = "Request object is neither a JSON document nor a valid JWT: ${e.message}"
            )
        }

        val signedJwt = when (jwt) {
            is PlainJWT -> throw Oidc4VpException.AuthorizationRequestFetchFailed(
                requestUri = requestUri,
                reason = "Unsigned request object (alg=none) is not accepted"
            )
            is SignedJWT -> jwt
            else -> throw Oidc4VpException.AuthorizationRequestFetchFailed(
                requestUri = requestUri,
                reason = "Unsupported request object type: ${jwt.javaClass.simpleName}"
            )
        }

        val claimsJson = lenientJson.parseToJsonElement(signedJwt.payload.toString()).jsonObject

        val jwks = (claimsJson["client_metadata"] as? JsonObject)
            ?.get("jwks")
            ?.let { runCatching { JWKSet.parse(it.toString()) }.getOrNull() }

        if (jwks != null && jwks.keys.isNotEmpty()) {
            verifyRequestObjectSignature(signedJwt, jwks, requestUri)
        }

        return buildAuthorizationRequestFromJson(claimsJson)
    }

    /**
     * Verifies the JWS signature of a request object against the verifier's JWK Set.
     *
     * @throws Oidc4VpException.AuthorizationRequestFetchFailed when no key verifies the signature
     */
    private fun verifyRequestObjectSignature(jwt: SignedJWT, jwks: JWKSet, requestUri: String) {
        val kid = jwt.header.keyID
        val candidates = jwks.keys.filter { kid == null || it.keyID == null || it.keyID == kid }

        val verified = candidates.any { jwk ->
            val verifier = verifierFor(jwk) ?: return@any false
            runCatching { jwt.verify(verifier) }.getOrDefault(false)
        }

        if (!verified) {
            throw Oidc4VpException.AuthorizationRequestFetchFailed(
                requestUri = requestUri,
                reason = "Request object signature verification failed against client_metadata jwks"
            )
        }
    }

    /** Builds a [JWSVerifier] for the given JWK, or `null` if the key type is unsupported. */
    private fun verifierFor(jwk: JWK): JWSVerifier? = try {
        when (jwk) {
            is ECKey -> ECDSAVerifier(jwk.toPublicJWK())
            is RSAKey -> RSASSAVerifier(jwk)
            is OctetKeyPair -> Ed25519Verifier(jwk.toPublicJWK())
            else -> null
        }
    } catch (_: Throwable) {
        null
    }

    /**
     * Builds an [AuthorizationRequest] from a JSON document (plain JSON request or
     * JWT request object claims).
     */
    private fun buildAuthorizationRequestFromJson(jsonElement: JsonObject): AuthorizationRequest {
        // presentation_definition may be a nested JsonObject or an embedded JSON string
        val presentationDefinition = jsonElement["presentation_definition"]?.let { elem ->
            when {
                elem is JsonObject -> elem
                elem is JsonPrimitive && elem.isString -> try {
                    Json.parseToJsonElement(elem.content).jsonObject
                } catch (_: Exception) {
                    null
                }
                else -> null
            }
        }

        val dcqlQuery = jsonElement["dcql_query"]?.let { elem ->
            try {
                when {
                    elem is JsonObject -> lenientJson.decodeFromJsonElement<DcqlQuery>(elem)
                    elem is JsonPrimitive && elem.isString -> lenientJson.decodeFromString<DcqlQuery>(elem.content)
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }

        return AuthorizationRequest(
            responseUri = jsonElement["response_uri"]?.jsonPrimitive?.content,
            redirectUri = jsonElement["redirect_uri"]?.jsonPrimitive?.content,
            clientId = jsonElement["client_id"]?.jsonPrimitive?.content,
            clientIdScheme = jsonElement["client_id_scheme"]?.jsonPrimitive?.content
                ?.let { ClientIdScheme.fromString(it) }
                ?: ClientIdScheme.PRE_REGISTERED,
            requestUri = jsonElement["request_uri"]?.jsonPrimitive?.content,
            presentationDefinition = presentationDefinition,
            nonce = jsonElement["nonce"]?.jsonPrimitive?.content,
            state = jsonElement["state"]?.jsonPrimitive?.content,
            responseMode = jsonElement["response_mode"]?.jsonPrimitive?.content,
            dcqlQuery = dcqlQuery,
            clientMetadata = jsonElement["client_metadata"] as? JsonObject,
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
        
        lenientJson.decodeFromString<VerifierMetadata>(body)
    }

    /**
     * Creates a VP token JWT from a VerifiablePresentation.
     *
     * The presentation envelope is serialized with the module's kotlinx-serialization models,
     * but the embedded `verifiableCredential` array is replaced with verifier-consumable W3C
     * credential JSON produced by [credentialToW3cJson].
     */
    private suspend fun createVpTokenJwt(
        presentation: VerifiablePresentation,
        holderDid: String,
        keyId: String,
        authorizationRequest: AuthorizationRequest
    ): String {
        val encoded = vpJson.encodeToJsonElement(VerifiablePresentation.serializer(), presentation).jsonObject
        val vp = JsonObject(
            encoded + ("verifiableCredential" to
                JsonArray(presentation.verifiableCredential.map { credentialToW3cJson(it) }))
        )
        return signVpTokenJwt(vp, holderDid, keyId, authorizationRequest)
    }

    /**
     * Creates a VP token JWT wrapping the selected credentials in a W3C Verifiable Presentation.
     */
    private suspend fun createVpTokenJwtFromCredentials(
        credentials: List<VerifiableCredential>,
        holderDid: String,
        keyId: String,
        authorizationRequest: AuthorizationRequest
    ): String {
        val vp = buildJsonObject {
            put("@context", JsonArray(listOf(JsonPrimitive(W3C_CREDENTIALS_V1_CONTEXT))))
            put("type", JsonArray(listOf(JsonPrimitive("VerifiablePresentation"))))
            put("holder", holderDid)
            put("verifiableCredential", JsonArray(credentials.map { credentialToW3cJson(it) }))
        }
        return signVpTokenJwt(vp, holderDid, keyId, authorizationRequest)
    }

    /**
     * Serializes a [VerifiableCredential] into plain W3C VC JSON for embedding in a vp_token.
     *
     * This intentionally mirrors `PresentationDefinitionMatcher.vcToDocument` so that the
     * credentials referenced by `presentation_submission` descriptor paths actually satisfy
     * the JSONPath field paths they were matched on:
     * - `issuer` is a plain IRI string (or a `{"id": ..., "name": ...}` object), never a
     *   polymorphic object with a class-discriminator field;
     * - `credentialSubject` carries the flattened claims next to `id` — no internal
     *   `"claims"` wrapper;
     * - `proof` is a plain W3C proof object (see [w3cProofOf]) or omitted for envelope
     *   proof formats.
     */
    private fun credentialToW3cJson(credential: VerifiableCredential): JsonObject = buildJsonObject {
        put("@context", JsonArray(credential.context.map { JsonPrimitive(it) }))
        credential.id?.let { put("id", it.value) }
        put("type", JsonArray(credential.type.map { JsonPrimitive(it.value) }))
        when (val issuer = credential.issuer) {
            is Issuer.IriIssuer -> put("issuer", issuer.id.value)
            is Issuer.ObjectIssuer -> put(
                "issuer",
                buildJsonObject {
                    put("id", issuer.id.value)
                    issuer.name?.let { put("name", it) }
                    issuer.additionalProperties.forEach { (key, value) -> put(key, value) }
                }
            )
        }
        credential.issuanceDate?.let { put("issuanceDate", it.toString()) }
        credential.validFrom?.let { put("validFrom", it.toString()) }
        credential.expirationDate?.let { put("expirationDate", it.toString()) }
        credential.validUntil?.let { put("validUntil", it.toString()) }
        credential.name?.let { put("name", it) }
        credential.description?.let { put("description", it) }
        credential.credentialStatus?.let { status ->
            put(
                "credentialStatus",
                buildJsonObject {
                    put("id", status.id.value)
                    put("type", status.type)
                }
            )
        }
        credential.credentialSchema?.let { schema ->
            put(
                "credentialSchema",
                buildJsonObject {
                    put("id", schema.id.value)
                    put("type", schema.type)
                }
            )
        }
        put(
            "credentialSubject",
            buildJsonObject {
                credential.credentialSubject.id?.let { put("id", it.value) }
                credential.credentialSubject.claims.forEach { (key, value) -> put(key, value) }
            }
        )
        w3cProofOf(credential.proof)?.let { put("proof", it) }
    }

    /**
     * Maps an embedded data-integrity proof to a plain W3C proof object
     * (`type`/`created`/`verificationMethod`/`proofPurpose`/`proofValue` plus any
     * additional properties such as `jws` for JsonWebSignature2020).
     *
     * Envelope proof formats (VC-JWT, SD-JWT VC, mdoc, JAdES) return `null`: there the
     * proof *is* the credential envelope and has no in-document proof object representation.
     */
    private fun w3cProofOf(proof: CredentialProof?): JsonObject? = when (proof) {
        is CredentialProof.LinkedDataProof -> buildJsonObject {
            put("type", proof.type)
            put("created", proof.created.toString())
            put("verificationMethod", proof.verificationMethod)
            put("proofPurpose", proof.proofPurpose)
            if (proof.proofValue.isNotBlank()) put("proofValue", proof.proofValue)
            proof.additionalProperties.forEach { (key, value) -> put(key, value) }
        }
        else -> null
    }

    /**
     * Signs a VP token JWT embedding [vp] under the `vp` claim.
     *
     * The `aud` claim is the verifier's `client_id` (or, when absent, the response endpoint)
     * from the authorization request; the `nonce` claim echoes the request nonce.
     */
    private suspend fun signVpTokenJwt(
        vp: JsonObject,
        holderDid: String,
        keyId: String,
        authorizationRequest: AuthorizationRequest
    ): String {
        val header = buildJsonObject {
            put("alg", "EdDSA")
            put("typ", "JWT")
            put("kid", keyId)
        }

        val now = System.currentTimeMillis() / 1000
        val payload = buildJsonObject {
            put("iss", holderDid)
            authorizationRequest.audience?.let { put("aud", it) }
            put("iat", now)
            put("exp", now + 3600) // 1 hour expiration
            authorizationRequest.nonce?.let { put("nonce", it) }
            put("vp", vp)
        }

        return signJwt(header, payload, keyId)
    }

    /**
     * Builds the DIF `presentation_submission` for the given presentation definition,
     * mapping each input descriptor to the satisfying credential's position in the
     * vp_token's `verifiableCredential` array.
     *
     * Returns `null` when there is no presentation definition, it cannot be decoded,
     * or no descriptor is satisfied by the supplied credentials.
     */
    private fun buildPresentationSubmission(
        presentationDefinition: JsonObject?,
        credentials: List<VerifiableCredential>,
    ): JsonObject? {
        if (presentationDefinition == null || credentials.isEmpty()) return null

        val definition = try {
            lenientJson.decodeFromJsonElement<PresentationDefinition>(presentationDefinition)
        } catch (_: Exception) {
            return null
        }

        val matches = PresentationDefinitionMatcher.match(definition, credentials)

        val descriptorMap = definition.inputDescriptors.mapNotNull { descriptor ->
            val matched = matches[descriptor.id]?.firstOrNull() ?: return@mapNotNull null
            val index = credentials.indexOf(matched)
            if (index < 0) return@mapNotNull null
            DescriptorMap(
                id = descriptor.id,
                format = credentialFormatOf(matched),
                path = "$.verifiableCredential[$index]",
            )
        }
        if (descriptorMap.isEmpty()) return null

        val submission = PresentationSubmission(
            id = UUID.randomUUID().toString(),
            definitionId = definition.id,
            descriptorMap = descriptorMap,
        )
        return lenientJson.encodeToJsonElement(PresentationSubmission.serializer(), submission).jsonObject
    }

    /** Maps a credential's proof type to its DIF PEX format identifier. */
    private fun credentialFormatOf(credential: VerifiableCredential): String = when (credential.proof) {
        is CredentialProof.SdJwtVcProof -> "vc+sd-jwt"
        is CredentialProof.MdocProof -> "mso_mdoc"
        is CredentialProof.JwtProof -> "jwt_vc"
        else -> "ldp_vc"
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

