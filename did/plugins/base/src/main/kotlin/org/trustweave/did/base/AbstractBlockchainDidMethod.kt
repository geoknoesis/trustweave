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
import kotlinx.coroutines.sync.withLock

/**
 * Abstract base class for blockchain-based DID method implementations.
 *
 * Provides common functionality for DID methods that store documents on blockchain:
 * - Integration with BlockchainAnchorClient for on-chain storage
 * - Document anchoring to blockchain
 * - Document resolution from blockchain
 * - Fallback to in-memory storage for testing
 *
 * **Local deactivation is authoritative, even over a successful chain read.** The DID
 * Resolution 1.0 CR does not settle where a blockchain-backed method should learn of
 * deactivation from, so [resolveFromBlockchain] makes an explicit, fail-safe choice: once this
 * instance has recorded a DID as deactivated (via [deactivateDocumentOnBlockchain]), every
 * subsequent resolution returns [DidResolutionResult.Deactivated] for that DID — whether the
 * chain read fails and falls back to the stored document, or the chain read succeeds and returns
 * a live, still-anchored (pre-deactivation) document. A revoked DID must never verify; treating
 * the chain as authoritative over local state would let a stale or unpruned on-chain anchor
 * silently resurrect a DID TrustWeave believes is dead. The accepted tradeoff is that a DID
 * deactivated on one instance still resolves live on another instance that never observed the
 * deactivation, since this state is local and is not propagated to, or re-derived from, the
 * chain. This mirrors the same rule applied to did:web in
 * [AbstractWebDidMethod.resolveFromHttp].
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
    kms: KeyManagementService,
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
    protected suspend fun anchorDocument(document: DidDocument): String =
        withContext(Dispatchers.IO) {
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
                    cause = e,
                )
            }
        }

    /**
     * Resolves a DID document from the blockchain.
     *
     * **Local deactivation is authoritative, even over a successful chain read** (see the class
     * KDoc above). Once a DID has been recorded as deactivated via
     * [deactivateDocumentOnBlockchain], this method returns [DidResolutionResult.Deactivated] for
     * it on every path — the stored-document fallback (no known tx hash, or the chain read
     * throws) just as much as the ordinary successful chain read below — even if that read
     * returns a valid, still-anchored document from before the deactivation.
     *
     * @param did The DID to resolve
     * @param txHash Optional transaction hash (if known)
     * @return DidResolutionResult
     * @throws NotFoundException if document not found
     */
    protected suspend fun resolveFromBlockchain(
        did: String,
        txHash: String? = null,
    ): DidResolutionResult =
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
                            metadata?.updated,
                            metadata?.deactivated ?: false,
                        )
                    }

                    throw TrustWeaveException.NotFound(
                        resource = "DID document: $did",
                    )
                }

                // Read from blockchain
                val anchorRef =
                    org.trustweave.anchor.AnchorRef(
                        chainId = chainId,
                        txHash = hash,
                    )

                val result = anchorClient.readPayload(anchorRef)

                // Convert JsonElement to DidDocument
                val document = jsonElementToDocument(result.payload)

                // Store locally for caching. storeDocument() preserves any deactivation this
                // instance has already recorded for the DID (see its KDoc) instead of resetting
                // it — and, once deactivated, leaves `updated` pinned at the deactivation time
                // rather than bumping it to this resolve's read time (DID Core §7.3 / DID
                // Resolution 1.0 §4.3) — so local state stays authoritative for blockchain-backed
                // methods even though the chain read just succeeded. No separate before/after
                // capture of `deactivated`/`updated` is needed: a single post-store metadata read
                // already reflects the correct values either way.
                storeDocument(document.id.value, document)

                val metadata = getDocumentMetadata(did)
                val deactivated = metadata?.deactivated ?: false
                org.trustweave.did.base.DidMethodUtils.createSuccessResolutionResult(
                    document,
                    method,
                    updated = if (deactivated) metadata.updated else null,
                    deactivated = deactivated,
                )
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
                        metadata?.updated,
                        metadata?.deactivated ?: false,
                    )
                }

                throw TrustWeaveException(
                    code = "DID_RESOLUTION_FAILED",
                    message = "Failed to resolve DID from blockchain: ${e.message}",
                    cause = e,
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
    protected suspend fun updateDocumentOnBlockchain(
        did: String,
        document: DidDocument,
    ): String {
        validateDidFormat(Did(did))

        // Anchor updated document. Deliberately *outside* updateMutex: anchorDocument() calls
        // storeDocument(), which takes the same non-reentrant Mutex — wrapping this call would
        // self-deadlock. It is also remote I/O, which must not hold a lock that every resolve
        // contends for.
        val txHash = anchorDocument(document)

        // Update local storage under updateMutex, the same lock storeDocument takes, so this
        // write cannot be lost to (or lose to) a concurrent cache-store's read-modify-write.
        updateMutex.withLock {
            val now =
                kotlinx.datetime.Clock.System
                    .now()
            documentMetadata[did] =
                (documentMetadata[did] ?: DidDocumentMetadata(created = now))
                    .copy(updated = now)
        }

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
        deactivatedDocument: DidDocument,
    ): Boolean {
        validateDidFormat(Did(did))

        try {
            // Anchor deactivated document. Deliberately *outside* updateMutex: anchorDocument()
            // calls storeDocument(), which takes the same non-reentrant Mutex — wrapping this call
            // would self-deadlock. It is also remote I/O, which must not hold the lock.
            anchorDocument(deactivatedDocument)

            // Keep the deactivated document locally and flag the metadata as
            // deactivated (W3C DID Core §7.3) so subsequent resolutions can
            // surface the deactivation instead of silently "losing" the DID.
            //
            // Both writes go under updateMutex, the same lock storeDocument takes. This is the
            // §4.4 security property, not bookkeeping: storeDocument runs on every successful
            // resolve, reading the existing metadata and writing the merged value back, so a
            // deactivation written outside the lock could land between that read and that write
            // and be silently clobbered — and the DID would resolve live again.
            updateMutex.withLock {
                val now =
                    kotlinx.datetime.Clock.System
                        .now()
                documents[did] = deactivatedDocument
                documentMetadata[did] =
                    (documentMetadata[did] ?: DidDocumentMetadata(created = now))
                        .copy(updated = now, deactivated = true)
            }

            return true
        } catch (e: Exception) {
            throw TrustWeaveException(
                code = "DID_DEACTIVATION_FAILED",
                message = "Failed to deactivate DID on blockchain: ${e.message}",
                cause = e,
            )
        }
    }
}
