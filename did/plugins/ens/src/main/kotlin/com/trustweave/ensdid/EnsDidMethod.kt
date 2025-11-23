package com.trustweave.ensdid

import com.trustweave.anchor.BlockchainAnchorClient
import com.trustweave.core.NotFoundException
import com.trustweave.core.TrustWeaveException
import com.trustweave.did.*
import com.trustweave.did.base.AbstractBlockchainDidMethod
import com.trustweave.did.base.DidMethodUtils
import com.trustweave.ethrdid.EthrDidMethod
import com.trustweave.kms.KeyManagementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementation of did:ens method using ENS resolver integration.
 * 
 * did:ens uses Ethereum Name Service (ENS) resolver with Ethereum DID documents:
 * - Format: `did:ens:{domain-name}` (e.g., "did:ens:example.eth")
 * - Resolves ENS names to Ethereum addresses, then resolves as did:ethr
 * - Integrates with ENS resolver for human-readable names
 * 
 * **Example Usage:**
 * ```kotlin
 * val kms = InMemoryKeyManagementService()
 * val config = EnsDidConfig.mainnet("https://eth-mainnet.g.alchemy.com/v2/KEY")
 * val anchorClient = PolygonBlockchainAnchorClient(config.chainId, config.toMap())
 * val method = EnsDidMethod(kms, anchorClient, config)
 * 
 * // Resolve DID
 * val result = method.resolveDid("did:ens:example.eth")
 * ```
 */
class EnsDidMethod(
    kms: KeyManagementService,
    private val anchorClient: BlockchainAnchorClient,
    private val config: EnsDidConfig
) : AbstractBlockchainDidMethod("ens", kms) {

    // Delegate to EthrDidMethod for Ethereum DID resolution
    private val delegate: EthrDidMethod

    init {
        // Create EthrDidConfig from EnsDidConfig
        val ethrConfig = com.trustweave.ethrdid.EthrDidConfig(
            rpcUrl = config.rpcUrl,
            chainId = config.chainId,
            privateKey = config.privateKey,
            network = config.network ?: "mainnet",
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
        return null // ENS resolution doesn't use txHash directly
    }

    override suspend fun createDid(options: DidCreationOptions): DidDocument = withContext(Dispatchers.IO) {
        throw TrustWeaveException(
            "did:ens does not support DID creation. " +
            "Use ENS to register a domain name first, then resolve it as did:ens."
        )
    }

    override suspend fun resolveDid(did: String): DidResolutionResult = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)
            
            // Extract ENS domain from did:ens
            val ensDomain = extractEnsDomain(did)
            
            // Resolve ENS domain to Ethereum address
            val ethAddress = resolveEnsToAddress(ensDomain)
            
            // Resolve as did:ethr
            val ethrDid = "did:ethr:$ethAddress"
            val ethrResult = delegate.resolveDid(ethrDid)
            
            // Convert result to did:ens format
            val ethrDoc = ethrResult.document
            if (ethrDoc != null) {
                val ensDocument = ethrDoc.copy(id = did)
                storeDocument(ensDocument.id, ensDocument)
                
                DidMethodUtils.createSuccessResolutionResult(
                    ensDocument,
                    method,
                    ethrResult.documentMetadata.created,
                    ethrResult.documentMetadata.updated
                )
            } else {
                DidMethodUtils.createErrorResolutionResult(
                    "notFound",
                    "ENS domain not found or not linked to DID",
                    method
                )
            }
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
        throw TrustWeaveException(
            "did:ens does not support DID updates. " +
            "Update the underlying did:ethr DID instead."
        )
    }

    override suspend fun deactivateDid(did: String): Boolean = withContext(Dispatchers.IO) {
        throw TrustWeaveException(
            "did:ens does not support DID deactivation. " +
            "Deactivate the underlying did:ethr DID instead."
        )
    }

    /**
     * Extracts ENS domain from did:ens identifier.
     */
    private fun extractEnsDomain(did: String): String {
        val parsed = DidMethodUtils.parseDid(did)
            ?: throw IllegalArgumentException("Invalid DID format: $did")
        
        if (parsed.first != "ens") {
            throw IllegalArgumentException("Not a did:ens DID: $did")
        }
        
        return parsed.second
    }

    /**
     * Resolves ENS domain to Ethereum address.
     */
    private suspend fun resolveEnsToAddress(ensDomain: String): String = withContext(Dispatchers.IO) {
        // In a full implementation, we'd query the ENS resolver contract
        // to resolve the domain to an Ethereum address
        
        // Simplified implementation: query ENS resolver via Web3j
        try {
            // Use Web3j to query ENS resolver
            // This is a placeholder - real implementation needs ENS resolver contract interaction
            throw TrustWeaveException(
                "ENS resolution not fully implemented. " +
                "Query ENS resolver contract at ${config.ensRegistryAddress} for domain: $ensDomain"
            )
        } catch (e: Exception) {
            throw TrustWeaveException(
                "Failed to resolve ENS domain to address: ${e.message}",
                e
            )
        }
    }
}

