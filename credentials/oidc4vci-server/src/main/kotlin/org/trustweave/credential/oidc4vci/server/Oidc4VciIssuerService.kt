package org.trustweave.credential.oidc4vci.server

import kotlinx.serialization.json.*
import org.trustweave.core.util.decodeBase58
import org.trustweave.core.util.encodeBase58
import org.trustweave.credential.oidc4vci.Oidc4VciService
import org.trustweave.credential.oidc4vci.models.*
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class OfferState(
    val credentialTypes: List<String>,
    val txCode: TxCode?,
    val txCodeValue: String?,
)

data class TokenEntry(
    val offerState: OfferState,
    val issuedAt: Long = System.currentTimeMillis(),
    /** Current `c_nonce` the wallet must echo in its proof-of-possession JWT (OID4VCI v1.0 §7.2). */
    val cNonce: String = UUID.randomUUID().toString(),
    val cNonceIssuedAt: Long = System.currentTimeMillis(),
)

/** The access token is unknown or has outlived its advertised `expires_in` (→ `invalid_token`). */
class InvalidTokenException(message: String) : SecurityException(message)

/**
 * The proof of possession is missing/invalid (→ OID4VCI `invalid_proof`).
 *
 * Carries the freshly rotated [freshCNonce] that MUST be included in the error response so
 * the wallet can retry with a valid proof (OID4VCI v1.0 §7.3.1).
 */
class InvalidProofException(
    message: String,
    val freshCNonce: String,
    val cNonceExpiresIn: Long,
) : SecurityException(message)

/** Raw Ed25519 public key length in bytes (RFC 8032). */
private const val ED25519_RAW_PUBLIC_KEY_LENGTH_BYTES = 32

/** Ed25519 signature length in bytes (RFC 8032). */
private const val ED25519_SIGNATURE_LENGTH_BYTES = 64

/** Multicodec prefix for `ed25519-pub` (0xED 0x01) used by did:key. */
private val ED25519_MULTICODEC_PREFIX = byteArrayOf(0xED.toByte(), 0x01)

/**
 * Fixed DER prefix of an Ed25519 SubjectPublicKeyInfo (RFC 8410):
 * `SEQUENCE(SEQUENCE(OID 1.3.101.112), BIT STRING(0x00 || raw 32-byte key))`.
 * Appending the raw key bytes yields an X.509-encoded public key consumable by JCA.
 */
private val ED25519_SPKI_PREFIX = byteArrayOf(
    0x30, 0x2A, 0x30, 0x05, 0x06, 0x03, 0x2B, 0x65, 0x70, 0x03, 0x21, 0x00,
)

class Oidc4VciIssuerService(
    val baseUrl: String,
    val issuerDid: String,
    val supportedConfigurations: Map<String, CredentialConfiguration> = emptyMap(),
    /** Access-token lifetime advertised as `expires_in` and enforced at the credential endpoint. */
    val tokenTtlSeconds: Long = 3600,
    /** Lifetime of each issued `c_nonce`; an expired nonce yields `invalid_proof` with a fresh one. */
    val cNonceTtlSeconds: Long = 300,
) {
    private val pendingOffers = ConcurrentHashMap<String, OfferState>()
    private val activeTokens = ConcurrentHashMap<String, TokenEntry>()
    private val deferredCredentials = ConcurrentHashMap<String, String>() // transactionId -> credentialJson

    fun getMetadata(): CredentialIssuerMetadata = CredentialIssuerMetadata(
        credentialIssuer = baseUrl,
        credentialEndpoint = "$baseUrl/credential",
        tokenEndpoint = "$baseUrl/token",
        deferredCredentialEndpoint = "$baseUrl/deferred_credential",
        notificationEndpoint = "$baseUrl/notification",
        credentialConfigurationsSupported = supportedConfigurations,
    )

    fun createOffer(credentialTypes: List<String>, txCode: TxCode? = null, txCodeValue: String? = null): CreateOfferResponse {
        val preAuthCode = UUID.randomUUID().toString()
        pendingOffers[preAuthCode] = OfferState(credentialTypes, txCode, txCodeValue)
        return CreateOfferResponse(buildCredentialOfferUri(credentialTypes, preAuthCode, txCode), preAuthCode)
    }

    /**
     * Builds a spec-format credential offer URI (OID4VCI v1.0 §4.1): a single
     * `credential_offer` query parameter carrying the URL-encoded offer JSON, with the
     * pre-authorized code grant (and `tx_code` requirement, §4.1.1) embedded in `grants`.
     *
     * Mirrors the wallet-side parser/builder in
     * [org.trustweave.credential.oidc4vci.Oidc4VciService].
     */
    private fun buildCredentialOfferUri(
        credentialTypes: List<String>,
        preAuthCode: String,
        txCode: TxCode?,
    ): String {
        val preAuthGrant = buildJsonObject {
            put("pre-authorized_code", preAuthCode)
            if (txCode != null) {
                // encodeDefaults so the defaulted input_mode ("numeric") is still emitted
                // in the offer; explicitNulls=false drops absent length/description.
                val json = Json {
                    encodeDefaults = true
                    explicitNulls = false
                }
                put("tx_code", json.encodeToJsonElement(TxCode.serializer(), txCode))
            }
        }
        val offerJson = buildJsonObject {
            put("credential_issuer", baseUrl)
            put("credential_configuration_ids", JsonArray(credentialTypes.map { JsonPrimitive(it) }))
            put("grants", buildJsonObject {
                put(Oidc4VciService.PRE_AUTHORIZED_CODE_GRANT_TYPE, preAuthGrant)
            })
        }
        val encodedOffer = URLEncoder.encode(
            Json.encodeToString(JsonObject.serializer(), offerJson),
            "UTF-8"
        )
        return "openid-credential-offer://?credential_offer=$encodedOffer"
    }

    /**
     * Exchanges a pre-authorized code for an access token.
     *
     * The `tx_code` comparison is constant-time ([MessageDigest.isEqual]) so an attacker
     * cannot learn the correct PIN byte-by-byte from response timing.
     *
     * The token response carries the initial `c_nonce` (+ expiry) the wallet must echo in
     * the proof-of-possession JWT at the credential endpoint.
     */
    fun exchangePreAuthCode(preAuthCode: String, txCodeValue: String?): TokenResponse {
        val offerState = pendingOffers.remove(preAuthCode)
            ?: throw IllegalArgumentException("Unknown or expired pre-authorized_code")
        if (offerState.txCode != null) {
            val expected = offerState.txCodeValue?.toByteArray(Charsets.UTF_8)
            val provided = txCodeValue?.toByteArray(Charsets.UTF_8)
            require(expected != null && provided != null && MessageDigest.isEqual(provided, expected)) {
                "Invalid tx_code"
            }
        }
        val accessToken = UUID.randomUUID().toString()
        val entry = TokenEntry(offerState)
        activeTokens[accessToken] = entry
        return TokenResponse(
            accessToken = accessToken,
            expiresIn = tokenTtlSeconds,
            cNonce = entry.cNonce,
            cNonceExpiresIn = cNonceTtlSeconds,
        )
    }

    /**
     * Issues a credential after enforcing token validity and proof of possession.
     *
     * The credential request MUST carry a `proof.jwt` (OID4VCI v1.0 §7.2.1.1) whose:
     * - signature verifies against the key carried in its own JOSE header (`jwk` header
     *   with an OKP/Ed25519 key, or a `did:key` `kid`);
     * - `aud` claim equals this issuer's URL;
     * - `nonce` claim equals the current (unexpired) `c_nonce` bound to the access token.
     *
     * Any violation throws [InvalidProofException] carrying a freshly rotated `c_nonce`
     * (the route surfaces it in the `invalid_proof` error response so wallets can retry);
     * an unknown or expired access token throws [InvalidTokenException].
     *
     * The issued credential's subject is bound to the proven key: `credentialSubject.id`
     * is the `did:key` derived from the proof's `jwk` header (or the `kid` DID).
     */
    fun issueCredential(
        accessToken: String,
        format: String,
        credentialTypes: List<String>,
        proofJwt: String?,
    ): CredentialServerResponse {
        val entry = requireValidToken(accessToken)
        val subjectDid = verifyProofOrThrow(accessToken, proofJwt)
        val credentialJson = buildMinimalCredential(
            types = entry.offerState.credentialTypes.ifEmpty { credentialTypes },
            subjectDid = subjectDid,
        )
        // Rotate the c_nonce on success too (OID4VCI v1.0 §7.3): each proof is single-use.
        val freshNonce = rotateCNonce(accessToken)
        return CredentialServerResponse(
            credential = credentialJson,
            format = format,
            cNonce = freshNonce,
            cNonceExpiresIn = cNonceTtlSeconds,
        )
    }

    fun getDeferredCredential(transactionId: String, accessToken: String): CredentialServerResponse? {
        runCatching { requireValidToken(accessToken) }.getOrNull() ?: return null
        val cred = deferredCredentials.remove(transactionId) ?: return null
        return CredentialServerResponse(credential = cred)
    }

    fun recordNotification(notification: Oidc4VciNotification) {
        // no-op — extend to persist/emit events
    }

    /** Returns the live [TokenEntry] or throws [InvalidTokenException] (unknown/expired). */
    private fun requireValidToken(accessToken: String): TokenEntry {
        val entry = activeTokens[accessToken]
            ?: throw InvalidTokenException("Invalid or expired access_token")
        if (System.currentTimeMillis() - entry.issuedAt >= tokenTtlSeconds * 1000) {
            activeTokens.remove(accessToken)
            throw InvalidTokenException("access_token expired")
        }
        return entry
    }

    /** Rotates the `c_nonce` bound to [accessToken] and returns the fresh value. */
    private fun rotateCNonce(accessToken: String): String {
        val fresh = UUID.randomUUID().toString()
        activeTokens.computeIfPresent(accessToken) { _, entry ->
            entry.copy(cNonce = fresh, cNonceIssuedAt = System.currentTimeMillis())
        }
        return fresh
    }

    /**
     * Verifies the proof-of-possession JWT and returns the proven subject DID.
     *
     * Fail-closed: every violation rotates the token's `c_nonce` and throws
     * [InvalidProofException] carrying the fresh nonce. Supported proof keys:
     * - JOSE `jwk` header with an OKP/Ed25519 public key (`alg: EdDSA`), or
     * - JOSE `kid` header that is a `did:key` Ed25519 DID URL.
     */
    private fun verifyProofOrThrow(accessToken: String, proofJwt: String?): String {
        fun reject(reason: String): Nothing =
            throw InvalidProofException(reason, rotateCNonce(accessToken), cNonceTtlSeconds)

        if (proofJwt.isNullOrBlank()) reject("Missing proof.jwt in credential request")

        val parts = proofJwt.split(".")
        when {
            parts.size == 5 -> reject("Encrypted proof JWTs (JWE) are not supported")
            parts.size != 3 -> reject("proof.jwt is not a compact JWS")
        }

        val decoder = Base64.getUrlDecoder()
        val lenientJson = Json { ignoreUnknownKeys = true }
        val header = runCatching {
            lenientJson.parseToJsonElement(String(decoder.decode(parts[0]), Charsets.UTF_8)).jsonObject
        }.getOrNull() ?: reject("proof.jwt header is not valid base64url JSON")
        val payload = runCatching {
            lenientJson.parseToJsonElement(String(decoder.decode(parts[1]), Charsets.UTF_8)).jsonObject
        }.getOrNull() ?: reject("proof.jwt payload is not valid base64url JSON")

        val alg = header["alg"]?.jsonPrimitive?.contentOrNull
        if (alg == null || alg.equals("none", ignoreCase = true)) {
            reject("Unsigned proof (alg=none) is not accepted")
        }
        if (alg != "EdDSA") reject("Unsupported proof alg '$alg' — only EdDSA (Ed25519) is supported")
        header["typ"]?.jsonPrimitive?.contentOrNull?.let { typ ->
            if (typ != "openid4vci-proof+jwt") reject("proof.jwt typ must be 'openid4vci-proof+jwt', got '$typ'")
        }

        val proofKey = extractProofKey(header)
            ?: reject("proof.jwt carries no usable key (jwk header with OKP/Ed25519, or did:key kid, required)")

        val signature = runCatching { decoder.decode(parts[2]) }.getOrNull()
            ?: reject("proof.jwt signature is not valid base64url")
        if (signature.size != ED25519_SIGNATURE_LENGTH_BYTES) {
            reject("proof.jwt signature has invalid length for Ed25519")
        }
        val verified = runCatching {
            Signature.getInstance("Ed25519").run {
                initVerify(proofKey.publicKey)
                update("${parts[0]}.${parts[1]}".toByteArray(Charsets.UTF_8))
                verify(signature)
            }
        }.getOrDefault(false)
        if (!verified) reject("proof.jwt signature verification failed against the key in its header")

        val aud = payload["aud"]?.jsonPrimitive?.contentOrNull
        if (aud != baseUrl) {
            reject("proof.jwt aud '${aud ?: "<absent>"}' does not match credential issuer '$baseUrl'")
        }

        val nonce = payload["nonce"]?.jsonPrimitive?.contentOrNull
            ?: reject("proof.jwt is missing the nonce claim")
        // Atomic consume-and-rotate: the compare and the rotation happen inside one
        // computeIfPresent so a c_nonce is strictly single-use — two concurrent
        // credential requests echoing the same nonce cannot both pass.
        if (!consumeCNonce(accessToken, nonce)) {
            reject("proof.jwt nonce does not match the current c_nonce (or it expired) — retry with the fresh c_nonce")
        }

        return proofKey.subjectDid
    }

    /**
     * Atomically validates [presentedNonce] against the token's live, unexpired `c_nonce`
     * and rotates it in the same [ConcurrentHashMap.computeIfPresent] step (single-use).
     */
    private fun consumeCNonce(accessToken: String, presentedNonce: String): Boolean {
        var consumed = false
        activeTokens.computeIfPresent(accessToken) { _, entry ->
            val live = System.currentTimeMillis() - entry.cNonceIssuedAt < cNonceTtlSeconds * 1000
            val matches = MessageDigest.isEqual(
                presentedNonce.toByteArray(Charsets.UTF_8),
                entry.cNonce.toByteArray(Charsets.UTF_8)
            )
            if (live && matches) {
                consumed = true
                entry.copy(cNonce = UUID.randomUUID().toString(), cNonceIssuedAt = System.currentTimeMillis())
            } else {
                entry
            }
        }
        return consumed
    }

    /** A verified proof key: the JCA public key plus the subject DID it binds the credential to. */
    private data class ProofKey(val publicKey: PublicKey, val subjectDid: String)

    /**
     * Extracts the holder's Ed25519 key from the proof JWT's JOSE header — either the
     * embedded `jwk` (OKP/Ed25519) or a `did:key` `kid`. Returns `null` when no usable
     * key is present (fail-closed).
     */
    private fun extractProofKey(header: JsonObject): ProofKey? {
        (header["jwk"] as? JsonObject)?.let { jwk ->
            if (jwk["kty"]?.jsonPrimitive?.contentOrNull != "OKP") return null
            if (jwk["crv"]?.jsonPrimitive?.contentOrNull != "Ed25519") return null
            val x = jwk["x"]?.jsonPrimitive?.contentOrNull ?: return null
            val raw = runCatching { Base64.getUrlDecoder().decode(x) }.getOrNull() ?: return null
            val publicKey = createEd25519PublicKey(raw) ?: return null
            val didKey = "did:key:z" + (ED25519_MULTICODEC_PREFIX + raw).encodeBase58()
            return ProofKey(publicKey, didKey)
        }

        (header["kid"]?.jsonPrimitive?.contentOrNull)?.let { kid ->
            if (!kid.startsWith("did:key:z")) return null
            val didKey = kid.substringBefore("#")
            val multibase = didKey.removePrefix("did:key:")
            val decoded = runCatching { multibase.removePrefix("z").decodeBase58() }.getOrNull()
                ?: return null
            if (decoded.size != ED25519_RAW_PUBLIC_KEY_LENGTH_BYTES + 2 ||
                decoded[0] != ED25519_MULTICODEC_PREFIX[0] || decoded[1] != ED25519_MULTICODEC_PREFIX[1]
            ) {
                return null
            }
            val raw = decoded.copyOfRange(2, decoded.size)
            val publicKey = createEd25519PublicKey(raw) ?: return null
            return ProofKey(publicKey, didKey)
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
            KeyFactory.getInstance("Ed25519")
                .generatePublic(X509EncodedKeySpec(ED25519_SPKI_PREFIX + rawKeyBytes))
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Builds the minimal credential JSON via kotlinx-serialization — no string templates,
     * so attacker-controlled credential types (or any other value) cannot inject JSON.
     *
     * The credential's subject is bound to [subjectDid], the DID proven by the wallet's
     * proof-of-possession JWT.
     */
    private fun buildMinimalCredential(types: List<String>, subjectDid: String): String {
        val credential = buildJsonObject {
            put("@context", JsonArray(listOf(JsonPrimitive("https://www.w3.org/2018/credentials/v1"))))
            put("type", JsonArray(types.map { JsonPrimitive(it) }))
            put("issuer", issuerDid)
            put("issuanceDate", java.time.Instant.now().toString())
            put("credentialSubject", buildJsonObject { put("id", subjectDid) })
        }
        return Json.encodeToString(JsonObject.serializer(), credential)
    }
}

data class CreateOfferResponse(val offerUri: String, val preAuthCode: String)

data class TokenResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long = 3600,
    val cNonce: String,
    val cNonceExpiresIn: Long = 300,
)

data class CredentialServerResponse(
    val credential: String? = null,
    val transactionId: String? = null,
    val format: String? = null,
    val cNonce: String? = null,
    val cNonceExpiresIn: Long? = null,
)
