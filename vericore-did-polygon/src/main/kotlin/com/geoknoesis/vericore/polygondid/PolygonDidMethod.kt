package com.geoknoesis.vericore.polygondid

import com.geoknoesis.vericore.anchor.BlockchainAnchorClient
import com.geoknoesis.vericore.did.*
import com.geoknoesis.vericore.did.base.AbstractBlockchainDidMethod
import com.geoknoesis.vericore.kms.KeyManagementService
import com.geoknoesis.vericore.ethrdid.EthrDidMethod
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
    anchorClient: BlockchainAnchorClient,
    private val config: PolygonDidConfig
) : AbstractBlockchainDidMethod("polygon", kms) {

    // Delegate to EthrDidMethod for implementation since Polygon is EVM-compatible
    private val delegate: EthrDidMethod

    init {
        // Convert PolygonDidConfig to EthrDidConfig for delegation
        val ethrConfig = com.geoknoesis.vericore.ethrdid.EthrDidConfig(
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
        return delegate.getBlockchainAnchorClient()
    }

    override fun getChainId(): String {
        return config.chainId
    }

    override suspend fun canSubmitTransaction(): Boolean {
        return delegate.canSubmitTransaction()
    }

    override suspend fun findDocumentTxHash(did: String): String? {
        return delegate.findDocumentTxHash(did)
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
            throw com.geoknoesis.vericore.core.VeriCoreException(
                "Failed to create did:polygon: ${e.message}",
                e
            )
        }
    }

    override suspend fun resolveDid(did: String): com.geoknoesis.vericore.did.DidResolutionResult = 
        withContext(Dispatchers.IO) {
            try {
                validateDidFormat(did)
                
                // Convert did:polygon to did:ethr for resolution
                val ethrDid = did.replace("did:polygon:", "did:ethr:")
                
                // Resolve using delegate
                val ethrResult = delegate.resolveDid(ethrDid)
                
                // Convert result back to polygon format
                return@withContext if (ethrResult.document != null) {
                    val polygonDocument = ethrResult.document.copy(
                        id = ethrResult.document.id.replace("did:ethr:", "did:polygon:")
                    )
                    
                    storeDocument(polygonDocument.id, polygonDocument)
                    
                    com.geoknoesis.vericore.did.base.DidMethodUtils.createSuccessResolutionResult(
                        polygonDocument,
                        method,
                        ethrResult.documentMetadata.created,
                        ethrResult.documentMetadata.updated
                    )
                } else {
                    ethrResult.copy(resolutionMetadata = ethrResult.resolutionMetadata + mapOf("method" to method))
                }
            } catch (e: Exception) {
                com.geoknoesis.vericore.did.base.DidMethodUtils.createErrorResolutionResult(
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
            throw com.geoknoesis.vericore.core.VeriCoreException(
                "Failed to update did:polygon: ${e.message}",
                e
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
            throw com.geoknoesis.vericore.core.VeriCoreException(
                "Failed to deactivate did:polygon: ${e.message}",
                e
            )
        }
    }
}

