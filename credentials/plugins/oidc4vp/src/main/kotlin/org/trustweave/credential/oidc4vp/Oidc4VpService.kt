package org.trustweave.credential.oidc4vp

import org.trustweave.core.identifiers.KeyId
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.core.util.decodeBase58
import org.trustweave.credential.exchange.exception.ExchangeException
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
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
import com.nimbusds.jose.JWSAlgorithm
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
import java.net.URLDecoder
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
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

/** Raw Ed25519 public key length in bytes (RFC 8032). */
private const val ED25519_RAW_PUBLIC_KEY_LENGTH_BYTES = 32

/** Ed25519 signature length in bytes (RFC 8032). */
private const val ED25519_SIGNATURE_LENGTH_BYTES = 64

/**
 * Fixed DER prefix of an Ed25519 SubjectPublicKeyInfo (RFC 8410):
 * `SEQUENCE(SEQUENCE(OID 1.3.101.112), BIT STRING(0x00 || raw 32-byte key))`.
 * Appending the raw key bytes yields an X.509-encoded public key consumable by JCA.
 */
private val ED25519_SPKI_PREFIX = byteArrayOf(
    0x30, 0x2A, 0x30, 0x05, 0x06, 0x03, 0x2B, 0x65, 0x70, 0x03, 0x21, 0x00,
)

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
    private val httpClient: OkHttpClient = org.trustweave.core.net.ssrfGuardedOkHttpClient(),
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
    /**
     * Resolver used to pin request-object signing keys to the verifier's DID document
     * when the `client_id` scheme is `did` (or the `client_id` itself is a DID).
     *
     * When a signed JWT request object is received for a DID `client_id`, the JWS
     * signature MUST verify against a key from the independently resolved DID document —
     * the self-attested `client_metadata.jwks` embedded in the request is ignored.
     *
     * Fail-closed: a signed request object with a DID `client_id` is REJECTED when this
     * resolver is `null`, because the verifier identity cannot be authenticated.
     */
    val didResolver: DidResolver? = null,
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
                val (fetched, fromSignedRequestObject) = fetchAuthorizationRequest(
                    requestUri = requestUri,
                    urlClientId = urlRequest.clientId,
                    urlClientIdScheme = queryParams["client_id_scheme"]?.let { ClientIdScheme.fromString(it) },
                )
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
        // Field-level selective disclosure (selectedFields) is NOT implemented: the credential is
        // embedded whole in the vp_token (see credentialToW3cJson). Honoring a field subset would
        // require SD-JWT/BBS derivation, and simply dropping claims from an embedded VC-LD credential
        // would invalidate its data-integrity proof. So when a caller requests minimization, refuse
        // rather than silently disclose the entire credential.
        if (selectedFields.any { it.isNotEmpty() }) {
            throw UnsupportedOperationException(
                "Field-level selective disclosure (selectedFields) is not supported: the full " +
                    "credential would be disclosed. Omit selectedFields to disclose the full " +
                    "credential explicitly, or present an SD-JWT credential. Refusing to silently " +
                    "over-disclose.",
            )
        }
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
        
        // OID4VP direct_post response mode (v1.0 §7.2): the Authorization Response is
        // posted as application/x-www-form-urlencoded form parameters. vp_token and state
        // are plain form values; presentation_submission is its JSON serialization.
        val formBody = FormBody.Builder()
            .add("vp_token", permissionResponse.vpToken)
            .apply {
                permissionResponse.presentationSubmission?.let {
                    add("presentation_submission", it.toString())
                }
                permissionResponse.state?.let { add("state", it) }
            }
            .build()

        val httpRequest = Request.Builder()
            .url(responseUri)
            .post(formBody)
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
     * verified per the request's `client_id` scheme (see [parseRequestObjectJwt]).
     *
     * @param urlClientId `client_id` from the authorization URL query parameters, used as
     *   an additional DID-scheme signal (the signed request object's claims remain
     *   authoritative for the resulting [AuthorizationRequest])
     * @param urlClientIdScheme `client_id_scheme` from the authorization URL query
     *   parameters, used as an additional DID-scheme signal
     */
    private suspend fun fetchAuthorizationRequest(
        requestUri: String,
        urlClientId: String? = null,
        urlClientIdScheme: ClientIdScheme? = null,
    ): FetchedAuthorizationRequest {
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
                request = parseRequestObjectJwt(trimmed, requestUri, urlClientId, urlClientIdScheme),
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
     * - **DID client_id scheme — key resolution is pinned.** When `client_id_scheme` is
     *   `did` or the `client_id` is a DID (either signal, from the request object claims
     *   or the authorization URL), the JWS signature MUST verify against a verification
     *   method from the CLIENT's DID document, resolved independently via [didResolver].
     *   The self-attested `client_metadata.jwks` is NOT consulted. If no [didResolver] is
     *   configured, the request is rejected (fail closed). Supported key material:
     *   Ed25519 (`publicKeyJwk` OKP or `publicKeyMultibase`, verified via JCA) and
     *   EC P-256 (`publicKeyJwk`, verified via Nimbus).
     * - For non-DID client_ids: when the request object carries verifier keys
     *   (`client_metadata.jwks`), the JWS signature MUST verify against one of them;
     *   otherwise the request is rejected. Without resolvable keys the claims are
     *   accepted unverified (best effort) — trust then rests on the TLS channel to the
     *   request_uri host.
     *
     * **Known limitation — non-DID schemes still rely on self-attested keys.** For
     * non-DID client_ids the JWK Set used for signature verification is carried inside
     * the request object itself, so a successful verification only proves internal
     * consistency: whoever minted the request object controls both the keys and the
     * signature. It does NOT authenticate the verifier. The DID scheme is now pinned via
     * independent DID resolution (see above); X.509 SAN/chain validation for
     * `x509_san_dns` / `x509_san_uri` / `verifier_attestation` remains unsupported. For
     * those schemes, trust in the request content ultimately rests on the TLS channel to
     * the request_uri host.
     */
    private suspend fun parseRequestObjectJwt(
        jwtString: String,
        requestUri: String,
        urlClientId: String? = null,
        urlClientIdScheme: ClientIdScheme? = null,
    ): AuthorizationRequest {
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

        val claimClientId = (claimsJson["client_id"] as? JsonPrimitive)?.contentOrNull
        val claimClientIdScheme = (claimsJson["client_id_scheme"] as? JsonPrimitive)?.contentOrNull
            ?.let { ClientIdScheme.fromString(it) }

        // DID-scheme signals: an explicit client_id_scheme=did (claims or URL) or a
        // client_id that is itself a DID (claims, or URL when the claims carry none).
        val effectiveClientId = claimClientId ?: urlClientId
        val didPinned = claimClientIdScheme == ClientIdScheme.DID ||
            urlClientIdScheme == ClientIdScheme.DID ||
            effectiveClientId?.startsWith("did:") == true

        if (didPinned) {
            // Keys MUST come from the client's independently resolved DID document.
            // The self-attested client_metadata.jwks is intentionally NOT consulted.
            verifyRequestObjectAgainstClientDid(signedJwt, effectiveClientId, requestUri)
        } else {
            val jwks = (claimsJson["client_metadata"] as? JsonObject)
                ?.get("jwks")
                ?.let { runCatching { JWKSet.parse(it.toString()) }.getOrNull() }

            if (jwks != null && jwks.keys.isNotEmpty()) {
                verifyRequestObjectSignature(signedJwt, jwks, requestUri)
            }
        }

        return buildAuthorizationRequestFromJson(claimsJson)
    }

    /**
     * Verifies a signed request object against the client's DID document
     * (OID4VP `client_id_scheme=did`).
     *
     * Fail-closed at every step:
     * - no [didResolver] configured → reject (the verifier identity cannot be pinned);
     * - `client_id` absent or not a valid DID → reject;
     * - DID resolution fails → reject;
     * - no verification method matches the JWS `kid` (or, when `kid` is absent, no
     *   `authentication`-authorized verification method) → reject;
     * - signature does not verify against any candidate key → reject.
     *
     * @throws Oidc4VpException.AuthorizationRequestFetchFailed on any of the above
     */
    private suspend fun verifyRequestObjectAgainstClientDid(
        jwt: SignedJWT,
        clientId: String?,
        requestUri: String,
    ) {
        fun reject(reason: String): Nothing = throw Oidc4VpException.AuthorizationRequestFetchFailed(
            requestUri = requestUri,
            reason = reason,
        )

        if (clientId == null || !clientId.startsWith("did:")) {
            reject("client_id_scheme is 'did' but client_id '${clientId ?: "<absent>"}' is not a DID")
        }

        val resolver = didResolver
            ?: reject(
                "Signed request object with DID client_id '$clientId' cannot be verified: " +
                    "no DidResolver is configured on Oidc4VpService. Configure a DidResolver " +
                    "to pin request-object signing keys to the verifier's DID document " +
                    "(rejecting per fail-closed policy)."
            )

        val did = try {
            Did(clientId)
        } catch (e: IllegalArgumentException) {
            reject("client_id '$clientId' is not a valid DID: ${e.message}")
        }

        val document = when (val result = resolver.resolve(did)) {
            is DidResolutionResult.Success -> result.document
            else -> reject(
                "DID resolution of client_id '$clientId' failed (${result.javaClass.simpleName}) — " +
                    "request object signing key cannot be pinned"
            )
        }

        // Request-object signing is an authentication act: regardless of how the key is
        // selected (kid or not), it must be authorized under the DID document's
        // `authentication` relationship — a key listed only under e.g. assertionMethod
        // or keyAgreement must not authenticate the verifier.
        val authenticationAuthorized = document.verificationMethod
            .filter { vm -> document.authentication.any { it.value == vm.id.value } }
        val kid = jwt.header.keyID
        val candidates = if (kid != null) {
            authenticationAuthorized.filter { vm -> verificationMethodMatchesKid(vm, kid) }
                .ifEmpty {
                    reject(
                        "No authentication-authorized verification method matching kid '$kid' " +
                            "found in DID document of client_id '$clientId'"
                    )
                }
        } else {
            authenticationAuthorized.ifEmpty {
                reject(
                    "Request object has no kid and DID document of client_id '$clientId' " +
                        "has no authentication-authorized verification method"
                )
            }
        }

        val verified = candidates.any { vm ->
            runCatching { verifyJwsWithVerificationMethod(jwt, vm) }.getOrDefault(false)
        }
        if (!verified) {
            reject(
                "Request object signature verification failed against the DID document keys " +
                    "of client_id '$clientId'"
            )
        }
    }

    /**
     * Matches a DID document verification method against a JWS `kid`, which may be a full
     * DID URL (`did:ex:123#key-1`), a relative fragment (`#key-1`), or a bare key id
     * (`key-1`).
     */
    private fun verificationMethodMatchesKid(vm: VerificationMethod, kid: String): Boolean {
        val vmId = vm.id.value
        return when {
            kid.startsWith("did:") -> vmId == kid
            kid.startsWith("#") -> vmId.endsWith(kid)
            else -> vmId.endsWith("#$kid")
        }
    }

    /**
     * Verifies the JWS of a request object against a DID document verification method.
     *
     * Algorithm/key-type confusion is rejected:
     * - `EdDSA` → Ed25519 key from `publicKeyJwk` (OKP/Ed25519) or `publicKeyMultibase`,
     *   verified via JCA (avoids Nimbus' optional Tink dependency for Ed25519);
     * - `ES256` → EC P-256 key from `publicKeyJwk`, verified via Nimbus [ECDSAVerifier].
     *
     * Any other algorithm, missing/unsupported key material, or verification error yields
     * `false` (fail-closed).
     */
    private fun verifyJwsWithVerificationMethod(jwt: SignedJWT, vm: VerificationMethod): Boolean =
        when (jwt.header.algorithm) {
            JWSAlgorithm.EdDSA -> {
                val publicKey = extractEd25519PublicKey(vm)
                if (publicKey == null) {
                    false
                } else {
                    try {
                        val signatureBytes = jwt.signature.decode()
                        if (signatureBytes.size != ED25519_SIGNATURE_LENGTH_BYTES) {
                            false
                        } else {
                            val signature = java.security.Signature.getInstance("Ed25519")
                            signature.initVerify(publicKey)
                            signature.update(jwt.signingInput)
                            signature.verify(signatureBytes)
                        }
                    } catch (_: Exception) {
                        false
                    }
                }
            }
            JWSAlgorithm.ES256 -> {
                try {
                    val jwkMap = vm.publicKeyJwk
                        ?.filterValues { it != null }
                        ?.mapValues { (_, value) -> value as Any }
                    if (jwkMap == null) {
                        false
                    } else {
                        val jwk = JWK.parse(jwkMap)
                        val ecKey = jwk as? ECKey
                        if (ecKey == null || ecKey.curve != com.nimbusds.jose.jwk.Curve.P_256) {
                            false
                        } else {
                            jwt.verify(ECDSAVerifier(ecKey.toPublicJWK()))
                        }
                    }
                } catch (_: Exception) {
                    false
                }
            }
            else -> false
        }

    /**
     * Extracts an Ed25519 [PublicKey] from a verification method's `publicKeyJwk`
     * (OKP/Ed25519) or `publicKeyMultibase` (base58btc `z` prefix; multicodec
     * `ed25519-pub` `0xED 0x01` + 32 bytes, or raw 32 bytes).
     *
     * Mirrors `ProofEngineUtils` in credential-api, but constructs the key via the
     * standard RFC 8410 SubjectPublicKeyInfo prefix + JCA `KeyFactory` (no BouncyCastle
     * dependency in this module). Returns `null` on any failure (fail-closed).
     */
    private fun extractEd25519PublicKey(vm: VerificationMethod): PublicKey? {
        vm.publicKeyJwk?.let { jwkMap ->
            val kty = jwkMap["kty"] as? String ?: return null
            if (kty != "OKP" || jwkMap["crv"] as? String != "Ed25519") return null
            val x = jwkMap["x"] as? String ?: return null
            val raw = try {
                Base64.getUrlDecoder().decode(x)
            } catch (_: IllegalArgumentException) {
                return null
            }
            return createEd25519PublicKey(raw)
        }

        vm.publicKeyMultibase?.let { multibase ->
            if (multibase.length < 2) return null
            val decoded = try {
                when (multibase[0]) {
                    'z' -> multibase.substring(1).decodeBase58()
                    'u' -> Base64.getUrlDecoder().decode(multibase.substring(1))
                    else -> return null
                }
            } catch (_: Exception) {
                return null
            }
            val raw = when {
                decoded.size == ED25519_RAW_PUBLIC_KEY_LENGTH_BYTES + 2 &&
                    decoded[0] == 0xED.toByte() && decoded[1] == 0x01.toByte() ->
                    decoded.copyOfRange(2, decoded.size)
                decoded.size == ED25519_RAW_PUBLIC_KEY_LENGTH_BYTES -> decoded
                else -> return null
            }
            return createEd25519PublicKey(raw)
        }

        return null
    }

    /**
     * Constructs an Ed25519 [PublicKey] from raw 32-byte key material by prepending the
     * fixed RFC 8410 SubjectPublicKeyInfo DER prefix and going through the JCA
     * `KeyFactory`. Returns `null` on failure (fail-closed).
     */
    private fun createEd25519PublicKey(rawKeyBytes: ByteArray): PublicKey? {
        if (rawKeyBytes.size != ED25519_RAW_PUBLIC_KEY_LENGTH_BYTES) return null
        return try {
            val spki = ED25519_SPKI_PREFIX + rawKeyBytes
            KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(spki))
        } catch (_: Exception) {
            null
        }
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
     * Returns `null` when there is no presentation definition or it cannot be decoded.
     *
     * Per DIF PEX v2.0, when a presentation definition carries no `submission_requirements`
     * every input descriptor is required. If any descriptor is not satisfied by the
     * supplied credentials, a [Oidc4VpException.RequiredCredentialMissing] is thrown so
     * the holder gets a clear error instead of a silently partial submission. When
     * `submission_requirements` are present, unmatched descriptors are tolerated (the
     * requirements may only demand a subset) and simply omitted from the descriptor map.
     */
    private fun buildPresentationSubmission(
        presentationDefinition: JsonObject?,
        credentials: List<VerifiableCredential>,
    ): JsonObject? {
        if (presentationDefinition == null) return null

        val definition = try {
            lenientJson.decodeFromJsonElement<PresentationDefinition>(presentationDefinition)
        } catch (_: Exception) {
            return null
        }

        val matches = PresentationDefinitionMatcher.match(definition, credentials)

        // submission_requirements absent → all input descriptors are required (PEX v2.0 §4.2)
        if (definition.submissionRequirements.isNullOrEmpty()) {
            val unmatched = definition.inputDescriptors
                .filter { matches[it.id].isNullOrEmpty() }
                .map { it.id }
            if (unmatched.isNotEmpty()) {
                throw Oidc4VpException.RequiredCredentialMissing(
                    definitionId = definition.id,
                    descriptorIds = unmatched,
                )
            }
        }

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

    /**
     * Maps a credential's proof type to its registered OID4VP format identifier
     * (OID4VP v1.0 Appendix B): `jwt_vc_json`, `ldp_vc`, `vc+sd-jwt`, `mso_mdoc`.
     */
    private fun credentialFormatOf(credential: VerifiableCredential): String = when (credential.proof) {
        is CredentialProof.SdJwtVcProof -> "vc+sd-jwt"
        is CredentialProof.MdocProof -> "mso_mdoc"
        is CredentialProof.JwtProof -> "jwt_vc_json"
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

