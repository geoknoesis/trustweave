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
import kotlin.time.Clock

data class OfferState(
    val credentialTypes: List<String>,
    val txCode: TxCode?,
    val txCodeValue: String?,
    /**
     * Claims the issuer will put in the credential's `credentialSubject` (alongside the `id` it
     * derives from the holder's proven key). Set by whoever creates the offer.
     */
    val claims: JsonObject = JsonObject(emptyMap()),
    /**
     * When the pre-authorized code was minted, so it can stop being redeemable.
     *
     * This used to be absent, which made a pre-authorized code valid forever. The code travels in
     * a `credential_offer` URI — a QR image, an email, a link that ends up in server logs and
     * browser history — and OID4VCI treats it as a short-lived, single-use secret precisely
     * because it travels that way. Without an issue time there was nothing to expire against, and
     * the rejection message already said "Unknown or expired pre-authorized_code" for a state the
     * server could not detect.
     */
    val issuedAt: Long = System.currentTimeMillis(),
)

data class TokenEntry(
    val offerState: OfferState,
    val issuedAt: Long = System.currentTimeMillis(),
    /** Current `c_nonce` the wallet must echo in its proof-of-possession JWT (OID4VCI v1.0 §7.2). */
    val cNonce: String = UUID.randomUUID().toString(),
    val cNonceIssuedAt: Long = System.currentTimeMillis(),
    /**
     * How many more credentials this access token may obtain. Starts at the number of credential
     * configurations in the offer (at least one), so a single redemption cannot be turned into
     * unlimited issuance.
     */
    val remainingCredentials: Int = offerState.credentialTypes.size.coerceAtLeast(1),
) {
    /** Binary-compatible constructor from before [remainingCredentials] existed. */
    constructor(
        offerState: OfferState,
        issuedAt: Long,
        cNonce: String,
        cNonceIssuedAt: Long,
    ) : this(offerState, issuedAt, cNonce, cNonceIssuedAt, offerState.credentialTypes.size.coerceAtLeast(1))
}

/** The access token is unknown or has outlived its advertised `expires_in` (→ `invalid_token`). */
class InvalidTokenException(
    message: String,
) : SecurityException(message)

/**
 * The access token has already obtained every credential its offer covered (→ OID4VCI
 * `invalid_request`, HTTP 400). The token stays valid for deferred pickup and notifications.
 */
class CredentialLimitExceededException(
    message: String,
) : IllegalStateException(message)

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

/**
 * The requested credential `format` cannot be issued (→ OID4VCI `unsupported_credential_format`).
 *
 * Raised for a format the configured [Oidc4VciCredentialBuilder] does not sign, and for every
 * format when no builder is configured: the issuer never emits a credential it did not sign.
 */
class UnsupportedCredentialFormatException(
    message: String,
) : IllegalArgumentException(message)

/**
 * The issuer is holding as many offers, tokens or deferred credentials as it is configured to,
 * even after expired entries were purged. Surfaces as `503 temporarily_unavailable`; refusing is
 * deliberate, because silently evicting a live entry would invalidate a holder's valid offer.
 */
class IssuerCapacityExceededException(
    message: String,
) : IllegalStateException(message)

/** A deferred credential awaiting pickup, with the time it was registered (for expiry). */
data class DeferredEntry(
    val credential: String,
    val issuedAt: Long,
    /**
     * SHA-256 (hex) of the access token this credential may be collected with, or `null` for an
     * entry any live access token may collect. Never the token itself.
     */
    val ownerTokenHash: String? = null,
)

/** What an [Oidc4VciCredentialBuilder] is asked to issue. */
data class Oidc4VciCredentialRequest(
    /** The OID4VCI `format` identifier, always one of [Oidc4VciCredentialBuilder.supportedFormats]. */
    val format: String,
    val credentialTypes: List<String>,
    val issuerDid: String,
    /** The DID derived from the key the holder proved possession of. */
    val subjectDid: String,
    /** The claims carried by the offer, without any `id` (the subject id is [subjectDid]). */
    val claims: JsonObject,
)

/**
 * The signing hook of [Oidc4VciIssuerService]: turns a verified request into the credential string
 * returned to the wallet (a compact JWT for `jwt_vc_json`, a JSON document for `ldp_vc`).
 *
 * The issuer service has no way to produce a credential on its own; it fails closed
 * (`unsupported_credential_format`) until one is configured. [CredentialServiceCredentialBuilder]
 * adapts a `CredentialService` (and through it the KMS); a host may supply its own.
 */
interface Oidc4VciCredentialBuilder {
    /** OID4VCI `format` identifiers this builder signs. Anything else is refused before issuance. */
    val supportedFormats: Set<String>

    suspend fun build(request: Oidc4VciCredentialRequest): String
}

/** One message for an unknown and an expired access token, so the response does not say which. */
private const val INVALID_TOKEN_MESSAGE = "Invalid or expired access_token"

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
private val ED25519_SPKI_PREFIX =
    byteArrayOf(
        0x30,
        0x2A,
        0x30,
        0x05,
        0x06,
        0x03,
        0x2B,
        0x65,
        0x70,
        0x03,
        0x21,
        0x00,
    )

class Oidc4VciIssuerService
    @JvmOverloads
    constructor(
        val baseUrl: String,
        val issuerDid: String,
        val supportedConfigurations: Map<String, CredentialConfiguration> = emptyMap(),
        /** Access-token lifetime advertised as `expires_in` and enforced at the credential endpoint. */
        val tokenTtlSeconds: Long = 3600,
        /** Lifetime of each issued `c_nonce`; an expired nonce yields `invalid_proof` with a fresh one. */
        val cNonceTtlSeconds: Long = 300,
        /**
         * How long a pre-authorized code stays redeemable. Five minutes by default.
         *
         * Short because the code is a bearer secret that travels through channels nobody controls,
         * and because the wallet redeems it seconds after the holder scans the offer. Raise it only
         * for a flow where the holder genuinely needs longer, and know that the window is exactly how
         * long a leaked offer stays usable.
         */
        val offerTtlSeconds: Long = 300,
        /** Upper bound on retained, unredeemed offers (after expired ones are purged). */
        val maxPendingOffers: Int = 10_000,
        /** Upper bound on retained access tokens (after expired ones are purged). */
        val maxActiveTokens: Int = 10_000,
        /** How long a deferred credential waits for pickup before it is dropped. */
        val deferredTtlSeconds: Long = 3600,
        /** Upper bound on retained deferred credentials. */
        val maxDeferredCredentials: Int = 10_000,
        /**
         * Where offers, access tokens and deferred credentials are kept. Defaults to a process-local
         * in-memory store; supply another [Oidc4VciIssuerStateStore] to share or persist issuer state.
         *
         * Deferred credentials awaiting pickup at the deferred endpoint are keyed by transaction id.
         * Nothing in this service defers issuance by itself; a host that does registers results through
         * [registerDeferredCredential]. Entries expire after [deferredTtlSeconds] and the store is
         * bounded by [maxDeferredCredentials].
         */
        val stateStore: Oidc4VciIssuerStateStore = InMemoryOidc4VciIssuerStateStore(),
        /**
         * Signs the credentials this issuer hands out. When `null` (the default) the issuer fails
         * closed: every credential request is refused with `unsupported_credential_format`, because
         * an unsigned credential is not something a wallet can rely on.
         */
        val credentialBuilder: Oidc4VciCredentialBuilder? = null,
        /** Time source for offer, token, `c_nonce` and deferred-credential lifetimes; replaceable in tests. */
        private val clock: Clock = Clock.System,
    ) {
        private fun nowMillis(): Long = clock.now().toEpochMilliseconds()

        fun getMetadata(): CredentialIssuerMetadata =
            CredentialIssuerMetadata(
                credentialIssuer = baseUrl,
                credentialEndpoint = "$baseUrl/credential",
                tokenEndpoint = "$baseUrl/token",
                deferredCredentialEndpoint = "$baseUrl/deferred_credential",
                notificationEndpoint = "$baseUrl/notification",
                credentialConfigurationsSupported = supportedConfigurations,
            )

        fun createOffer(
            credentialTypes: List<String>,
            txCode: TxCode? = null,
            txCodeValue: String? = null,
            claims: JsonObject = JsonObject(emptyMap()),
        ): CreateOfferResponse {
            require(credentialTypes.isNotEmpty()) { "credentialTypes must not be empty" }
            val unsupported = credentialTypes.filter { it !in supportedConfigurations }
            require(unsupported.isEmpty()) {
                "credentialTypes not in this issuer's supported configurations: $unsupported"
            }
            require((txCode == null) == (txCodeValue == null)) {
                "txCode and txCodeValue must be provided together"
            }
            require(txCodeValue == null || txCodeValue.isNotEmpty()) { "txCodeValue must not be empty" }
            purgeExpired()
            val preAuthCode = UUID.randomUUID().toString()
            val offerState = OfferState(credentialTypes, txCode, txCodeValue, claims, issuedAt = nowMillis())
            if (!stateStore.putOffer(preAuthCode, offerState, maxPendingOffers)) {
                throw IssuerCapacityExceededException("Too many pending credential offers ($maxPendingOffers)")
            }
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
            val preAuthGrant =
                buildJsonObject {
                    put("pre-authorized_code", preAuthCode)
                    if (txCode != null) {
                        // encodeDefaults so the defaulted input_mode ("numeric") is still emitted
                        // in the offer; explicitNulls=false drops absent length/description.
                        val json =
                            Json {
                                encodeDefaults = true
                                explicitNulls = false
                            }
                        put("tx_code", json.encodeToJsonElement(TxCode.serializer(), txCode))
                    }
                }
            val offerJson =
                buildJsonObject {
                    put("credential_issuer", baseUrl)
                    put("credential_configuration_ids", JsonArray(credentialTypes.map { JsonPrimitive(it) }))
                    put(
                        "grants",
                        buildJsonObject {
                            put(Oidc4VciService.PRE_AUTHORIZED_CODE_GRANT_TYPE, preAuthGrant)
                        },
                    )
                }
            val encodedOffer =
                URLEncoder.encode(
                    Json.encodeToString(JsonObject.serializer(), offerJson),
                    "UTF-8",
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
        fun exchangePreAuthCode(
            preAuthCode: String,
            txCodeValue: String?,
        ): TokenResponse {
            purgeExpired()
            // Removed before it is judged, so a redemption attempt consumes the code either way: a
            // wrong tx_code must not leave the offer available for another guess.
            val offerState =
                stateStore.consumeOffer(preAuthCode)
                    ?: throw IllegalArgumentException("Unknown or expired pre-authorized_code")
            if (nowMillis() - offerState.issuedAt >= offerTtlSeconds * 1000) {
                throw IllegalArgumentException("Unknown or expired pre-authorized_code")
            }
            if (offerState.txCode != null) {
                val expected = offerState.txCodeValue?.toByteArray(Charsets.UTF_8)
                val provided = txCodeValue?.toByteArray(Charsets.UTF_8)
                require(expected != null && provided != null && MessageDigest.isEqual(provided, expected)) {
                    "Invalid tx_code"
                }
            }
            val accessToken = UUID.randomUUID().toString()
            val entry = TokenEntry(offerState, issuedAt = nowMillis(), cNonceIssuedAt = nowMillis())
            if (!stateStore.putToken(accessToken, entry, maxActiveTokens)) {
                throw IssuerCapacityExceededException("Too many active access tokens ($maxActiveTokens)")
            }
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
         * - `typ` header equals `openid4vci-proof+jwt`;
         * - `aud` claim equals this issuer's URL;
         * - `nonce` claim equals the current (unexpired) `c_nonce` bound to the access token.
         *
         * Any violation throws [InvalidProofException] carrying a freshly rotated `c_nonce`
         * (the route surfaces it in the `invalid_proof` error response so wallets can retry);
         * an unknown or expired access token throws [InvalidTokenException].
         *
         * The issued credential's subject is bound to the proven key: `credentialSubject.id`
         * is the `did:key` derived from the proof's `jwk` header (or the `kid` DID). The credential
         * is produced and signed by [credentialBuilder] and carries the offer's claims; a format it
         * does not support (or no builder at all) throws [UnsupportedCredentialFormatException].
         */
        suspend fun issueCredential(
            accessToken: String,
            format: String,
            credentialTypes: List<String>,
            proofJwt: String?,
        ): CredentialServerResponse {
            val entry = requireValidToken(accessToken)
            if (entry.remainingCredentials <= 0) throw tokenExhausted()
            // Refused before the proof is judged, so an unsupported format does not burn a c_nonce.
            val builder = credentialBuilder
            if (builder == null || format !in builder.supportedFormats) {
                throw UnsupportedCredentialFormatException(
                    if (builder == null) {
                        "This issuer has no credential signing backend configured"
                    } else {
                        "Credential format '$format' is not supported; supported: ${builder.supportedFormats.sorted()}"
                    },
                )
            }
            val subjectDid = verifyProofOrThrow(accessToken, proofJwt)
            // Reserved atomically before signing, so concurrent requests cannot together exceed the
            // offer; given back if signing fails, since nothing was issued.
            reserveCredential(accessToken)
            val credential =
                try {
                    builder.build(
                        Oidc4VciCredentialRequest(
                            format = format,
                            credentialTypes = entry.offerState.credentialTypes.ifEmpty { credentialTypes },
                            issuerDid = issuerDid,
                            subjectDid = subjectDid,
                            claims = JsonObject(entry.offerState.claims.filterKeys { it != "id" }),
                        ),
                    )
                } catch (cancelled: kotlin.coroutines.cancellation.CancellationException) {
                    releaseCredential(accessToken)
                    throw cancelled
                } catch (e: Throwable) {
                    releaseCredential(accessToken)
                    throw e
                }
            // Rotate the c_nonce on success too (OID4VCI v1.0 §7.3): each proof is single-use.
            val freshNonce = rotateCNonce(accessToken)
            return CredentialServerResponse(
                credential = credential,
                format = format,
                cNonce = freshNonce,
                cNonceExpiresIn = cNonceTtlSeconds,
            )
        }

        fun getDeferredCredential(
            transactionId: String,
            accessToken: String,
        ): CredentialServerResponse? {
            requireValidToken(accessToken)
            // Only the access token the credential was registered for may collect it; a mismatch
            // leaves the entry in place for its rightful owner.
            val entry = stateStore.consumeDeferredOwnedBy(transactionId, tokenHash(accessToken)) ?: return null
            if (nowMillis() - entry.issuedAt >= deferredTtlSeconds * 1000) return null
            return CredentialServerResponse(credential = entry.credential)
        }

        /**
         * Makes [credentialJson] collectable at the deferred endpoint under [transactionId].
         * Throws [IssuerCapacityExceededException] when the bound is reached.
         *
         * Pass the [ownerAccessToken] of the wallet session that requested the credential to bind it
         * to that session: only that access token can then collect it, so a guessed or leaked
         * transaction id is useless to anyone else. Without it any live access token can collect it.
         */
        @JvmOverloads
        fun registerDeferredCredential(
            transactionId: String,
            credentialJson: String,
            ownerAccessToken: String? = null,
        ) {
            purgeExpired()
            val entry = DeferredEntry(credentialJson, nowMillis(), ownerAccessToken?.let(::tokenHash))
            if (!stateStore.putDeferred(transactionId, entry, maxDeferredCredentials)) {
                throw IssuerCapacityExceededException("Too many deferred credentials ($maxDeferredCredentials)")
            }
        }

        private fun tokenHash(accessToken: String): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(accessToken.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        /** Throws [InvalidTokenException] unless [accessToken] is a live access token. */
        fun requireAccessToken(accessToken: String) {
            requireValidToken(accessToken)
        }

        fun recordNotification(notification: Oidc4VciNotification) {
            // no-op — extend to persist/emit events
        }

        /**
         * Drops offers and tokens that can no longer be redeemed, and reports how many went.
         *
         * Both maps were only ever emptied by a caller arriving with the right key: an offer on
         * redemption, a token when someone happened to present an expired one. An offer nobody
         * redeems and a token nobody reuses therefore stayed for the life of the process, which for a
         * long-running issuer means every one of them.
         *
         * Called on the paths that add entries, so the steady-state size is bounded by issuance rate
         * times TTL rather than by uptime. Public so a host can also run it on its own schedule, and
         * so the bound is testable.
         */
        fun purgeExpired(now: Long = nowMillis()): Int =
            stateStore.purgeExpired(
                offersIssuedAtOrBefore = now - offerTtlSeconds * 1000,
                tokensIssuedAtOrBefore = now - tokenTtlSeconds * 1000,
                deferredIssuedAtOrBefore = now - deferredTtlSeconds * 1000,
            )

        /** Retained offer and token counts, for a host metric or a test. Never the secrets themselves. */
        fun retainedState(): Pair<Int, Int> = stateStore.offerCount() to stateStore.tokenCount()

        /** Retained deferred-credential count, for a host metric or a test. */
        fun retainedDeferredCount(): Int = stateStore.deferredCount()

        /** Returns the live [TokenEntry] or throws [InvalidTokenException] (unknown/expired). */
        private fun requireValidToken(accessToken: String): TokenEntry {
            val entry =
                stateStore.getToken(accessToken)
                    ?: throw InvalidTokenException(INVALID_TOKEN_MESSAGE)
            if (nowMillis() - entry.issuedAt >= tokenTtlSeconds * 1000) {
                stateStore.removeToken(accessToken)
                throw InvalidTokenException(INVALID_TOKEN_MESSAGE)
            }
            return entry
        }

        private fun tokenExhausted() =
            CredentialLimitExceededException(
                "This access token has already obtained the credentials its offer covered",
            )

        private fun reserveCredential(accessToken: String) {
            val reserved =
                stateStore.updateToken(accessToken) { entry ->
                    if (entry.remainingCredentials > 0) {
                        entry.copy(remainingCredentials = entry.remainingCredentials - 1) to true
                    } else {
                        entry to false
                    }
                } ?: throw InvalidTokenException(INVALID_TOKEN_MESSAGE)
            if (!reserved) throw tokenExhausted()
        }

        private fun releaseCredential(accessToken: String) {
            stateStore.updateToken(accessToken) { entry ->
                entry.copy(remainingCredentials = entry.remainingCredentials + 1) to Unit
            }
        }

        /** Rotates the `c_nonce` bound to [accessToken] and returns the fresh value. */
        private fun rotateCNonce(accessToken: String): String {
            val fresh = UUID.randomUUID().toString()
            stateStore.updateToken(accessToken) { entry ->
                entry.copy(cNonce = fresh, cNonceIssuedAt = nowMillis()) to Unit
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
        private fun verifyProofOrThrow(
            accessToken: String,
            proofJwt: String?,
        ): String {
            fun reject(reason: String): Nothing = throw InvalidProofException(reason, rotateCNonce(accessToken), cNonceTtlSeconds)

            if (proofJwt.isNullOrBlank()) reject("Missing proof.jwt in credential request")

            val parts = proofJwt.split(".")
            when {
                parts.size == 5 -> reject("Encrypted proof JWTs (JWE) are not supported")
                parts.size != 3 -> reject("proof.jwt is not a compact JWS")
            }

            val decoder = Base64.getUrlDecoder()
            val lenientJson = Json { ignoreUnknownKeys = true }
            val header =
                runCatching {
                    lenientJson.parseToJsonElement(String(decoder.decode(parts[0]), Charsets.UTF_8)).jsonObject
                }.getOrNull() ?: reject("proof.jwt header is not valid base64url JSON")
            val payload =
                runCatching {
                    lenientJson.parseToJsonElement(String(decoder.decode(parts[1]), Charsets.UTF_8)).jsonObject
                }.getOrNull() ?: reject("proof.jwt payload is not valid base64url JSON")

            val alg = header["alg"]?.jsonPrimitive?.contentOrNull
            if (alg == null || alg.equals("none", ignoreCase = true)) {
                reject("Unsigned proof (alg=none) is not accepted")
            }
            if (alg != "EdDSA") reject("Unsupported proof alg '$alg' — only EdDSA (Ed25519) is supported")
            // OID4VCI v1.0 Appendix F.1: the typ header is REQUIRED, which is what stops a JWT minted
            // for another purpose from being replayed as a proof of possession.
            val typ = header["typ"]?.jsonPrimitive?.contentOrNull
            if (typ != "openid4vci-proof+jwt") {
                reject("proof.jwt typ must be 'openid4vci-proof+jwt', got '${typ ?: "<absent>"}'")
            }

            val proofKey =
                extractProofKey(header)
                    ?: reject("proof.jwt carries no usable key (jwk header with OKP/Ed25519, or did:key kid, required)")

            val signature =
                runCatching { decoder.decode(parts[2]) }.getOrNull()
                    ?: reject("proof.jwt signature is not valid base64url")
            if (signature.size != ED25519_SIGNATURE_LENGTH_BYTES) {
                reject("proof.jwt signature has invalid length for Ed25519")
            }
            val verified =
                runCatching {
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

            val nonce =
                payload["nonce"]?.jsonPrimitive?.contentOrNull
                    ?: reject("proof.jwt is missing the nonce claim")
            // Atomic consume-and-rotate: the compare and the rotation happen inside one
            // updateToken step so a c_nonce is strictly single-use — two concurrent
            // credential requests echoing the same nonce cannot both pass.
            if (!consumeCNonce(accessToken, nonce)) {
                reject("proof.jwt nonce does not match the current c_nonce (or it expired) — retry with the fresh c_nonce")
            }

            return proofKey.subjectDid
        }

        /**
         * Atomically validates [presentedNonce] against the token's live, unexpired `c_nonce`
         * and rotates it in the same [Oidc4VciIssuerStateStore.updateToken] step (single-use).
         */
        private fun consumeCNonce(
            accessToken: String,
            presentedNonce: String,
        ): Boolean =
            stateStore.updateToken(accessToken) { entry ->
                val live = nowMillis() - entry.cNonceIssuedAt < cNonceTtlSeconds * 1000
                val matches =
                    MessageDigest.isEqual(
                        presentedNonce.toByteArray(Charsets.UTF_8),
                        entry.cNonce.toByteArray(Charsets.UTF_8),
                    )
                if (live && matches) {
                    entry.copy(cNonce = UUID.randomUUID().toString(), cNonceIssuedAt = nowMillis()) to true
                } else {
                    entry to false
                }
            } ?: false

        /** A verified proof key: the JCA public key plus the subject DID it binds the credential to. */
        private data class ProofKey(
            val publicKey: PublicKey,
            val subjectDid: String,
        )

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
                val decoded =
                    runCatching { multibase.removePrefix("z").decodeBase58() }.getOrNull()
                        ?: return null
                if (decoded.size != ED25519_RAW_PUBLIC_KEY_LENGTH_BYTES + 2 ||
                    decoded[0] != ED25519_MULTICODEC_PREFIX[0] ||
                    decoded[1] != ED25519_MULTICODEC_PREFIX[1]
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
                KeyFactory
                    .getInstance("Ed25519")
                    .generatePublic(X509EncodedKeySpec(ED25519_SPKI_PREFIX + rawKeyBytes))
            } catch (_: Exception) {
                null
            }
        }
    }

data class CreateOfferResponse(
    val offerUri: String,
    val preAuthCode: String,
)

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
