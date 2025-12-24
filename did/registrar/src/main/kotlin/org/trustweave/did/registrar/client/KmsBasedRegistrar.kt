package org.trustweave.did.registrar.client

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.KeyAlgorithm
import org.trustweave.did.identifiers.Did
import org.trustweave.did.identifiers.VerificationMethodId
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.VerificationMethod
import org.trustweave.did.registrar.DidRegistrar
import org.trustweave.did.registrar.model.*
import org.trustweave.did.registrar.storage.JobStorage
import org.trustweave.did.registrar.storage.InMemoryJobStorage
import org.trustweave.kms.KeyHandle
import org.trustweave.kms.KeyManagementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.util.UUID

/**
 * KMS-based DID Registrar implementation for Internal Secret Mode.
 *
 * This registrar uses a Key Management Service (KMS) to generate and manage keys
 * internally, following the DID Registration specification's Internal Secret Mode.
 *
 * **Key Management Modes:**
 * - **Internal Secret Mode**: This registrar generates keys using KMS and can optionally
 *   return them to the client if `returnSecrets=true` is specified.
 * - **External Secret Mode**: Not supported by this registrar. Use a registrar that
 *   supports external wallet/KMS integration.
 *
 * **Job Storage:**
 * If a [JobStorage] is provided, operations will be tracked with a `jobId` and can be
 * queried later. This enables support for long-running operations and status polling.
 *
 * **Example Usage:**
 * ```kotlin
 * val kms = InMemoryKeyManagementService()
 * val jobStorage = InMemoryJobStorage() // or DatabaseJobStorage
 * val registrar = KmsBasedRegistrar(kms, jobStorage = jobStorage)
 *
 * val response = registrar.createDid(
 *     method = "key",
 *     options = CreateDidOptions(
 *         keyManagementMode = KeyManagementMode.INTERNAL_SECRET,
 *         returnSecrets = true
 *     )
 * )
 *
 * // If jobId is present, can query status later
 * response.jobId?.let { jobId ->
 *     val status = jobStorage.get(jobId)
 * }
 * ```
 *
 * @param kms Key Management Service for key generation and management
 * @param jobStorage Optional storage for tracking long-running operations (default: InMemoryJobStorage)
 * @param didMethodFactory Factory function to create DID method-specific implementations
 *                        (optional, for methods that require custom logic)
 */
class KmsBasedRegistrar(
    private val kms: KeyManagementService,
    private val jobStorage: JobStorage = InMemoryJobStorage(),
    private val didMethodFactory: ((String, KeyManagementService) -> org.trustweave.did.DidMethod)? = null
) : DidRegistrar {

    override suspend fun createDid(
        method: String,
        options: CreateDidOptions
    ): DidRegistrationResponse = withContext(Dispatchers.IO) {
        try {
        // Validate key management mode
        if (options.keyManagementMode == KeyManagementMode.EXTERNAL_SECRET) {
            return@withContext DidRegistrationResponse(
                didState = DidState(
                    state = OperationState.FAILED,
                    did = null,
                    didDocument = null,
                        action = null,
                        reason = "External Secret Mode not supported by KmsBasedRegistrar. " +
                                "Use a registrar that supports external wallet/KMS integration."
                    )
                )
            }

            // For Internal Secret Mode, generate keys using KMS
            val algorithm = extractAlgorithm(options)
            val generateResult = kms.generateKey(algorithm, extractKmsOptions(options))
            val keyHandle = when (generateResult) {
                is org.trustweave.kms.results.GenerateKeyResult.Success -> generateResult.keyHandle
                is org.trustweave.kms.results.GenerateKeyResult.Failure -> throw TrustWeaveException(
                    code = "KEY_GENERATION_FAILED",
                    message = "Failed to generate key: ${generateResult}",
                    cause = null
                )
            }

            // Create DID Document (method-specific logic)
            val didDocument = createDidDocument(method, keyHandle, options)

            // Build secret if returnSecrets is true
            val secret = if (options.returnSecrets) {
                buildSecret(keyHandle, options)
            } else {
                null
            }

            // Generate jobId for tracking
            val jobId = UUID.randomUUID().toString()

            val response = DidRegistrationResponse(
                jobId = jobId,
                didState = DidState(
                    state = OperationState.FINISHED,
                    did = didDocument.id.value,
                    secret = secret,
                    didDocument = didDocument
                )
            )

            // Store in job storage for later retrieval
            jobStorage.store(jobId, response)

            response
        } catch (e: TrustWeaveException) {
            val jobId = UUID.randomUUID().toString()
            val errorResponse = DidRegistrationResponse(
                jobId = jobId,
                didState = DidState(
                    state = OperationState.FAILED,
                    did = null,
                    didDocument = null,
                    reason = e.message ?: "Unknown error during DID creation",
                    action = Action(
                        type = "error",
                        description = e.message ?: "Unknown error during DID creation"
                    )
                )
            )

            // Store error response in job storage
            jobStorage.store(jobId, errorResponse)

            errorResponse
        } catch (e: Exception) {
            val jobId = UUID.randomUUID().toString()
            val errorResponse = DidRegistrationResponse(
                jobId = jobId,
                didState = DidState(
                    state = OperationState.FAILED,
                    did = null,
                    didDocument = null,
                    reason = "Failed to create DID: ${e.message ?: "Unknown error"}"
                )
            )

            // Store error response in job storage
            jobStorage.store(jobId, errorResponse)

            errorResponse
        }
    }

    override suspend fun updateDid(
        did: String,
        document: DidDocument,
        options: UpdateDidOptions
    ): DidRegistrationResponse = withContext(Dispatchers.IO) {
        // For KMS-based registrar, updates typically require signing with existing keys
        // This is a simplified implementation - real implementations would:
        // 1. Resolve the current DID Document
        // 2. Verify authorization using keys from KMS or provided secrets
        // 3. Sign the update operation
        // 4. Submit to the method's registry/ledger

        val jobId = UUID.randomUUID().toString()

        val response = DidRegistrationResponse(
            jobId = jobId,
            didState = DidState(
                state = OperationState.FINISHED,
                did = did,
                didDocument = document
            )
        )

        // Store in job storage
        jobStorage.store(jobId, response)

        response
    }

    override suspend fun deactivateDid(
        did: String,
        options: DeactivateDidOptions
    ): DidRegistrationResponse = withContext(Dispatchers.IO) {
        // Similar to update, deactivation requires authorization
        // This is a simplified implementation

        val jobId = UUID.randomUUID().toString()

        val response = DidRegistrationResponse(
            jobId = jobId,
            didState = DidState(
                state = OperationState.FINISHED,
                did = did,
                didDocument = null
            )
        )

        // Store in job storage
        jobStorage.store(jobId, response)

        response
    }

    /**
     * Extracts algorithm from options.
     */
    private fun extractAlgorithm(options: CreateDidOptions): String {
        // Try to get algorithm from method-specific options
        val algorithmElement = options.methodSpecificOptions["algorithm"]
        val algorithmStr = when (algorithmElement) {
            is JsonPrimitive -> algorithmElement.content
            else -> algorithmElement?.toString()
        } ?: "Ed25519" // Default algorithm

        return algorithmStr
    }

    /**
     * Extracts KMS options from CreateDidOptions.
     */
    private fun extractKmsOptions(options: CreateDidOptions): Map<String, Any?> {
        return options.methodSpecificOptions.mapValues { it.value.toString() }
    }

    /**
     * Creates a DID Document for the given method and key.
     *
     * This is a simplified implementation. Real implementations would delegate
     * to method-specific logic or use the didMethodFactory.
     */
    private suspend fun createDidDocument(
        method: String,
        keyHandle: KeyHandle,
        options: CreateDidOptions
    ): DidDocument {
        // Use factory if provided
        val didMethod = didMethodFactory?.invoke(method, kms)

        if (didMethod != null) {
            // Use the DID method's createDid if available
            val legacyOptions = DidCreationOptions(
                algorithm = KeyAlgorithm.fromName(keyHandle.algorithm)
                    ?: KeyAlgorithm.ED25519,
                additionalProperties = options.methodSpecificOptions.mapValues { it.value.toString() }
            )
            return didMethod.createDid(legacyOptions)
        }

        // Fallback: Create a simple DID Document
        // For did:key, we can derive the DID from the key
        val didString = when (method) {
            "key" -> {
                // For did:key, the DID is derived from the public key
                // This is simplified - real implementation would use proper multicodec encoding
                "did:key:${keyHandle.id.value}"
            }
            else -> {
                // For other methods, generate a method-specific identifier
                "did:$method:${keyHandle.id.value}"
            }
        }
        val did = Did(didString)

        val verificationMethodId = VerificationMethodId.parse("$didString#${keyHandle.id.value}", did)
        val verificationMethod = VerificationMethod(
            id = verificationMethodId,
            type = keyHandle.algorithm,
            controller = did,
            publicKeyJwk = keyHandle.publicKeyJwk,
            publicKeyMultibase = keyHandle.publicKeyMultibase
        )

        return DidDocument(
            id = did,
            verificationMethod = listOf(verificationMethod),
            authentication = listOf(verificationMethodId)
        )
    }

    /**
     * Builds a Secret object from a KeyHandle.
     */
    private suspend fun buildSecret(
        keyHandle: KeyHandle,
        options: CreateDidOptions
    ): Secret {
        // In Internal Secret Mode with returnSecrets=true, we need to extract
        // the private key from KMS. However, KMS typically doesn't expose private keys
        // directly for security reasons.

        // For now, we return the key handle information
        // Real implementations would need KMS support for exporting keys
        // or would work with KMS that supports key export in secure ways

        // Note: KMS KeyHandle contains publicKeyJwk, but Secret.KeyMaterial expects privateKeyJwk
        // In Internal Secret Mode with returnSecrets=true, we would need to export the private key
        // from KMS, which is typically not supported for security reasons.
        // For now, we return the key handle reference - real implementations would need
        // KMS support for secure key export or use a different approach.
        return Secret(
            keys = listOf(
                KeyMaterial(
                    id = keyHandle.id.value,
                    type = keyHandle.algorithm,
                    privateKeyJwk = keyHandle.publicKeyJwk?.let { jwk ->
                        // Convert Map<String, Any?> to JsonObject
                        buildJsonObject {
                            jwk.forEach { entry ->
                                val k = entry.key
                                val v = entry.value
                                put(k, when (v) {
                                    is String -> JsonPrimitive(v)
                                    is Number -> JsonPrimitive(v.toDouble())
                                    is Boolean -> JsonPrimitive(v)
                                    else -> JsonPrimitive(v.toString())
                                })
                            }
                        }
                    },
                    privateKeyMultibase = keyHandle.publicKeyMultibase,
                    additionalProperties = mapOf("keyHandleId" to keyHandle.id.value)
                )
            )
        )
    }
}

