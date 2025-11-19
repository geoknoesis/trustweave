package com.geoknoesis.vericore.ethrdid

import com.geoknoesis.vericore.anchor.BlockchainAnchorClient
import com.geoknoesis.vericore.core.NotFoundException
import com.geoknoesis.vericore.core.VeriCoreException
import com.geoknoesis.vericore.did.*
import com.geoknoesis.vericore.did.base.AbstractBlockchainDidMethod
import com.geoknoesis.vericore.did.base.DidMethodUtils
import com.geoknoesis.vericore.kms.KeyManagementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.protocol.http.HttpService
import org.web3j.tx.RawTransactionManager
import org.web3j.tx.TransactionManager
import org.web3j.utils.Numeric
import java.math.BigInteger

/**
 * Implementation of did:ethr method for Ethereum blockchain.
 * 
 * did:ethr uses Ethereum addresses as DID identifiers:
 * - Format: `did:ethr:{network}:{address}` or `did:ethr:{address}`
 * - Stores DID documents on Ethereum blockchain via anchoring
 * - Can integrate with ERC1056 registry contract for standard resolution
 * 
 * **Example Usage:**
 * ```kotlin
 * val kms = InMemoryKeyManagementService()
 * val config = EthrDidConfig.sepolia("https://eth-sepolia.g.alchemy.com/v2/KEY")
 * val anchorClient = PolygonBlockchainAnchorClient(config.chainId, config.toMap())
 * val method = EthrDidMethod(kms, anchorClient, config)
 * 
 * // Create DID
 * val options = didCreationOptions {
 *     algorithm = KeyAlgorithm.SECP256K1
 * }
 * val document = method.createDid(options)
 * 
 * // Resolve DID
 * val result = method.resolveDid("did:ethr:0x...")
 * ```
 * 
 * @see <a href="https://github.com/decentralized-identity/ethr-did-resolver">ethr-did-resolver</a>
 */
class EthrDidMethod(
    kms: KeyManagementService,
    private val anchorClient: BlockchainAnchorClient,
    private val config: EthrDidConfig
) : AbstractBlockchainDidMethod("ethr", kms) {

    private val web3j: Web3j
    private val transactionManager: TransactionManager?
    private val didToTxHash = mutableMapOf<String, String>()

    init {
        // Initialize Web3j client
        web3j = Web3j.build(HttpService(config.rpcUrl))
        
        // Initialize transaction manager if private key provided
        transactionManager = config.privateKey?.let { privateKeyHex ->
            try {
                val credentials = org.web3j.crypto.Credentials.create(
                    privateKeyHex.removePrefix("0x")
                )
                val chainIdNum = parseChainId(config.chainId)
                RawTransactionManager(web3j, credentials, chainIdNum)
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun getBlockchainAnchorClient(): BlockchainAnchorClient {
        return anchorClient
    }

    override fun getChainId(): String {
        return config.chainId
    }

    override suspend fun canSubmitTransaction(): Boolean {
        return transactionManager != null
    }

    override suspend fun findDocumentTxHash(did: String): String? {
        // First check local cache
        val cached = didToTxHash[did]
        if (cached != null) {
            return cached
        }
        
        // For did:ethr, we can derive the transaction hash from the DID
        // In a full ERC1056 implementation, we would query the registry contract
        // For now, we use a simpler approach with blockchain anchoring
        
        // Try to resolve from stored documents
        val stored = getStoredDocument(did)
        if (stored != null) {
            // If we have a stored document, it was anchored
            // In a real implementation, we'd store the txHash when anchoring
            return null
        }
        
        return null
    }

    override suspend fun createDid(options: DidCreationOptions): DidDocument = withContext(Dispatchers.IO) {
        try {
            // For did:ethr, we need to derive an Ethereum address from a key
            // Generate a secp256k1 key (Ethereum-compatible)
            val algorithm = options.algorithm.algorithmName
            if (algorithm.uppercase() != "SECP256K1" && algorithm.uppercase() != "ED25519") {
                throw IllegalArgumentException("did:ethr requires secp256k1 or Ed25519 algorithm")
            }
            
            val keyHandle = kms.generateKey(algorithm, options.additionalProperties)
            
            // Derive Ethereum address from key
            // Note: In a full implementation, we'd derive the address from the public key
            // For now, we use a simplified approach
            val ethereumAddress = deriveEthereumAddress(keyHandle)
            
            // Build DID identifier
            val did = if (config.network != null) {
                "did:ethr:${config.network}:$ethereumAddress"
            } else {
                "did:ethr:$ethereumAddress"
            }
            
            // Create verification method
            val verificationMethod = DidMethodUtils.createVerificationMethod(
                did = did,
                keyHandle = keyHandle,
                algorithm = options.algorithm
            )
            
            // Build DID document
            val document = DidMethodUtils.buildDidDocument(
                did = did,
                verificationMethod = listOf(verificationMethod),
                authentication = listOf(verificationMethod.id),
                assertionMethod = if (options.purposes.contains(DidCreationOptions.KeyPurpose.ASSERTION)) {
                    listOf(verificationMethod.id)
                } else null
            )
            
            // Anchor document to blockchain
            try {
                val txHash = anchorDocument(document)
                didToTxHash[did] = txHash
            } catch (e: Exception) {
                // If anchoring fails, still store locally for testing
                storeDocument(document.id, document)
            }
            
            document
        } catch (e: VeriCoreException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw VeriCoreException(
                "Failed to create did:ethr: ${e.message}",
                e
            )
        }
    }

    override suspend fun resolveDid(did: String): DidResolutionResult = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)
            
            // Try to resolve from blockchain
            try {
                return@withContext resolveFromBlockchain(did, didToTxHash[did])
            } catch (e: NotFoundException) {
                // If not found on blockchain, try stored document
                val stored = getStoredDocument(did)
                if (stored != null) {
                    return@withContext DidMethodUtils.createSuccessResolutionResult(
                        stored,
                        method,
                        getDocumentMetadata(did)?.created,
                        getDocumentMetadata(did)?.updated
                    )
                }
                
                // Return not found
                return@withContext DidMethodUtils.createErrorResolutionResult(
                    "notFound",
                    "DID document not found on blockchain",
                    method
                )
            }
        } catch (e: VeriCoreException) {
            throw e
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
            
            // Anchor updated document to blockchain
            val txHash = updateDocumentOnBlockchain(did, updatedDocument)
            didToTxHash[did] = txHash
            
            updatedDocument
        } catch (e: NotFoundException) {
            throw e
        } catch (e: VeriCoreException) {
            throw e
        } catch (e: Exception) {
            throw VeriCoreException(
                "Failed to update did:ethr: ${e.message}",
                e
            )
        }
    }

    override suspend fun deactivateDid(did: String): Boolean = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)
            
            // Resolve current document
            val currentResult = resolveDid(did)
            val currentDocument = currentResult.document
                ?: return@withContext false
            
            // Create deactivated document
            val deactivatedDocument = currentDocument.copy(
                verificationMethod = emptyList(),
                authentication = emptyList(),
                assertionMethod = emptyList(),
                keyAgreement = emptyList(),
                capabilityInvocation = emptyList(),
                capabilityDelegation = emptyList()
            )
            
            // Anchor deactivated document
            deactivateDocumentOnBlockchain(did, deactivatedDocument)
            
            // Remove from cache
            didToTxHash.remove(did)
            
            true
        } catch (e: NotFoundException) {
            false
        } catch (e: Exception) {
            throw VeriCoreException(
                "Failed to deactivate did:ethr: ${e.message}",
                e
            )
        }
    }

    /**
     * Derives an Ethereum address from a key handle.
     * 
     * In a full implementation, this would properly derive the address from the public key.
     * For now, we use a simplified approach.
     */
    private fun deriveEthereumAddress(keyHandle: com.geoknoesis.vericore.kms.KeyHandle): String {
        // For secp256k1 keys, derive Ethereum address from public key
        // In a full implementation, we'd:
        // 1. Extract public key from JWK
        // 2. Compute Keccak-256 hash
        // 3. Take last 20 bytes
        // 4. Prepend 0x
        
        // Simplified: generate address from key ID
        // This is for demonstration - real implementation needs proper address derivation
        val keyIdHash = keyHandle.id.hashCode().toString(16).take(40).padStart(40, '0')
        return "0x$keyIdHash"
    }

    /**
     * Parses chain ID to long.
     */
    private fun parseChainId(chainId: String): Long {
        require(chainId.startsWith("eip155:")) {
            "Invalid Ethereum chain ID format: $chainId"
        }
        val idStr = chainId.substringAfter(":")
        return idStr.toLongOrNull() ?: throw IllegalArgumentException("Invalid chain ID: $chainId")
    }
}

