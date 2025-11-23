package com.trustweave.iondid

import com.trustweave.core.NotFoundException
import com.trustweave.core.TrustWeaveException
import com.trustweave.did.*
import com.trustweave.did.base.AbstractDidMethod
import com.trustweave.did.base.DidMethodUtils
import com.trustweave.kms.KeyManagementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import java.net.URL

/**
 * Implementation of did:ion method using Sidetree protocol.
 * 
 * did:ion uses Microsoft ION (Identity Overlay Network) which is built on the Sidetree protocol:
 * - Format: `did:ion:{suffix}` (short-form) or `did:ion:{long-form}` (long-form)
 * - Operations anchored to Bitcoin blockchain
 * - Resolved through ION nodes
 * 
 * **Example Usage:**
 * ```kotlin
 * val kms = InMemoryKeyManagementService()
 * val config = IonDidConfig.testnet()
 * val method = IonDidMethod(kms, config)
 * 
 * // Create DID
 * val options = didCreationOptions {
 *     algorithm = KeyAlgorithm.ED25519
 * }
 * val document = method.createDid(options)
 * 
 * // Resolve DID (short-form)
 * val result = method.resolveDid("did:ion:EiA2...")
 * 
 * // Resolve DID (long-form, for newly created DIDs)
 * val longFormDid = document.id // Contains long-form DID
 * val result2 = method.resolveDid(longFormDid)
 * ```
 * 
 * @see <a href="https://identity.foundation/ion/">ION Specification</a>
 * @see <a href="https://identity.foundation/sidetree/spec/">Sidetree Protocol</a>
 */
class IonDidMethod(
    kms: KeyManagementService,
    private val config: IonDidConfig
) : AbstractDidMethod("ion", kms) {

    private val httpClient: OkHttpClient
    private val sidetreeClient: SidetreeClient

    init {
        // Create HTTP client with timeout
        httpClient = OkHttpClient.Builder()
            .connectTimeout(config.timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(config.timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(config.timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
            .build()
        
        // Create Sidetree client
        sidetreeClient = SidetreeClient(httpClient, config)
    }

    override suspend fun createDid(options: DidCreationOptions): DidDocument = withContext(Dispatchers.IO) {
        try {
            // Generate keys using KMS
            val algorithm = options.algorithm.algorithmName
            val keyHandle = kms.generateKey(algorithm, options.additionalProperties)
            
            // Create Sidetree create operation
            val createOperation = sidetreeClient.createCreateOperation(
                publicKeyJwk = keyHandle.publicKeyJwk
                    ?: throw TrustWeaveException("Public key JWK is required for did:ion")
            )
            
            // Submit operation to ION node
            val operationResponse = sidetreeClient.submitOperation(createOperation)
            
            // Extract DID from operation response
            val longFormDid = operationResponse.longFormDid
                ?: throw TrustWeaveException("Failed to create did:ion: no DID in response")
            
            // Create DID document from operation
            val document = buildIonDocument(
                did = longFormDid,
                keyHandle = keyHandle,
                algorithm = options.algorithm,
                purposes = options.purposes
            )
            
            // Store locally
            storeDocument(document.id, document)
            
            document
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException(
                "Failed to create did:ion: ${e.message}",
                e
            )
        }
    }

    override suspend fun resolveDid(did: String): DidResolutionResult = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)
            
            // Resolve through ION node
            val resolutionResult = sidetreeClient.resolveDid(did)
            
            if (resolutionResult.document == null) {
                return@withContext DidMethodUtils.createErrorResolutionResult(
                    "notFound",
                    "DID not found in ION network",
                    method
                )
            }
            
            // Convert ION document to TrustWeave format
            val document = convertIonDocument(resolutionResult.document)
            
            // Store locally for caching
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
            
            // Apply updater
            val updatedDocument = updater(currentDocument)
            
            // Create Sidetree update operation
            val updateOperation = sidetreeClient.createUpdateOperation(
                did = did,
                previousOperationHash = currentResult.documentMetadata.versionId ?: "",
                updatedDocument = updatedDocument
            )
            
            // Submit update operation
            sidetreeClient.submitOperation(updateOperation)
            
            // Store updated document
            storeDocument(updatedDocument.id, updatedDocument)
            
            updatedDocument
        } catch (e: NotFoundException) {
            throw e
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException(
                "Failed to update did:ion: ${e.message}",
                e
            )
        }
    }

    override suspend fun deactivateDid(did: String): Boolean = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)
            
            // Resolve current document to get operation hash
            val currentResult = resolveDid(did)
            if (currentResult.document == null) {
                return@withContext false
            }
            
            // Create Sidetree deactivate operation
            val deactivateOperation = sidetreeClient.createDeactivateOperation(
                did = did,
                previousOperationHash = currentResult.documentMetadata.versionId ?: ""
            )
            
            // Submit deactivate operation
            sidetreeClient.submitOperation(deactivateOperation)
            
            // Remove from local storage
            documents.remove(did)
            documentMetadata.remove(did)
            
            true
        } catch (e: NotFoundException) {
            false
        } catch (e: Exception) {
            throw TrustWeaveException(
                "Failed to deactivate did:ion: ${e.message}",
                e
            )
        }
    }

    /**
     * Builds a DID document from ION/Sidetree operation.
     */
    private fun buildIonDocument(
        did: String,
        keyHandle: com.trustweave.kms.KeyHandle,
        algorithm: DidCreationOptions.KeyAlgorithm,
        purposes: List<DidCreationOptions.KeyPurpose>
    ): DidDocument {
        // Create verification method
        val verificationMethod = DidMethodUtils.createVerificationMethod(
            did = did,
            keyHandle = keyHandle,
            algorithm = algorithm
        )
        
        return DidMethodUtils.buildDidDocument(
            did = did,
            verificationMethod = listOf(verificationMethod),
            authentication = listOf(verificationMethod.id),
            assertionMethod = if (purposes.contains(DidCreationOptions.KeyPurpose.ASSERTION)) {
                listOf(verificationMethod.id)
            } else null
        )
    }

    /**
     * Converts ION document JSON to TrustWeave DidDocument.
     */
    private fun convertIonDocument(ionDocJson: JsonObject): DidDocument {
        val did = ionDocJson["id"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing id in ION document")
        
        val verificationMethods = ionDocJson["verificationMethod"]?.jsonArray?.mapNotNull { vm ->
            val vmObj = vm.jsonObject
            VerificationMethodRef(
                id = vmObj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                type = vmObj["type"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                controller = vmObj["controller"]?.jsonPrimitive?.content ?: did,
                publicKeyJwk = vmObj["publicKeyJwk"]?.jsonObject?.let { jsonObjectToMap(it) },
                publicKeyMultibase = vmObj["publicKeyMultibase"]?.jsonPrimitive?.content
            )
        } ?: emptyList()
        
        val authentication = ionDocJson["authentication"]?.jsonArray?.mapNotNull { 
            (it as? JsonPrimitive)?.content 
        } ?: emptyList()
        
        val assertionMethod = ionDocJson["assertionMethod"]?.jsonArray?.mapNotNull { 
            (it as? JsonPrimitive)?.content 
        } ?: emptyList()
        
        val service = ionDocJson["service"]?.jsonArray?.mapNotNull { s ->
            val sObj = s.jsonObject
            Service(
                id = sObj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                type = sObj["type"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                serviceEndpoint = sObj["serviceEndpoint"]?.let {
                    when (it) {
                        is JsonPrimitive -> it.content
                        else -> it.toString()
                    }
                } ?: return@mapNotNull null
            )
        } ?: emptyList()
        
        return DidDocument(
            id = did,
            verificationMethod = verificationMethods,
            authentication = authentication,
            assertionMethod = assertionMethod,
            service = service
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
}

