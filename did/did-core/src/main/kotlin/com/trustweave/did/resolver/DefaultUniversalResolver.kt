package com.trustweave.did.resolver

import com.trustweave.core.identifiers.KeyId
import com.trustweave.did.identifiers.Did
import com.trustweave.did.identifiers.VerificationMethodId
import com.trustweave.did.model.DidDocument
import com.trustweave.did.model.DidDocumentMetadata
import com.trustweave.did.model.DidService
import com.trustweave.did.model.VerificationMethod
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
        try {
            // Use protocol adapter to build URL
            val url = protocolAdapter.buildResolveUrl(baseUrl, did)

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
                    throw com.trustweave.core.exception.TrustWeaveException.Unknown(
                        message = "Failed to resolve DID $did: HTTP ${response.statusCode()}",
                        context = mapOf(
                            "did" to did,
                            "statusCode" to response.statusCode(),
                            "provider" to protocolAdapter.providerName
                        )
                    )
                }
            }
        } catch (e: Exception) {
            throw com.trustweave.core.exception.TrustWeaveException.Unknown(
                message = "Failed to resolve DID $did: ${e.message ?: "Unknown error"}",
                context = mapOf(
                    "did" to did,
                    "provider" to protocolAdapter.providerName
                ),
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
     */
    private fun parseDidDocumentFromJson(json: JsonObject): DidDocument {
        val id = json["id"]?.jsonPrimitive?.content
            ?: throw com.trustweave.did.exception.DidException.InvalidDidFormat(
                did = "unknown",
                reason = "DID document missing 'id' field"
            )

        // Extract @context (can be string or array in JSON-LD)
        val context = when {
            json["@context"] != null -> {
                when (val ctx = json["@context"]) {
                    is JsonPrimitive -> listOf(ctx.content)
                    is JsonArray -> ctx.mapNotNull { it.jsonPrimitive?.content }
                    else -> listOf("https://www.w3.org/ns/did/v1")
                }
            }
            else -> listOf("https://www.w3.org/ns/did/v1")
        }

        // Helper function to parse verification method ID
        fun parseVmId(vmIdString: String): VerificationMethodId {
            return try {
                VerificationMethodId.parse(vmIdString, Did(id))
            } catch (e: Exception) {
                // Fallback: try to parse manually
                val (didPart, keyPart) = if (vmIdString.contains("#")) {
                    val parts = vmIdString.split("#", limit = 2)
                    parts[0] to parts[1]
                } else {
                    id to vmIdString
                }
                VerificationMethodId(Did(didPart), KeyId(keyPart))
            }
        }

        // Extract verification methods
        val verificationMethod = json["verificationMethod"]?.jsonArray?.mapNotNull { vmJson ->
            val vmObj = vmJson.jsonObject
            val vmId = vmObj["id"]?.jsonPrimitive?.content
            val vmType = vmObj["type"]?.jsonPrimitive?.content
            val controller = vmObj["controller"]?.jsonPrimitive?.content ?: id
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

        // Extract authentication references
        val authentication = json["authentication"]?.jsonArray?.mapNotNull { it.jsonPrimitive?.content?.let { parseVmId(it) } }
            ?: json["authentication"]?.jsonPrimitive?.content?.let { listOf(parseVmId(it)) }
            ?: emptyList()

        // Extract assertion method references
        val assertionMethod = json["assertionMethod"]?.jsonArray?.mapNotNull { it.jsonPrimitive?.content?.let { parseVmId(it) } }
            ?: json["assertionMethod"]?.jsonPrimitive?.content?.let { listOf(parseVmId(it)) }
            ?: emptyList()

        // Extract key agreement references
        val keyAgreement = json["keyAgreement"]?.jsonArray?.mapNotNull { it.jsonPrimitive?.content?.let { parseVmId(it) } }
            ?: json["keyAgreement"]?.jsonPrimitive?.content?.let { listOf(parseVmId(it)) }
            ?: emptyList()

        // Extract capability invocation references
        val capabilityInvocation = json["capabilityInvocation"]?.jsonArray?.mapNotNull { it.jsonPrimitive?.content?.let { parseVmId(it) } }
            ?: json["capabilityInvocation"]?.jsonPrimitive?.content?.let { listOf(parseVmId(it)) }
            ?: emptyList()

        // Extract capability delegation references
        val capabilityDelegation = json["capabilityDelegation"]?.jsonArray?.mapNotNull { it.jsonPrimitive?.content?.let { parseVmId(it) } }
            ?: json["capabilityDelegation"]?.jsonPrimitive?.content?.let { listOf(parseVmId(it)) }
            ?: emptyList()

        // Extract services
        val service = json["service"]?.jsonArray?.mapNotNull { sJson ->
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

