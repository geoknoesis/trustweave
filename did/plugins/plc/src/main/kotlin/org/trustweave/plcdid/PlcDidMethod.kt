package org.trustweave.plcdid

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.*
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.toServiceTypeJsonElement
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.base.AbstractDidMethod
import org.trustweave.did.base.DidMethodUtils
import org.trustweave.kms.KeyManagementService
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
            val algorithm = options.algorithm.algorithmName
            val keyHandle = generateKey(algorithm, options.additionalProperties)

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
                authentication = listOf(verificationMethod.id.value),
                assertionMethod = if (options.purposes.contains(KeyPurpose.ASSERTION)) {
                    listOf(verificationMethod.id.value)
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
            throw TrustWeaveException.Unknown(
                code = "CREATE_FAILED",
                message = "Failed to create did:plc: ${e.message}",
                cause = e
            )
        }
    }

    override suspend fun resolveDid(did: Did): DidResolutionResult = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)

            val didString = did.value
            // Resolve from PLC registry
            val url = "${config.plcRegistryUrl}/did/$didString"

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
                        method,
                        didString
                    )
                }

                throw TrustWeaveException.Unknown(
                    code = "RESOLVE_FAILED",
                    message = "Failed to resolve DID: HTTP ${response.code} ${response.message}"
                )
            }

            val body = response.body ?: throw TrustWeaveException.Unknown(
                code = "EMPTY_RESPONSE",
                message = "Empty response body"
            )
            val jsonString = body.string()

            // Parse JSON to DidDocument
            val json = Json.parseToJsonElement(jsonString)
            val document = jsonElementToDocument(json)

            // Validate DID matches - document.id is Did, so compare values
            if (document.id.value != didString) {
                // Rebuild with correct DID
                val correctedDocument = document.copy(id = did)
                storeDocument(correctedDocument.id.value, correctedDocument)
                return@withContext DidMethodUtils.createSuccessResolutionResult(correctedDocument, method)
            }

            storeDocument(document.id.value, document)
            DidMethodUtils.createSuccessResolutionResult(document, method)
        } catch (e: TrustWeaveException) {
            DidMethodUtils.createErrorResolutionResult(
                "invalidDid",
                e.message,
                method,
                did.value
            )
        } catch (e: Exception) {
            DidMethodUtils.createErrorResolutionResult(
                "invalidDid",
                e.message,
                method,
                did.value
            )
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
            val currentResult = resolveDid(did)
            val currentDocument = when (currentResult) {
                is DidResolutionResult.Success -> currentResult.document
                else -> throw TrustWeaveException.NotFound(
                    message = "DID document not found: $didString"
                )
            }

            // Apply updater (use explicit variable to avoid smart cast issue)
            val doc = currentDocument
            val updatedDocument = updater(doc)

            // Update in PLC registry
            if (config.plcRegistryUrl != null) {
                updateInPlcRegistry(updatedDocument)
            }

            // Store locally
            storeDocument(updatedDocument.id.value, updatedDocument)

            updatedDocument
        } catch (e: TrustWeaveException.NotFound) {
            throw e
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                code = "UPDATE_FAILED",
                message = "Failed to update did:plc: ${e.message}",
                cause = e
            )
        }
    }

    override suspend fun deactivateDid(did: Did): Boolean = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)

            val didString = did.value

            // Resolve current document
            val currentResult = resolveDid(did)
            val currentDocument = when (currentResult) {
                is DidResolutionResult.Success -> currentResult.document
                else -> return@withContext false
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
            documents.remove(didString)
            documentMetadata.remove(didString)

            true
        } catch (e: TrustWeaveException.NotFound) {
            false
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                code = "DEACTIVATE_FAILED",
                message = "Failed to deactivate did:plc: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Generates a PLC DID identifier.
     */
    private fun generatePlcDid(keyHandle: org.trustweave.kms.KeyHandle): String {
        // PLC DID format: did:plc:{base32-encoded-hash}
        // Simplified implementation - real implementation needs proper base32 encoding
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(keyHandle.id.value.toByteArray())
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
            throw TrustWeaveException.Unknown(
                code = "REGISTRY_REGISTER_FAILED",
                message = "Failed to register with PLC registry: HTTP ${response.code}: $errorBody"
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
            throw TrustWeaveException.Unknown(
                code = "REGISTRY_UPDATE_FAILED",
                message = "Failed to update in PLC registry: HTTP ${response.code}: $errorBody"
            )
        }
    }

}

