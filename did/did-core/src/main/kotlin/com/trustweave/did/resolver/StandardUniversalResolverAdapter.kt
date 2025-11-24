package com.trustweave.did.resolver

import java.net.URLEncoder
import java.net.http.HttpRequest
import kotlinx.serialization.json.*

/**
 * Protocol adapter for standard Universal Resolver implementations.
 * 
 * Uses the standard endpoint pattern as defined by the Universal Resolver reference implementation:
 * - Resolution: `GET /1.0/identifiers/{did}`
 * - Methods: `GET /1.0/methods`
 * 
 * This is the pattern used by:
 * - dev.uniresolver.io (public instance)
 * - Reference Universal Resolver implementation
 * - Most self-hosted Universal Resolver instances
 * 
 * **Example Usage**:
 * ```kotlin
 * val resolver = DefaultUniversalResolver(
 *     baseUrl = "https://dev.uniresolver.io",
 *     protocolAdapter = StandardUniversalResolverAdapter()
 * )
 * ```
 */
class StandardUniversalResolverAdapter : UniversalResolverProtocolAdapter {
    
    override fun buildResolveUrl(baseUrl: String, did: String): String {
        val encodedDid = URLEncoder.encode(did, "UTF-8")
        return "$baseUrl/1.0/identifiers/$encodedDid"
    }
    
    override fun buildMethodsUrl(baseUrl: String): String? {
        return "$baseUrl/1.0/methods"
    }
    
    override fun configureAuth(requestBuilder: HttpRequest.Builder, apiKey: String?) {
        apiKey?.let {
            requestBuilder.header("Authorization", "Bearer $it")
        }
    }
    
    override fun extractDidDocument(jsonResponse: JsonObject): JsonObject? {
        // Standard format: { "didDocument": {...}, "didDocumentMetadata": {...}, ... }
        // If "didDocument" key exists, use it; otherwise assume the root is the document
        return jsonResponse["didDocument"]?.jsonObject ?: jsonResponse
    }
    
    override fun extractDocumentMetadata(jsonResponse: JsonObject): JsonObject? {
        return jsonResponse["didDocumentMetadata"]?.jsonObject
    }
    
    override fun extractResolutionMetadata(jsonResponse: JsonObject): JsonObject {
        return jsonResponse["didResolutionMetadata"]?.jsonObject ?: buildJsonObject { }
    }
    
    override val providerName: String = "universal-resolver"
}

