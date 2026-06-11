package org.trustweave.anchor

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
 * {"alg":"SHA-256","digest":"<base64url(sha256(payload bytes))>","mediaType":"application/json"}
 * ```
 *
 * **Payload bytes are defined deterministically** as the UTF-8 encoding of the payload
 * JSON string exactly as serialized by the existing write path
 * (`Json.encodeToString(JsonElement.serializer(), payload)`). No canonicalization
 * (key sorting, whitespace normalisation, …) is applied: verifying a digest therefore
 * requires presenting a [JsonElement] that serializes to the same bytes — in practice,
 * the same element (or a structurally identical one with the same key order) that was
 * anchored.
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

    private val ENVELOPE_FIELDS = setOf(FIELD_ALG, FIELD_DIGEST, FIELD_MEDIA_TYPE)

    /** Byte length of a SHA-256 digest. */
    private const val SHA256_LENGTH_BYTES = 32

    /** SHA-256 of [payloadBytes]. */
    @JvmStatic
    fun sha256(payloadBytes: ByteArray): ByteArray =
        MessageDigest.getInstance(ALGORITHM).digest(payloadBytes)

    /** Unpadded base64url encoding of `sha256(payloadBytes)`. */
    @JvmStatic
    fun digestBase64Url(payloadBytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(payloadBytes))

    /**
     * Builds the digest envelope anchored on-chain in digest payload mode.
     *
     * @param payloadBytes the exact bytes the full-payload write path would have
     *   anchored (UTF-8 of the serialized payload JSON)
     * @param mediaType the media type of the original payload
     */
    @JvmStatic
    fun envelope(payloadBytes: ByteArray, mediaType: String): JsonObject = buildJsonObject {
        put(FIELD_ALG, ALGORITHM)
        put(FIELD_DIGEST, digestBase64Url(payloadBytes))
        put(FIELD_MEDIA_TYPE, mediaType)
    }

    /**
     * Whether [element] has the shape of a digest envelope: a JSON object whose key
     * set is exactly `{alg, digest, mediaType}` (the exact set [envelope] emits),
     * with `alg == "SHA-256"` and a string `digest` that base64url-decodes to
     * exactly 32 bytes (the SHA-256 digest length).
     *
     * Shape-based recognition can in principle yield a false positive for a caller
     * payload that deliberately mimics the envelope; such payloads should be anchored
     * in digest mode (or wrapped) to avoid ambiguity. The exact-key-set and
     * digest-length requirements keep that surface as small as possible.
     */
    @JvmStatic
    fun isEnvelope(element: JsonElement): Boolean {
        if (element !is JsonObject) return false
        if (element.keys != ENVELOPE_FIELDS) return false
        val alg = (element[FIELD_ALG] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (alg != ALGORITHM) return false
        val digest = (element[FIELD_DIGEST] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (digest.isNullOrEmpty()) return false
        val decoded = try {
            Base64.getUrlDecoder().decode(digest)
        } catch (_: IllegalArgumentException) {
            return false
        }
        return decoded.size == SHA256_LENGTH_BYTES
    }

    /**
     * Whether [envelope] is a digest envelope whose digest matches [payloadBytes].
     * Uses a constant-time comparison; malformed envelopes or undecodable digests
     * yield `false`.
     */
    @JvmStatic
    fun matches(envelope: JsonObject, payloadBytes: ByteArray): Boolean {
        if (!isEnvelope(envelope)) return false
        val encoded = (envelope[FIELD_DIGEST] as? JsonPrimitive)?.content ?: return false
        val anchored = try {
            Base64.getUrlDecoder().decode(encoded)
        } catch (_: IllegalArgumentException) {
            return false
        }
        return MessageDigest.isEqual(anchored, sha256(payloadBytes))
    }
}
