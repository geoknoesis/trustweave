package org.trustweave.godiddy.resolver

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.model.DidService
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.resolver.UniversalResolver
import org.trustweave.godiddy.GodiddyClient
import org.trustweave.godiddy.models.GodiddyResolutionResponse
import io.ktor.client.call.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock

/**
 * GoDiddy implementation of Universal Resolver.
 *
 * This class implements the [UniversalResolver] interface using GoDiddy's
 * hosted Universal Resolver service. GoDiddy provides access to 20+ DID methods
 * through a standard Universal Resolver HTTP API.
 *
 * **Example Usage**:
 * ```kotlin
 * val config = GodiddyConfig(baseUrl = "https://api.godiddy.com")
 * val client = GodiddyClient(config)
 * val resolver: UniversalResolver = GodiddyResolver(client)
 *
 * val result = resolver.resolveDid("did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
 * ```
 */
class GodiddyResolver(
    private val client: GodiddyClient
) : UniversalResolver {

    override val baseUrl: String
        get() = client.config.baseUrl
    override suspend fun resolveDid(did: String): DidResolutionResult = withContext(Dispatchers.IO) {
        try {
            // Universal Resolver endpoint: GET /1.0.0/universal-resolver/identifiers/{did}
            val path = "/1.0.0/universal-resolver/identifiers/$did"
            val response = client.getJson(path)
            val status = response.status

            if (status == HttpStatusCode.NotFound) {
                return@withContext DidResolutionResult.Failure.NotFound(
                    did = Did(did),
                    reason = "notFound",
                    resolutionMetadata = mapOf(
                        "error" to "notFound",
                        "provider" to "godiddy"
                    )
                )
            }

            if (!status.isSuccess()) {
                throw org.trustweave.core.exception.TrustWeaveException.Unknown(
                    message = "Failed to resolve DID $did: HTTP $status",
                    context = mapOf("did" to did, "status" to status.toString(), "provider" to "godiddy")
                )
            }

            val jsonResponse: JsonObject = response.body()

            // Universal Resolver returns the DID document directly or wrapped
            // Check if it's a wrapped response or direct document
            val didDocumentJson = jsonResponse["didDocument"]?.jsonObject ?: jsonResponse
            val didDocumentMetadata = jsonResponse["didDocumentMetadata"]?.jsonObject
            val didResolutionMetadata = jsonResponse["didResolutionMetadata"]?.jsonObject

            // Convert godiddy response to TrustWeave DidResolutionResult
            val document = try {
                convertToDidDocument(didDocumentJson)
            } catch (e: Exception) {
                null // Document might be missing or invalid
            }

            val documentMetadata = convertToDidDocumentMetadata(didDocumentMetadata)

            val resolutionMetadata = (didResolutionMetadata?.entries?.associate { it.key to convertJsonElement(it.value) } ?: emptyMap())
                .plus("provider" to "godiddy")

            if (document != null) {
                DidResolutionResult.Success(
                    document = document,
                    documentMetadata = documentMetadata,
                    resolutionMetadata = resolutionMetadata
                )
            } else {
                DidResolutionResult.Failure.NotFound(
                    did = Did(did),
                    reason = "Document conversion failed",
                    resolutionMetadata = resolutionMetadata
                )
            }
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: Exception) {
            throw org.trustweave.core.exception.TrustWeaveException.Unknown(
                message = "Failed to resolve DID $did: ${e.message ?: "Unknown error"}",
                context = mapOf("did" to did, "provider" to "godiddy"),
                cause = e
            )
        }
    }

    /**
     * Converts JsonObject to DidDocument.
     */
    private fun convertToDidDocument(json: JsonObject): DidDocument {
        // This is a simplified conversion - in practice, you'd need full JSON-LD parsing
        // For now, we'll extract basic fields
        val id = json["id"]?.jsonPrimitive?.content ?: throw org.trustweave.did.exception.DidException.InvalidDidFormat(
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
                val didObj = Did(id)
                VerificationMethod(
                    id = VerificationMethodId.parse(vmId, didObj),
                    type = vmType,
                    controller = Did(controller),
                    publicKeyJwk = publicKeyJwk,
                    publicKeyMultibase = publicKeyMultibase
                )
            } else null
        } ?: emptyList()

        // Extract authentication references
        val authentication = json["authentication"]?.jsonArray?.mapNotNull { it.jsonPrimitive?.content }
            ?: json["authentication"]?.jsonPrimitive?.content?.let { listOf(it) }
            ?: emptyList()

        // Extract assertion method references
        val assertionMethod = json["assertionMethod"]?.jsonArray?.mapNotNull { it.jsonPrimitive?.content }
            ?: json["assertionMethod"]?.jsonPrimitive?.content?.let { listOf(it) }
            ?: emptyList()

        // Extract key agreement references
        val keyAgreement = json["keyAgreement"]?.jsonArray?.mapNotNull { it.jsonPrimitive?.content }
            ?: json["keyAgreement"]?.jsonPrimitive?.content?.let { listOf(it) }
            ?: emptyList()

        // Extract capability invocation references
        val capabilityInvocation = json["capabilityInvocation"]?.jsonArray?.mapNotNull { it.jsonPrimitive?.content }
            ?: json["capabilityInvocation"]?.jsonPrimitive?.content?.let { listOf(it) }
            ?: emptyList()

        // Extract capability delegation references
        val capabilityDelegation = json["capabilityDelegation"]?.jsonArray?.mapNotNull { it.jsonPrimitive?.content }
            ?: json["capabilityDelegation"]?.jsonPrimitive?.content?.let { listOf(it) }
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

        val didObj = Did(id)
        return DidDocument(
            id = didObj,
            context = context,
            verificationMethod = verificationMethod,
            authentication = authentication.map { VerificationMethodId.parse(it, didObj) },
            assertionMethod = assertionMethod.map { VerificationMethodId.parse(it, didObj) },
            keyAgreement = keyAgreement.map { VerificationMethodId.parse(it, didObj) },
            capabilityInvocation = capabilityInvocation.map { VerificationMethodId.parse(it, didObj) },
            capabilityDelegation = capabilityDelegation.map { VerificationMethodId.parse(it, didObj) },
            service = service
        )
    }

    /**
     * Converts JsonObject to DidDocumentMetadata.
     */
    private fun convertToDidDocumentMetadata(json: JsonObject?): DidDocumentMetadata {
        if (json == null) return DidDocumentMetadata()

        val created = json["created"]?.jsonPrimitive?.content?.let {
            try { Instant.parse(it) } catch (e: Exception) { null }
        }
        val updated = json["updated"]?.jsonPrimitive?.content?.let {
            try { Instant.parse(it) } catch (e: Exception) { null }
        }
        val versionId = json["versionId"]?.jsonPrimitive?.content
        val nextUpdate = json["nextUpdate"]?.jsonPrimitive?.content?.let {
            try { Instant.parse(it) } catch (e: Exception) { null }
        }
        val canonicalId = json["canonicalId"]?.jsonPrimitive?.content
        val equivalentId = json["equivalentId"]?.jsonArray?.mapNotNull { it.jsonPrimitive?.content } ?: emptyList()

        return DidDocumentMetadata(
            created = created,
            updated = updated,
            versionId = versionId,
            nextUpdate = nextUpdate,
            canonicalId = canonicalId?.let { Did(it) },
            equivalentId = equivalentId.map { Did(it) }
        )
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

    override suspend fun getSupportedMethods(): List<String>? {
        // GoDiddy supports many methods, but doesn't expose a standard API endpoint
        // to query them. Return null to indicate this information is not available.
        // In practice, the supported methods are documented or can be discovered
        // through the DID method provider SPI.
        return null
    }
}

