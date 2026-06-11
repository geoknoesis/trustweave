package org.trustweave.credential.vi.crypto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A parsed Verifiable Intent SD-JWT: the issuer/holder-signed JWT plus its trailing disclosures.
 *
 * Compact form: `<header.payload.sig>~<d1>~...~<dN>~`. VI carries holder binding inside the signed
 * JWT itself (`typ` = `kb-sd-jwt` / `kb-sd-jwt+kb`), so there is no separately appended KB-JWT
 * segment — every non-empty `~`-segment after the JWT is a disclosure. Mirrors
 * `crypto/sd_jwt.py::decode_sd_jwt`.
 */
internal class ViSdJwt private constructor(
    /** The literal compact string, exactly as received — all VI hashes are over these bytes. */
    val compact: String,
    /** The issuer/holder-signed JWT (first `~`-segment), parseable by Nimbus `SignedJWT`. */
    val jwt: String,
    val header: JsonObject,
    val payload: JsonObject,
    val disclosures: List<String>,
) {
    private val parsedDisclosures: List<Disclosure> = disclosures.mapNotNull { Disclosures.parse(it) }

    /** Map of disclosure digest → its base64url string (for reference/identity bindings). */
    fun discStringByHash(): Map<String, String> = disclosures.associateBy { Disclosures.hash(it) }

    /** The raw, unresolved `delegate_payload` array (`{"...": digest}` refs), or empty. */
    val rawDelegatePayload: JsonArray
        get() = payload["delegate_payload"] as? JsonArray ?: JsonArray(emptyList())

    /**
     * Resolves top-level `_sd` object-property disclosures and `delegate_payload` array refs into a
     * full claim set. Nested merchant/item refs inside mandates remain `{"...": digest}` (resolved
     * on demand via integrity bindings), matching `crypto/sd_jwt.py::resolve_disclosures`.
     */
    fun resolve(): JsonObject {
        val result = LinkedHashMap<String, JsonElement>(payload)
        val sdHashes: Set<String> =
            (payload["_sd"] as? JsonArray)?.mapNotNull { it.contentOrNull() }?.toSet() ?: emptySet()

        for (d in parsedDisclosures) {
            if (!d.isArrayElement && Disclosures.hash(d.b64) in sdHashes) {
                result[d.name!!] = d.value
            }
        }

        val dp = payload["delegate_payload"] as? JsonArray
        if (dp != null) {
            val byHash: Map<String, Disclosure> = parsedDisclosures.associateBy { Disclosures.hash(it.b64) }
            val resolved = dp.map { item ->
                val refHash = (item as? JsonObject)?.get("...")?.contentOrNull()
                if (refHash != null) byHash[refHash]?.value ?: item else item
            }
            result["delegate_payload"] = JsonArray(resolved)
        }
        return JsonObject(result)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Parses a compact SD-JWT; throws [IllegalArgumentException] on malformed structure. */
        fun parse(compact: String): ViSdJwt {
            val parts = compact.split("~")
            val jwt = parts.firstOrNull()
                ?.takeIf { it.count { c -> c == '.' } == 2 }
                ?: throw IllegalArgumentException("Malformed SD-JWT: first segment is not a JWT")
            val segs = jwt.split(".")
            val header = decodeSegment(segs[0]) ?: throw IllegalArgumentException("Malformed SD-JWT header")
            val payload = decodeSegment(segs[1]) ?: throw IllegalArgumentException("Malformed SD-JWT payload")
            val disclosures = parts.drop(1).filter { it.isNotEmpty() }
            return ViSdJwt(compact, jwt, header, payload, disclosures)
        }

        private fun decodeSegment(seg: String): JsonObject? = runCatching {
            json.parseToJsonElement(String(B64.decode(seg), Charsets.UTF_8)).jsonObject
        }.getOrNull()
    }
}

internal fun JsonElement.contentOrNull(): String? = runCatching { jsonPrimitive.content }.getOrNull()
