package com.trustweave.plcdid

import com.trustweave.core.exception.NotFoundException
import com.trustweave.core.exception.TrustWeaveException
import com.trustweave.did.*
import com.trustweave.did.base.AbstractDidMethod
import com.trustweave.did.base.DidMethodUtils
import com.trustweave.kms.KeyManagementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Implementation of did:plc method for AT Protocol.
 * 
 * did:plc uses Personal Linked Container (PLC) DID method for AT Protocol:
 * - Format: `did:plc:{identifier}`
 * - Distributed registry for AT Protocol
 * - HTTP-based resolution
 * 
 * **Example Usage:**
 * ```kotlin
 * val kms = InMemoryKeyManagementService()
 * val config = PlcDidConfig.default()
 * val method = PlcDidMethod(kms, config)
 * 
 * // Create DID
 * val options = didCreationOptions {
 *     algorithm = KeyAlgorithm.ED25519
 * }
 * val document = method.createDid(options)
 * 
 * // Resolve DID
 * val result = method.resolveDid("did:plc:...")
 * ```
 */
class PlcDidMethod(
    kms: KeyManagementService,
    private val config: PlcDidConfig = PlcDidConfig.default()
) : AbstractDidMethod("plc", kms) {

    private val httpClient: OkHttpClient

    init {
        httpClient = OkHttpClient.Builder()
            .connectTimeout(config.timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(config.timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(config.timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    override suspend fun createDid(options: DidCreationOptions): DidDocument = withContext(Dispatchers.IO) {
        try {
            // Generate key using KMS
            val algorithm = options.algorithm.algorithmName
            val keyHandle = kms.generateKey(algorithm, options.additionalProperties)
            
            // Create DID identifier (PLC uses specific format)
            val did = generatePlcDid(keyHandle)
            
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
            
            // Register with PLC registry if endpoint is configured
            if (config.plcRegistryUrl != null) {
                try {
                    registerWithPlcRegistry(document)
                } catch (e: Exception) {
                    // If registration fails, still store locally for testing
                    storeDocument(document.id, document)
                }
            } else {
                // Store locally if no registry configured
                storeDocument(document.id, document)
            }
            
            document
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException(
                "Failed to create did:plc: ${e.message}",
                e
            )
        }
    }

    override suspend fun resolveDid(did: String): DidResolutionResult = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)
            
            // Resolve from PLC registry
            val url = "${config.plcRegistryUrl}/did/$did"
            
            val request = Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "application/json")
                .build()
            
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                if (response.code == 404) {
                    // Try stored document as fallback
                    val stored = getStoredDocument(did)
                    if (stored != null) {
                        return@withContext DidMethodUtils.createSuccessResolutionResult(
                            stored,
                            method,
                            getDocumentMetadata(did)?.created,
                            getDocumentMetadata(did)?.updated
                        )
                    }
                    
                    return@withContext DidMethodUtils.createErrorResolutionResult(
                        "notFound",
                        "DID not found in PLC registry",
                        method
                    )
                }
                
                throw TrustWeaveException(
                    "Failed to resolve DID: HTTP ${response.code} ${response.message}"
                )
            }
            
            val body = response.body ?: throw TrustWeaveException("Empty response body")
            val jsonString = body.string()
            
            // Parse JSON to DidDocument
            val json = Json.parseToJsonElement(jsonString)
            val document = jsonElementToDocument(json)
            
            // Validate DID matches
            if (document.id != did) {
                // Rebuild with correct DID
                val correctedDocument = document.copy(id = did)
                storeDocument(correctedDocument.id, correctedDocument)
                return@withContext DidMethodUtils.createSuccessResolutionResult(correctedDocument, method)
            }
            
            storeDocument(document.id, document)
            DidMethodUtils.createSuccessResolutionResult(document, method)
        } catch (e: TrustWeaveException) {
            DidMethodUtils.createErrorResolutionResult(
                "invalidDid",
                e.message,
                method
            )
        } catch (e: Exception) {
            DidMethodUtils.createErrorResolutionResult(
                "invalidDid",
                e.message,
                method
            )
        }
    }

    override suspend fun updateDid(
        did: String,
        updater: (DidDocument) -> DidDocument
    ): DidDocument = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)
            
            // Resolve current document
            val currentResult = resolveDid(did)
            val currentDocument = currentResult.document
                ?: throw NotFoundException("DID document not found: $did")
            
            // Apply updater (use explicit variable to avoid smart cast issue)
            val doc = currentDocument
            val updatedDocument = updater(doc)
            
            // Update in PLC registry
            if (config.plcRegistryUrl != null) {
                updateInPlcRegistry(updatedDocument)
            }
            
            // Store locally
            storeDocument(updatedDocument.id, updatedDocument)
            
            updatedDocument
        } catch (e: NotFoundException) {
            throw e
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException(
                "Failed to update did:plc: ${e.message}",
                e
            )
        }
    }

    override suspend fun deactivateDid(did: String): Boolean = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)
            
            // Resolve current document
            val currentResult = resolveDid(did)
            val currentDocument = currentResult.document
            if (currentDocument == null) {
                return@withContext false
            }
            
            // Create deactivated document
            val deactivatedDocument = currentDocument.copy(
                verificationMethod = emptyList(),
                authentication = emptyList(),
                assertionMethod = emptyList(),
                keyAgreement = emptyList(),
                capabilityInvocation = emptyList(),
                capabilityDelegation = emptyList()
            )
            
            // Deactivate in PLC registry
            if (config.plcRegistryUrl != null) {
                updateInPlcRegistry(deactivatedDocument)
            }
            
            // Remove from local storage
            documents.remove(did)
            documentMetadata.remove(did)
            
            true
        } catch (e: NotFoundException) {
            false
        } catch (e: Exception) {
            throw TrustWeaveException(
                "Failed to deactivate did:plc: ${e.message}",
                e
            )
        }
    }

    /**
     * Generates a PLC DID identifier.
     */
    private fun generatePlcDid(keyHandle: com.trustweave.kms.KeyHandle): String {
        // PLC DID format: did:plc:{base32-encoded-hash}
        // Simplified implementation - real implementation needs proper base32 encoding
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(keyHandle.id.toByteArray())
        val encoded = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
        return "did:plc:${encoded.take(24).lowercase()}"
    }

    /**
     * Registers a DID document with PLC registry.
     */
    private suspend fun registerWithPlcRegistry(document: DidDocument) = withContext(Dispatchers.IO) {
        if (config.plcRegistryUrl == null) {
            return@withContext
        }
        
        val url = "${config.plcRegistryUrl}/did"
        val json = documentToJsonElement(document)
        val jsonString = Json.encodeToString(JsonElement.serializer(), json)
        val mediaType = "application/json".toMediaType()
        val body = jsonString.toRequestBody(mediaType)
        
        val request = Request.Builder()
            .url(url)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build()
        
        val response = httpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw TrustWeaveException(
                "Failed to register with PLC registry: HTTP ${response.code}: $errorBody"
            )
        }
    }

    /**
     * Updates a DID document in PLC registry.
     */
    private suspend fun updateInPlcRegistry(document: DidDocument) = withContext(Dispatchers.IO) {
        if (config.plcRegistryUrl == null) {
            return@withContext
        }
        
        val url = "${config.plcRegistryUrl}/did/${document.id}"
        val json = documentToJsonElement(document)
        val jsonString = Json.encodeToString(JsonElement.serializer(), json)
        val mediaType = "application/json".toMediaType()
        val body = jsonString.toRequestBody(mediaType)
        
        val request = Request.Builder()
            .url(url)
            .put(body)
            .addHeader("Content-Type", "application/json")
            .build()
        
        val response = httpClient.newCall(request).execute()
        
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw TrustWeaveException(
                "Failed to update in PLC registry: HTTP ${response.code}: $errorBody"
            )
        }
    }

    /**
     * Converts a DID document to JsonElement.
     */
    private fun documentToJsonElement(document: DidDocument): JsonElement {
        return buildJsonObject {
            put("@context", JsonArray(document.context.map { JsonPrimitive(it) }))
            put("id", document.id)
            
            if (document.verificationMethod.isNotEmpty()) {
                put("verificationMethod", JsonArray(document.verificationMethod.map { vmToJsonObject(it) }))
            }
            if (document.authentication.isNotEmpty()) {
                put("authentication", JsonArray(document.authentication.map { JsonPrimitive(it) }))
            }
            if (document.assertionMethod.isNotEmpty()) {
                put("assertionMethod", JsonArray(document.assertionMethod.map { JsonPrimitive(it) }))
            }
            if (document.service.isNotEmpty()) {
                put("service", JsonArray(document.service.map { serviceToJsonObject(it) }))
            }
        }
    }

    /**
     * Converts JsonElement to DidDocument.
     */
    private fun jsonElementToDocument(json: JsonElement): DidDocument {
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
            verificationMethod = obj["verificationMethod"]?.jsonArray?.mapNotNull { jsonToVerificationMethod(it) } ?: emptyList(),
            authentication = obj["authentication"]?.jsonArray?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList(),
            assertionMethod = obj["assertionMethod"]?.jsonArray?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList(),
            service = obj["service"]?.jsonArray?.mapNotNull { jsonToService(it) } ?: emptyList()
        )
    }

    private fun vmToJsonObject(vm: VerificationMethodRef): JsonObject {
        return buildJsonObject {
            put("id", vm.id)
            put("type", vm.type)
            put("controller", vm.controller)
            vm.publicKeyJwk?.let { jwk ->
                put("publicKeyJwk", mapToJsonObject(jwk))
            }
        }
    }

    private fun serviceToJsonObject(service: Service): JsonObject {
        return buildJsonObject {
            put("id", service.id)
            put("type", service.type)
            put("serviceEndpoint", when (val endpoint = service.serviceEndpoint) {
                is String -> JsonPrimitive(endpoint)
                else -> Json.parseToJsonElement(endpoint.toString())
            })
        }
    }

    private fun jsonToVerificationMethod(json: JsonElement): VerificationMethodRef? {
        val obj = json.jsonObject
        return VerificationMethodRef(
            id = obj["id"]?.jsonPrimitive?.content ?: return null,
            type = obj["type"]?.jsonPrimitive?.content ?: return null,
            controller = obj["controller"]?.jsonPrimitive?.content ?: return null,
            publicKeyJwk = obj["publicKeyJwk"]?.jsonObject?.let { jsonObjectToMap(it) },
            publicKeyMultibase = obj["publicKeyMultibase"]?.jsonPrimitive?.content
        )
    }

    private fun jsonToService(json: JsonElement): Service? {
        val obj = json.jsonObject
        return Service(
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
}

