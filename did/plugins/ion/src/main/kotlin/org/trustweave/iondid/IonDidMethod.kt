package org.trustweave.iondid

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.*
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.model.DidService
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.base.AbstractDidMethod
import org.trustweave.did.base.DidMethodUtils
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.GenerateKeyResult
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
 * val kms = InMemoryKeyManagementDidService()
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
            val generateResult = kms.generateKey(algorithm, options.additionalProperties)
            val keyHandle = when (generateResult) {
                is GenerateKeyResult.Success -> generateResult.keyHandle
                is GenerateKeyResult.Failure.UnsupportedAlgorithm -> throw TrustWeaveException.Unknown(
                    code = "UNSUPPORTED_ALGORITHM",
                    message = generateResult.reason ?: "Algorithm not supported"
                )
                is GenerateKeyResult.Failure.InvalidOptions -> throw TrustWeaveException.Unknown(
                    code = "INVALID_OPTIONS",
                    message = generateResult.reason
                )
                is GenerateKeyResult.Failure.Error -> throw TrustWeaveException.Unknown(
                    code = "KEY_GENERATION_ERROR",
                    message = generateResult.reason,
                    cause = generateResult.cause
                )
            }

            // Create Sidetree create operation
            val createOperation = sidetreeClient.createCreateOperation(
                publicKeyJwk = keyHandle.publicKeyJwk
                    ?: throw TrustWeaveException.Unknown(
                        code = "MISSING_JWK",
                        message = "Public key JWK is required for did:ion"
                    )
            )

            // Submit operation to ION node
            val operationResponse = sidetreeClient.submitOperation(createOperation)

            // Extract DID from operation response
            val longFormDid = operationResponse.longFormDid
                ?: throw TrustWeaveException.Unknown(
                    code = "CREATE_FAILED",
                    message = "Failed to create did:ion: no DID in response"
                )

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
            throw TrustWeaveException.Unknown(
                code = "CREATE_FAILED",
                message = "Failed to create did:ion: ${e.message}",
                cause = e
            )
        }
    }

    override suspend fun resolveDid(did: Did): DidResolutionResult = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)

            val didString = did.value
            // Resolve through ION node
            val resolutionResult = sidetreeClient.resolveDid(didString)

            val document = when (resolutionResult) {
                is DidResolutionResult.Success -> resolutionResult.document
                else -> {
                    return@withContext DidMethodUtils.createErrorResolutionResult(
                        "notFound",
                        "DID not found in ION network",
                        method,
                        didString
                    )
                }
            }

            // Convert ION document to TrustWeave format
            val convertedDocument = convertIonDocument(document)

            // Store locally for caching
            storeDocument(convertedDocument.id.value, convertedDocument)

            DidMethodUtils.createSuccessResolutionResult(convertedDocument, method)
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

            // Apply updater
            val updatedDocument = updater(currentDocument)

            // Create Sidetree update operation
            val updateOperation = sidetreeClient.createUpdateOperation(
                did = didString,
                previousOperationHash = (currentResult as? DidResolutionResult.Success)?.documentMetadata?.versionId ?: "",
                updatedDocument = updatedDocument
            )

            // Submit update operation
            sidetreeClient.submitOperation(updateOperation)

            // Store updated document
            storeDocument(updatedDocument.id.value, updatedDocument)

            updatedDocument
        } catch (e: TrustWeaveException.NotFound) {
            throw e
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                code = "UPDATE_FAILED",
                message = "Failed to update did:ion: ${e.message}",
                cause = e
            )
        }
    }

    override suspend fun deactivateDid(did: Did): Boolean = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)

            val didString = did.value
            // Resolve current document to get operation hash
            val currentResult = resolveDid(did)
            val currentDocument = when (currentResult) {
                is DidResolutionResult.Success -> currentResult.document
                else -> return@withContext false
            }

            // Create Sidetree deactivate operation
            val deactivateOperation = sidetreeClient.createDeactivateOperation(
                did = didString,
                previousOperationHash = (currentResult as? DidResolutionResult.Success)?.documentMetadata?.versionId ?: ""
            )

            // Submit deactivate operation
            sidetreeClient.submitOperation(deactivateOperation)

            // Remove from local storage
            documents.remove(didString)
            documentMetadata.remove(didString)

            true
        } catch (e: TrustWeaveException.NotFound) {
            false
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                code = "DEACTIVATE_FAILED",
                message = "Failed to deactivate did:ion: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Builds a DID document from ION/Sidetree operation.
     */
    private fun buildIonDocument(
        did: String,
        keyHandle: org.trustweave.kms.KeyHandle,
        algorithm: KeyAlgorithm,
        purposes: List<KeyPurpose>
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
            authentication = listOf(verificationMethod.id.value),
            assertionMethod = if (purposes.contains(KeyPurpose.ASSERTION)) {
                listOf(verificationMethod.id.value)
            } else null
        )
    }

    /**
     * Converts ION document JSON to TrustWeave DidDocument.
     */
    private fun convertIonDocument(ionDocJson: JsonObject): DidDocument {
        val did = ionDocJson["id"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("Missing id in ION document")

        val didObj = Did(did)
        val verificationMethods = ionDocJson["verificationMethod"]?.jsonArray?.mapNotNull { vm ->
            val vmObj = vm.jsonObject
            val vmIdString = vmObj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            VerificationMethod(
                id = VerificationMethodId.parse(vmIdString, didObj),
                type = vmObj["type"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                controller = Did(vmObj["controller"]?.jsonPrimitive?.content ?: did),
                publicKeyJwk = vmObj["publicKeyJwk"]?.jsonObject?.let { jsonObjectToMap(it) },
                publicKeyMultibase = vmObj["publicKeyMultibase"]?.jsonPrimitive?.content
            )
        } ?: emptyList()

        val authentication = ionDocJson["authentication"]?.jsonArray?.mapNotNull {
            (it as? JsonPrimitive)?.content?.let { idStr -> VerificationMethodId.parse(idStr, didObj) }
        } ?: emptyList()

        val assertionMethod = ionDocJson["assertionMethod"]?.jsonArray?.mapNotNull {
            (it as? JsonPrimitive)?.content?.let { idStr -> VerificationMethodId.parse(idStr, didObj) }
        } ?: emptyList()

        val service = ionDocJson["service"]?.jsonArray?.mapNotNull { s ->
            val sObj = s.jsonObject
            DidService(
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
            id = didObj,
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

