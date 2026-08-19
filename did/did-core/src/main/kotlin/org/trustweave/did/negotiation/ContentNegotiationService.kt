package org.trustweave.did.negotiation

import org.trustweave.did.model.DidDocument
import org.trustweave.did.representation.DidMediaTypes
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

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
        defaultType: String = DidMediaTypes.DID
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
        contentType: String
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
        contentType: String
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
        defaultType: String
    ): String {
        if (acceptHeader == null) {
            return defaultType
        }

        // Parse Accept header (simplified - full implementation would handle q-values)
        val acceptedTypes = parseAcceptHeader(acceptHeader)

        // Find best match
        return acceptedTypes.firstOrNull { DidMediaTypes.isSupportedDocumentType(it) }
            ?.let { DidMediaTypes.normalize(it) }
            ?: defaultType
    }
    
    override suspend fun serializeDocument(
        document: DidDocument,
        contentType: String
    ): ByteArray {
        return when (DidMediaTypes.normalize(contentType)) {
            DidMediaTypes.DID,
            DidMediaTypes.DID_LD_JSON,
            DidMediaTypes.DID_JSON,
            DidMediaTypes.JSON -> {
                Json {
                    prettyPrint = false
                    encodeDefaults = false
                }.encodeToString(
                    org.trustweave.did.model.DidDocument.serializer(),
                    document
                ).toByteArray(Charsets.UTF_8)
            }
            else -> {
                throw UnsupportedContentTypeException(contentType)
            }
        }
    }
    
    override suspend fun deserializeDocument(
        data: ByteArray,
        contentType: String
    ): DidDocument {
        return when (DidMediaTypes.normalize(contentType)) {
            DidMediaTypes.DID,
            DidMediaTypes.DID_LD_JSON,
            DidMediaTypes.DID_JSON,
            DidMediaTypes.JSON -> {
                Json.decodeFromString(
                    org.trustweave.did.model.DidDocument.serializer(),
                    data.toString(Charsets.UTF_8)
                )
            }
            else -> {
                throw UnsupportedContentTypeException(contentType)
            }
        }
    }
    
    private fun parseAcceptHeader(accept: String): List<String> {
        return accept.split(',')
            .map { it.trim().split(';')[0].trim() }
            .sortedByDescending { type ->
                val index = DidMediaTypes.SUPPORTED_DOCUMENT_TYPES.indexOf(DidMediaTypes.normalize(type))
                if (index < 0) -1 else DidMediaTypes.SUPPORTED_DOCUMENT_TYPES.size - index
            }
    }
}

/**
 * Exception thrown when an unsupported content type is requested.
 */
class UnsupportedContentTypeException(contentType: String) :
    IllegalArgumentException("Unsupported content type: $contentType. Supported types: ${DefaultContentNegotiationService.SUPPORTED_TYPES.joinToString()}")

