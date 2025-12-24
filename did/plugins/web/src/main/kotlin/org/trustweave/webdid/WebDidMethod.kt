package org.trustweave.webdid

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.*
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.base.AbstractWebDidMethod
import org.trustweave.did.base.DidMethodUtils
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.GenerateKeyResult
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
 * - Document URL: `https://{domain}{path}/.well-known/did.json`
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
         * Creates a WebDidMethod with default configuration.
         */
        fun create(
            kms: KeyManagementService,
            httpClient: OkHttpClient = OkHttpClient()
        ): WebDidMethod {
            return WebDidMethod(kms, httpClient, WebDidConfig.default())
        }
    }

    override fun getDocumentUrl(did: String): String {
        val (domain, path) = parseWebDid(did)
        val normalizedDomain = DidMethodUtils.normalizeDomain(domain)

        // Build URL according to W3C spec
        // For did:web:example.com, URL is https://example.com/.well-known/did.json
        // For did:web:example.com:user:alice, URL is https://example.com/user/alice/.well-known/did.json
        val urlPath = if (path != null && path.isNotBlank()) {
            // Replace colons with slashes in path
            val pathParts = path.split(":")
            "/${pathParts.joinToString("/")}${config.documentPath}"
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
            val response = executeRequest(request)

            if (!response.isSuccessful) {
                throw TrustWeaveException.Unknown(
                    code = "HTTP_ERROR",
                    message = "Failed to publish DID document: HTTP ${response.code} ${response.message}"
                )
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

            // Generate key using KMS
            val algorithm = options.algorithm.algorithmName
            val generateResult = kms.generateKey(algorithm, options.additionalProperties)
            val keyHandle = when (generateResult) {
                is GenerateKeyResult.Success -> generateResult.keyHandle
                is GenerateKeyResult.Failure.UnsupportedAlgorithm -> throw TrustWeaveException.Unknown(
                    code = "UNSUPPORTED_ALGORITHM",
                    message = generateResult.reason ?: "Algorithm not supported"
                )
                is GenerateKeyResult.Failure.InvalidOptions -> throw TrustWeaveException.Unknown(
                    code = "INVALID_OPTIONS",
                    message = generateResult.reason
                )
                is GenerateKeyResult.Failure.Error -> throw TrustWeaveException.Unknown(
                    code = "KEY_GENERATION_ERROR",
                    message = generateResult.reason,
                    cause = generateResult.cause
                )
            }

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
            val url = getDocumentUrl(didString)
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
     * Parses a did:web identifier into domain and path components.
     *
     * @param did The DID string (e.g., "did:web:example.com" or "did:web:example.com:user:alice")
     * @return Pair of (domain, path) where path may be null
     */
    private fun parseWebDid(did: String): Pair<String, String?> {
        val parsed = DidMethodUtils.parseDid(did)
            ?: throw IllegalArgumentException("Invalid DID format: $did")

        if (parsed.first != "web") {
            throw IllegalArgumentException("Not a did:web DID: $did")
        }

        val identifier = parsed.second

        // Check if identifier contains path (colons after domain)
        val colonIndex = identifier.indexOf(':')
        return if (colonIndex >= 0) {
            val domain = identifier.substring(0, colonIndex)
            val path = identifier.substring(colonIndex + 1)
            domain to path
        } else {
            identifier to null
        }
    }
}

