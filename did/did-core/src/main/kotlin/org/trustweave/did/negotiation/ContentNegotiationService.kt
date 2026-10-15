package org.trustweave.did.negotiation

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.trustweave.did.model.DidDocument
import org.trustweave.did.representation.DidMediaTypes

/**
 * Content negotiation service for DID resolution.
 *
 * Supports multiple content types for DID documents to ensure interoperability
 * with different clients and use cases.
 *
 * **Supported Content Types:**
 * - `application/did` (default) - DID 1.1 / DID Resolution 1.0 media type
 * - `application/did+ld+json` - JSON-LD format with full context (legacy, DID Core 1.0)
 * - `application/did+json` - Plain JSON format without JSON-LD processing (legacy, DID Core 1.0)
 * - `application/did+cbor` - CBOR format for compact representation
 * - `application/json` - Fallback JSON format
 *
 * **Example Usage:**
 * ```kotlin
 * val negotiationService = DefaultContentNegotiationService()
 *
 * // Negotiate content type from Accept header
 * val contentType = negotiationService.negotiateContentType("application/did+cbor")
 *
 * // Serialize document
 * val bytes = negotiationService.serializeDocument(document, contentType)
 * ```
 */
interface ContentNegotiationService {
    /**
     * Negotiate content type from Accept header.
     *
     * @param acceptHeader The Accept header value (e.g., "application/did+ld+json, application/json")
     * @param defaultType Default content type if negotiation fails
     * @return Negotiated content type
     */
    suspend fun negotiateContentType(
        acceptHeader: String?,
        defaultType: String = DidMediaTypes.DID,
    ): String

    /**
     * Serialize document to requested content type.
     *
     * @param document The DID document
     * @param contentType The content type to serialize to
     * @return Serialized document as byte array
     */
    suspend fun serializeDocument(
        document: DidDocument,
        contentType: String,
    ): ByteArray

    /**
     * Deserialize document from content type.
     *
     * @param data The serialized document
     * @param contentType The content type
     * @return Deserialized DID document
     */
    suspend fun deserializeDocument(
        data: ByteArray,
        contentType: String,
    ): DidDocument
}

/**
 * Default implementation of content negotiation.
 */
class DefaultContentNegotiationService : ContentNegotiationService {
    companion object {
        val SUPPORTED_TYPES: List<String> = DidMediaTypes.SUPPORTED_DOCUMENT_TYPES
        // CBOR (application/did+cbor) is not yet implemented. Add here once a CBOR
        // serialization library (e.g. Jackson CBOR) is wired in.
    }

    override suspend fun negotiateContentType(
        acceptHeader: String?,
        defaultType: String,
    ): String {
        if (acceptHeader == null) {
            return defaultType
        }

        val acceptedTypes = parseAcceptHeader(acceptHeader)

        // Find best match
        return acceptedTypes
            .firstOrNull { DidMediaTypes.isSupportedDocumentType(it) }
            ?.let { DidMediaTypes.normalize(it) }
            ?: defaultType
    }

    override suspend fun serializeDocument(
        document: DidDocument,
        contentType: String,
    ): ByteArray =
        when (DidMediaTypes.normalize(contentType)) {
            DidMediaTypes.DID,
            DidMediaTypes.DID_LD_JSON,
            DidMediaTypes.DID_JSON,
            DidMediaTypes.JSON,
            -> {
                Json {
                    prettyPrint = false
                    encodeDefaults = false
                }.encodeToString(
                    org.trustweave.did.model.DidDocument
                        .serializer(),
                    document,
                ).toByteArray(Charsets.UTF_8)
            }
            else -> {
                throw UnsupportedContentTypeException(contentType)
            }
        }

    override suspend fun deserializeDocument(
        data: ByteArray,
        contentType: String,
    ): DidDocument =
        when (DidMediaTypes.normalize(contentType)) {
            DidMediaTypes.DID,
            DidMediaTypes.DID_LD_JSON,
            DidMediaTypes.DID_JSON,
            DidMediaTypes.JSON,
            -> {
                Json.decodeFromString(
                    org.trustweave.did.model.DidDocument
                        .serializer(),
                    data.toString(Charsets.UTF_8),
                )
            }
            else -> {
                throw UnsupportedContentTypeException(contentType)
            }
        }

    /**
     * Parses [accept] into the acceptable media types, best first: higher `q` wins, `q=0` means
     * not acceptable (RFC 9110 section 12.4.2) and equal `q` keeps the server's own preference order.
     */
    private fun parseAcceptHeader(accept: String): List<String> =
        accept
            .split(',')
            .mapNotNull { part ->
                val pieces = part.split(';').map { it.trim() }
                val type = pieces[0]
                val q =
                    pieces
                        .drop(1)
                        .firstOrNull { it.startsWith("q=", ignoreCase = true) }
                        ?.substring(2)
                        ?.trim()
                        ?.toDoubleOrNull()
                        ?.coerceIn(0.0, 1.0)
                        ?: 1.0
                if (type.isEmpty() || q <= 0.0) null else type to q
            }.sortedWith(
                compareByDescending<Pair<String, Double>> { it.second }
                    .thenBy { (type, _) ->
                        val index = DidMediaTypes.SUPPORTED_DOCUMENT_TYPES.indexOf(DidMediaTypes.normalize(type))
                        if (index < 0) Int.MAX_VALUE else index
                    },
            ).map { it.first }
}

/**
 * Exception thrown when an unsupported content type is requested.
 */
class UnsupportedContentTypeException(
    contentType: String,
) : IllegalArgumentException(
        "Unsupported content type: $contentType. Supported types: ${DefaultContentNegotiationService.SUPPORTED_TYPES.joinToString()}",
    )
