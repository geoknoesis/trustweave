package org.trustweave.did.resolution

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.trustweave.did.resolver.DidResolutionError
import org.trustweave.did.util.XmlDateTimeSerializer

/**
 * Input options to the DID resolution function per DID Resolution 1.0 §4.1.
 *
 * The structure is REQUIRED as an input to `resolve` but MAY be empty — use [EMPTY].
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
     * Set when an inbound option value failed to parse (e.g. a malformed `versionTime`).
     * Surfaced by [validate] as INVALID_OPTIONS rather than thrown, because §4.4 requires an
     * error *result*, not an exception.
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
         * Builds options from query-parameter style input, as delivered by the §12.1 GET binding.
         * A malformed `versionTime` is recorded and surfaced by [validate] as INVALID_OPTIONS.
         */
        fun fromQueryParameters(params: Map<String, String>): ResolutionOptions {
            var parseError: String? = null
            val versionTime = params["versionTime"]?.let { raw ->
                try {
                    Instant.parse(raw)
                } catch (_: IllegalArgumentException) {
                    parseError = "versionTime is not a valid datetime (DID Resolution 1.0 §3.1): '$raw'"
                    null
                }
            }
            return ResolutionOptions(
                accept = params["accept"],
                expandRelativeUrls = params["expandRelativeUrls"]?.equals("true", ignoreCase = true) == true,
                versionId = params["versionId"],
                versionTime = versionTime,
                noCache = params["noCache"]?.equals("true", ignoreCase = true) == true,
                additional = params.filterKeys { it !in SPEC_KEYS },
                parseError = parseError
            )
        }
    }
}
