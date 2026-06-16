package org.trustweave.did.base

import org.trustweave.core.exception.SerializationException
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.core.net.PrivateNetworkGuard
import org.trustweave.did.*
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.kms.KeyManagementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URL
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

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
    protected val httpClient: OkHttpClient
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
    protected abstract suspend fun publishDocument(url: String, document: DidDocument): Boolean

    /**
     * Validates that a URL uses HTTPS (required for did:web).
     *
     * @param url The URL to validate
     * @throws IllegalArgumentException if URL doesn't use HTTPS
     */
    protected fun validateHttps(url: String) {
        val parsedUrl = try {
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
        val host = try {
            URL(url).host
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid URL: $url", e)
        }
        PrivateNetworkGuard.rejectionReason(host)?.let { reason ->
            throw TrustWeaveException.Unknown(
                message = "did:web SSRF guard rejected resolution: $reason",
                context = mapOf("url" to url, "method" to method)
            )
        }
    }

    /**
     * Resolves a DID document from an HTTP endpoint.
     *
     * @param did The DID to resolve
     * @return DidResolutionResult
     * @throws NotFoundException if document not found
     * @throws TrustWeaveException if resolution fails
     */
    protected suspend fun resolveFromHttp(didString: String): DidResolutionResult = withContext(Dispatchers.IO) {
        val did = Did(didString)
        validateDidFormat(did)

        try {
            val url = getDocumentUrl(didString)
            validateHttps(url)
            assertHostAllowed(url)

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "application/json")
                .build()

            // Close the response on every path (including non-2xx) to avoid leaking
            // the underlying OkHttp connection.
            val jsonString = httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 404) {
                        throw TrustWeaveException.NotFound(
                            message = "DID document not found at: $url"
                        )
                    }
                    throw org.trustweave.core.exception.TrustWeaveException.Unknown(
                        message = "Failed to resolve DID document: HTTP ${response.code} ${response.message}",
                        context = mapOf("statusCode" to response.code, "url" to url, "method" to method)
                    )
                }

                val body = response.body ?: throw SerializationException.InvalidJson(
                    parseError = "Empty response body",
                    jsonString = null
                )
                // Cap the body to avoid a memory-exhaustion DoS from a malicious/compromised host.
                val maxResponseBytes = 1 * 1024 * 1024 // 1 MB
                val bytes = body.byteStream().readNBytes(maxResponseBytes + 1)
                if (bytes.size > maxResponseBytes) {
                    throw TrustWeaveException.Unknown(
                        message = "DID document exceeds maximum allowed size ($maxResponseBytes bytes) at: $url",
                        context = mapOf("url" to url, "method" to method)
                    )
                }
                String(bytes, Charsets.UTF_8)
            }

            // Parse JSON to DidDocument
            val json = Json.parseToJsonElement(jsonString)
            val document = jsonElementToDocument(json)

            // Validate that document ID matches DID
            if (document.id.value != didString) {
                throw org.trustweave.did.exception.DidException.InvalidDidFormat(
                    did = document.id.value,
                    reason = "Document ID mismatch: expected $didString, got ${document.id.value}"
                )
            }

            // Store locally for caching
            storeDocument(document.id.value, document)

            org.trustweave.did.base.DidMethodUtils.createSuccessResolutionResult(document, method)
        } catch (e: TrustWeaveException.NotFound) {
            throw e
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: IOException) {
            // Try fallback to stored document
            val stored = getStoredDocument(did)
            if (stored != null) {
                return@withContext org.trustweave.did.base.DidMethodUtils.createSuccessResolutionResult(
                    stored,
                    method,
                    getDocumentMetadata(did)?.created,
                    getDocumentMetadata(did)?.updated,
                    getDocumentMetadata(did)?.deactivated ?: false
                )
            }

            val url = getDocumentUrl(didString)
            throw org.trustweave.core.exception.TrustWeaveException.Unknown(
                message = "Failed to resolve DID from HTTP endpoint: ${e.message ?: "Unknown error"}",
                context = mapOf("did" to didString, "method" to method, "url" to url),
                cause = e
            )
        } catch (e: Exception) {
            throw org.trustweave.core.exception.TrustWeaveException.Unknown(
                message = "Failed to resolve DID document: ${e.message ?: "Unknown error"}",
                context = mapOf("did" to didString, "method" to method),
                cause = e
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
    protected suspend fun updateDocumentOnHttp(didString: String, document: DidDocument): Boolean =
        withContext(Dispatchers.IO) {
            val did = Did(didString)
            validateDidFormat(did)

            try {
                val url = getDocumentUrl(didString)
                validateHttps(url)

                // Publish updated document
                val success = publishDocument(url, document)

                if (success) {
                    // Update local storage
                    val now = Clock.System.now()
                    documentMetadata[didString] = (documentMetadata[didString] ?: DidDocumentMetadata(created = now))
                        .copy(updated = now)
                }

                success
            } catch (e: Exception) {
                throw org.trustweave.core.exception.TrustWeaveException.Unknown(
                    message = "Failed to update DID document on HTTP endpoint: ${e.message ?: "Unknown error"}",
                    context = mapOf("did" to didString, "method" to method),
                    cause = e
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
        deactivatedDocument: DidDocument
    ): Boolean = withContext(Dispatchers.IO) {
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
                val now = Clock.System.now()
                documents[didString] = deactivatedDocument
                documentMetadata[didString] = (documentMetadata[didString] ?: DidDocumentMetadata(created = now))
                    .copy(updated = now, deactivated = true)
            }

            success
        } catch (e: Exception) {
            throw org.trustweave.core.exception.TrustWeaveException.Unknown(
                message = "Failed to deactivate DID document on HTTP endpoint: ${e.message ?: "Unknown error"}",
                context = mapOf("did" to didString, "method" to method),
                cause = e
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
    protected fun createPublishRequest(url: String, document: DidDocument): Request {
        val jsonElement = documentToJsonElement(document)
        val json = Json.encodeToString(JsonElement.serializer(), jsonElement)

        val mediaType = "application/json".toMediaType()
        val body = json.toRequestBody(mediaType)

        return Request.Builder()
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
    protected suspend fun executeRequest(request: Request): Response = withContext(Dispatchers.IO) {
        httpClient.newCall(request).execute()
    }
}

