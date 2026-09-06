package org.trustweave.did.resolver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import org.trustweave.did.exception.DidException
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import org.trustweave.did.parser.DidDocumentJsonParser
import org.trustweave.did.representation.DidMediaTypes
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

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
    private val retryConfig: RetryConfig =
        RetryConfig.default().copy(
            nonRetryableExceptions =
                listOf(
                    org.trustweave.did.exception.DidException.InvalidDidFormat::class,
                    // Any DidException subtype is a domain-level error, not a transient network condition,
                    // and must never be retried.
                    org.trustweave.did.exception.DidException::class,
                ),
        ),
    httpClient: HttpClient? = null,
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
                reason = "Invalid base URL format: ${e.message}",
            )
        }
    }

    private val httpClient: HttpClient =
        httpClient ?: HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(timeout.toLong()))
            .build()

    private val logger =
        org.trustweave.did.util.DidLogging
            .getLogger(DefaultUniversalResolver::class.java)

    private val maxResponseBytes = 1 * 1024 * 1024 // 1 MB

    private fun sanitizeDid(did: String): String = did.replace(Regex("[\\r\\n\\t\\x00-\\x1F\\x7F]"), "?").take(200)

    /**
     * Normalises a URI host string for case-insensitive, JVM-version-independent comparison.
     *
     * Strips IPv6 brackets (added by some JDK versions but not others) and lowercases the
     * result so that `[::1]` and `::1` compare equal, and host names are matched case-insensitively
     * regardless of JDK version.
     *
     * Returns `null` when [h] is `null` (opaque URI — SSRF guard should reject the request).
     */
    private fun normalizeHost(h: String?): String? = h?.lowercase()?.removePrefix("[")?.removeSuffix("]")

    override suspend fun resolveDid(did: String): DidResolutionResult =
        withContext(Dispatchers.IO) {
            // Validate input before entering the try/retry block so that format errors
            // are not caught by the generic catch(e: Exception) clause and misrouted.
            if (did.isBlank()) {
                throw DidException.InvalidDidFormat(did = sanitizeDid(did), reason = "DID cannot be blank")
            }
            if (!did.startsWith("did:")) {
                throw DidException.InvalidDidFormat(did = sanitizeDid(did), reason = "DID must start with 'did:'")
            }

            // Use retry logic for HTTP operations
            try {
                retryConfig.executeWithRetry {
                    performResolution(did)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                val safeDid =
                    try {
                        Did(sanitizeDid(did))
                    } catch (_: Exception) {
                        Did("did:unknown:interrupted")
                    }
                throw DidException.DidResolutionFailed(
                    did = safeDid,
                    reason = "HTTP request interrupted",
                    cause = e,
                )
            } catch (e: DidException) {
                throw e
            } catch (e: Exception) {
                val safeDid =
                    try {
                        Did(sanitizeDid(did))
                    } catch (_: Exception) {
                        Did("did:unknown:resolution-error")
                    }
                throw DidException.DidResolutionFailed(
                    did = safeDid,
                    reason = e.message ?: "Unknown error during resolution",
                    cause = e,
                )
            }
        }

    private suspend fun performResolution(did: String): DidResolutionResult {
        // The protocol adapter is responsible for encoding the DID value in the URL.
        // Pass the raw DID string directly so the adapter can apply the correct encoding
        // strategy (e.g. URI path-segment encoding) without double-encoding.
        val url = protocolAdapter.buildResolveUrl(baseUrl, did)
        require(url.isNotBlank()) { "Resolve URL cannot be blank" }

        // Validate URL format and guard against SSRF via DID path traversal.
        // Use URI.create() (single-arg, throws IllegalArgumentException) for consistency
        // with the rest of this class. URISyntaxException from the multi-arg constructor
        // would silently bypass the SSRF guard and fall through to a ResolutionError.
        val uri =
            try {
                val resolvedUri = URI.create(url)
                val baseUri = URI.create(baseUrl)
                val basePort = baseUri.port.takeIf { it != -1 } ?: if (baseUri.scheme == "https") 443 else 80
                val resolvedPort = resolvedUri.port.takeIf { it != -1 } ?: if (resolvedUri.scheme == "https") 443 else 80
                // RFC 3986 §3.2.2: host comparison must be case-insensitive and bracket-stripped
                // so IPv6 behaviour is consistent across JDK versions.
                // resolvedUri.host is null for opaque URIs (urn:, data:, etc.) — treat as a
                // guard failure rather than letting a null-receiver NPE bypass the check.
                if (normalizeHost(resolvedUri.host) == null ||
                    normalizeHost(resolvedUri.host) != normalizeHost(baseUri.host) ||
                    resolvedUri.scheme != baseUri.scheme ||
                    resolvedPort != basePort
                ) {
                    throw DidException.InvalidDidFormat(
                        did = sanitizeDid(did),
                        reason = "SSRF guard: resolved URL host is null or does not match base URL",
                    )
                }
                // A bare base URL (no path component) passes host/scheme/port checks but indicates
                // the adapter failed to build a proper resolve path, which could facilitate SSRF.
                if (resolvedUri.path.isNullOrBlank()) {
                    throw DidException.InvalidDidFormat(
                        did = sanitizeDid(did),
                        reason = "SSRF guard: adapter-built URL has no path component: $url",
                    )
                }
                resolvedUri
            } catch (e: IllegalArgumentException) {
                // URI.create() throws IllegalArgumentException for a malformed URL.
                // A malformed adapter-built URL is treated as an SSRF/format failure.
                throw DidException.InvalidDidFormat(
                    did = sanitizeDid(did),
                    reason = "Invalid resolver URL format: $url. Error: ${e.message}",
                )
            }

        val requestBuilder =
            HttpRequest
                .newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(timeout.toLong()))
                .header("Accept", DidMediaTypes.DID_RESOLUTION)

        // Use protocol adapter to configure authentication
        protocolAdapter.configureAuth(requestBuilder, apiKey)

        val request = requestBuilder.build()
        val response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).await()

        return response.body().use { bodyStream ->
            when (response.statusCode()) {
                200 -> {
                    // Parse JSON response
                    val responseBody =
                        run {
                            val bytes = bodyStream.readNBytes(maxResponseBytes + 1)
                            if (bytes.size > maxResponseBytes) {
                                return@use DidResolutionResult.Failure.ResolutionError(
                                    did = Did(sanitizeDid(did)),
                                    reason = "Response exceeds maximum allowed size",
                                )
                            }
                            String(bytes, Charsets.UTF_8)
                        }
                    val jsonResponse =
                        try {
                            parseJsonResponse(responseBody)
                        } catch (e: kotlinx.serialization.SerializationException) {
                            return@use DidResolutionResult.Failure.ResolutionError(
                                did = Did(sanitizeDid(did)),
                                reason = "Malformed JSON in resolver response: ${e.message}",
                                cause = e,
                                resolutionMetadata =
                                    DidResolutionMetadata(
                                        error =
                                            DidResolutionError.internalError(
                                                "Malformed JSON in resolver response: ${e.message}",
                                            ),
                                    ),
                            )
                        }

                    // A null response means the root element was not a JSON object —
                    // not a valid DID resolution response.
                    if (jsonResponse == null) {
                        return@use DidResolutionResult.Failure.ResolutionError(
                            did = Did(sanitizeDid(did)),
                            reason = "Resolver response is not a JSON object",
                            cause = null,
                            resolutionMetadata =
                                DidResolutionMetadata(
                                    error = DidResolutionError.internalError("Resolver response is not a JSON object"),
                                ),
                        )
                    }

                    // Use protocol adapter to extract data
                    val didDocumentJson = protocolAdapter.extractDidDocument(jsonResponse)
                    // §4.4: a non-null didDocumentJson means the upstream *did* return a document
                    // — DidDocumentJsonParser.parse throws DidException.InvalidDidFormat (missing
                    // `id`) or IllegalArgumentException (malformed `id` value) for a malformed one.
                    // Left unguarded, that throw escapes resolveDid() and gets misclassified as
                    // INVALID_DID (400) upstream — blaming the caller's well-formed requested DID
                    // for an upstream document defect. Catch it here instead, mirroring
                    // GodiddyResolver's identical guard, and record why so the failure branch below
                    // can report INVALID_DID_DOCUMENT rather than folding this into "not found".
                    var malformedDocumentReason: String? = null
                    val document =
                        didDocumentJson?.let {
                            try {
                                parseDidDocumentFromJson(it)
                            } catch (e: DidException) {
                                malformedDocumentReason = e.message ?: "Malformed DID document in resolver response"
                                null
                            } catch (e: IllegalArgumentException) {
                                malformedDocumentReason = e.message ?: "Malformed DID document in resolver response"
                                null
                            }
                        }
                    val documentMetadata =
                        parseDidDocumentMetadata(
                            protocolAdapter.extractDocumentMetadata(jsonResponse),
                        )
                    val resolutionMetadataMap =
                        parseResolutionMetadata(
                            protocolAdapter.extractResolutionMetadata(jsonResponse),
                        )
                    val resolutionMetadata =
                        DidResolutionMetadata.fromMap(
                            resolutionMetadataMap.plus("provider" to protocolAdapter.providerName),
                        )

                    when {
                        // §4.4/§12.1: deactivation is checked before document-presence. An
                        // upstream HTTP 200 can carry both a non-null `didDocument` and
                        // `didDocumentMetadata.deactivated: true` — this is exactly the shape a
                        // DID Resolution v0.3-era resolver emits (a 0.6 client behind a 0.5
                        // server reproduces it directly), and the CR does not forbid it either.
                        // Checking deactivation first means that shape still yields Deactivated,
                        // never a Success carrying a revoked document — the same guarantee the
                        // 410 branch below already provides, now also on the 200 path.
                        documentMetadata.deactivated -> {
                            DidResolutionResult.Deactivated(
                                did = Did(did),
                                documentMetadata = documentMetadata.copy(deactivated = true),
                                resolutionMetadata = resolutionMetadata,
                            )
                        }
                        document != null -> {
                            DidResolutionResult.Success(
                                document = document,
                                documentMetadata = documentMetadata,
                                resolutionMetadata = resolutionMetadata,
                            )
                        }
                        malformedDocumentReason != null -> {
                            // The upstream *did* return a document under `didDocument` — it just
                            // failed to parse. This is an upstream data defect, not "this DID does
                            // not exist" (NotFound would be dishonest) and not "the requested DID is
                            // invalid" (the caller's DID was fine). INVALID_DID_DOCUMENT names what
                            // actually happened, mirroring GodiddyResolver's identical
                            // document-conversion-failure guard.
                            val reason = malformedDocumentReason ?: "Malformed DID document in resolver response"
                            DidResolutionResult.Failure.ResolutionError(
                                did = Did(did),
                                reason = reason,
                                resolutionMetadata =
                                    resolutionMetadata.copy(
                                        error = DidResolutionError.invalidDidDocument(reason),
                                    ),
                            )
                        }
                        else -> {
                            // Prefer the human-readable detail/title over the raw §11 type URL —
                            // `error?.type` is a machine identifier (e.g.
                            // "https://www.w3.org/ns/did#NOT_FOUND"), not failure text. Fall back
                            // to a bare errorMessage straight from the raw map: this branch is
                            // already committed to a failure (document == null), so — unlike
                            // DidResolutionMetadata.fromMap, which must stay silent on a bare
                            // errorMessage to avoid mislabeling a successful resolution — it is
                            // safe here to treat an upstream errorMessage with no structured error
                            // as the failure reason.
                            val upstreamReason =
                                resolutionMetadata.error?.detail
                                    ?: (resolutionMetadataMap["errorMessage"] as? String)
                                    ?: resolutionMetadata.error?.title
                                    ?: "DID document not found in response"
                            // §4 / Failure's invariant: every Failure MUST carry a non-null
                            // error. `resolutionMetadata` here is parsed straight from an
                            // upstream body that may carry no structured error member at all —
                            // synthesize one from the reason we just computed rather than pass
                            // resolutionMetadata through unchanged.
                            DidResolutionResult.Failure.NotFound(
                                did = Did(did),
                                reason = upstreamReason,
                                resolutionMetadata =
                                    resolutionMetadata.copy(
                                        error =
                                            resolutionMetadata.error
                                                ?: DidResolutionError.notFound(upstreamReason),
                                    ),
                            )
                        }
                    }
                }
                404 -> {
                    // body is ignored; use{} closes the stream
                    DidResolutionResult.Failure.NotFound(
                        did = Did(did),
                        reason = "DID not found",
                        resolutionMetadata =
                            DidResolutionMetadata(
                                error = DidResolutionError.notFound("DID not found"),
                                properties = mapOf("provider" to protocolAdapter.providerName),
                            ),
                    )
                }
                410 -> {
                    // §4.4/§12.1: 410 means the DID exists but has been deactivated. This is not
                    // an error — no error object is attached to the resolution metadata. The body
                    // may carry the upstream didDocumentMetadata; parse it best-effort but always
                    // force deactivated = true regardless of what (if anything) parsed. Same
                    // read-one-extra-byte-and-compare oversized-body guard shape as the 200
                    // branch, but an oversized body degrades to the default here instead of
                    // failing resolution — the 410 status itself already establishes
                    // deactivation, so a body we can't/won't fully buffer must not downgrade
                    // that to an error.
                    val documentMetadata =
                        try {
                            val bytes = bodyStream.readNBytes(maxResponseBytes + 1)
                            if (bytes.size > maxResponseBytes) {
                                null
                            } else {
                                val body = String(bytes, Charsets.UTF_8)
                                parseJsonResponse(body)
                                    ?.let { protocolAdapter.extractDocumentMetadata(it) }
                                    ?.let { parseDidDocumentMetadata(it) }
                            }
                        } catch (_: kotlinx.serialization.SerializationException) {
                            null
                        } catch (_: IllegalArgumentException) {
                            // Belt-and-braces backstop: parseJsonResponse/extractDocumentMetadata/
                            // parseDidDocumentMetadata are all now guarded against wrong-shaped and
                            // JsonNull-valued fields (see parseDidDocumentMetadata's kdoc), so this
                            // should not fire today. It stays because this exact call chain has
                            // already had two unguarded-cast regressions found one level apart across
                            // two review rounds — the one property that must hold unconditionally is
                            // "410 always yields Deactivated", and that guarantee should not depend on
                            // every downstream helper staying perfectly guarded as this code evolves.
                            null
                        } ?: DidDocumentMetadata(deactivated = true)

                    DidResolutionResult.Deactivated(
                        did = Did(did),
                        documentMetadata = documentMetadata.copy(deactivated = true),
                        resolutionMetadata =
                            DidResolutionMetadata(
                                properties = mapOf("provider" to protocolAdapter.providerName),
                            ),
                    )
                }
                else -> {
                    val statusCode = response.statusCode()
                    // Throw for retryable status codes so executeWithRetry can catch and retry
                    // use{} ensures the stream is closed before the exception propagates
                    if (statusCode in retryConfig.retryableStatusCodes) {
                        throw IOException("Retryable HTTP error: $statusCode")
                    }
                    // Non-retryable errors map through the §12.1 HTTP status table to the
                    // matching error type; unmapped statuses fall back to INTERNAL_ERROR.
                    val errorType =
                        when (statusCode) {
                            400 -> DidErrorType.INVALID_DID
                            406 -> DidErrorType.REPRESENTATION_NOT_SUPPORTED
                            501 -> DidErrorType.METHOD_NOT_SUPPORTED
                            else -> DidErrorType.INTERNAL_ERROR
                        }
                    val detail = "Upstream resolver returned HTTP $statusCode"
                    if (errorType == DidErrorType.METHOD_NOT_SUPPORTED) {
                        DidResolutionResult.Failure.MethodNotRegistered(
                            method = Did(did).method,
                            resolutionMetadata =
                                DidResolutionMetadata(
                                    error = DidResolutionError.methodNotSupported(detail),
                                    properties =
                                        mapOf(
                                            "statusCode" to statusCode.toString(),
                                            "provider" to protocolAdapter.providerName,
                                        ),
                                ),
                        )
                    } else {
                        DidResolutionResult.Failure.ResolutionError(
                            did = Did(did),
                            reason = detail,
                            cause = null,
                            resolutionMetadata =
                                DidResolutionMetadata(
                                    error = DidResolutionError.of(errorType, detail),
                                    properties =
                                        mapOf(
                                            "statusCode" to statusCode.toString(),
                                            "provider" to protocolAdapter.providerName,
                                        ),
                                ),
                        )
                    }
                }
            }
        }
    }

    override suspend fun getSupportedMethods(): List<String>? =
        withContext(Dispatchers.IO) {
            // Use protocol adapter to build methods URL
            val methodsUrl = protocolAdapter.buildMethodsUrl(baseUrl) ?: return@withContext null

            try {
                // Parse once with URI.create (consistent with the rest of this class).
                // Reuse the same parsed URI for the SSRF host-check and the HTTP request.
                val methodsUri =
                    try {
                        URI.create(methodsUrl)
                    } catch (e: IllegalArgumentException) {
                        return@withContext emptyList()
                    }
                val baseUri =
                    try {
                        URI.create(baseUrl)
                    } catch (e: IllegalArgumentException) {
                        return@withContext emptyList()
                    }
                val basePortM = baseUri.port.takeIf { it != -1 } ?: if (baseUri.scheme == "https") 443 else 80
                val resolvedPortM = methodsUri.port.takeIf { it != -1 } ?: if (methodsUri.scheme == "https") 443 else 80
                // RFC 3986 §3.2.2: host comparison must be case-insensitive and bracket-stripped
                // so IPv6 behaviour is consistent across JDK versions.
                // methodsUri.host is null for opaque URIs (urn:, data:, etc.) — treat as a
                // guard failure rather than letting a null-receiver NPE bypass the check.
                if (normalizeHost(methodsUri.host) == null ||
                    normalizeHost(methodsUri.host) != normalizeHost(baseUri.host) ||
                    methodsUri.scheme != baseUri.scheme ||
                    resolvedPortM != basePortM
                ) {
                    return@withContext emptyList()
                }
                // A bare base URL (no path component) passes host/scheme/port checks but indicates
                // the adapter failed to build a proper methods path, which could facilitate SSRF.
                if (methodsUri.path.isNullOrBlank()) {
                    return@withContext emptyList()
                }

                val requestBuilder =
                    HttpRequest
                        .newBuilder()
                        .uri(methodsUri)
                        .timeout(Duration.ofSeconds(timeout.toLong()))
                        .header("Accept", "application/json")

                // Use protocol adapter to configure authentication
                protocolAdapter.configureAuth(requestBuilder, apiKey)

                val request = requestBuilder.build()
                val response = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).await()

                response.body().use { bodyStream ->
                    if (response.statusCode() == 200) {
                        val bytes = bodyStream.readNBytes(maxResponseBytes + 1)
                        if (bytes.size > maxResponseBytes) {
                            return@withContext emptyList()
                        }
                        val responseBody = String(bytes, Charsets.UTF_8)
                        parseMethodsList(responseBody)
                    } else {
                        // body is ignored; use{} closes the stream
                        null
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                logger.warn("getSupportedMethods interrupted for $baseUrl")
                return@withContext emptyList()
            } catch (e: java.io.IOException) {
                // Endpoint not available or network failure — treat as unsupported
                logger.warn("getSupportedMethods: I/O error contacting methods endpoint: ${e.message}")
                null
            } catch (e: kotlinx.serialization.SerializationException) {
                // Malformed response from endpoint — treat as unsupported
                logger.warn("getSupportedMethods: serialization error parsing methods response: ${e.message}")
                null
            }
            // All other exceptions (SecurityException, programming errors, etc.) propagate
        }

    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    /**
     * Parses the Universal Resolver JSON response.
     *
     * Returns `null` when the root JSON element is not an object (e.g. an array or primitive),
     * since a non-object root is not a valid DID resolution response. Using a safe-return here
     * avoids the [IllegalStateException] that `.jsonObject` would throw for non-object roots;
     * only [kotlinx.serialization.SerializationException] is propagated to the caller for
     * malformed JSON.
     */
    private fun parseJsonResponse(jsonString: String): JsonObject? =
        when (val element = json.parseToJsonElement(jsonString)) {
            is JsonObject -> element
            else -> null // non-object root is not a valid DID resolution response
        }

    /**
     * Parses a DID document from a JsonObject using the shared conforming consumer.
     *
     * @param json JSON object containing the DID document
     * @return Parsed DidDocument
     * @throws DidException.InvalidDidFormat if the document is invalid
     */
    private fun parseDidDocumentFromJson(json: JsonObject): DidDocument = DidDocumentJsonParser.parse(json)

    /**
     * Parses DID document metadata from JSON.
     *
     * Every field extraction is guarded against both a wrong-shaped value (e.g. an object where
     * a primitive is expected) and an explicit JSON `null` sub-field. `as? JsonPrimitive`/
     * `as? JsonArray` (rather than the `.jsonPrimitive`/`.jsonArray` extensions) degrade to
     * Kotlin `null` instead of throwing for a wrong-shaped value; `.contentOrNull` (rather than
     * `.content`) degrades to Kotlin `null` instead of the literal string `"null"` for a
     * `JsonNull` value — `JsonNull` is itself a `JsonPrimitive` subtype, so `.jsonPrimitive`
     * alone does not catch it the way `.jsonObject`/`.jsonArray` reject it. `Did(...)` is
     * wrapped in try/catch, mirroring the existing `Instant.parse` pattern below, since a
     * `null`-turned-`"null"` (or any other non-DID string) would otherwise throw uncaught.
     */
    private fun parseDidDocumentMetadata(metadataJson: JsonObject?): DidDocumentMetadata {
        if (metadataJson == null) return DidDocumentMetadata()

        val created =
            (metadataJson["created"] as? JsonPrimitive)?.contentOrNull?.let {
                try {
                    Instant.parse(it)
                } catch (e: Exception) {
                    null
                }
            }
        val updated =
            (metadataJson["updated"] as? JsonPrimitive)?.contentOrNull?.let {
                try {
                    Instant.parse(it)
                } catch (e: Exception) {
                    null
                }
            }
        val deactivated = (metadataJson["deactivated"] as? JsonPrimitive)?.booleanOrNull ?: false
        val versionId = (metadataJson["versionId"] as? JsonPrimitive)?.contentOrNull
        val nextUpdate =
            (metadataJson["nextUpdate"] as? JsonPrimitive)?.contentOrNull?.let {
                try {
                    Instant.parse(it)
                } catch (e: Exception) {
                    null
                }
            }
        val nextVersionId = (metadataJson["nextVersionId"] as? JsonPrimitive)?.contentOrNull
        val canonicalId =
            (metadataJson["canonicalId"] as? JsonPrimitive)?.contentOrNull?.let {
                try {
                    Did(it)
                } catch (e: Exception) {
                    null
                }
            }
        val equivalentId =
            (metadataJson["equivalentId"] as? JsonArray)?.mapNotNull { element ->
                (element as? JsonPrimitive)?.contentOrNull?.let { id ->
                    try {
                        Did(id)
                    } catch (e: Exception) {
                        null
                    }
                }
            } ?: emptyList()
        // §4.3 `proof`: controller/VDR proofs. toJson() has always emitted this; read it back too
        // so a §9 round-trip does not silently drop it.
        val proof = (metadataJson["proof"] as? JsonArray)?.filterIsInstance<JsonObject>() ?: emptyList()

        return DidDocumentMetadata(
            created = created,
            updated = updated,
            deactivated = deactivated,
            versionId = versionId,
            nextUpdate = nextUpdate,
            nextVersionId = nextVersionId,
            canonicalId = canonicalId,
            equivalentId = equivalentId,
            proof = proof,
        )
    }

    /**
     * Parses resolution metadata from JSON.
     */
    private fun parseResolutionMetadata(metadataJson: JsonObject): Map<String, Any?> =
        metadataJson.entries.associate { it.key to convertJsonElement(it.value) }

    /**
     * Converts JsonElement to Any for metadata maps.
     */
    private fun convertJsonElement(element: JsonElement): Any? =
        when (element) {
            // `JsonNull` is itself a `JsonPrimitive` subtype, so this branch must be checked
            // before `is JsonPrimitive` below — otherwise it is unreachable dead code and a
            // genuine JSON null silently becomes the literal string "null" via the `else ->
            // element.content` fallback in the JsonPrimitive branch.
            is JsonNull -> null
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
        }

    /**
     * Parses a list of supported methods from JSON response.
     *
     * Returns `null` when the response cannot be parsed or the root element is not a JSON array.
     * Using `as?` safe cast avoids the [IllegalStateException] that `.jsonArray` throws for
     * non-array roots; only [kotlinx.serialization.SerializationException] is caught for
     * malformed JSON.
     */
    private fun parseMethodsList(jsonString: String): List<String>? {
        // This is a non-suspend function; CancellationException cannot arrive here,
        // so no CE guard is needed.
        return try {
            val element = json.parseToJsonElement(jsonString)
            val array = element as? JsonArray ?: return null
            array.mapNotNull { it.jsonPrimitive?.content }
        } catch (e: kotlinx.serialization.SerializationException) {
            null
        }
    }
}
