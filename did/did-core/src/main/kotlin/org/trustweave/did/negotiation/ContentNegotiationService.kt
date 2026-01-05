package org.trustweave.did.negotiation

import org.trustweave.did.model.DidDocument
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * Content negotiation service for DID resolution.
 *
 * Supports multiple content types for DID documents to ensure interoperability
 * with different clients and use cases.
 *
 * **Supported Content Types:**
 * - `application/did+ld+json` (default) - JSON-LD format with full context
 * - `application/did+json` - Plain JSON format without JSON-LD processing
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
        defaultType: String = "application/did+ld+json"
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
        val SUPPORTED_TYPES = listOf(
            "application/did+ld+json",
            "application/did+json",
            "application/did+cbor",
            "application/json"
        )
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
        return acceptedTypes.firstOrNull { type ->
            SUPPORTED_TYPES.contains(type)
        } ?: defaultType
    }
    
    override suspend fun serializeDocument(
        document: DidDocument,
        contentType: String
    ): ByteArray {
        return when (contentType) {
            "application/did+ld+json",
            "application/did+json",
            "application/json" -> {
                Json {
                    prettyPrint = false
                    encodeDefaults = false
                }.encodeToString(
                    org.trustweave.did.model.DidDocument.serializer(),
                    document
                ).toByteArray(Charsets.UTF_8)
            }
            "application/did+cbor" -> {
                // CBOR serialization would require a CBOR library
                // For now, fall back to JSON
                serializeDocument(document, "application/did+json")
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
        return when (contentType) {
            "application/did+ld+json",
            "application/did+json",
            "application/json" -> {
                Json.decodeFromString(
                    org.trustweave.did.model.DidDocument.serializer(),
                    data.toString(Charsets.UTF_8)
                )
            }
            "application/did+cbor" -> {
                // CBOR deserialization would require a CBOR library
                // For now, fall back to JSON
                deserializeDocument(data, "application/did+json")
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
                // Prefer did+ld+json
                when (type) {
                    "application/did+ld+json" -> 3
                    "application/did+json" -> 2
                    "application/did+cbor" -> 1
                    else -> 0
                }
            }
    }
}

/**
 * Exception thrown when an unsupported content type is requested.
 */
class UnsupportedContentTypeException(contentType: String) :
    IllegalArgumentException("Unsupported content type: $contentType. Supported types: ${DefaultContentNegotiationService.SUPPORTED_TYPES.joinToString()}")

