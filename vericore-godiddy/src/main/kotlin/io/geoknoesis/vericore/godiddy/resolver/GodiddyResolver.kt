package io.geoknoesis.vericore.godiddy.resolver

import io.geoknoesis.vericore.core.VeriCoreException
import io.geoknoesis.vericore.did.DidDocument
import io.geoknoesis.vericore.did.DidResolutionResult
import io.geoknoesis.vericore.godiddy.GodiddyClient
import io.geoknoesis.vericore.godiddy.models.GodiddyResolutionResponse
import io.ktor.client.call.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/**
 * Client for godiddy Universal Resolver service.
 */
class GodiddyResolver(
    private val client: GodiddyClient
) {
    /**
     * Resolves a DID using Universal Resolver.
     *
     * @param did The DID to resolve
     * @return DidResolutionResult containing the resolved document
     */
    suspend fun resolveDid(did: String): DidResolutionResult = withContext(Dispatchers.IO) {
        try {
            // Universal Resolver endpoint: GET /1.0.0/universal-resolver/identifiers/{did}
            val path = "/1.0.0/universal-resolver/identifiers/$did"
            val response = client.getJson(path)
            val status = response.status
            
            if (status == HttpStatusCode.NotFound) {
                return@withContext DidResolutionResult(
                    document = null,
                    documentMetadata = emptyMap(),
                    resolutionMetadata = mapOf(
                        "error" to "notFound",
                        "provider" to "godiddy"
                    )
                )
            }
            
            if (!status.isSuccess()) {
                throw VeriCoreException("Failed to resolve DID $did: HTTP $status")
            }
            
            val jsonResponse: JsonObject = response.body()
            
            // Universal Resolver returns the DID document directly or wrapped
            // Check if it's a wrapped response or direct document
            val didDocumentJson = jsonResponse["didDocument"]?.jsonObject ?: jsonResponse
            val didDocumentMetadata = jsonResponse["didDocumentMetadata"]?.jsonObject
            val didResolutionMetadata = jsonResponse["didResolutionMetadata"]?.jsonObject
            
            // Convert godiddy response to VeriCore DidResolutionResult
            val document = try {
                convertToDidDocument(didDocumentJson)
            } catch (e: Exception) {
                null // Document might be missing or invalid
            }
            
            val documentMetadata = didDocumentMetadata?.entries?.associate { it.key to convertJsonElement(it.value) } ?: emptyMap()
            
            val resolutionMetadata = (didResolutionMetadata?.entries?.associate { it.key to convertJsonElement(it.value) } ?: emptyMap())
                .plus("provider" to "godiddy")
            
            DidResolutionResult(
                document = document,
                documentMetadata = documentMetadata,
                resolutionMetadata = resolutionMetadata
            )
        } catch (e: VeriCoreException) {
            throw e
        } catch (e: Exception) {
            throw VeriCoreException("Failed to resolve DID $did: ${e.message}", e)
        }
    }
    
    /**
     * Converts JsonObject to DidDocument.
     */
    private fun convertToDidDocument(json: JsonObject): DidDocument {
        // This is a simplified conversion - in practice, you'd need full JSON-LD parsing
        // For now, we'll extract basic fields
        val id = json["id"]?.jsonPrimitive?.content ?: throw VeriCoreException("DID document missing 'id' field")
        
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
                io.geoknoesis.vericore.did.VerificationMethodRef(
                    id = vmId,
                    type = vmType,
                    controller = controller,
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
        
        // Extract services
        val service = json["service"]?.jsonArray?.mapNotNull { sJson ->
            val sObj = sJson.jsonObject
            val sId = sObj["id"]?.jsonPrimitive?.content
            val sType = sObj["type"]?.jsonPrimitive?.content
            val sEndpoint = sObj["serviceEndpoint"]
            
            if (sId != null && sType != null && sEndpoint != null) {
                val endpoint = convertJsonElement(sEndpoint) ?: return@mapNotNull null
                io.geoknoesis.vericore.did.Service(
                    id = sId,
                    type = sType,
                    serviceEndpoint = endpoint
                )
            } else null
        } ?: emptyList()
        
        return DidDocument(
            id = id,
            verificationMethod = verificationMethod,
            authentication = authentication,
            assertionMethod = assertionMethod,
            service = service
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
}

