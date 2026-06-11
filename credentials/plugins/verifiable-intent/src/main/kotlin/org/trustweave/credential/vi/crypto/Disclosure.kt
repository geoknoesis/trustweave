package org.trustweave.credential.vi.crypto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.security.SecureRandom

/**
 * A decoded SD-JWT disclosure.
 *
 * Two shapes per the SD-JWT spec:
 * - object property: `[salt, name, value]` (3 elements) — used for L1 `email`, etc.
 * - array element: `[salt, value]` (2 elements) — used for `delegate_payload` mandate refs and the
 *   nested merchant/item allowlists. **This 2-element form is the mechanism the current TrustWeave
 *   `SdJwtProofEngine` does not implement** and is load-bearing for VI's selective routing.
 */
internal data class Disclosure(
    val b64: String,
    val salt: String,
    val name: String?,
    val value: JsonElement,
) {
    val isArrayElement: Boolean get() = name == null
}

internal object Disclosures {
    private val json = Json { ignoreUnknownKeys = true }

    /** Decodes a base64url disclosure into [Disclosure], or null if malformed. */
    fun parse(b64: String): Disclosure? = runCatching {
        val arr: JsonArray = json.parseToJsonElement(String(B64.decode(b64), Charsets.UTF_8)).jsonArray
        when (arr.size) {
            3 -> Disclosure(b64, arr[0].jsonPrimitive.content, arr[1].jsonPrimitive.content, arr[2])
            2 -> Disclosure(b64, arr[0].jsonPrimitive.content, null, arr[1])
            else -> null
        }
    }.getOrNull()

    /**
     * SHA-256 of the **ASCII base64url string** (not the decoded bytes), base64url-encoded — the
     * SD-JWT disclosure digest used in `_sd` arrays and `{"...": digest}` refs.
     */
    fun hash(b64: String): String = sha256B64Url(b64.toByteArray(Charsets.US_ASCII))

    // -- Issuance-side factories --------------------------------------------------------------

    private val random = SecureRandom()

    private fun salt(): String = B64.encode(ByteArray(16).also { random.nextBytes(it) })

    /** A freshly created disclosure with its base64url form and digest. */
    data class Made(val b64: String, val hash: String)

    /** Object-property disclosure `[salt, name, value]` — referenced from `_sd`. */
    fun makeClaim(name: String, value: JsonElement): Made = make(buildJsonArray { add(salt()); add(name); add(value) })

    /** Array-element disclosure `[salt, value]` — referenced from `delegate_payload` / allowlists. */
    fun makeArrayElement(value: JsonElement): Made = make(buildJsonArray { add(salt()); add(value) })

    private fun make(arr: JsonArray): Made {
        val b64 = B64.encode(arr.toString().toByteArray(Charsets.UTF_8))
        return Made(b64, hash(b64))
    }
}
