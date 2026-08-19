package org.trustweave.did.resolution

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.trustweave.did.resolver.DidResolutionError
import org.trustweave.did.util.XmlDateTimeSerializer

/**
 * Input options to the DID resolution function per DID Resolution 1.0 §4.1.
 *
 * The structure is REQUIRED as an input to `resolve` but MAY be empty — use [EMPTY].
 *
 * [fromQueryParameters] and [fromJson] are the supported entry points for options arriving from
 * outside this library (the §12.1 GET and POST bindings respectively): both parse defensively and
 * surface a malformed value via [validate] rather than throwing. Direct
 * `Json.decodeFromString<ResolutionOptions>(…)` is reserved for round-tripping values this
 * library itself already produced — a malformed `versionTime` there still throws out of
 * [XmlDateTimeSerializer], since that serializer is shared with [org.trustweave.did.resolver.DidResolutionMetadata]
 * and [org.trustweave.did.model.DidDocumentMetadata], where silently nulling a bad timestamp
 * would hide data loss.
 *
 * @param accept media type of the caller's preferred DID document representation (§4.1)
 * @param expandRelativeUrls expand relative DID URLs in the document to absolute ones (§4.1)
 * @param versionId resolve a specific document version (§3, §13.4)
 * @param versionTime resolve the version valid at this time (§3, §13.4)
 * @param noCache request a fresh read from the verifiable data registry (§13.2)
 * @param additional registered or method-specific options not modelled above
 */
@Serializable
data class ResolutionOptions(
    val accept: String? = null,
    val expandRelativeUrls: Boolean = false,
    val versionId: String? = null,
    @Serializable(with = XmlDateTimeSerializer::class) val versionTime: Instant? = null,
    val noCache: Boolean = false,
    val additional: Map<String, String> = emptyMap(),
    /**
     * Set when one or more inbound option values failed to parse (e.g. a malformed `versionTime`
     * or an unrecognised boolean); multiple failures are joined into one message. Surfaced by
     * [validate] as INVALID_OPTIONS rather than thrown, when the instance is built via
     * [fromQueryParameters] or [fromJson] — this class's two defensive entry points. `private`
     * here only hides the generated accessor: nothing stops constructing or `copy`-ing this field
     * directly, so treat it as an internal parse-result carrier rather than a type-enforced
     * invariant.
     */
    @Transient private val parseError: String? = null
) {
    /** True when no option is set — the "MAY be empty" case of §4. */
    fun isEmpty(): Boolean =
        accept == null && !expandRelativeUrls && versionId == null &&
            versionTime == null && !noCache && additional.isEmpty() && parseError == null

    /**
     * Options that only a DID method can satisfy. A resolver that cannot delegate these MUST
     * return FEATURE_NOT_SUPPORTED (§4.4 step 3).
     *
     * `accept` and `expandRelativeUrls` are deliberately excluded: they are method-independent
     * and handled by the generic resolver.
     */
    fun methodSpecificOptions(): Set<String> = buildSet {
        if (versionId != null) add("versionId")
        if (versionTime != null) add("versionTime")
        if (noCache) add("noCache")
    }

    /** Returns an INVALID_OPTIONS error when the options are invalid (§4.4 step 4), else null. */
    fun validate(): DidResolutionError? = when {
        parseError != null -> DidResolutionError.invalidOptions(parseError)
        versionId != null && versionTime != null ->
            DidResolutionError.invalidOptions(
                "versionId and versionTime are mutually exclusive (DID Resolution 1.0 §13.4)"
            )
        else -> null
    }

    companion object {
        /** The empty options structure. */
        val EMPTY: ResolutionOptions = ResolutionOptions()

        private val SPEC_KEYS = setOf("accept", "expandRelativeUrls", "versionId", "versionTime", "noCache")

        /**
         * Parses a §13.2-style boolean option strictly: an absent value stays absent (`false`,
         * no error), but §13.2 fixes the value space at exactly `"true"` / `"false"`
         * (case-insensitively), so a *present but unrecognised* value (e.g. `"1"`, `"yes"`) is a
         * caller error appended to [errors] rather than silently coerced to `false`.
         */
        private fun parseStrictBoolean(name: String, raw: String?, errors: MutableList<String>): Boolean =
            when {
                raw == null -> false
                raw.equals("true", ignoreCase = true) -> true
                raw.equals("false", ignoreCase = true) -> false
                else -> {
                    errors += "$name must be \"true\" or \"false\" (DID Resolution 1.0 §13.2): '$raw'"
                    false
                }
            }

        /**
         * Builds options from query-parameter style input, as delivered by the §12.1 GET binding.
         * A malformed `versionTime` or an unrecognised `expandRelativeUrls`/`noCache` value is
         * recorded and surfaced by [validate] as INVALID_OPTIONS rather than thrown.
         */
        fun fromQueryParameters(params: Map<String, String>): ResolutionOptions {
            val errors = mutableListOf<String>()
            val versionTime = params["versionTime"]?.let { raw ->
                runCatching { Instant.parse(raw) }.getOrElse {
                    errors += "versionTime is not a valid datetime (DID Resolution 1.0 §3.1): '$raw'"
                    null
                }
            }
            val expandRelativeUrls = parseStrictBoolean("expandRelativeUrls", params["expandRelativeUrls"], errors)
            val noCache = parseStrictBoolean("noCache", params["noCache"], errors)
            return ResolutionOptions(
                accept = params["accept"],
                expandRelativeUrls = expandRelativeUrls,
                versionId = params["versionId"],
                versionTime = versionTime,
                noCache = noCache,
                additional = params.filterKeys { it !in SPEC_KEYS },
                parseError = errors.takeIf { it.isNotEmpty() }?.joinToString("; ")
            )
        }

        /**
         * Builds options from a JSON object, as delivered by the §12.1 POST binding request body.
         * Mirrors [fromQueryParameters]: a malformed `versionTime` or an unrecognised
         * `expandRelativeUrls`/`noCache` value is recorded and surfaced by [validate] as
         * INVALID_OPTIONS rather than thrown. Unrecognised member types (objects, arrays) are
         * dropped defensively rather than throwing, matching the degrade style used by
         * [org.trustweave.did.resolver.DidResolutionMetadata.fromJson].
         */
        fun fromJson(json: JsonObject): ResolutionOptions {
            val errors = mutableListOf<String>()
            val versionTimeRaw = (json["versionTime"] as? JsonPrimitive)?.contentOrNull
            val versionTime = versionTimeRaw?.let { raw ->
                runCatching { Instant.parse(raw) }.getOrElse {
                    errors += "versionTime is not a valid datetime (DID Resolution 1.0 §3.1): '$raw'"
                    null
                }
            }
            val expandRelativeUrls = parseStrictBoolean(
                "expandRelativeUrls",
                (json["expandRelativeUrls"] as? JsonPrimitive)?.contentOrNull,
                errors
            )
            val noCache = parseStrictBoolean(
                "noCache",
                (json["noCache"] as? JsonPrimitive)?.contentOrNull,
                errors
            )
            return ResolutionOptions(
                accept = (json["accept"] as? JsonPrimitive)?.contentOrNull,
                expandRelativeUrls = expandRelativeUrls,
                versionId = (json["versionId"] as? JsonPrimitive)?.contentOrNull,
                versionTime = versionTime,
                noCache = noCache,
                additional = json.entries
                    .filter { it.key !in SPEC_KEYS }
                    .mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }
                    .toMap(),
                parseError = errors.takeIf { it.isNotEmpty() }?.joinToString("; ")
            )
        }
    }
}
