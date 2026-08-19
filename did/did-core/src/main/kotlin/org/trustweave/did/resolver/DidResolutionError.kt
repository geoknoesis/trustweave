package org.trustweave.did.resolver

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Error type URIs defined by DID Resolution 1.0 §11, plus the §12.1 HTTP status mapping.
 *
 * Per §11, error types that are not already URLs MUST be prefixed with [BASE].
 */
object DidErrorType {
    /** Prefix mandated by §11 for all non-URL error identifiers. */
    const val BASE: String = "https://www.w3.org/ns/did#"

    const val INVALID_DID: String = BASE + "INVALID_DID"
    const val INVALID_DID_DOCUMENT: String = BASE + "INVALID_DID_DOCUMENT"
    const val NOT_FOUND: String = BASE + "NOT_FOUND"
    const val REPRESENTATION_NOT_SUPPORTED: String = BASE + "REPRESENTATION_NOT_SUPPORTED"
    const val INVALID_DID_URL: String = BASE + "INVALID_DID_URL"
    const val METHOD_NOT_SUPPORTED: String = BASE + "METHOD_NOT_SUPPORTED"
    const val INVALID_OPTIONS: String = BASE + "INVALID_OPTIONS"
    const val INTERNAL_ERROR: String = BASE + "INTERNAL_ERROR"
    const val FEATURE_NOT_SUPPORTED: String = BASE + "FEATURE_NOT_SUPPORTED"

    /** HTTP status code for an error type per the §12.1 binding table. Unknown types map to 500. */
    fun httpStatus(type: String): Int = when (type) {
        INVALID_DID, INVALID_DID_URL, INVALID_OPTIONS -> 400
        NOT_FOUND -> 404
        REPRESENTATION_NOT_SUPPORTED -> 406
        METHOD_NOT_SUPPORTED, FEATURE_NOT_SUPPORTED -> 501
        else -> 500
    }

    /** Short human-readable title for an error type (§11: `title` SHOULD be present). */
    fun title(type: String): String = when (type) {
        INVALID_DID -> "Invalid DID"
        INVALID_DID_DOCUMENT -> "Invalid DID document"
        NOT_FOUND -> "Not found"
        REPRESENTATION_NOT_SUPPORTED -> "Representation not supported"
        INVALID_DID_URL -> "Invalid DID URL"
        METHOD_NOT_SUPPORTED -> "Method not supported"
        INVALID_OPTIONS -> "Invalid options"
        INTERNAL_ERROR -> "Internal error"
        FEATURE_NOT_SUPPORTED -> "Feature not supported"
        else -> "DID resolution error"
    }

    /**
     * Maps a DID Resolution v0.3 / FPWD camelCase error code to its CR type URI.
     *
     * Public universal resolvers still emit the legacy strings, so inbound responses must be
     * upgraded rather than rejected. Values that are already absolute URLs pass through; any
     * other unknown value is prefixed with [BASE] as §11 requires.
     */
    fun fromLegacyCode(code: String): String = when (code) {
        "invalidDid", "invalidDidFormat" -> INVALID_DID
        "invalidDidDocument" -> INVALID_DID_DOCUMENT
        "notFound" -> NOT_FOUND
        "representationNotSupported" -> REPRESENTATION_NOT_SUPPORTED
        "invalidDidUrl" -> INVALID_DID_URL
        "methodNotSupported", "unsupportedDidMethod" -> METHOD_NOT_SUPPORTED
        "invalidOptions" -> INVALID_OPTIONS
        "internalError", "resolutionError" -> INTERNAL_ERROR
        "featureNotSupported" -> FEATURE_NOT_SUPPORTED
        else -> if (code.startsWith("http://") || code.startsWith("https://")) code else BASE + code
    }
}

/**
 * An error data structure per [RFC 9457][https://www.rfc-editor.org/rfc/rfc9457] as required by
 * DID Resolution 1.0 §4.2 / §5.2 / §11.
 *
 * @param type absolute URL identifying the error condition; see [DidErrorType]
 * @param title short human-readable summary (SHOULD be present per §11)
 * @param detail longer human-readable explanation (SHOULD be present per §11)
 */
@Serializable
data class DidResolutionError(
    val type: String,
    val title: String? = null,
    val detail: String? = null
) {
    init {
        require(type.startsWith("http://") || type.startsWith("https://")) {
            "RFC 9457 error type MUST be a URL (DID Resolution 1.0 §11), got: '$type'"
        }
    }

    /** HTTP status code this error maps to per §12.1. */
    val httpStatus: Int get() = DidErrorType.httpStatus(type)

    /** Serializes to the RFC 9457 JSON object, omitting absent members. */
    fun toJson(): JsonObject = buildJsonObject {
        put("type", type)
        title?.let { put("title", it) }
        detail?.let { put("detail", it) }
    }

    companion object {
        /** Builds an error with the registered [DidErrorType.title] for [type]. */
        fun of(type: String, detail: String? = null): DidResolutionError =
            DidResolutionError(type = type, title = DidErrorType.title(type), detail = detail)

        fun invalidDid(detail: String): DidResolutionError = of(DidErrorType.INVALID_DID, detail)

        fun invalidDidDocument(detail: String): DidResolutionError =
            of(DidErrorType.INVALID_DID_DOCUMENT, detail)

        fun notFound(detail: String): DidResolutionError = of(DidErrorType.NOT_FOUND, detail)

        fun representationNotSupported(detail: String): DidResolutionError =
            of(DidErrorType.REPRESENTATION_NOT_SUPPORTED, detail)

        fun invalidDidUrl(detail: String): DidResolutionError = of(DidErrorType.INVALID_DID_URL, detail)

        fun methodNotSupported(detail: String): DidResolutionError =
            of(DidErrorType.METHOD_NOT_SUPPORTED, detail)

        fun invalidOptions(detail: String): DidResolutionError = of(DidErrorType.INVALID_OPTIONS, detail)

        fun internalError(detail: String): DidResolutionError = of(DidErrorType.INTERNAL_ERROR, detail)

        fun featureNotSupported(detail: String): DidResolutionError =
            of(DidErrorType.FEATURE_NOT_SUPPORTED, detail)

        /**
         * Parses an `error` member from a resolution-metadata JSON structure.
         *
         * Accepts both the CR object form and the legacy v0.3 bare-string form; the legacy
         * string is preserved as [detail] so upstream diagnostics are not lost.
         */
        fun fromJson(element: JsonElement?): DidResolutionError? = when {
            element == null || element is JsonNull -> null
            element is JsonPrimitive && element.isString ->
                DidResolutionError(
                    type = DidErrorType.fromLegacyCode(element.content),
                    title = DidErrorType.title(DidErrorType.fromLegacyCode(element.content)),
                    detail = element.content
                )
            element is JsonObject -> {
                val type = element["type"]?.jsonPrimitive?.contentOrNull
                if (type == null) {
                    null
                } else {
                    DidResolutionError(
                        type = DidErrorType.fromLegacyCode(type),
                        title = element["title"]?.jsonPrimitive?.contentOrNull
                            ?: DidErrorType.title(DidErrorType.fromLegacyCode(type)),
                        detail = element["detail"]?.jsonPrimitive?.contentOrNull
                    )
                }
            }
            else -> null
        }
    }
}
