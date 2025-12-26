package org.trustweave.did.resolver

import org.trustweave.core.identifiers.KeyId
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import org.trustweave.did.model.DidService
import org.trustweave.did.model.VerificationMethod
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
 * @param timeout Request timeout in seconds (default: 30)
 * @param apiKey Optional API key for authentication (if required by the resolver)
 * @param protocolAdapter Protocol adapter for the specific Universal Resolver implementation
 *                        (defaults to [StandardUniversalResolverAdapter])
 */
class DefaultUniversalResolver(
    override val baseUrl: String,
    private val timeout: Int = 30,
    private val apiKey: String? = null,
    private val protocolAdapter: UniversalResolverProtocolAdapter = StandardUniversalResolverAdapter()
) : UniversalResolver {

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(timeout.toLong()))
        .build()

    override suspend fun resolveDid(did: String): DidResolutionResult = withContext(Dispatchers.IO) {
        // Validate input
        require(did.isNotBlank()) { "DID cannot be blank" }
        require(did.startsWith("did:")) { "DID must start with 'did:'" }
        
        try {
            // Use protocol adapter to build URL
            val url = protocolAdapter.buildResolveUrl(baseUrl, did)
            require(url.isNotBlank()) { "Resolve URL cannot be blank" }

            val requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeout.toLong()))
                .header("Accept", "application/json")

            // Use protocol adapter to configure authentication
            protocolAdapter.configureAuth(requestBuilder, apiKey)

            val request = requestBuilder.build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            when (response.statusCode()) {
                200 -> {
                    // Parse JSON response
                    val jsonResponse = parseJsonResponse(response.body())

                    // Use protocol adapter to extract data
                    val didDocumentJson = protocolAdapter.extractDidDocument(jsonResponse)
                    val document = didDocumentJson?.let { parseDidDocumentFromJson(it) }
                    val documentMetadata = parseDidDocumentMetadata(
                        protocolAdapter.extractDocumentMetadata(jsonResponse)
                    )
                    val resolutionMetadata = parseResolutionMetadata(
                        protocolAdapter.extractResolutionMetadata(jsonResponse)
                    )

                    if (document != null) {
                        DidResolutionResult.Success(
                            document = document,
                            documentMetadata = documentMetadata,
                            resolutionMetadata = resolutionMetadata.plus("provider" to protocolAdapter.providerName)
                        )
                    } else {
                        DidResolutionResult.Failure.NotFound(
                            did = Did(did),
                            reason = "DID document not found in response",
                            resolutionMetadata = resolutionMetadata.plus("provider" to protocolAdapter.providerName)
                        )
                    }
                }
                404 -> {
                    DidResolutionResult.Failure.NotFound(
                        did = Did(did),
                        reason = "DID not found",
                        resolutionMetadata = mapOf(
                            "error" to "notFound",
                            "provider" to protocolAdapter.providerName
                        )
                    )
                }
                else -> {
                    DidResolutionResult.Failure.ResolutionError(
                        did = Did(did),
                        reason = "HTTP ${response.statusCode()}",
                        cause = null,
                        resolutionMetadata = mapOf(
                            "statusCode" to response.statusCode(),
                            "provider" to protocolAdapter.providerName
                        )
                    )
                }
            }
        } catch (e: org.trustweave.did.exception.DidException) {
            // Re-throw DidException as-is
            throw e
        } catch (e: Exception) {
            throw org.trustweave.did.exception.DidException.DidResolutionFailed(
                did = Did(did),
                reason = e.message ?: "Unknown error during resolution",
                cause = e
            )
        }
    }

    override suspend fun getSupportedMethods(): List<String>? {
        // Use protocol adapter to build methods URL
        val methodsUrl = protocolAdapter.buildMethodsUrl(baseUrl) ?: return null

        return try {
            val requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(methodsUrl))
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
     * Parses a DID document from a JsonObject.
     *
     * @param json JSON object containing the DID document
     * @return Parsed DidDocument
     * @throws DidException.InvalidDidFormat if the document is invalid
     */
    private fun parseDidDocumentFromJson(json: JsonObject): DidDocument {
        val id = json["id"]?.jsonPrimitive?.content
            ?: throw DidException.InvalidDidFormat(
                did = "unknown",
                reason = "DID document missing 'id' field"
            )

        val context = parseContext(json)
        val parseVmId = createVmIdParser(id)
        val verificationMethod = parseVerificationMethods(json, parseVmId, id)
        val authentication = parseVerificationMethodReferences(json, "authentication", parseVmId)
        val assertionMethod = parseVerificationMethodReferences(json, "assertionMethod", parseVmId)
        val keyAgreement = parseVerificationMethodReferences(json, "keyAgreement", parseVmId)
        val capabilityInvocation = parseVerificationMethodReferences(json, "capabilityInvocation", parseVmId)
        val capabilityDelegation = parseVerificationMethodReferences(json, "capabilityDelegation", parseVmId)
        val service = parseServices(json)

        return DidDocument(
            id = Did(id),
            context = context,
            verificationMethod = verificationMethod,
            authentication = authentication,
            assertionMethod = assertionMethod,
            keyAgreement = keyAgreement,
            capabilityInvocation = capabilityInvocation,
            capabilityDelegation = capabilityDelegation,
            service = service
        )
    }

    /**
     * Parses the @context field from a DID document JSON.
     */
    private fun parseContext(json: JsonObject): List<String> {
        return when {
            json["@context"] != null -> {
                when (val ctx = json["@context"]) {
                    is JsonPrimitive -> listOf(ctx.content)
                    is JsonArray -> ctx.mapNotNull { it.jsonPrimitive?.content }
                    else -> listOf("https://www.w3.org/ns/did/v1")
                }
            }
            else -> listOf("https://www.w3.org/ns/did/v1")
        }
    }

    /**
     * Creates a parser function for verification method IDs.
     */
    private fun createVmIdParser(baseDid: String): (String) -> VerificationMethodId {
        return { vmIdString: String ->
            try {
                VerificationMethodId.parse(vmIdString, Did(baseDid))
            } catch (e: IllegalArgumentException) {
                // If parsing fails, try manual parsing as fallback
                // This handles edge cases where the format is slightly non-standard
                val (didPart, keyPart) = if (vmIdString.contains("#")) {
                    val parts = vmIdString.split("#", limit = 2)
                    if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                        parts[0] to parts[1]
                    } else {
                        throw DidException.InvalidDidFormat(
                            did = vmIdString,
                            reason = "Invalid verification method ID format: missing DID or fragment"
                        )
                    }
                } else {
                    // Relative reference - use base DID
                    baseDid to vmIdString
                }
                try {
                    VerificationMethodId(Did(didPart), KeyId(keyPart))
                } catch (e2: Exception) {
                    throw DidException.InvalidDidFormat(
                        did = vmIdString,
                        reason = "Failed to parse verification method ID: ${e2.message}"
                    )
                }
            }
        }
    }

    /**
     * Parses verification methods from a DID document JSON.
     */
    private fun parseVerificationMethods(
        json: JsonObject,
        parseVmId: (String) -> VerificationMethodId,
        baseDid: String
    ): List<VerificationMethod> {
        return json["verificationMethod"]?.jsonArray?.mapNotNull { vmJson ->
            val vmObj = vmJson.jsonObject
            val vmId = vmObj["id"]?.jsonPrimitive?.content
            val vmType = vmObj["type"]?.jsonPrimitive?.content
            val controller = vmObj["controller"]?.jsonPrimitive?.content ?: baseDid
            val publicKeyJwk = vmObj["publicKeyJwk"]?.jsonObject?.let { jwk ->
                jwk.entries.associate { it.key to convertJsonElement(it.value) }
            }
            val publicKeyMultibase = vmObj["publicKeyMultibase"]?.jsonPrimitive?.content

            if (vmId != null && vmType != null) {
                val parsedVmId = parseVmId(vmId)
                VerificationMethod(
                    id = parsedVmId,
                    type = vmType,
                    controller = Did(controller),
                    publicKeyJwk = publicKeyJwk,
                    publicKeyMultibase = publicKeyMultibase
                )
            } else null
        } ?: emptyList()
    }

    /**
     * Parses verification method references (authentication, assertionMethod, etc.).
     */
    private fun parseVerificationMethodReferences(
        json: JsonObject,
        fieldName: String,
        parseVmId: (String) -> VerificationMethodId
    ): List<VerificationMethodId> {
        return json[fieldName]?.jsonArray?.mapNotNull { it.jsonPrimitive?.content?.let { parseVmId(it) } }
            ?: json[fieldName]?.jsonPrimitive?.content?.let { listOf(parseVmId(it)) }
            ?: emptyList()
    }

    /**
     * Parses service endpoints from a DID document JSON.
     */
    private fun parseServices(json: JsonObject): List<DidService> {
        return json["service"]?.jsonArray?.mapNotNull { sJson ->
            val sObj = sJson.jsonObject
            val sId = sObj["id"]?.jsonPrimitive?.content
            val sType = sObj["type"]?.jsonPrimitive?.content
            val sEndpoint = sObj["serviceEndpoint"]

            if (sId != null && sType != null && sEndpoint != null) {
                val endpoint = convertJsonElement(sEndpoint) ?: return@mapNotNull null
                DidService(
                    id = sId,
                    type = sType,
                    serviceEndpoint = endpoint
                )
            } else null
        } ?: emptyList()
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

