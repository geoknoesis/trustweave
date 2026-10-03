package org.trustweave.did.base

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.trustweave.core.exception.SerializationException
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.kms.KeyManagementService
import java.io.IOException
import java.net.URL
import kotlin.time.Duration

/**
 * Abstract base class for HTTP-based DID method implementations (e.g., did:web).
 *
 * Provides common functionality for DID methods that host documents over HTTP/HTTPS:
 * - HTTP client for document retrieval and publishing
 * - Document hosting abstraction
 * - HTTPS validation
 * - Common HTTP error handling
 *
 * Subclasses should implement:
 * - [createDid]: Create a new DID and publish its document
 * - [resolveDid]: Resolve DID from HTTP endpoint
 * - [getDocumentUrl]: Get the HTTP URL for a DID document
 * - [publishDocument]: Publish a document to the HTTP endpoint
 *
 * Pattern: HTTP client abstraction for web-based DID methods.
 *
 * **Example Usage:**
 * ```kotlin
 * class WebDidMethod(
 *     kms: KeyManagementService,
 *     private val httpClient: OkHttpClient,
 *     private val documentHost: DocumentHost
 * ) : AbstractWebDidMethod("web", kms, httpClient) {
 *
 *     override fun getDocumentUrl(did: String): String {
 *         val (_, domain, path) = parseWebDid(did)
 *         return if (path != null) {
 *             "https://$domain/.well-known/did.json"
 *         } else {
 *             "https://$domain/.well-known/did.json"
 *         }
 *     }
 *
 *     override suspend fun publishDocument(url: String, document: DidDocument): Boolean {
 *         return documentHost.publish(url, document)
 *     }
 * }
 * ```
 */
abstract class AbstractWebDidMethod(
    method: String,
    kms: KeyManagementService,
    protected val httpClient: OkHttpClient,
) : AbstractDidMethod(method, kms) {
    /**
     * Gets the HTTP URL for a DID document.
     *
     * @param did The DID to get the URL for
     * @return HTTP/HTTPS URL string
     */
    protected abstract fun getDocumentUrl(did: String): String

    /**
     * Publishes a DID document to an HTTP endpoint.
     *
     * Subclasses should implement this to publish documents to their hosting infrastructure.
     *
     * @param url The URL to publish to
     * @param document The DID document to publish
     * @return true if successful
     * @throws TrustWeaveException if publishing fails
     */
    protected abstract suspend fun publishDocument(
        url: String,
        document: DidDocument,
    ): Boolean

    /**
     * Validates that a URL uses HTTPS (required for did:web).
     *
     * @param url The URL to validate
     * @throws IllegalArgumentException if URL doesn't use HTTPS
     */
    protected fun validateHttps(url: String) {
        val parsedUrl =
            try {
                URL(url)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid URL: $url", e)
            }

        if (parsedUrl.protocol != "https") {
            throw IllegalArgumentException("did:web requires HTTPS: $url")
        }
    }

    /**
     * SSRF guard for resolution. A did:web identifier's host is attacker-controlled (it comes from
     * the DID being resolved, e.g. an issuer or holder DID), so reject any host that resolves to a
     * loopback / private / link-local / cloud-metadata address before opening a connection.
     *
     * @throws TrustWeaveException.Unknown if the host is disallowed or unresolvable.
     */
    protected fun assertHostAllowed(url: String) {
        val host =
            try {
                URL(url).host
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid URL: $url", e)
            }
        ResolutionNetworkGuard.rejectionReason(host)?.let { reason ->
            throw TrustWeaveException.Unknown(
                message = "did:web SSRF guard rejected resolution: $reason",
                context = mapOf("url" to url, "method" to method),
            )
        }
    }

    /**
     * How many HTTP redirects [resolveFromHttp] follows. Default `0`: a redirect fails the
     * resolution. The HTTP client's own redirect handling is always disabled for resolution;
     * redirects are followed manually so every hop is re-checked against the HTTPS requirement
     * and the SSRF guard.
     */
    protected open val maxRedirects: Int = 0

    /**
     * How old (since the last successful fetch or write) a cached document may be and still be
     * served when the endpoint is unreachable (an [IOException]). Default [Duration.ZERO]: never
     * serve a cached live document on a network failure — fail instead. A locally recorded
     * deactivation is served regardless, because reporting Deactivated is fail-safe.
     */
    protected open val maxStaleCacheAge: Duration = Duration.ZERO

    /**
     * Hook for subclasses to adjust the client used for resolution (e.g. a call timeout). Runs
     * before the redirect and DNS guards are applied, so it cannot re-enable automatic redirects
     * or remove the resolved-address guard.
     */
    protected open fun configureResolutionClient(builder: OkHttpClient.Builder) {}

    /**
     * The client used for resolution: [httpClient] with automatic redirects disabled and its DNS
     * wrapped in [ResolutionGuardedDns], so the addresses actually connected to are checked too
     * (closing the DNS-rebinding gap the pre-flight [assertHostAllowed] check leaves open).
     */
    private val resolutionClient: OkHttpClient by lazy {
        httpClient
            .newBuilder()
            .also { configureResolutionClient(it) }
            .followRedirects(false)
            .followSslRedirects(false)
            .dns(ResolutionGuardedDns(httpClient.dns))
            .build()
    }

    /**
     * Fetches the document at [initialUrl], following at most [maxRedirects] redirects and
     * re-validating HTTPS and the SSRF guard on every hop.
     */
    private fun fetchDocumentJson(initialUrl: String): String {
        var url = initialUrl
        var redirects = 0
        while (true) {
            validateHttps(url)
            assertHostAllowed(url)

            val request =
                Request
                    .Builder()
                    .url(url)
                    .get()
                    .addHeader("Accept", "application/json")
                    .build()

            // Close the response on every path (including non-2xx) to avoid leaking
            // the underlying OkHttp connection.
            val next: String =
                resolutionClient.newCall(request).execute().use { response ->
                    if (response.isRedirect) {
                        if (redirects >= maxRedirects) {
                            throw TrustWeaveException.Unknown(
                                message =
                                    "DID document request was redirected (HTTP ${response.code}) and " +
                                        "redirect limit $maxRedirects was reached at: $url",
                                context = mapOf("url" to url, "method" to method),
                            )
                        }
                        val location =
                            response.header("Location")
                                ?: throw TrustWeaveException.Unknown(
                                    message = "HTTP ${response.code} redirect without a Location header at: $url",
                                    context = mapOf("url" to url, "method" to method),
                                )
                        val target =
                            response.request.url.resolve(location)
                                ?: throw TrustWeaveException.Unknown(
                                    message = "Invalid redirect Location '$location' at: $url",
                                    context = mapOf("url" to url, "method" to method),
                                )
                        return@use target.toString()
                    }
                    if (!response.isSuccessful) {
                        if (response.code == 404) {
                            throw TrustWeaveException.NotFound(
                                message = "DID document not found at: $url",
                            )
                        }
                        throw TrustWeaveException.Unknown(
                            message = "Failed to resolve DID document: HTTP ${response.code} ${response.message}",
                            context = mapOf("statusCode" to response.code, "url" to url, "method" to method),
                        )
                    }
                    return readCappedBody(response, url)
                }
            redirects++
            url = next
        }
    }

    private fun readCappedBody(
        response: Response,
        url: String,
    ): String {
        val body =
            response.body ?: throw SerializationException.InvalidJson(
                parseError = "Empty response body",
                jsonString = null,
            )
        // Cap the body to avoid a memory-exhaustion DoS from a malicious/compromised host.
        val maxResponseBytes = 1 * 1024 * 1024 // 1 MB
        val bytes = body.byteStream().readNBytes(maxResponseBytes + 1)
        if (bytes.size > maxResponseBytes) {
            throw TrustWeaveException.Unknown(
                message = "DID document exceeds maximum allowed size ($maxResponseBytes bytes) at: $url",
                context = mapOf("url" to url, "method" to method),
            )
        }
        return String(bytes, Charsets.UTF_8)
    }

    /**
     * Resolves a DID document from an HTTP endpoint.
     *
     * **Local deactivation is authoritative for did:web, even over a live endpoint response.**
     * The DID Resolution 1.0 CR does not settle where a web-hosted method should learn of
     * deactivation from, so TrustWeave makes an explicit, fail-safe choice: once this instance
     * has recorded a did:web DID as deactivated (via [deactivateDocumentOnHttp]), every
     * subsequent [resolveFromHttp] call returns [DidResolutionResult.Deactivated] for that DID —
     * on the ordinary HTTP-200 path just as much as on the offline-fallback path below — even if
     * the hosted endpoint keeps serving a live 200 with the (still-published) document. A revoked
     * DID must never verify; treating the endpoint as authoritative over local state would let a
     * did:web controller (or an attacker who compromises the host after deactivation) silently
     * resurrect a DID TrustWeave believes is dead. The accepted tradeoff is that a DID deactivated
     * on one machine still resolves live on another instance that never observed the
     * deactivation, since deactivation state here is local and is not fetched from, or propagated
     * to, the hosted endpoint.
     *
     * @param did The DID to resolve
     * @return DidResolutionResult
     * @throws NotFoundException if document not found
     * @throws TrustWeaveException if resolution fails
     */
    protected suspend fun resolveFromHttp(didString: String): DidResolutionResult =
        withContext(Dispatchers.IO) {
            val did = Did(didString)
            validateDidFormat(did)

            try {
                val jsonString = fetchDocumentJson(getDocumentUrl(didString))

                // Parse JSON to DidDocument
                val json = Json.parseToJsonElement(jsonString)
                val document = jsonElementToDocument(json)

                // Validate that document ID matches DID
                if (document.id.value != didString) {
                    throw org.trustweave.did.exception.DidException.InvalidDidFormat(
                        did = document.id.value,
                        reason = "Document ID mismatch: expected $didString, got ${document.id.value}",
                    )
                }

                // Store locally for caching. storeDocument() is a cache-store, not a DID operation:
                // it leaves existing §4.3 metadata untouched (see its KDoc), so any deactivation
                // this instance has already recorded survives — local state stays authoritative for
                // did:web even though the endpoint just answered with a live 200 — and `updated`
                // keeps reporting the last real Update operation instead of this fetch's clock
                // reading. The fetch time is reported separately, as §4.2 `retrieved`.
                storeDocument(document.id.value, document)

                val metadata = getDocumentMetadata(did)
                org.trustweave.did.base.DidMethodUtils.createSuccessResolutionResult(
                    document,
                    method,
                    created = metadata?.created,
                    updated = metadata?.updated,
                    deactivated = metadata?.deactivated ?: false,
                    retrieved = getLastFetched(did),
                )
            } catch (e: TrustWeaveException.NotFound) {
                throw e
            } catch (e: TrustWeaveException) {
                throw e
            } catch (e: IOException) {
                // Offline fallback, bounded by maxStaleCacheAge (see its KDoc). A locally recorded
                // deactivation is always served: reporting Deactivated is fail-safe.
                val stored = getStoredDocument(did)
                val metadata = getDocumentMetadata(did)
                val lastFetched = getLastFetched(did)
                val deactivated = metadata?.deactivated ?: false
                val fresh =
                    lastFetched != null &&
                        maxStaleCacheAge > Duration.ZERO &&
                        Clock.System.now() - lastFetched <= maxStaleCacheAge
                if (stored != null && (deactivated || fresh)) {
                    return@withContext org.trustweave.did.base.DidMethodUtils.createSuccessResolutionResult(
                        stored,
                        method,
                        metadata?.created,
                        metadata?.updated,
                        deactivated,
                        retrieved = lastFetched,
                    )
                }

                val url = getDocumentUrl(didString)
                val staleNote =
                    if (stored != null) {
                        " (a cached copy exists but is older than the allowed max stale age $maxStaleCacheAge)"
                    } else {
                        ""
                    }
                throw org.trustweave.core.exception.TrustWeaveException.Unknown(
                    message = "Failed to resolve DID from HTTP endpoint: ${e.message ?: "Unknown error"}$staleNote",
                    context = mapOf("did" to didString, "method" to method, "url" to url),
                    cause = e,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                throw org.trustweave.core.exception.TrustWeaveException.Unknown(
                    message = "Failed to resolve DID document: ${e.message ?: "Unknown error"}",
                    context = mapOf("did" to didString, "method" to method),
                    cause = e,
                )
            }
        }

    /**
     * Updates a DID document on an HTTP endpoint.
     *
     * @param did The DID to update
     * @param document The updated document
     * @return true if successful
     */
    protected suspend fun updateDocumentOnHttp(
        didString: String,
        document: DidDocument,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val did = Did(didString)
            validateDidFormat(did)

            try {
                val url = getDocumentUrl(didString)
                validateHttps(url)

                // Publish updated document
                val success = publishDocument(url, document)

                if (success) {
                    // Update local storage under updateMutex, the same lock storeDocument takes.
                    // Writing outside it reopened the lost-update race the lock exists to close: a
                    // concurrent storeDocument (which runs on *every* successful resolve) reads the
                    // existing metadata and writes the merged value back, so an unlocked write
                    // landing in between is silently overwritten. Only the map writes are inside
                    // the lock — publishDocument() above is network I/O and must not hold it, and
                    // holding it there would also serialise every resolve behind a remote call.
                    updateMutex.withLock {
                        val now = Clock.System.now()
                        documentMetadata[didString] =
                            (documentMetadata[didString] ?: DidDocumentMetadata(created = now))
                                .copy(updated = now)
                        // getLastFetched's contract is "last fetched or wrote" — a successful
                        // publish is a write, so it counts too.
                        lastFetched[didString] = now
                    }
                }

                success
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                throw org.trustweave.core.exception.TrustWeaveException.Unknown(
                    message = "Failed to update DID document on HTTP endpoint: ${e.message ?: "Unknown error"}",
                    context = mapOf("did" to didString, "method" to method),
                    cause = e,
                )
            }
        }

    /**
     * Deactivates a DID document on an HTTP endpoint.
     *
     * @param did The DID to deactivate
     * @param deactivatedDocument The deactivated document
     * @return true if successful
     */
    protected suspend fun deactivateDocumentOnHttp(
        didString: String,
        deactivatedDocument: DidDocument,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val did = Did(didString)
            validateDidFormat(did)

            try {
                val url = getDocumentUrl(didString)
                validateHttps(url)

                // Publish deactivated document
                val success = publishDocument(url, deactivatedDocument)

                if (success) {
                    // Keep the deactivated document locally and flag the metadata as
                    // deactivated (W3C DID Core §7.3) so subsequent resolutions can
                    // surface the deactivation instead of silently "losing" the DID.
                    //
                    // Both writes go under updateMutex, the same lock storeDocument takes. This is
                    // the §4.4 security property, not bookkeeping: storeDocument runs on every
                    // successful resolve, reading the existing metadata and writing the merged
                    // value back, so a deactivation written outside the lock could land between
                    // that read and that write and be silently clobbered — and the DID would
                    // resolve live again. Only the map writes are inside the lock; publishDocument()
                    // above is network I/O and must not hold it.
                    updateMutex.withLock {
                        val now = Clock.System.now()
                        documents[didString] = deactivatedDocument
                        documentMetadata[didString] =
                            (documentMetadata[didString] ?: DidDocumentMetadata(created = now))
                                .copy(updated = now, deactivated = true)
                        lastFetched[didString] = now
                    }
                }

                success
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                throw org.trustweave.core.exception.TrustWeaveException.Unknown(
                    message = "Failed to deactivate DID document on HTTP endpoint: ${e.message ?: "Unknown error"}",
                    context = mapOf("did" to didString, "method" to method),
                    cause = e,
                )
            }
        }

    /**
     * Helper function to create an HTTP request for publishing a document.
     *
     * @param url The URL to publish to
     * @param document The DID document
     * @return Request for PUT/PATCH
     */
    protected fun createPublishRequest(
        url: String,
        document: DidDocument,
    ): Request {
        val jsonElement = documentToJsonElement(document)
        val json = Json.encodeToString(JsonElement.serializer(), jsonElement)

        val mediaType = "application/json".toMediaType()
        val body = json.toRequestBody(mediaType)

        return Request
            .Builder()
            .url(url)
            .put(body) // Use PUT for full replacement, subclasses can override to use PATCH
            .addHeader("Content-Type", "application/json")
            .build()
    }

    /**
     * Helper function to execute an HTTP request.
     *
     * @param request The HTTP request
     * @return Response
     * @throws IOException if request fails
     */
    protected suspend fun executeRequest(request: Request): Response =
        withContext(Dispatchers.IO) {
            httpClient.newCall(request).execute()
        }
}
