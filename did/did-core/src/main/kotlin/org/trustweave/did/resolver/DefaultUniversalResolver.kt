package org.trustweave.did.resolver

import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import org.trustweave.did.model.DidService
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.parser.DidDocumentJsonParser
import org.trustweave.did.exception.DidException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.datetime.Instant

/**
 * Default implementation of [UniversalResolver] using Java's built-in HTTP client.
 *
 * This implementation works with any Universal Resolver instance through a pluggable
 * protocol adapter pattern. Different providers can use different endpoint patterns,
 * authentication mechanisms, and response formats.
 *
 * **Example Usage**:
 * ```kotlin
 * // Use public dev.uniresolver.io instance with standard adapter (default)
 * val resolver = DefaultUniversalResolver("https://dev.uniresolver.io")
 *
 * // Use self-hosted instance with standard adapter
 * val resolver = DefaultUniversalResolver(
 *     baseUrl = "https://resolver.example.com",
 *     protocolAdapter = StandardUniversalResolverAdapter()
 * )
 *
 * // Use GoDiddy with custom adapter
 * val resolver = DefaultUniversalResolver(
 *     baseUrl = "https://api.godiddy.com",
 *     protocolAdapter = GodiddyProtocolAdapter()
 * )
 *
 * val result = resolver.resolveDid("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
 * ```
 *
 * @param baseUrl Base URL of the Universal Resolver instance (e.g., "https://dev.uniresolver.io")
 * @param timeout Request timeout in seconds (default: 30). Ignored when [httpClient] is provided.
 * @param apiKey Optional API key for authentication (if required by the resolver)
 * @param protocolAdapter Protocol adapter for the specific Universal Resolver implementation
 *                        (defaults to [StandardUniversalResolverAdapter])
 * @param httpClient Optional pre-configured [HttpClient] to use for HTTP requests. When `null`
 *                   a default client with [timeout]-based connect timeout is created. Inject a
 *                   shared client in production to allow connection-pool reuse and easier testing.
 */
class DefaultUniversalResolver(
    override val baseUrl: String,
    private val timeout: Int = 30,
    private val apiKey: String? = null,
    private val protocolAdapter: UniversalResolverProtocolAdapter = StandardUniversalResolverAdapter(),
    private val retryConfig: RetryConfig = RetryConfig.default(),
    httpClient: HttpClient? = null
) : UniversalResolver {

    init {
        // Validate and cache baseUrl format once during initialization
        require(baseUrl.isNotBlank()) { "Base URL cannot be blank" }
        require(baseUrl.matches(Regex("^https?://[^/]+"))) {
            "Invalid base URL format: $baseUrl. Must be a valid HTTP/HTTPS URL."
        }
        // Validate URL can be parsed
        try {
            URI.create(baseUrl)
        } catch (e: IllegalArgumentException) {
            throw DidException.InvalidDidFormat(
                did = baseUrl,
                reason = "Invalid base URL format: ${e.message}"
            )
        }
    }

    private val httpClient: HttpClient = httpClient ?: HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(timeout.toLong()))
        .build()

    override suspend fun resolveDid(did: String): DidResolutionResult = withContext(Dispatchers.IO) {
        // Validate input
        require(did.isNotBlank()) { "DID cannot be blank" }
        require(did.startsWith("did:")) { "DID must start with 'did:'" }
        
        // Use retry logic for HTTP operations
        try {
            retryConfig.executeWithRetry {
                performResolution(did)
            }
        } catch (e: DidException) {
            throw e
        } catch (e: Exception) {
            throw DidException.DidResolutionFailed(
                did = Did(did),
                reason = e.message ?: "Unknown error during resolution",
                cause = e
            )
        }
    }

    private suspend fun performResolution(did: String): DidResolutionResult {
        // Use protocol adapter to build URL
        val url = protocolAdapter.buildResolveUrl(baseUrl, did)
        require(url.isNotBlank()) { "Resolve URL cannot be blank" }
        
        // Validate URL format
        val uri = try {
            URI.create(url)
        } catch (e: IllegalArgumentException) {
            throw DidException.InvalidDidFormat(
                did = did,
                reason = "Invalid resolver URL format: $url. Error: ${e.message}"
            )
        }

        val requestBuilder = HttpRequest.newBuilder()
            .uri(uri)
            .timeout(Duration.ofSeconds(timeout.toLong()))
            .header("Accept", "application/json")

        // Use protocol adapter to configure authentication
        protocolAdapter.configureAuth(requestBuilder, apiKey)

        val request = requestBuilder.build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        return when (response.statusCode()) {
            200 -> {
                // Parse JSON response
                val jsonResponse = parseJsonResponse(response.body())

                // Use protocol adapter to extract data
                val didDocumentJson = protocolAdapter.extractDidDocument(jsonResponse)
                val document = didDocumentJson?.let { parseDidDocumentFromJson(it) }
                val documentMetadata = parseDidDocumentMetadata(
                    protocolAdapter.extractDocumentMetadata(jsonResponse)
                )
                val resolutionMetadataMap = parseResolutionMetadata(
                    protocolAdapter.extractResolutionMetadata(jsonResponse)
                )
                val resolutionMetadata = DidResolutionMetadata.fromMap(
                    resolutionMetadataMap.plus("provider" to protocolAdapter.providerName)
                )

                if (document != null) {
                    DidResolutionResult.Success(
                        document = document,
                        documentMetadata = documentMetadata,
                        resolutionMetadata = resolutionMetadata
                    )
                } else {
                    DidResolutionResult.Failure.NotFound(
                        did = Did(did),
                        reason = "DID document not found in response",
                        resolutionMetadata = resolutionMetadata
                    )
                }
            }
            404 -> {
                DidResolutionResult.Failure.NotFound(
                    did = Did(did),
                    reason = "DID not found",
                    resolutionMetadata = DidResolutionMetadata(
                        error = "notFound",
                        errorMessage = "DID not found",
                        properties = mapOf("provider" to protocolAdapter.providerName)
                    )
                )
            }
            else -> {
                // Retryable errors (5xx) will be retried by retryConfig
                // Non-retryable errors (4xx except 404) return immediately
                DidResolutionResult.Failure.ResolutionError(
                    did = Did(did),
                    reason = "HTTP ${response.statusCode()}",
                    cause = null,
                    resolutionMetadata = DidResolutionMetadata(
                        error = "resolutionError",
                        errorMessage = "HTTP ${response.statusCode()}",
                        properties = mapOf(
                            "statusCode" to response.statusCode().toString(),
                            "provider" to protocolAdapter.providerName
                        )
                    )
                )
            }
        }
    }

    override suspend fun getSupportedMethods(): List<String>? {
        // Use protocol adapter to build methods URL
        val methodsUrl = protocolAdapter.buildMethodsUrl(baseUrl) ?: return null

        return try {
            // Validate URL format
            val uri = try {
                URI.create(methodsUrl)
            } catch (e: IllegalArgumentException) {
                // Invalid URL format - return null instead of throwing
                return null
            }
            
            val requestBuilder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(timeout.toLong()))
                .header("Accept", "application/json")

            // Use protocol adapter to configure authentication
            protocolAdapter.configureAuth(requestBuilder, apiKey)

            val request = requestBuilder.build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 200) {
                parseMethodsList(response.body())
            } else {
                null
            }
        } catch (e: Exception) {
            // Endpoint not available or not supported
            null
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Parses the Universal Resolver JSON response.
     */
    private fun parseJsonResponse(jsonString: String): JsonObject {
        return json.parseToJsonElement(jsonString).jsonObject
    }

    /**
     * Parses a DID document from a JsonObject using the shared conforming consumer.
     *
     * @param json JSON object containing the DID document
     * @return Parsed DidDocument
     * @throws DidException.InvalidDidFormat if the document is invalid
     */
    private fun parseDidDocumentFromJson(json: JsonObject): DidDocument {
        return DidDocumentJsonParser.parse(json)
    }

    /**
     * Parses DID document metadata from JSON.
     */
    private fun parseDidDocumentMetadata(metadataJson: JsonObject?): DidDocumentMetadata {
        if (metadataJson == null) return DidDocumentMetadata()

        val created = metadataJson["created"]?.jsonPrimitive?.content?.let {
            try { Instant.parse(it) } catch (e: Exception) { null }
        }
        val updated = metadataJson["updated"]?.jsonPrimitive?.content?.let {
            try { Instant.parse(it) } catch (e: Exception) { null }
        }
        val versionId = metadataJson["versionId"]?.jsonPrimitive?.content
        val nextUpdate = metadataJson["nextUpdate"]?.jsonPrimitive?.content?.let {
            try { Instant.parse(it) } catch (e: Exception) { null }
        }
        val canonicalId = metadataJson["canonicalId"]?.jsonPrimitive?.content?.let { Did(it) }
        val equivalentId = metadataJson["equivalentId"]?.jsonArray?.mapNotNull { it.jsonPrimitive?.content?.let { Did(it) } } ?: emptyList()

        return DidDocumentMetadata(
            created = created,
            updated = updated,
            versionId = versionId,
            nextUpdate = nextUpdate,
            canonicalId = canonicalId,
            equivalentId = equivalentId
        )
    }

    /**
     * Parses resolution metadata from JSON.
     */
    private fun parseResolutionMetadata(metadataJson: JsonObject): Map<String, Any?> {
        return metadataJson.entries.associate { it.key to convertJsonElement(it.value) }
    }

    /**
     * Converts JsonElement to Any for metadata maps.
     */
    private fun convertJsonElement(element: JsonElement): Any? {
        return when (element) {
            is JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    element.booleanOrNull != null -> element.boolean
                    element.longOrNull != null -> element.long
                    element.doubleOrNull != null -> element.double
                    else -> element.content
                }
            }
            is JsonArray -> element.map { convertJsonElement(it) }
            is JsonObject -> element.entries.associate { it.key to convertJsonElement(it.value) }
            is JsonNull -> null
        }
    }

    /**
     * Parses a list of supported methods from JSON response.
     */
    private fun parseMethodsList(jsonString: String): List<String>? {
        return try {
            val jsonArray = json.parseToJsonElement(jsonString).jsonArray
            jsonArray.mapNotNull { it.jsonPrimitive?.content }
        } catch (e: Exception) {
            null
        }
    }
}

