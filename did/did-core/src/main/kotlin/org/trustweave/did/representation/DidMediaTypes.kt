package org.trustweave.did.representation

/**
 * Media types defined by DID 1.1 and DID Resolution 1.0.
 *
 * `application/did` is the DID document media type (§9.1 example). `application/did-resolution`
 * and `application/did-url-dereferencing` identify the result envelopes of §9 and §10.
 * The `+ld+json` / `+json` forms are DID Core 1.0 representation types, retained so existing
 * peers and stored documents keep working.
 */
object DidMediaTypes {
    const val DID: String = "application/did"
    const val DID_RESOLUTION: String = "application/did-resolution"
    const val DID_URL_DEREFERENCING: String = "application/did-url-dereferencing"

    const val DID_LD_JSON: String = "application/did+ld+json"
    const val DID_JSON: String = "application/did+json"
    const val JSON: String = "application/json"

    /** Document representations this build can produce and consume, most preferred first. */
    val SUPPORTED_DOCUMENT_TYPES: List<String> = listOf(DID, DID_LD_JSON, DID_JSON, JSON)

    /** True when [mediaType] (parameters and case ignored) is a supported document representation. */
    fun isSupportedDocumentType(mediaType: String): Boolean =
        SUPPORTED_DOCUMENT_TYPES.contains(normalize(mediaType))

    /** Strips media type parameters and lowercases, per RFC 9110 §8.3.1. */
    fun normalize(mediaType: String): String = mediaType.substringBefore(';').trim().lowercase()
}
