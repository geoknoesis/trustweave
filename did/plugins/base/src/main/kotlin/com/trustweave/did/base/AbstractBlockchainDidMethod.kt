package com.trustweave.did.base

import com.trustweave.anchor.BlockchainAnchorClient
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

/**
 * Abstract base class for blockchain-based DID method implementations.
 *
 * Provides common functionality for DID methods that store documents on blockchain:
 * - Integration with BlockchainAnchorClient for on-chain storage
 * - Document anchoring to blockchain
 * - Document resolution from blockchain
 * - Fallback to in-memory storage for testing
 *
 * Subclasses should implement:
 * - [createDid]: Create a new DID and anchor its document
 * - [resolveDid]: Resolve DID from blockchain
 * - [getBlockchainAnchorClient]: Provide the blockchain anchor client
 * - [getChainId]: Provide the blockchain chain ID
 *
 * Pattern: Reuses existing blockchain anchoring infrastructure.
 *
 * **Example Usage:**
 * ```kotlin
 * class EthrDidMethod(
 *     kms: KeyManagementService,
 *     private val anchorClient: BlockchainAnchorClient,
 *     private val chainId: String = "eip155:1"
 * ) : AbstractBlockchainDidMethod("ethr", kms) {
 *
 *     override fun getBlockchainAnchorClient(): BlockchainAnchorClient = anchorClient
 *
 *     override fun getChainId(): String = chainId
 *
 *     override suspend fun createDid(options: DidCreationOptions): DidDocument {
 *         // Create DID document
 *         val document = createDocument(options)
 *
 *         // Anchor to blockchain
 *         anchorDocument(document)
 *
 *         return document
 *     }
 *
 *     override suspend fun resolveDid(did: String): DidResolutionResult {
 *         // Resolve from blockchain
 *         return resolveFromBlockchain(did)
 *     }
 * }
 * ```
 */
abstract class AbstractBlockchainDidMethod(
    method: String,
    kms: KeyManagementService
) : AbstractDidMethod(method, kms) {

    /**
     * Gets the blockchain anchor client for this method.
     *
     * @return BlockchainAnchorClient instance
     */
    protected abstract fun getBlockchainAnchorClient(): BlockchainAnchorClient

    /**
     * Gets the blockchain chain ID for this method.
     *
     * @return Chain ID (e.g., "eip155:1" for Ethereum mainnet)
     */
    protected abstract fun getChainId(): String

    /**
     * Checks if this method can submit transactions to the blockchain.
     *
     * Default implementation checks if the anchor client can submit transactions.
     * Subclasses can override for custom logic.
     *
     * @return true if transactions can be submitted
     */
    protected open suspend fun canSubmitTransaction(): Boolean {
        // Check if anchor client supports transaction submission
        // This is implementation-dependent, subclasses should override
        return true
    }

    /**
     * Anchors a DID document to the blockchain.
     *
     * Uses the blockchain anchor client to store the document.
     *
     * @param document The DID document to anchor
     * @return Transaction hash or anchor reference
     * @throws TrustWeaveException if anchoring fails
     */
    protected suspend fun anchorDocument(document: DidDocument): String = withContext(Dispatchers.IO) {
        try {
            val anchorClient = getBlockchainAnchorClient()

            // Convert document to JsonElement
            val payload = documentToJsonElement(document)

            // Anchor to blockchain
            val result = anchorClient.writePayload(payload, "application/json")

            // Store locally for fallback
            storeDocument(document.id, document)

            // Return transaction hash
            result.ref.txHash
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException(
                code = "DID_ANCHOR_FAILED",
                message = "Failed to anchor DID document to blockchain: ${e.message}",
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
     * Converts a verification method to JsonObject.
     */
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

    /**
     * Converts a service to JsonObject.
     */
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

    /**
     * Converts a Map to JsonObject.
     */
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
     * Resolves a DID document from the blockchain.
     *
     * @param did The DID to resolve
     * @param txHash Optional transaction hash (if known)
     * @return DidResolutionResult
     * @throws NotFoundException if document not found
     */
    protected suspend fun resolveFromBlockchain(did: String, txHash: String? = null): DidResolutionResult =
        withContext(Dispatchers.IO) {
            validateDidFormat(did)

            try {
                val anchorClient = getBlockchainAnchorClient()
                val chainId = getChainId()

                // If txHash is provided, use it directly
                val hash = txHash ?: findDocumentTxHash(did)

                if (hash == null) {
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

                    throw TrustWeaveException.NotFound(
                        resource = "DID document: $did"
                    )
                }

                // Read from blockchain
                val anchorRef = com.trustweave.anchor.AnchorRef(
                    chainId = chainId,
                    txHash = hash
                )

                val result = anchorClient.readPayload(anchorRef)

                // Convert JsonElement to DidDocument
                val document = jsonElementToDocument(result.payload)

                // Store locally for caching
                storeDocument(document.id, document)

                com.trustweave.did.base.DidMethodUtils.createSuccessResolutionResult(document, method)
            } catch (e: TrustWeaveException.NotFound) {
                throw e
            } catch (e: TrustWeaveException) {
                throw e
            } catch (e: Exception) {
                // Try fallback to stored document
                val stored = getStoredDocument(did)
                if (stored != null) {
                    return@withContext DidMethodUtils.createSuccessResolutionResult(
                        stored,
                        method,
                        getDocumentMetadata(did)?.created,
                        getDocumentMetadata(did)?.updated
                    )
                }

                throw TrustWeaveException(
                    code = "DID_RESOLUTION_FAILED",
                    message = "Failed to resolve DID from blockchain: ${e.message}",
                    cause = e
                )
            }
        }

    /**
     * Finds the transaction hash for a DID document.
     *
     * This is method-specific - some methods store a mapping of DID to txHash,
     * others derive it from the DID itself.
     *
     * Subclasses should override this to provide method-specific lookup.
     *
     * @param did The DID to find
     * @return Transaction hash or null if not found
     */
    protected open suspend fun findDocumentTxHash(did: String): String? {
        // Default implementation - subclasses should override
        return null
    }

    /**
     * Updates a DID document on the blockchain.
     *
     * @param did The DID to update
     * @param document The updated document
     * @return Transaction hash
     */
    protected suspend fun updateDocumentOnBlockchain(did: String, document: DidDocument): String {
        validateDidFormat(did)

        // Anchor updated document
        val txHash = anchorDocument(document)

        // Update local storage
        val now = java.time.Instant.now()
        documentMetadata[did] = (documentMetadata[did] ?: DidDocumentMetadata(created = now))
            .copy(updated = now)

        return txHash
    }

    /**
     * Deactivates a DID document on the blockchain.
     *
     * @param did The DID to deactivate
     * @param deactivatedDocument The deactivated document (with deactivated flag)
     * @return true if successful
     */
    protected suspend fun deactivateDocumentOnBlockchain(
        did: String,
        deactivatedDocument: DidDocument
    ): Boolean {
        validateDidFormat(did)

        try {
            // Anchor deactivated document
            anchorDocument(deactivatedDocument)

            // Remove from local storage
            documents.remove(did)
            documentMetadata.remove(did)

            return true
        } catch (e: Exception) {
            throw TrustWeaveException(
                code = "DID_DEACTIVATION_FAILED",
                message = "Failed to deactivate DID on blockchain: ${e.message}",
                cause = e
            )
        }
    }
}

