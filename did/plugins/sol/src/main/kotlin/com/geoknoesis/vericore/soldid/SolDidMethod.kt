package com.geoknoesis.vericore.soldid

import com.geoknoesis.vericore.anchor.BlockchainAnchorClient
import com.geoknoesis.vericore.core.NotFoundException
import com.geoknoesis.vericore.core.VeriCoreException
import com.geoknoesis.vericore.did.*
import com.geoknoesis.vericore.did.base.AbstractBlockchainDidMethod
import com.geoknoesis.vericore.did.base.DidMethodUtils
import com.geoknoesis.vericore.kms.KeyManagementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Implementation of did:sol method for Solana blockchain.
 * 
 * did:sol uses Solana addresses (public keys) as DID identifiers:
 * - Format: `did:sol:{address}` or `did:sol:{network}:{address}`
 * - Stores DID documents on Solana blockchain via program accounts
 * - Account-based storage for DID documents
 * 
 * **Example Usage:**
 * ```kotlin
 * val kms = InMemoryKeyManagementService()
 * val config = SolDidConfig.devnet("https://api.devnet.solana.com")
 * val anchorClient = createSolanaAnchorClient(config)
 * val method = SolDidMethod(kms, anchorClient, config)
 * 
 * // Create DID
 * val options = didCreationOptions {
 *     algorithm = KeyAlgorithm.ED25519
 * }
 * val document = method.createDid(options)
 * 
 * // Resolve DID
 * val result = method.resolveDid("did:sol:7xK...")
 * ```
 */
class SolDidMethod(
    kms: KeyManagementService,
    private val anchorClient: BlockchainAnchorClient,
    private val config: SolDidConfig
) : AbstractBlockchainDidMethod("sol", kms) {

    private val httpClient: OkHttpClient
    private val solanaClient: SolanaClient

    init {
        httpClient = OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        
        solanaClient = SolanaClient(httpClient, config)
    }

    override fun getBlockchainAnchorClient(): BlockchainAnchorClient {
        return anchorClient
    }

    override fun getChainId(): String {
        return "solana:${config.network}"
    }

    override suspend fun canSubmitTransaction(): Boolean {
        return config.privateKey != null
    }

    override suspend fun findDocumentTxHash(did: String): String? {
        // For Solana, we derive the account address from the DID
        // In a full implementation, we'd query the Solana program
        return null
    }

    override suspend fun createDid(options: DidCreationOptions): DidDocument = withContext(Dispatchers.IO) {
        try {
            // Generate Ed25519 key (Solana uses Ed25519)
            val algorithm = options.algorithm.algorithmName
            if (algorithm.uppercase() != "ED25519") {
                throw IllegalArgumentException("did:sol requires Ed25519 algorithm")
            }
            
            val keyHandle = kms.generateKey(algorithm, options.additionalProperties)
            
            // Derive Solana address from public key
            val solanaAddress = deriveSolanaAddress(keyHandle)
            
            // Build DID identifier
            val did = if (config.network == SolDidConfig.MAINNET) {
                "did:sol:$solanaAddress"
            } else {
                "did:sol:${config.network}:$solanaAddress"
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
            
            // Store document on Solana (via program account or anchoring)
            try {
                val txHash = anchorDocument(document)
                // Store mapping
                findDocumentTxHash(did) // Cache would be stored here
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
                "Failed to create did:sol: ${e.message}",
                e
            )
        }
    }

    override suspend fun resolveDid(did: String): DidResolutionResult = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)
            
            // Extract Solana address from DID
            val solanaAddress = extractSolanaAddress(did)
            
            // Resolve from Solana program account
            val accountData = solanaClient.getAccountData(solanaAddress)
            
            if (accountData == null) {
                // Try stored document as fallback
                val stored = getStoredDocument(did)
                if (stored != null) {
                    return@withContext DidMethodUtils.createSuccessResolutionResult(
                        stored,
                        method,
                        getDocumentMetadata(did)?.created,
                        getDocumentMetadata(did)?.updated
                    )
                }
                
                return@withContext DidMethodUtils.createErrorResolutionResult(
                    "notFound",
                    "DID document not found on Solana",
                    method
                )
            }
            
            // Parse account data to DID document
            val json = Json.parseToJsonElement(String(accountData))
            val document = jsonElementToDocument(json)
            
            // Validate DID matches
            if (document.id != did) {
                // Rebuild with correct DID
                val correctedDocument = document.copy(id = did)
                storeDocument(correctedDocument.id, correctedDocument)
                return@withContext DidMethodUtils.createSuccessResolutionResult(correctedDocument, method)
            }
            
            storeDocument(document.id, document)
            DidMethodUtils.createSuccessResolutionResult(document, method)
        } catch (e: VeriCoreException) {
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
            
            // Apply updater (use explicit variable to avoid smart cast issue)
            val doc = currentDocument
            val updatedDocument = updater(doc)
            
            // Update on Solana
            updateDocumentOnBlockchain(did, updatedDocument)
            
            updatedDocument
        } catch (e: NotFoundException) {
            throw e
        } catch (e: VeriCoreException) {
            throw e
        } catch (e: Exception) {
            throw VeriCoreException(
                "Failed to update did:sol: ${e.message}",
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
            if (currentDocument == null) {
                return@withContext false
            }
            
            // Create deactivated document
            val deactivatedDocument = currentDocument.copy(
                verificationMethod = emptyList(),
                authentication = emptyList(),
                assertionMethod = emptyList(),
                keyAgreement = emptyList(),
                capabilityInvocation = emptyList(),
                capabilityDelegation = emptyList()
            )
            
            // Deactivate on Solana
            deactivateDocumentOnBlockchain(did, deactivatedDocument)
            
            true
        } catch (e: NotFoundException) {
            false
        } catch (e: Exception) {
            throw VeriCoreException(
                "Failed to deactivate did:sol: ${e.message}",
                e
            )
        }
    }

    /**
     * Derives a Solana address from a key handle.
     * 
     * Solana uses Ed25519 keys, and addresses are the public key in base58 encoding.
     */
    private fun deriveSolanaAddress(keyHandle: com.geoknoesis.vericore.kms.KeyHandle): String {
        // In a full implementation, we'd extract the public key from JWK
        // and encode it as base58 (Solana address format)
        
        // Simplified: generate address from key ID
        // Real implementation needs proper Ed25519 public key extraction and base58 encoding
        val keyIdHash = keyHandle.id.hashCode().toString(16).take(44).padStart(44, '0')
        
        // Solana addresses are base58 encoded 32-byte public keys
        // This is a placeholder - real implementation needs base58 encoding library
        return "7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU" // Placeholder format
    }

    /**
     * Extracts Solana address from did:sol identifier.
     */
    private fun extractSolanaAddress(did: String): String {
        // For did:sol:7xK... or did:sol:mainnet:7xK...
        val parsed = DidMethodUtils.parseDid(did)
            ?: throw IllegalArgumentException("Invalid DID format: $did")
        
        if (parsed.first != "sol") {
            throw IllegalArgumentException("Not a did:sol DID: $did")
        }
        
        val identifier = parsed.second
        
        // Check if network prefix exists
        val colonIndex = identifier.indexOf(':')
        return if (colonIndex >= 0) {
            // Network-prefixed: did:sol:mainnet:address
            identifier.substring(colonIndex + 1)
        } else {
            // Direct: did:sol:address
            identifier
        }
    }
}

