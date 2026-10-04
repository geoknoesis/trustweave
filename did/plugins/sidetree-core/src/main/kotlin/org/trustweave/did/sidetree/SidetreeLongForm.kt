package org.trustweave.did.sidetree

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.Base64

/**
 * Recognises and verifies Sidetree long-form DIDs: `<namespace>:[<anchor>:]<suffix>:<initial-state>`.
 *
 * The anchor reference of an Orb DID (a hashlink such as `hl:uEi...:uoQ-...`) may itself contain
 * colons, so the suffix is found from the right: the last segment is the initial state, the one
 * before it the suffix. A DID whose last segment is itself a suffix is a short form (possibly with an
 * anchor) and is never treated as a long form.
 */
object SidetreeLongForm {
    /** A long-form DID split into its parts. */
    data class Parsed(
        /** Canonical short form: the DID without its initial state. */
        val canonicalDid: String,
        val suffix: String,
        /** Base64url-encoded initial state (`{suffixData, delta}`). */
        val initialState: String,
    )

    /**
     * Returns the parts of [did] when it is a long form under [namespace] (for example `did:orb`),
     * or `null` for a short form or a DID outside the namespace.
     */
    fun parse(
        namespace: String,
        did: String,
    ): Parsed? {
        val prefix = "${namespace.trimEnd(':')}:"
        if (!did.startsWith(prefix)) return null
        val segments = did.removePrefix(prefix).split(":")
        if (segments.size < 2) return null
        val suffix = segments[segments.size - 2]
        val initialState = segments.last()
        if (!isSuffix(suffix) || isSuffix(initialState) || initialState.isEmpty()) return null
        return Parsed(did.removeSuffix(":$initialState"), suffix, initialState)
    }

    /**
     * Returns `null` when the initial state is `{suffixData, delta}` and
     * `base64url(multihash(sha256(JCS(suffixData))))` equals the suffix (and, when a delta is present,
     * `suffixData.deltaHash` is the hash of the delta); otherwise a human-readable reason.
     */
    fun verifyInitialState(parsed: Parsed): String? {
        val b64url = Base64.getUrlEncoder().withoutPadding()
        val state =
            try {
                val bytes = Base64.getUrlDecoder().decode(parsed.initialState)
                Json.parseToJsonElement(String(bytes, Charsets.UTF_8)) as? JsonObject
            } catch (e: IllegalArgumentException) {
                null
            } catch (e: SerializationException) {
                null
            } ?: return "initial state is not a base64url-encoded JSON object"
        val suffixData = state["suffixData"] as? JsonObject ?: return "initial state has no suffixData"
        val computed = b64url.encodeToString(SidetreeJcs.multihashSha256(SidetreeJcs.canonicalize(suffixData)))
        if (computed != parsed.suffix) return "initial state does not hash to the DID suffix"
        val delta = state["delta"] ?: return null
        val deltaObject = delta as? JsonObject ?: return "delta is not an object"
        val expected = b64url.encodeToString(SidetreeJcs.multihashSha256(SidetreeJcs.canonicalize(deltaObject)))
        val declared = (suffixData["deltaHash"] as? JsonPrimitive)?.contentOrNull
        return if (declared == expected) null else "delta does not match suffixData.deltaHash"
    }

    /** A multihash-SHA-256 value: 46 base64url characters starting with `Ei`. */
    private fun isSuffix(segment: String): Boolean = segment.length == 46 && segment.startsWith("Ei")
}
