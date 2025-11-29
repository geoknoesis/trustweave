package com.trustweave.polygondid

import com.trustweave.anchor.BlockchainAnchorClient
import com.trustweave.did.*
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.did.base.AbstractBlockchainDidMethod
import com.trustweave.did.base.DidMethodUtils
import com.trustweave.core.exception.TrustWeaveException
import com.trustweave.kms.KeyManagementService
import com.trustweave.ethrdid.EthrDidMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementation of did:polygon method for Polygon blockchain.
 *
 * did:polygon uses Polygon addresses as DID identifiers, similar to did:ethr:
 * - Format: `did:polygon:{network}:{address}` or `did:polygon:{address}`
 * - Stores DID documents on Polygon blockchain via anchoring
 * - Lower transaction costs than Ethereum mainnet
 *
 * This implementation reuses the Ethereum DID pattern since Polygon is EVM-compatible.
 *
 * **Example Usage:**
 * ```kotlin
 * val kms = InMemoryKeyManagementService()
 * val config = PolygonDidConfig.mumbai("https://rpc-mumbai.maticvigil.com")
 * val anchorClient = PolygonBlockchainAnchorClient(config.chainId, config.toMap())
 * val method = PolygonDidMethod(kms, anchorClient, config)
 *
 * // Create DID
 * val options = didCreationOptions {
 *     algorithm = KeyAlgorithm.SECP256K1
 * }
 * val document = method.createDid(options)
 *
 * // Resolve DID
 * val result = method.resolveDid("did:polygon:0x...")
 * ```
 */
class PolygonDidMethod(
    kms: KeyManagementService,
    private val anchorClient: BlockchainAnchorClient,
    private val config: PolygonDidConfig
) : AbstractBlockchainDidMethod("polygon", kms) {

    // Delegate to EthrDidMethod for implementation since Polygon is EVM-compatible
    private val delegate: EthrDidMethod

    init {
        // Convert PolygonDidConfig to EthrDidConfig for delegation
        val ethrConfig = com.trustweave.ethrdid.EthrDidConfig(
            rpcUrl = config.rpcUrl,
            chainId = config.chainId,
            registryAddress = config.registryAddress,
            privateKey = config.privateKey,
            network = config.network ?: "polygon",
            additionalProperties = config.additionalProperties
        )

        delegate = EthrDidMethod(kms, anchorClient, ethrConfig)
    }

    override fun getBlockchainAnchorClient(): BlockchainAnchorClient {
        return anchorClient
    }

    override fun getChainId(): String {
        return config.chainId
    }

    override suspend fun canSubmitTransaction(): Boolean {
        return config.privateKey != null
    }

    override suspend fun findDocumentTxHash(did: String): String? {
        // Convert did:polygon to did:ethr and resolve via stored document
        val ethrDid = did.replace("did:polygon:", "did:ethr:")
        val stored = getStoredDocument(did) ?: getStoredDocument(ethrDid)
        return if (stored != null) {
            // Return a synthetic tx hash for stored documents
            did.hashCode().toString(16)
        } else {
            null
        }
    }

    override suspend fun createDid(options: DidCreationOptions): DidDocument = withContext(Dispatchers.IO) {
        try {
            // Use delegate but convert DID format from ethr to polygon
            val ethrDocument = delegate.createDid(options)

            // Convert did:ethr to did:polygon
            val polygonDid = ethrDocument.id.replace("did:ethr:", "did:polygon:")

            // Rebuild document with polygon DID
            val polygonDocument = ethrDocument.copy(id = polygonDid)

            // Store locally
            storeDocument(polygonDocument.id, polygonDocument)

            polygonDocument
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                code = "CREATE_FAILED",
                message = "Failed to create did:polygon: ${e.message}",
                cause = e
            )
        }
    }

    override suspend fun resolveDid(did: String): DidResolutionResult =
        withContext(Dispatchers.IO) {
            try {
                validateDidFormat(did)

                // Convert did:polygon to did:ethr for resolution
                val ethrDid = did.replace("did:polygon:", "did:ethr:")

                // Resolve using delegate
                val ethrResult = delegate.resolveDid(ethrDid)

                // Convert result back to polygon format
                return@withContext when (ethrResult) {
                    is DidResolutionResult.Success -> {
                        val ethrDoc = ethrResult.document
                        val polygonDocument = ethrDoc.copy(
                            id = ethrDoc.id.replace("did:ethr:", "did:polygon:")
                        )

                        storeDocument(polygonDocument.id, polygonDocument)

                        DidMethodUtils.createSuccessResolutionResult(
                            polygonDocument,
                            method,
                            ethrResult.documentMetadata.created,
                            ethrResult.documentMetadata.updated
                        )
                    }
                    is DidResolutionResult.Failure.NotFound -> {
                        DidResolutionResult.Failure.NotFound(
                            did = com.trustweave.core.types.Did(did),
                            reason = ethrResult.reason,
                            resolutionMetadata = ethrResult.resolutionMetadata + mapOf("method" to method)
                        )
                    }
                    is DidResolutionResult.Failure.InvalidFormat -> {
                        DidResolutionResult.Failure.InvalidFormat(
                            did = did,
                            reason = ethrResult.reason,
                            resolutionMetadata = ethrResult.resolutionMetadata + mapOf("method" to method)
                        )
                    }
                    is DidResolutionResult.Failure.MethodNotRegistered -> {
                        DidResolutionResult.Failure.MethodNotRegistered(
                            method = method,
                            availableMethods = ethrResult.availableMethods,
                            resolutionMetadata = ethrResult.resolutionMetadata + mapOf("method" to method)
                        )
                    }
                    is DidResolutionResult.Failure.ResolutionError -> {
                        DidResolutionResult.Failure.ResolutionError(
                            did = com.trustweave.core.types.Did(did),
                            reason = ethrResult.reason,
                            cause = ethrResult.cause,
                            resolutionMetadata = ethrResult.resolutionMetadata + mapOf("method" to method)
                        )
                    }
                }
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

            // Convert did:polygon to did:ethr for update
            val ethrDid = did.replace("did:polygon:", "did:ethr:")

            // Update using delegate
            val ethrUpdated = delegate.updateDid(ethrDid) { ethrDoc ->
                // Apply updater with polygon DID
                val polygonDoc = ethrDoc.copy(id = did)
                updater(polygonDoc)
            }

            // Convert back to polygon format
            val polygonUpdated = ethrUpdated.copy(id = did)
            storeDocument(polygonUpdated.id, polygonUpdated)

            polygonUpdated
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                code = "UPDATE_FAILED",
                message = "Failed to update did:polygon: ${e.message}",
                cause = e
            )
        }
    }

    override suspend fun deactivateDid(did: String): Boolean = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)

            // Convert did:polygon to did:ethr for deactivation
            val ethrDid = did.replace("did:polygon:", "did:ethr:")

            val deactivated = delegate.deactivateDid(ethrDid)

            if (deactivated) {
                documents.remove(did)
                documentMetadata.remove(did)
            }

            deactivated
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                code = "DEACTIVATE_FAILED",
                message = "Failed to deactivate did:polygon: ${e.message}",
                cause = e
            )
        }
    }
}

