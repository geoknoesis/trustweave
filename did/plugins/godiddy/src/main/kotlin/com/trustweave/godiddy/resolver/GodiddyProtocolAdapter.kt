package com.trustweave.godiddy.resolver

import com.trustweave.did.resolver.UniversalResolverProtocolAdapter
import java.net.URLEncoder
import java.net.http.HttpRequest
import kotlinx.serialization.json.*

/**
 * Protocol adapter for GoDiddy's Universal Resolver implementation.
 *
 * GoDiddy uses a slightly different endpoint pattern than the standard Universal Resolver:
 * - Resolution: `GET /1.0.0/universal-resolver/identifiers/{did}`
 * - Methods: `GET /1.0.0/methods`
 *
 * This adapter allows [DefaultUniversalResolver] to work with GoDiddy's API
 * without requiring the Ktor HTTP client used by [GodiddyResolver].
 *
 * **Example Usage**:
 * ```kotlin
 * val resolver = DefaultUniversalResolver(
 *     baseUrl = "https://api.godiddy.com",
 *     protocolAdapter = GodiddyProtocolAdapter(),
 *     apiKey = "your-api-key" // if required
 * )
 * ```
 */
class GodiddyProtocolAdapter : UniversalResolverProtocolAdapter {

    override fun buildResolveUrl(baseUrl: String, did: String): String {
        val encodedDid = URLEncoder.encode(did, "UTF-8")
        return "$baseUrl/1.0.0/universal-resolver/identifiers/$encodedDid"
    }

    override fun buildMethodsUrl(baseUrl: String): String? {
        return "$baseUrl/1.0.0/methods"
    }

    override fun configureAuth(requestBuilder: HttpRequest.Builder, apiKey: String?) {
        apiKey?.let {
            requestBuilder.header("Authorization", "Bearer $it")
        }
    }

    override fun extractDidDocument(jsonResponse: JsonObject): JsonObject? {
        // GoDiddy format is the same as standard: { "didDocument": {...}, ... }
        // If "didDocument" key exists, use it; otherwise assume the root is the document
        return jsonResponse["didDocument"]?.jsonObject ?: jsonResponse
    }

    override fun extractDocumentMetadata(jsonResponse: JsonObject): JsonObject? {
        return jsonResponse["didDocumentMetadata"]?.jsonObject
    }

    override fun extractResolutionMetadata(jsonResponse: JsonObject): JsonObject {
        return jsonResponse["didResolutionMetadata"]?.jsonObject ?: buildJsonObject { }
    }

    override val providerName: String = "godiddy"
}

