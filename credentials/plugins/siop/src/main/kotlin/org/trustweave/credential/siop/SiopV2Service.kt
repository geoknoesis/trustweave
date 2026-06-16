package org.trustweave.credential.siop

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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.trustweave.core.identifiers.KeyId
import org.trustweave.core.util.decodeBase58
import org.trustweave.credential.model.vc.VerifiablePresentation
import org.trustweave.credential.pex.PresentationDefinition
import org.trustweave.credential.pex.PresentationSubmission
import org.trustweave.credential.siop.models.SiopV2AuthorizationRequest
import org.trustweave.credential.siop.models.SiopV2AuthorizationResponse
import org.trustweave.credential.siop.models.SiopV2Session
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.DidResolver
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.SignResult
import java.net.URLDecoder
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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

class SiopV2Service(
    private val kms: KeyManagementService,
    private val config: SiopV2Config = SiopV2Config(),
    private val httpClient: OkHttpClient = org.trustweave.core.net.ssrfGuardedOkHttpClient(),
    /**
     * Resolver used to pin request-object signing keys to the verifier's DID document
     * when the request's `client_id` is a DID (or `client_id_scheme=did`).
     *
     * When a signed JWT request object is received for a DID `client_id`, the JWS
     * signature MUST verify against an authentication-authorized key from the
     * independently resolved DID document — the self-attested `client_metadata.jwks`
     * embedded in the request is ignored.
     *
     * Fail-closed: a signed request object with a DID `client_id` is REJECTED when this
     * resolver is `null`, because the verifier identity cannot be authenticated.
     */
    private val didResolver: DidResolver? = null,
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

    /**
     * Parses a SIOPv2 authorization request from a URL or fetches it from `request_uri`.
     *
     * Security:
     * - The `request_uri` is restricted to `https` (plain `http` only for loopback hosts)
     *   to prevent SSRF towards cleartext or link-local/metadata endpoints.
     * - A fetched body that is a JWT request object must be **signed** — `alg: none`
     *   and encrypted (JWE) request objects are rejected. When the `client_id` is a DID,
     *   the JWS is verified against the client's independently resolved DID document
     *   (see [didResolver]; fail-closed without a resolver). For non-DID client_ids the
     *   JWS is verified against `client_metadata.jwks` when present — note that those
     *   keys are **self-attested** (carried inside the request object itself), so a
     *   successful verification only proves internal consistency, not verifier identity.
     * - A fetched body that is a **plain JSON document** is accepted without signature
     *   verification (there is nothing to verify) — trust in its content rests entirely
     *   on the TLS channel to the `request_uri` host.
     */
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
            requireHttpsOrLoopback(requestUri)
            val response = httpClient.newCall(Request.Builder().url(requestUri).get().build()).execute()
            val body = response.body?.string()
                ?: throw SiopV2Exception("FETCH_FAILED", "Empty response from request_uri")
            if (!response.isSuccessful) {
                throw SiopV2Exception("FETCH_FAILED", "HTTP ${response.code} from request_uri")
            }
            val trimmed = body.trim()
            if (trimmed.startsWith("{")) {
                // Plain (unsigned) JSON request document: accepted as-is. There is no
                // signature to verify — trust rests on the TLS channel to request_uri.
                json.parseToJsonElement(trimmed).jsonObject
            } else {
                parseAndVerifyRequestObjectJwt(
                    jwtString = trimmed,
                    requestUri = requestUri,
                    urlClientId = params["client_id"],
                    urlClientIdScheme = params["client_id_scheme"],
                )
            }
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

    // ===================================================================================
    // request_uri / request-object hardening
    // ===================================================================================

    /**
     * Enforces the https-or-loopback URL policy for `request_uri`: `https` is always
     * allowed; plain `http` is allowed exclusively for loopback hosts (`localhost`,
     * `127.0.0.1`, `::1`) to support local development and tests. Everything else —
     * including non-http(s) schemes and malformed URIs — is rejected.
     *
     * Mirrors `Oidc4VciService.requireHttpsOrLoopback`: protects every URL an attacker
     * can steer via authorization-request QR codes from pointing at cleartext or
     * link-local/metadata endpoints (e.g. `http://169.254.169.254/`).
     */
    private fun requireHttpsOrLoopback(url: String) {
        val violation = httpsOrLoopbackViolation(url) ?: return
        throw SiopV2Exception("INSECURE_REQUEST_URI", "request_uri $violation")
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
     * Parses (and verifies) a JWT request object fetched from `request_uri`.
     *
     * Security (mirrors the oidc4vp plugin's request-object hardening):
     * - `alg: none` (unsigned) request objects are refused.
     * - Encrypted (JWE) request objects are not supported and refused.
     * - **DID client_id — key resolution is pinned.** When `client_id_scheme` is `did`
     *   or the `client_id` is a DID (either signal, from the request-object claims or
     *   the authorization URL), the JWS MUST verify against an authentication-authorized
     *   verification method from the CLIENT's DID document, resolved independently via
     *   [didResolver]. The self-attested `client_metadata.jwks` is NOT consulted. If no
     *   [didResolver] is configured, the request is rejected (fail closed).
     * - For non-DID client_ids: when the request object carries verifier keys
     *   (`client_metadata.jwks`), the JWS MUST verify against one of them; note those
     *   keys are self-attested, so success only proves internal consistency. Without
     *   such keys the claims are accepted unverified (best effort) — trust then rests
     *   on the TLS channel to the request_uri host.
     */
    private suspend fun parseAndVerifyRequestObjectJwt(
        jwtString: String,
        requestUri: String,
        urlClientId: String?,
        urlClientIdScheme: String?,
    ): JsonObject {
        val jwt = try {
            JWTParser.parse(jwtString)
        } catch (e: Exception) {
            throw SiopV2Exception(
                "INVALID_REQUEST_OBJECT",
                "Request object is neither a JSON document nor a valid JWT: ${e.message}",
            )
        }

        val signedJwt = when (jwt) {
            is PlainJWT -> throw SiopV2Exception(
                "INVALID_REQUEST_OBJECT",
                "Unsigned request object (alg=none) is not accepted",
            )
            is SignedJWT -> jwt
            else -> throw SiopV2Exception(
                "INVALID_REQUEST_OBJECT",
                "Unsupported request object type (encrypted request objects are not supported): " +
                    jwt.javaClass.simpleName,
            )
        }

        val lenientJson = Json { ignoreUnknownKeys = true }
        val claimsJson = lenientJson.parseToJsonElement(signedJwt.payload.toString()).jsonObject

        val claimClientId = (claimsJson["client_id"] as? JsonPrimitive)?.contentOrNull
        val claimClientIdScheme = (claimsJson["client_id_scheme"] as? JsonPrimitive)?.contentOrNull

        // DID-scheme signals: an explicit client_id_scheme=did (claims or URL) or a
        // client_id that is itself a DID (claims, or URL when the claims carry none).
        val effectiveClientId = claimClientId ?: urlClientId
        val didPinned = claimClientIdScheme == "did" ||
            urlClientIdScheme == "did" ||
            effectiveClientId?.startsWith("did:") == true

        if (didPinned) {
            // Keys MUST come from the client's independently resolved DID document.
            // The self-attested client_metadata.jwks is intentionally NOT consulted.
            verifyRequestObjectAgainstClientDid(signedJwt, effectiveClientId)
        } else {
            val jwks = (claimsJson["client_metadata"] as? JsonObject)
                ?.get("jwks")
                ?.let { runCatching { JWKSet.parse(it.toString()) }.getOrNull() }

            if (jwks != null && jwks.keys.isNotEmpty()) {
                verifyRequestObjectSignature(signedJwt, jwks)
            }
        }

        return claimsJson
    }

    /**
     * Verifies a signed request object against the client's DID document
     * (`client_id_scheme=did`). Ported from the oidc4vp plugin's
     * `Oidc4VpService.verifyRequestObjectAgainstClientDid`.
     *
     * Fail-closed at every step:
     * - no [didResolver] configured → reject (the verifier identity cannot be pinned);
     * - `client_id` absent or not a valid DID → reject;
     * - DID resolution fails → reject;
     * - no verification method matches the JWS `kid` (or, when `kid` is absent, no
     *   `authentication`-authorized verification method) → reject;
     * - signature does not verify against any candidate key → reject.
     *
     * @throws SiopV2Exception (`REQUEST_OBJECT_VERIFICATION_FAILED`) on any of the above
     */
    private suspend fun verifyRequestObjectAgainstClientDid(
        jwt: SignedJWT,
        clientId: String?,
    ) {
        fun reject(reason: String): Nothing =
            throw SiopV2Exception("REQUEST_OBJECT_VERIFICATION_FAILED", reason)

        if (clientId == null || !clientId.startsWith("did:")) {
            reject("client_id_scheme is 'did' but client_id '${clientId ?: "<absent>"}' is not a DID")
        }

        val resolver = didResolver
            ?: reject(
                "Signed request object with DID client_id '$clientId' cannot be verified: " +
                    "no DidResolver is configured on SiopV2Service. Configure a DidResolver " +
                    "to pin request-object signing keys to the verifier's DID document " +
                    "(rejecting per fail-closed policy).",
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
                    "request object signing key cannot be pinned",
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
                            "found in DID document of client_id '$clientId'",
                    )
                }
        } else {
            authenticationAuthorized.ifEmpty {
                reject(
                    "Request object has no kid and DID document of client_id '$clientId' " +
                        "has no authentication-authorized verification method",
                )
            }
        }

        val verified = candidates.any { vm ->
            runCatching { verifyJwsWithVerificationMethod(jwt, vm) }.getOrDefault(false)
        }
        if (!verified) {
            reject(
                "Request object signature verification failed against the DID document keys " +
                    "of client_id '$clientId'",
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
     * Returns `null` on any failure (fail-closed).
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
     * Verifies the JWS signature of a request object against the verifier's
     * (self-attested) `client_metadata.jwks` JWK Set — non-DID client_ids only.
     *
     * @throws SiopV2Exception (`REQUEST_OBJECT_VERIFICATION_FAILED`) when no key verifies
     */
    private fun verifyRequestObjectSignature(jwt: SignedJWT, jwks: JWKSet) {
        val kid = jwt.header.keyID
        val candidates = jwks.keys.filter { kid == null || it.keyID == null || it.keyID == kid }

        val verified = candidates.any { jwk ->
            val verifier = verifierFor(jwk) ?: return@any false
            runCatching { jwt.verify(verifier) }.getOrDefault(false)
        }

        if (!verified) {
            throw SiopV2Exception(
                "REQUEST_OBJECT_VERIFICATION_FAILED",
                "Request object signature verification failed against client_metadata jwks",
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
