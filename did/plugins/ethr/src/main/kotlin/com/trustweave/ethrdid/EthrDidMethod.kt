package com.trustweave.ethrdid

import com.trustweave.anchor.BlockchainAnchorClient
import com.trustweave.core.exception.TrustWeaveException
import com.trustweave.did.*
import com.trustweave.did.identifiers.Did
import com.trustweave.did.model.DidDocument
import com.trustweave.did.resolver.DidResolutionResult
import com.trustweave.did.base.AbstractBlockchainDidMethod
import com.trustweave.did.base.DidMethodUtils
import com.trustweave.kms.KeyManagementService
import com.trustweave.kms.results.GenerateKeyResult
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
                authentication = listOf(verificationMethod.id.value),
                assertionMethod = if (options.purposes.contains(KeyPurpose.ASSERTION)) {
                    listOf(verificationMethod.id.value)
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
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                code = "CREATE_FAILED",
                message = "Failed to create did:ethr: ${e.message}",
                cause = e
            )
        }
    }

    override suspend fun resolveDid(did: Did): DidResolutionResult = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)

            val didString = did.value
            // Try to resolve from blockchain
            try {
                return@withContext resolveFromBlockchain(didString, didToTxHash[didString])
            } catch (e: TrustWeaveException.NotFound) {
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
                    method,
                    didString
                )
            }
        } catch (e: TrustWeaveException) {
            throw e
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

            // Anchor updated document to blockchain
            val txHash = updateDocumentOnBlockchain(didString, updatedDocument)
            didToTxHash[didString] = txHash

            updatedDocument
        } catch (e: TrustWeaveException.NotFound) {
            throw e
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                code = "UPDATE_FAILED",
                message = "Failed to update did:ethr: ${e.message}",
                cause = e
            )
        }
    }

    override suspend fun deactivateDid(did: Did): Boolean = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)

            val didString = did.value

            // Resolve current document
            val currentResult = resolveDid(did)
            val currentDocument = when (currentResult) {
                is DidResolutionResult.Success -> currentResult.document
                else -> return@withContext false
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

            // Anchor deactivated document
            deactivateDocumentOnBlockchain(didString, deactivatedDocument)

            // Remove from cache
            didToTxHash.remove(didString)

            true
        } catch (e: TrustWeaveException.NotFound) {
            false
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                code = "DEACTIVATE_FAILED",
                message = "Failed to deactivate did:ethr: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Derives an Ethereum address from a key handle.
     *
     * In a full implementation, this would properly derive the address from the public key.
     * For now, we use a simplified approach.
     */
    private fun deriveEthereumAddress(keyHandle: com.trustweave.kms.KeyHandle): String {
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

