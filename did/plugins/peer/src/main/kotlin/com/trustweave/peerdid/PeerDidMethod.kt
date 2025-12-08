package com.trustweave.peerdid

import com.trustweave.core.exception.TrustWeaveException
import com.trustweave.did.*
import com.trustweave.did.identifiers.Did
import com.trustweave.did.model.DidDocument
import com.trustweave.did.model.DidService
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.did.base.AbstractDidMethod
import com.trustweave.did.base.DidMethodUtils
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.results.GenerateKeyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Base64

/**
 * Implementation of did:peer method for peer-to-peer DIDs.
 *
 * did:peer uses peer DIDs for P2P communication without external registries:
 * - Format: `did:peer:{numalgo}:{encoded-data}`
 * - No external registry required
 * - Documents embedded in DID or stored locally
 * - Supports numalgo 0, 1, and 2
 *
 * **Example Usage:**
 * ```kotlin
 * val kms = InMemoryKeyManagementService()
 * val config = PeerDidConfig.numalgo2()
 * val method = PeerDidMethod(kms, config)
 *
 * // Create DID
 * val options = didCreationOptions {
 *     algorithm = KeyAlgorithm.ED25519
 * }
 * val document = method.createDid(options)
 *
 * // Resolve DID (from embedded document)
 * val result = method.resolveDid(document.id)
 * ```
 */
class PeerDidMethod(
    kms: KeyManagementService,
    private val config: PeerDidConfig = PeerDidConfig.numalgo2()
) : AbstractDidMethod("peer", kms) {

    override suspend fun createDid(options: DidCreationOptions): DidDocument = withContext(Dispatchers.IO) {
        try {
            // Generate key using KMS
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

            // Generate peer DID based on numalgo
            val did = when (config.numalgo) {
                PeerDidConfig.NUMALGO_0 -> generateNumalgo0Did(keyHandle)
                PeerDidConfig.NUMALGO_1 -> generateNumalgo1Did(keyHandle)
                PeerDidConfig.NUMALGO_2 -> generateNumalgo2Did(keyHandle)
                else -> throw IllegalArgumentException("Unsupported numalgo: ${config.numalgo}")
            }

            // Create verification method
            val verificationMethod = DidMethodUtils.createVerificationMethod(
                did = did,
                keyHandle = keyHandle,
                algorithm = options.algorithm
            )

            // Build DID document
            val service = if (config.includeServices && options.additionalProperties.containsKey("serviceEndpoint")) {
                listOf(DidService(
                    id = "$did#didcomm",
                    type = "DIDCommMessaging",
                    serviceEndpoint = options.additionalProperties["serviceEndpoint"] as String
                ))
            } else {
                emptyList<DidService>()
            }

            val document = DidMethodUtils.buildDidDocument(
                did = did,
                verificationMethod = listOf(verificationMethod),
                authentication = listOf(verificationMethod.id.value),
                assertionMethod = if (options.purposes.contains(KeyPurpose.ASSERTION)) {
                    listOf(verificationMethod.id.value)
                } else null,
                service = service
            )

            // Store locally (peer DIDs don't use external registries)
            storeDocument(document.id, document)

            document
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                code = "CREATE_FAILED",
                message = "Failed to create did:peer: ${e.message}",
                cause = e
            )
        }
    }

    override suspend fun resolveDid(did: Did): DidResolutionResult = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)

            val didString = did.value
            // For peer DIDs, documents are stored locally or embedded in DID
            // First try to get from local storage
            val stored = getStoredDocument(did)
            if (stored != null) {
                return@withContext DidMethodUtils.createSuccessResolutionResult(
                    stored,
                    method,
                    getDocumentMetadata(did)?.created,
                    getDocumentMetadata(did)?.updated
                )
            }

            // Try to resolve from embedded document (for long-form peer DIDs)
            val embedded = resolveEmbeddedDocument(didString)
            if (embedded != null) {
                storeDocument(embedded.id, embedded)
                return@withContext DidMethodUtils.createSuccessResolutionResult(embedded, method)
            }

            // Not found
            DidMethodUtils.createErrorResolutionResult(
                "notFound",
                "DID document not found",
                method,
                didString
            )
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

            // Store updated document locally
            storeDocument(updatedDocument.id.value, updatedDocument)

            updatedDocument
        } catch (e: TrustWeaveException.NotFound) {
            throw e
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                code = "UPDATE_FAILED",
                message = "Failed to update did:peer: ${e.message}",
                cause = e
            )
        }
    }

    override suspend fun deactivateDid(did: Did): Boolean = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)

            val didString = did.value
            // Check if document exists
            val document = getStoredDocument(did)
            if (document == null) {
                return@withContext false
            }

            // Remove from local storage
            documents.remove(didString)
            documentMetadata.remove(didString)

            true
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                code = "DEACTIVATE_FAILED",
                message = "Failed to deactivate did:peer: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Generates a numalgo 0 peer DID (static numeric).
     */
    private fun generateNumalgo0Did(keyHandle: com.trustweave.kms.KeyHandle): String {
        // Numalgo 0 uses static numeric encoding
        // Simplified implementation
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(keyHandle.id.value.toByteArray())
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
        return "did:peer:0${encoded.take(22)}"
    }

    /**
     * Generates a numalgo 1 peer DID (short-form with inception key).
     */
    private fun generateNumalgo1Did(keyHandle: com.trustweave.kms.KeyHandle): String {
        // Numalgo 1 uses inception key
        // Simplified implementation
        val publicKey = keyHandle.publicKeyMultibase
            ?: throw TrustWeaveException.Unknown(
                code = "MISSING_PUBLIC_KEY",
                message = "Public key multibase required for numalgo 1"
            )

        val hash = MessageDigest.getInstance("SHA-256")
            .digest(publicKey.toByteArray())
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
        return "did:peer:1${encoded.take(22)}"
    }

    /**
     * Generates a numalgo 2 peer DID (short-form with multibase).
     */
    private fun generateNumalgo2Did(keyHandle: com.trustweave.kms.KeyHandle): String {
        // Numalgo 2 uses multibase encoding
        // Simplified implementation
        val publicKey = keyHandle.publicKeyMultibase
            ?: throw TrustWeaveException.Unknown(
                code = "MISSING_PUBLIC_KEY",
                message = "Public key multibase required for numalgo 2"
            )

        val hash = MessageDigest.getInstance("SHA-256")
            .digest(publicKey.toByteArray())
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(hash)
        return "did:peer:2${encoded.take(22)}"
    }

    /**
     * Resolves embedded document from long-form peer DID.
     */
    private fun resolveEmbeddedDocument(did: String): DidDocument? {
        // For long-form peer DIDs, the document might be embedded
        // This is a simplified implementation
        // In a full implementation, we'd decode the embedded document from the DID

        // For now, return null (documents are stored locally)
        return null
    }
}

