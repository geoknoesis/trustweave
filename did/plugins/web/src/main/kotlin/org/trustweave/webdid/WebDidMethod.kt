package org.trustweave.webdid

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.*
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.base.AbstractWebDidMethod
import org.trustweave.did.base.DidMethodUtils
import org.trustweave.kms.KeyManagementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Implementation of did:web method per W3C specification.
 *
 * did:web resolves DID documents from HTTPS URLs:
 * - Format: `did:web:{domain}` or `did:web:{domain}:{path}`
 * - Bare domain: `did:web:example.com` → `https://example.com/.well-known/did.json`
 * - With path: `did:web:example.com:user:alice` → `https://example.com/user/alice/did.json`
 * - With port (percent-encoded): `did:web:example.com%3A8080:user` → `https://example.com:8080/user/did.json`
 *
 * **Example Usage:**
 * ```kotlin
 * val kms = InMemoryKeyManagementService()
 * val httpClient = OkHttpClient()
 * val config = WebDidConfig.default()
 * val method = WebDidMethod(kms, httpClient, config)
 *
 * // Create DID
 * val domain = "example.com"
 * val options = didCreationOptions {
 *     property("domain", domain)
 * }
 * val document = method.createDid(options)
 *
 * // Resolve DID
 * val result = method.resolveDid("did:web:example.com")
 * ```
 *
 * @see <a href="https://w3c-ccg.github.io/did-method-web/">W3C did:web Specification</a>
 */
class WebDidMethod(
    kms: KeyManagementService,
    httpClient: OkHttpClient,
    private val config: WebDidConfig = WebDidConfig.default()
) : AbstractWebDidMethod("web", kms, httpClient) {

    companion object {
        /**
         * Default document path per W3C spec.
         */
        const val DEFAULT_DOCUMENT_PATH = "/.well-known/did.json"

        /**
         * Characters that must never appear in a percent-decoded HOST segment.
         * `:` is deliberately allowed (port separator, `%3A`).
         */
        private const val HOST_FORBIDDEN_CHARS = "@/?#"

        /**
         * Characters that must never appear in a percent-decoded PATH segment.
         * `@` is legal inside a URL path, so it is allowed here.
         */
        private const val PATH_FORBIDDEN_CHARS = "/?#"

        /**
         * Creates a WebDidMethod with default configuration.
         */
        fun create(
            kms: KeyManagementService,
            httpClient: OkHttpClient = OkHttpClient()
        ): WebDidMethod {
            return WebDidMethod(kms, httpClient, WebDidConfig.default())
        }
    }

    /**
     * Transforms a did:web identifier into its DID document URL per the W3C spec:
     * - `did:web:example.com` → `https://example.com/.well-known/did.json`
     * - `did:web:example.com:user:alice` → `https://example.com/user/alice/did.json`
     * - `did:web:example.com%3A8080:user` → `https://example.com:8080/user/did.json`
     *
     * The `/.well-known/` location applies ONLY to bare-domain DIDs; path-based DIDs
     * use `{path}/did.json`. Method-specific-id segments are percent-decoded before
     * URL construction, which is how ports are expressed (`%3A` → `:`).
     */
    public override fun getDocumentUrl(did: String): String {
        val (domain, pathSegments) = parseWebDid(did)
        val normalizedDomain = DidMethodUtils.normalizeDomain(domain)

        val urlPath = if (pathSegments.isNotEmpty()) {
            "/${pathSegments.joinToString("/")}/did.json"
        } else {
            config.documentPath
        }

        val url = "https://$normalizedDomain$urlPath"

        // Validate HTTPS if required
        if (config.requireHttps) {
            validateHttps(url)
        }

        return url
    }

    override suspend fun publishDocument(url: String, document: DidDocument): Boolean = withContext(Dispatchers.IO) {
        try {
            if (config.requireHttps) {
                validateHttps(url)
            }

            val request = createPublishRequest(url, document)
            // Close the response on every path to avoid leaking the OkHttp connection.
            executeRequest(request).use { response ->
                if (!response.isSuccessful) {
                    throw TrustWeaveException.Unknown(
                        code = "HTTP_ERROR",
                        message = "Failed to publish DID document: HTTP ${response.code} ${response.message}"
                    )
                }
            }

            true
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                code = "PUBLISH_FAILED",
                message = "Failed to publish DID document to $url: ${e.message}",
                cause = e
            )
        }
    }

    override suspend fun createDid(options: DidCreationOptions): DidDocument = withContext(Dispatchers.IO) {
        try {
            // Extract domain from options
            val domain = options.additionalProperties["domain"] as? String
                ?: throw IllegalArgumentException("did:web requires 'domain' option")

            val normalizedDomain = DidMethodUtils.normalizeDomain(domain)
            val path = options.additionalProperties["path"] as? String

            // Build DID identifier
            val did = DidMethodUtils.buildWebDid(normalizedDomain, path)

            val algorithm = options.algorithm.algorithmName
            val keyHandle = generateKey(algorithm, options.additionalProperties)

            // Create verification method
            val verificationMethod = DidMethodUtils.createVerificationMethod(
                did = did,
                keyHandle = keyHandle,
                algorithm = options.algorithm
            )

            // Build DID document
            val document = DidMethodUtils.buildDidDocument(
                did = did,
                verificationMethod = listOf(verificationMethod),
                authentication = listOf(verificationMethod.id.value),
                assertionMethod = if (options.purposes.contains(KeyPurpose.ASSERTION)) {
                    listOf(verificationMethod.id.value)
                } else null
            )

            // Publish document if hosting is available
            val hostingUrl = options.additionalProperties["hostingUrl"] as? String
            if (hostingUrl != null) {
                val publishUrl = if (hostingUrl.endsWith("/")) {
                    hostingUrl.dropLast(1) + config.documentPath
                } else {
                    hostingUrl + config.documentPath
                }
                publishDocument(publishUrl, document)
            }

            // Store locally
            storeDocument(document.id, document)

            document
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                code = "CREATE_FAILED",
                message = "Failed to create did:web: ${e.message}",
                cause = e
            )
        }
    }

    override suspend fun resolveDid(did: Did): DidResolutionResult = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)
            resolveFromHttp(did.value)
        } catch (e: TrustWeaveException.NotFound) {
            DidMethodUtils.createErrorResolutionResult("notFound", e.message, method, did.value)
        } catch (e: TrustWeaveException) {
            DidMethodUtils.createErrorResolutionResult("invalidDid", e.message, method, did.value)
        } catch (e: Exception) {
            DidMethodUtils.createErrorResolutionResult("invalidDid", e.message, method, did.value)
        }
    }

    override suspend fun updateDid(
        did: Did,
        updater: (DidDocument) -> DidDocument
    ): DidDocument = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)

            val didString = did.value
            // Resolve current document
            val currentResult = resolveFromHttp(didString)
            val currentDocument = when (currentResult) {
                is DidResolutionResult.Success -> currentResult.document
                else -> throw TrustWeaveException.NotFound(
                    message = "DID document not found: $didString"
                )
            }

            // Apply updater
            val updatedDocument = updater(currentDocument)

            // Publish updated document
            val url = getDocumentUrl(didString)
            updateDocumentOnHttp(didString, updatedDocument)

            updatedDocument
        } catch (e: TrustWeaveException.NotFound) {
            throw e
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                code = "UPDATE_FAILED",
                message = "Failed to update did:web: ${e.message}",
                cause = e
            )
        }
    }

    override suspend fun deactivateDid(did: Did): Boolean = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)

            val didString = did.value
            // Resolve current document
            val currentResult = resolveFromHttp(didString)
            val currentDocument = when (currentResult) {
                is DidResolutionResult.Success -> currentResult.document
                else -> return@withContext false
            }

            // Create deactivated document (with deactivated flag in metadata or empty document)
            val deactivatedDocument = currentDocument.copy(
                verificationMethod = emptyList(),
                authentication = emptyList(),
                assertionMethod = emptyList(),
                keyAgreement = emptyList(),
                capabilityInvocation = emptyList(),
                capabilityDelegation = emptyList()
            )

            // Publish deactivated document
            deactivateDocumentOnHttp(didString, deactivatedDocument)

            true
        } catch (e: TrustWeaveException.NotFound) {
            false
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                code = "DEACTIVATE_FAILED",
                message = "Failed to deactivate did:web: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Parses a did:web identifier into domain and percent-decoded path segments.
     *
     * Per the W3C did:web spec, the method-specific identifier is split on `:` and
     * each segment is percent-decoded before URL construction. This is what allows
     * a port to be expressed: `did:web:example.com%3A8080` → host `example.com:8080`.
     *
     * Security: because the decoded segments are concatenated into an
     * `https://{host}{path}` URL, percent-decoding must not be allowed to introduce
     * URL metacharacters. In the host segment only `:` (the port separator) may be
     * introduced — a decoded host containing `@` (userinfo trick: `did:web:foo%40evil.com`
     * would fetch from `evil.com`), `/`, `?`, `#`, whitespace, or control characters is
     * rejected. Decoded path segments must not contain `/`, `?`, `#`, or control
     * characters (`%2F` would otherwise inject extra path levels, e.g. `..%2F` traversal).
     *
     * @param did The DID string (e.g., "did:web:example.com" or "did:web:example.com:user:alice")
     * @return Pair of (domain, pathSegments) where pathSegments may be empty
     * @throws IllegalArgumentException if the DID is malformed or a decoded segment
     *         contains forbidden URL metacharacters
     */
    private fun parseWebDid(did: String): Pair<String, List<String>> {
        val parsed = DidMethodUtils.parseDid(did)
            ?: throw IllegalArgumentException("Invalid DID format: $did")

        if (parsed.first != "web") {
            throw IllegalArgumentException("Not a did:web DID: $did")
        }

        val rawSegments = parsed.second.split(":")
        if (rawSegments.any { it.isEmpty() }) {
            throw IllegalArgumentException(
                "Invalid did:web DID: empty method-specific-id segment in $did"
            )
        }

        val segments = rawSegments.map { DidMethodUtils.percentDecode(it) }

        val host = segments.first()
        if (host.any { it in HOST_FORBIDDEN_CHARS || it.isWhitespace() || it.isISOControl() }) {
            throw IllegalArgumentException(
                "Invalid did:web DID: decoded host segment contains forbidden URL characters: $did"
            )
        }

        val pathSegments = segments.drop(1)
        pathSegments.forEach { segment ->
            if (segment.any { it in PATH_FORBIDDEN_CHARS || it.isISOControl() }) {
                throw IllegalArgumentException(
                    "Invalid did:web DID: decoded path segment contains forbidden URL characters: $did"
                )
            }
        }

        return host to pathSegments
    }
}

