package org.trustweave.anchor

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.trustweave.core.util.JsonCanonicalization
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

/**
 * Builds and recognises the compact digest envelope anchored on-chain when a client
 * runs in digest payload mode (see [AbstractBlockchainAnchorClient.OPTION_PAYLOAD_MODE]).
 *
 * The envelope is a small JSON object that replaces the full payload on-chain so no
 * payload content (PII, business data, …) ever leaves the caller's custody:
 *
 * ```json
 * {"alg":"SHA-256","canon":"JCS","digest":"<base64url(sha256(JCS(payload)))>","mediaType":"application/json"}
 * ```
 *
 * **Payload bytes** are the RFC 8785 (JCS) canonical UTF-8 serialization of the payload
 * ([JsonCanonicalization]), so any structurally equal payload — whatever its key order,
 * whitespace or number spelling — verifies. The `canon` member records this.
 *
 * **Legacy envelopes** (written before canonicalization was introduced) have no `canon`
 * member; their digest is over the payload exactly as `Json.encodeToString(JsonElement
 * .serializer(), payload)` serialized it, i.e. key-order sensitive. They are still
 * recognised and verified with those legacy bytes, so existing anchors keep verifying. The
 * version is explicit in the envelope rather than guessed by trying both encodings, so a
 * JCS anchor can never be satisfied by a legacy-encoded payload or vice versa.
 *
 * The digest is base64url-encoded without padding (RFC 4648 §5).
 */
object AnchorDigest {
    /** The only digest algorithm currently produced and recognised. */
    const val ALGORITHM: String = "SHA-256"

    /** Envelope field holding the digest algorithm name. */
    const val FIELD_ALG: String = "alg"

    /** Envelope field holding the unpadded base64url digest value. */
    const val FIELD_DIGEST: String = "digest"

    /** Envelope field holding the media type of the original (off-chain) payload. */
    const val FIELD_MEDIA_TYPE: String = "mediaType"

    /** Envelope field naming the canonicalization applied before hashing (absent: legacy). */
    const val FIELD_CANONICALIZATION: String = "canon"

    /** [FIELD_CANONICALIZATION] value for RFC 8785 JSON Canonicalization Scheme. */
    const val CANONICALIZATION_JCS: String = "JCS"

    private val LEGACY_ENVELOPE_FIELDS = setOf(FIELD_ALG, FIELD_DIGEST, FIELD_MEDIA_TYPE)

    private val JCS_ENVELOPE_FIELDS = LEGACY_ENVELOPE_FIELDS + FIELD_CANONICALIZATION

    /** Byte length of a SHA-256 digest. */
    private const val SHA256_LENGTH_BYTES = 32

    /** SHA-256 of [payloadBytes]. */
    @JvmStatic
    fun sha256(payloadBytes: ByteArray): ByteArray = MessageDigest.getInstance(ALGORITHM).digest(payloadBytes)

    /** Unpadded base64url encoding of `sha256(payloadBytes)`. */
    @JvmStatic
    fun digestBase64Url(payloadBytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(payloadBytes))

    /**
     * Builds the RFC 8785 digest envelope anchored on-chain in digest payload mode: the
     * digest is over `JCS(payload)` and the envelope carries `canon = "JCS"`.
     *
     * @param payload the off-chain payload
     * @param mediaType the media type of the original payload
     */
    @JvmStatic
    fun envelope(
        payload: JsonElement,
        mediaType: String,
    ): JsonObject =
        buildJsonObject {
            put(FIELD_ALG, ALGORITHM)
            put(FIELD_CANONICALIZATION, CANONICALIZATION_JCS)
            put(FIELD_DIGEST, digestBase64Url(JsonCanonicalization.canonicalizeToBytes(payload)))
            put(FIELD_MEDIA_TYPE, mediaType)
        }

    /**
     * Builds a legacy (pre-canonicalization) digest envelope over raw [payloadBytes].
     *
     * Kept for compatibility; new anchors should use the [JsonElement] overload, whose digest
     * does not depend on key order.
     *
     * @param payloadBytes the exact bytes hashed (historically the UTF-8 of the serialized
     *   payload JSON)
     * @param mediaType the media type of the original payload
     */
    @JvmStatic
    fun envelope(
        payloadBytes: ByteArray,
        mediaType: String,
    ): JsonObject =
        buildJsonObject {
            put(FIELD_ALG, ALGORITHM)
            put(FIELD_DIGEST, digestBase64Url(payloadBytes))
            put(FIELD_MEDIA_TYPE, mediaType)
        }

    /**
     * Whether [element] has the shape of a digest envelope: a JSON object whose key
     * set is exactly `{alg, canon, digest, mediaType}` with `canon == "JCS"`, or the legacy
     * `{alg, digest, mediaType}`, with `alg == "SHA-256"` and a string `digest` that
     * base64url-decodes to exactly 32 bytes (the SHA-256 digest length).
     *
     * Shape-based recognition can in principle yield a false positive for a caller
     * payload that deliberately mimics the envelope; such payloads should be anchored
     * in digest mode (or wrapped) to avoid ambiguity. The exact-key-set and
     * digest-length requirements keep that surface as small as possible.
     */
    @JvmStatic
    fun isEnvelope(element: JsonElement): Boolean {
        if (element !is JsonObject) return false
        when (element.keys) {
            LEGACY_ENVELOPE_FIELDS -> Unit
            JCS_ENVELOPE_FIELDS -> {
                val canon = (element[FIELD_CANONICALIZATION] as? JsonPrimitive)?.takeIf { it.isString }?.content
                if (canon != CANONICALIZATION_JCS) return false
            }
            else -> return false
        }
        val alg = (element[FIELD_ALG] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (alg != ALGORITHM) return false
        val digest = (element[FIELD_DIGEST] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (digest.isNullOrEmpty()) return false
        val decoded =
            try {
                Base64.getUrlDecoder().decode(digest)
            } catch (_: IllegalArgumentException) {
                return false
            }
        return decoded.size == SHA256_LENGTH_BYTES
    }

    /** Whether [envelope] is a JCS (RFC 8785) envelope rather than a legacy one. */
    @JvmStatic
    fun isCanonicalized(envelope: JsonObject): Boolean = envelope.containsKey(FIELD_CANONICALIZATION)

    /**
     * Whether [envelope] attests to [payload], hashing the payload the way the envelope says it
     * was hashed: `JCS(payload)` for envelopes with `canon = "JCS"`, the legacy kotlinx
     * serialization bytes for envelopes without it.
     */
    @JvmStatic
    fun matches(
        envelope: JsonObject,
        payload: JsonElement,
    ): Boolean = matches(envelope, payload, requireCanonicalEnvelope = false)

    /**
     * Like [matches] with an option for verifiers that accept only RFC 8785 anchors.
     *
     * @param requireCanonicalEnvelope when true, a legacy envelope (no `canon` member) never
     *   matches. Legacy digests cover the kotlinx serialization bytes, which depend on key
     *   order and number spelling, so a strict verifier may prefer to reject them outright.
     *   Default behaviour ([matches] without this flag) keeps accepting legacy envelopes so old
     *   anchors keep verifying.
     */
    @JvmStatic
    fun matches(
        envelope: JsonObject,
        payload: JsonElement,
        requireCanonicalEnvelope: Boolean,
    ): Boolean {
        if (!isEnvelope(envelope)) return false
        if (requireCanonicalEnvelope && !isCanonicalized(envelope)) return false
        val bytes =
            if (isCanonicalized(envelope)) {
                try {
                    JsonCanonicalization.canonicalizeToBytes(payload)
                } catch (_: IllegalArgumentException) {
                    return false // e.g. a non-finite number cannot have been anchored
                }
            } else {
                kotlinx.serialization.json.Json
                    .encodeToString(JsonElement.serializer(), payload)
                    .toByteArray(StandardCharsets.UTF_8)
            }
        return matches(envelope, bytes)
    }

    /**
     * Whether [envelope] is a digest envelope whose digest matches [payloadBytes].
     * Uses a constant-time comparison; malformed envelopes or undecodable digests
     * yield `false`.
     */
    @JvmStatic
    fun matches(
        envelope: JsonObject,
        payloadBytes: ByteArray,
    ): Boolean {
        if (!isEnvelope(envelope)) return false
        val encoded = (envelope[FIELD_DIGEST] as? JsonPrimitive)?.content ?: return false
        val anchored =
            try {
                Base64.getUrlDecoder().decode(encoded)
            } catch (_: IllegalArgumentException) {
                return false
            }
        return MessageDigest.isEqual(anchored, sha256(payloadBytes))
    }
}
