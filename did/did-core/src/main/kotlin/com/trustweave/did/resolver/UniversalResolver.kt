package com.trustweave.did.resolver

/**
 * Interface for Universal Resolver implementations.
 *
 * Universal Resolver is a standard HTTP API specification (W3C DID Resolution)
 * that provides a unified interface for resolving DIDs across multiple methods.
 *
 * Different Universal Resolver providers may use different endpoint patterns:
 * - Standard: `GET /1.0/identifiers/{did}` (used by dev.uniresolver.io)
 * - GoDiddy: `GET /1.0.0/universal-resolver/identifiers/{did}`
 * - Custom: Provider-specific patterns
 *
 * The [DefaultUniversalResolver] implementation uses a pluggable protocol adapter
 * pattern ([UniversalResolverProtocolAdapter]) to support different providers
 * without code changes.
 *
 * Multiple providers can implement this interface:
 * - GoDiddy (commercial hosted service) - see `GodiddyResolver` in `did:plugins:godiddy`
 * - dev.uniresolver.io (public instance) - use `DefaultUniversalResolver` with `StandardUniversalResolverAdapter`
 * - Self-hosted Universal Resolver instances - use `DefaultUniversalResolver` with appropriate adapter
 *
 * **Example Usage**:
 * ```kotlin
 * // Using GoDiddy (Ktor-based)
 * val resolver: UniversalResolver = GodiddyResolver(client)
 *
 * // Using public dev.uniresolver.io (Java HttpClient with standard adapter)
 * val resolver: UniversalResolver = DefaultUniversalResolver("https://dev.uniresolver.io")
 *
 * // Using GoDiddy with Java HttpClient (alternative to Ktor)
 * val resolver: UniversalResolver = DefaultUniversalResolver(
 *     baseUrl = "https://api.godiddy.com",
 *     protocolAdapter = GodiddyProtocolAdapter(),
 *     apiKey = "your-key"
 * )
 *
 * // Using self-hosted instance with standard adapter
 * val resolver: UniversalResolver = DefaultUniversalResolver(
 *     baseUrl = "https://resolver.example.com",
 *     protocolAdapter = StandardUniversalResolverAdapter()
 * )
 *
 * val result = resolver.resolveDid("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
 * ```
 */
interface UniversalResolver {
    /**
     * Resolves a DID using Universal Resolver standard API.
     *
     * Follows the W3C DID Resolution specification and returns a standard
     * [DidResolutionResult] containing the document, document metadata, and resolution metadata.
     *
     * @param did The DID string to resolve
     * @return DidResolutionResult following W3C DID Resolution spec
     * @throws com.trustweave.core.exception.TrustWeaveException if resolution fails
     */
    suspend fun resolveDid(did: String): DidResolutionResult

    /**
     * Gets the base URL of the Universal Resolver instance.
     *
     * This is useful for debugging, logging, or displaying which resolver
     * instance is being used.
     */
    val baseUrl: String

    /**
     * Gets the list of supported DID methods (if available).
     *
     * Some Universal Resolver implementations expose a list of supported methods
     * via an API endpoint. This method queries that endpoint if available.
     *
     * @return List of supported DID method names, or null if not available/not supported
     */
    suspend fun getSupportedMethods(): List<String>?
}

