package com.trustweave.did.resolver

import java.net.http.HttpRequest
import kotlinx.serialization.json.JsonObject

/**
 * Protocol adapter for Universal Resolver implementations.
 *
 * Different Universal Resolver providers may use different:
 * - Endpoint paths (e.g., `/1.0/identifiers` vs `/1.0.0/universal-resolver/identifiers`)
 * - Authentication mechanisms
 * - Response formats
 * - Error handling
 *
 * This adapter abstracts these differences, allowing [DefaultUniversalResolver]
 * to work with any Universal Resolver-compatible service.
 *
 * **Example Usage**:
 * ```kotlin
 * // Standard adapter for dev.uniresolver.io
 * val adapter = StandardUniversalResolverAdapter()
 * val resolver = DefaultUniversalResolver("https://dev.uniresolver.io", protocolAdapter = adapter)
 *
 * // Custom adapter for specific provider
 * val customAdapter = object : UniversalResolverProtocolAdapter {
 *     override fun buildResolveUrl(baseUrl: String, did: String) = "$baseUrl/custom/path/$did"
 *     // ... implement other methods
 * }
 * ```
 */
interface UniversalResolverProtocolAdapter {
    /**
     * Builds the URL for resolving a DID.
     *
     * @param baseUrl Base URL of the resolver
     * @param did The DID to resolve (should be URL-encoded if needed)
     * @return Complete URL for the resolution request
     */
    fun buildResolveUrl(baseUrl: String, did: String): String

    /**
     * Builds the URL for querying supported methods.
     *
     * @param baseUrl Base URL of the resolver
     * @return URL for the methods endpoint, or null if not supported
     */
    fun buildMethodsUrl(baseUrl: String): String?

    /**
     * Configures HTTP request headers for authentication.
     *
     * @param requestBuilder HTTP request builder to configure
     * @param apiKey Optional API key for authentication
     */
    fun configureAuth(requestBuilder: HttpRequest.Builder, apiKey: String?)

    /**
     * Extracts the DID document from the response.
     *
     * Different providers may wrap the document differently:
     * - Standard: `{ "didDocument": {...} }`
     * - Direct: `{ ... }` (document is the root)
     *
     * @param jsonResponse Parsed JSON response
     * @return JsonObject containing the DID document, or null if not found
     */
    fun extractDidDocument(jsonResponse: JsonObject): JsonObject?

    /**
     * Extracts document metadata from the response.
     *
     * @param jsonResponse Parsed JSON response
     * @return JsonObject containing document metadata, or null if not found
     */
    fun extractDocumentMetadata(jsonResponse: JsonObject): JsonObject?

    /**
     * Extracts resolution metadata from the response.
     *
     * @param jsonResponse Parsed JSON response
     * @return JsonObject containing resolution metadata, or empty object if not found
     */
    fun extractResolutionMetadata(jsonResponse: JsonObject): JsonObject

    /**
     * Gets the provider name for metadata.
     * This is used in resolution metadata to identify which provider was used.
     */
    val providerName: String
}

