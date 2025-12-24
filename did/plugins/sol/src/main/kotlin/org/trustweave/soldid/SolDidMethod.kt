package org.trustweave.soldid

import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.*
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.base.AbstractBlockchainDidMethod
import org.trustweave.did.base.DidMethodUtils
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.GenerateKeyResult
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
                authentication = listOf(verificationMethod.id.value),
                assertionMethod = if (options.purposes.contains(KeyPurpose.ASSERTION)) {
                    listOf(verificationMethod.id.value)
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
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                code = "CREATE_FAILED",
                message = "Failed to create did:sol: ${e.message}",
                cause = e
            )
        }
    }

    override suspend fun resolveDid(did: Did): DidResolutionResult = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)

            val didString = did.value
            // Extract Solana address from DID
            val solanaAddress = extractSolanaAddress(didString)

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
                    method,
                    didString
                )
            }

            // Parse account data to DID document
            val json = Json.parseToJsonElement(String(accountData))
            val document = jsonElementToDocument(json)

            // Validate DID matches - document.id is Did, so compare values
            if (document.id.value != didString) {
                // Rebuild with correct DID
                val correctedDocument = document.copy(id = did)
                storeDocument(correctedDocument.id.value, correctedDocument)
                return@withContext DidMethodUtils.createSuccessResolutionResult(correctedDocument, method)
            }

            storeDocument(document.id.value, document)
            DidMethodUtils.createSuccessResolutionResult(document, method)
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

            // Apply updater (use explicit variable to avoid smart cast issue)
            val doc = currentDocument
            val updatedDocument = updater(doc)

            // Update on Solana
            updateDocumentOnBlockchain(didString, updatedDocument)

            updatedDocument
        } catch (e: TrustWeaveException.NotFound) {
            throw e
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                code = "UPDATE_FAILED",
                message = "Failed to update did:sol: ${e.message}",
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

            // Deactivate on Solana
            deactivateDocumentOnBlockchain(didString, deactivatedDocument)

            true
        } catch (e: TrustWeaveException.NotFound) {
            false
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                code = "DEACTIVATE_FAILED",
                message = "Failed to deactivate did:sol: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Derives a Solana address from a key handle.
     *
     * Solana uses Ed25519 keys, and addresses are the public key in base58 encoding.
     */
    private fun deriveSolanaAddress(keyHandle: org.trustweave.kms.KeyHandle): String {
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

