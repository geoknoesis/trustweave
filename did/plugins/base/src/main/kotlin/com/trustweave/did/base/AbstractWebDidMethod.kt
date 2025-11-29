package com.trustweave.did.base

// NotFoundException replaced with TrustWeaveException.NotFound
import com.trustweave.core.exception.TrustWeaveException
import com.trustweave.did.*
import com.trustweave.did.VerificationMethod
import com.trustweave.did.DidService
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.kms.KeyManagementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URL
import java.time.Instant

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
     * Resolves a DID document from an HTTP endpoint.
     *
     * @param did The DID to resolve
     * @return DidResolutionResult
     * @throws NotFoundException if document not found
     * @throws TrustWeaveException if resolution fails
     */
    protected suspend fun resolveFromHttp(did: String): DidResolutionResult = withContext(Dispatchers.IO) {
        validateDidFormat(did)

        try {
            val url = getDocumentUrl(did)
            validateHttps(url)

            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "application/json")
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                if (response.code == 404) {
                    throw TrustWeaveException.NotFound(
                        message = "DID document not found at: $url"
                    )
                }
                throw com.trustweave.core.exception.TrustWeaveException.Unknown(
                    message = "Failed to resolve DID document: HTTP ${response.code} ${response.message}",
                    context = mapOf("statusCode" to response.code, "url" to url, "method" to method)
                )
            }

            val body = response.body ?: throw com.trustweave.core.exception.TrustWeaveException.InvalidJson(
                parseError = "Empty response body",
                jsonString = null
            )
            val jsonString = body.string()

            // Parse JSON to DidDocument
            val json = Json.parseToJsonElement(jsonString)
            val document = jsonElementToDocument(json)

            // Validate that document ID matches DID
            if (document.id != did) {
                throw com.trustweave.did.exception.DidException.InvalidDidFormat(
                    did = document.id,
                    reason = "Document ID mismatch: expected $did, got ${document.id}"
                )
            }

            // Store locally for caching
            storeDocument(document.id, document)

            com.trustweave.did.base.DidMethodUtils.createSuccessResolutionResult(document, method)
        } catch (e: TrustWeaveException.NotFound) {
            throw e
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: IOException) {
            // Try fallback to stored document
            val stored = getStoredDocument(did)
            if (stored != null) {
                return@withContext com.trustweave.did.base.DidMethodUtils.createSuccessResolutionResult(
                    stored,
                    method,
                    getDocumentMetadata(did)?.created,
                    getDocumentMetadata(did)?.updated
                )
            }

            val url = getDocumentUrl(did)
            throw com.trustweave.core.exception.TrustWeaveException.Unknown(
                message = "Failed to resolve DID from HTTP endpoint: ${e.message ?: "Unknown error"}",
                context = mapOf("did" to did, "method" to method, "url" to url),
                cause = e
            )
        } catch (e: Exception) {
            throw com.trustweave.core.exception.TrustWeaveException.Unknown(
                message = "Failed to resolve DID document: ${e.message ?: "Unknown error"}",
                context = mapOf("did" to did, "method" to method),
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
    protected suspend fun updateDocumentOnHttp(did: String, document: DidDocument): Boolean =
        withContext(Dispatchers.IO) {
            validateDidFormat(did)

            try {
                val url = getDocumentUrl(did)
                validateHttps(url)

                // Publish updated document
                val success = publishDocument(url, document)

                if (success) {
                    // Update local storage
                    val now = Instant.now()
                    documentMetadata[did] = (documentMetadata[did] ?: DidDocumentMetadata(created = now))
                        .copy(updated = now)
                }

                success
            } catch (e: Exception) {
                throw com.trustweave.core.exception.TrustWeaveException.Unknown(
                    message = "Failed to update DID document on HTTP endpoint: ${e.message ?: "Unknown error"}",
                    context = mapOf("did" to did, "method" to method),
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
        did: String,
        deactivatedDocument: DidDocument
    ): Boolean = withContext(Dispatchers.IO) {
        validateDidFormat(did)

        try {
            val url = getDocumentUrl(did)
            validateHttps(url)

            // Publish deactivated document
            val success = publishDocument(url, deactivatedDocument)

            if (success) {
                // Remove from local storage
                documents.remove(did)
                documentMetadata.remove(did)
            }

            success
        } catch (e: Exception) {
            throw com.trustweave.core.exception.TrustWeaveException.Unknown(
                message = "Failed to deactivate DID document on HTTP endpoint: ${e.message ?: "Unknown error"}",
                context = mapOf("did" to did, "method" to method),
                cause = e
            )
        }
    }

    /**
     * Converts a DID document to JsonElement.
     *
     * @param document The DID document
     * @return JsonElement representation
     */
    protected fun documentToJsonElement(document: DidDocument): JsonElement {
        return buildJsonObject {
            put("@context", JsonArray(document.context.map { JsonPrimitive(it) }))
            put("id", document.id)

            if (document.alsoKnownAs.isNotEmpty()) {
                put("alsoKnownAs", JsonArray(document.alsoKnownAs.map { JsonPrimitive(it) }))
            }
            if (document.controller.isNotEmpty()) {
                put("controller", JsonArray(document.controller.map { JsonPrimitive(it) }))
            }
            if (document.verificationMethod.isNotEmpty()) {
                put("verificationMethod", JsonArray(document.verificationMethod.map { vmToJsonObject(it) }))
            }
            if (document.authentication.isNotEmpty()) {
                put("authentication", JsonArray(document.authentication.map { JsonPrimitive(it) }))
            }
            if (document.assertionMethod.isNotEmpty()) {
                put("assertionMethod", JsonArray(document.assertionMethod.map { JsonPrimitive(it) }))
            }
            if (document.keyAgreement.isNotEmpty()) {
                put("keyAgreement", JsonArray(document.keyAgreement.map { JsonPrimitive(it) }))
            }
            if (document.capabilityInvocation.isNotEmpty()) {
                put("capabilityInvocation", JsonArray(document.capabilityInvocation.map { JsonPrimitive(it) }))
            }
            if (document.capabilityDelegation.isNotEmpty()) {
                put("capabilityDelegation", JsonArray(document.capabilityDelegation.map { JsonPrimitive(it) }))
            }
            if (document.service.isNotEmpty()) {
                put("service", JsonArray(document.service.map { serviceToJsonObject(it) }))
            }
        }
    }

    /**
     * Converts JsonElement to DidDocument.
     *
     * @param json The JsonElement
     * @return DidDocument
     */
    protected fun jsonElementToDocument(json: JsonElement): DidDocument {
        val obj = json.jsonObject
        return DidDocument(
            id = obj["id"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing id"),
            context = obj["@context"]?.let {
                when (it) {
                    is JsonPrimitive -> listOf(it.content)
                    is JsonArray -> it.mapNotNull { (it as? JsonPrimitive)?.content }
                    else -> listOf("https://www.w3.org/ns/did/v1")
                }
            } ?: listOf("https://www.w3.org/ns/did/v1"),
            alsoKnownAs = obj["alsoKnownAs"]?.jsonArray?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList(),
            controller = obj["controller"]?.let {
                when (it) {
                    is JsonPrimitive -> listOf(it.content)
                    is JsonArray -> it.mapNotNull { (it as? JsonPrimitive)?.content }
                    else -> emptyList()
                }
            } ?: emptyList(),
            verificationMethod = obj["verificationMethod"]?.jsonArray?.mapNotNull { jsonToVerificationMethod(it) } ?: emptyList(),
            authentication = obj["authentication"]?.jsonArray?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList(),
            assertionMethod = obj["assertionMethod"]?.jsonArray?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList(),
            keyAgreement = obj["keyAgreement"]?.jsonArray?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList(),
            capabilityInvocation = obj["capabilityInvocation"]?.jsonArray?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList(),
            capabilityDelegation = obj["capabilityDelegation"]?.jsonArray?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList(),
            service = obj["service"]?.jsonArray?.mapNotNull { jsonToService(it) } ?: emptyList()
        )
    }

    private fun vmToJsonObject(vm: VerificationMethod): JsonObject {
        return buildJsonObject {
            put("id", vm.id)
            put("type", vm.type)
            put("controller", vm.controller)
            vm.publicKeyJwk?.let { jwk ->
                put("publicKeyJwk", mapToJsonObject(jwk))
            }
            vm.publicKeyMultibase?.let {
                put("publicKeyMultibase", it)
            }
        }
    }

    private fun serviceToJsonObject(service: DidService): JsonObject {
        return buildJsonObject {
            put("id", service.id)
            put("type", service.type)
            put("serviceEndpoint", when (val endpoint = service.serviceEndpoint) {
                is String -> JsonPrimitive(endpoint)
                else -> Json.parseToJsonElement(endpoint.toString())
            })
        }
    }

    private fun jsonToVerificationMethod(json: JsonElement): VerificationMethod? {
        val obj = json.jsonObject
        return VerificationMethod(
            id = obj["id"]?.jsonPrimitive?.content ?: return null,
            type = obj["type"]?.jsonPrimitive?.content ?: return null,
            controller = obj["controller"]?.jsonPrimitive?.content ?: return null,
            publicKeyJwk = obj["publicKeyJwk"]?.jsonObject?.let { jsonObjectToMap(it) },
            publicKeyMultibase = obj["publicKeyMultibase"]?.jsonPrimitive?.content
        )
    }

    private fun jsonToService(json: JsonElement): DidService? {
        val obj = json.jsonObject
        return DidService(
            id = obj["id"]?.jsonPrimitive?.content ?: return null,
            type = obj["type"]?.jsonPrimitive?.content ?: return null,
            serviceEndpoint = obj["serviceEndpoint"]?.let {
                when (it) {
                    is JsonPrimitive -> it.content
                    else -> it.toString()
                }
            } ?: return null
        )
    }

    private fun mapToJsonObject(map: Map<String, Any?>): JsonObject {
        return buildJsonObject {
            map.forEach { (key, value) ->
                when (value) {
                    null -> put(key, JsonNull)
                    is String -> put(key, value)
                    is Number -> put(key, value)
                    is Boolean -> put(key, value)
                    is Map<*, *> -> put(key, mapToJsonObject(value as Map<String, Any?>))
                    is List<*> -> put(key, JsonArray(value.map {
                        when (it) {
                            is String -> JsonPrimitive(it)
                            is Number -> JsonPrimitive(it)
                            is Boolean -> JsonPrimitive(it)
                            else -> JsonPrimitive(it.toString())
                        }
                    }))
                    else -> put(key, value.toString())
                }
            }
        }
    }

    private fun jsonObjectToMap(obj: JsonObject): Map<String, Any?> {
        return obj.entries.associate { (key, value) ->
            key to when (value) {
                is JsonPrimitive -> value.contentOrNull ?: value.booleanOrNull ?: value.longOrNull ?: value.doubleOrNull ?: value.toString()
                is JsonObject -> jsonObjectToMap(value)
                is JsonArray -> value.map { (it as? JsonPrimitive)?.content ?: it.toString() }
                else -> value.toString()
            }
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

