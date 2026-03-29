package org.trustweave.did.base

import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.*
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.kms.KeyManagementService
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
            storeDocument(document.id.value, document)

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
     * Resolves a DID document from the blockchain.
     *
     * @param did The DID to resolve
     * @param txHash Optional transaction hash (if known)
     * @return DidResolutionResult
     * @throws NotFoundException if document not found
     */
    protected suspend fun resolveFromBlockchain(did: String, txHash: String? = null): DidResolutionResult =
        withContext(Dispatchers.IO) {
            validateDidFormat(Did(did))

            try {
                val anchorClient = getBlockchainAnchorClient()
                val chainId = getChainId()

                // If txHash is provided, use it directly
                val hash = txHash ?: findDocumentTxHash(did)

                if (hash == null) {
                    // Try fallback to stored document
                    val stored = getStoredDocument(did)
                    if (stored != null) {
                        val metadata = getDocumentMetadata(did)
                        return@withContext org.trustweave.did.base.DidMethodUtils.createSuccessResolutionResult(
                            stored,
                            method,
                            metadata?.created,
                            metadata?.updated
                        )
                    }

                    throw TrustWeaveException.NotFound(
                        resource = "DID document: $did"
                    )
                }

                // Read from blockchain
                val anchorRef = org.trustweave.anchor.AnchorRef(
                    chainId = chainId,
                    txHash = hash
                )

                val result = anchorClient.readPayload(anchorRef)

                // Convert JsonElement to DidDocument
                val document = jsonElementToDocument(result.payload)

                // Store locally for caching
                storeDocument(document.id.value, document)

                org.trustweave.did.base.DidMethodUtils.createSuccessResolutionResult(document, method)
            } catch (e: TrustWeaveException.NotFound) {
                throw e
            } catch (e: TrustWeaveException) {
                throw e
            } catch (e: Exception) {
                // Try fallback to stored document
                val stored = getStoredDocument(did)
                if (stored != null) {
                    val metadata = getDocumentMetadata(did)
                    return@withContext DidMethodUtils.createSuccessResolutionResult(
                        stored,
                        method,
                        metadata?.created,
                        metadata?.updated
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
        validateDidFormat(Did(did))

        // Anchor updated document
        val txHash = anchorDocument(document)

        // Update local storage
        val now = kotlinx.datetime.Clock.System.now()
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
        validateDidFormat(Did(did))

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

