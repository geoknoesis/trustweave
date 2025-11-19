package com.geoknoesis.vericore.webdid

import com.geoknoesis.vericore.core.NotFoundException
import com.geoknoesis.vericore.core.VeriCoreException
import com.geoknoesis.vericore.did.*
import com.geoknoesis.vericore.did.base.AbstractWebDidMethod
import com.geoknoesis.vericore.did.base.DidMethodUtils
import com.geoknoesis.vericore.kms.KeyManagementService
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
                throw VeriCoreException(
                    "Failed to publish DID document: HTTP ${response.code} ${response.message}"
                )
            }
            
            true
        } catch (e: VeriCoreException) {
            throw e
        } catch (e: Exception) {
            throw VeriCoreException(
                "Failed to publish DID document to $url: ${e.message}",
                e
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
            val keyHandle = kms.generateKey(algorithm, options.additionalProperties)
            
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
                authentication = listOf(verificationMethod.id),
                assertionMethod = if (options.purposes.contains(DidCreationOptions.KeyPurpose.ASSERTION)) {
                    listOf(verificationMethod.id)
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
        } catch (e: VeriCoreException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw VeriCoreException(
                "Failed to create did:web: ${e.message}",
                e
            )
        }
    }

    override suspend fun resolveDid(did: String): DidResolutionResult = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)
            resolveFromHttp(did)
        } catch (e: NotFoundException) {
            DidMethodUtils.createErrorResolutionResult("notFound", e.message, method)
        } catch (e: VeriCoreException) {
            DidMethodUtils.createErrorResolutionResult("invalidDid", e.message, method)
        } catch (e: Exception) {
            DidMethodUtils.createErrorResolutionResult("invalidDid", e.message, method)
        }
    }

    override suspend fun updateDid(
        did: String,
        updater: (DidDocument) -> DidDocument
    ): DidDocument = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)
            
            // Resolve current document
            val currentResult = resolveFromHttp(did)
            val currentDocument = currentResult.document
                ?: throw NotFoundException("DID document not found: $did")
            
            // Apply updater
            val updatedDocument = updater(currentDocument)
            
            // Publish updated document
            val url = getDocumentUrl(did)
            updateDocumentOnHttp(did, updatedDocument)
            
            updatedDocument
        } catch (e: NotFoundException) {
            throw e
        } catch (e: VeriCoreException) {
            throw e
        } catch (e: Exception) {
            throw VeriCoreException(
                "Failed to update did:web: ${e.message}",
                e
            )
        }
    }

    override suspend fun deactivateDid(did: String): Boolean = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)
            
            // Resolve current document
            val currentResult = resolveFromHttp(did)
            val currentDocument = currentResult.document
                ?: return@withContext false
            
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
            val url = getDocumentUrl(did)
            deactivateDocumentOnHttp(did, deactivatedDocument)
            
            true
        } catch (e: NotFoundException) {
            false
        } catch (e: Exception) {
            throw VeriCoreException(
                "Failed to deactivate did:web: ${e.message}",
                e
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

