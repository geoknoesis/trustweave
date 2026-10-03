package org.trustweave.soldid

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.core.util.decodeBase58
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.KeyPurpose
import org.trustweave.did.base.AbstractBlockchainDidMethod
import org.trustweave.did.base.DidMethodUtils
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.kms.KeyManagementService

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
    private val config: SolDidConfig,
) : AbstractBlockchainDidMethod("sol", kms) {
    private val httpClient: OkHttpClient
    private val solanaClient: SolanaClient

    init {
        httpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()

        solanaClient = SolanaClient(httpClient, config)
    }

    override fun getBlockchainAnchorClient(): BlockchainAnchorClient = anchorClient

    override fun getChainId(): String = "solana:${config.network}"

    override suspend fun canSubmitTransaction(): Boolean = config.privateKey != null

    override suspend fun findDocumentTxHash(did: String): String? {
        // For Solana, we derive the account address from the DID
        // In a full implementation, we'd query the Solana program
        return null
    }

    override suspend fun createDid(options: DidCreationOptions): DidDocument =
        withContext(Dispatchers.IO) {
            try {
                // Generate Ed25519 key (Solana uses Ed25519)
                val algorithm = options.algorithm.algorithmName
                if (algorithm.uppercase() != "ED25519") {
                    throw IllegalArgumentException("did:sol requires Ed25519 algorithm")
                }

                val keyHandle = generateKey(algorithm, options.additionalProperties)

                // Derive Solana address from public key
                val solanaAddress = deriveSolanaAddress(keyHandle)

                // Build DID identifier
                val did =
                    if (config.network == SolDidConfig.MAINNET) {
                        "did:sol:$solanaAddress"
                    } else {
                        "did:sol:${config.network}:$solanaAddress"
                    }

                // Create verification method
                val verificationMethod =
                    DidMethodUtils.createVerificationMethod(
                        did = did,
                        keyHandle = keyHandle,
                        algorithm = options.algorithm,
                    )

                // Build DID document
                val document =
                    DidMethodUtils.buildDidDocument(
                        did = did,
                        verificationMethod = listOf(verificationMethod),
                        authentication = listOf(verificationMethod.id.value),
                        assertionMethod =
                            if (options.purposes.contains(KeyPurpose.ASSERTION)) {
                                listOf(verificationMethod.id.value)
                            } else {
                                null
                            },
                    )

                // Anchor the document. A failure here fails the creation: reporting a DID as created
                // when nothing was anchored would hand out an identifier no one else can resolve.
                anchorDocument(document)

                document
            } catch (e: TrustWeaveException) {
                throw e
            } catch (e: IllegalArgumentException) {
                throw e
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                throw TrustWeaveException.Unknown(
                    code = "CREATE_FAILED",
                    message = "Failed to create did:sol: ${e.message}",
                    cause = e,
                )
            }
        }

    override suspend fun resolveDid(did: Did): DidResolutionResult =
        withContext(Dispatchers.IO) {
            try {
                validateDidFormat(did)

                val didString = did.value
                // Extract Solana address from DID
                val solanaAddress = extractSolanaAddress(didString)

                // Resolve from Solana program account
                val accountData = solanaClient.getAccountData(solanaAddress)

                if (accountData == null) {
                    // The on-chain account is gone (or never existed). A cached copy must NOT be
                    // served in its place: that would keep a removed document resolving as live.
                    // The only thing the cache may still contribute is fail-safe: if this
                    // instance itself recorded the DID as deactivated, say so.
                    val stored = getStoredDocument(did)
                    val metadata = getDocumentMetadata(did)
                    if (stored != null && metadata?.deactivated == true) {
                        return@withContext DidMethodUtils.createSuccessResolutionResult(
                            stored,
                            method,
                            metadata.created,
                            metadata.updated,
                            true,
                            retrieved = getLastFetched(did),
                        )
                    }

                    return@withContext DidMethodUtils.createErrorResolutionResult(
                        "notFound",
                        "DID document not found on Solana",
                        method,
                        didString,
                    )
                }

                // Parse account data to DID document
                val json = Json.parseToJsonElement(String(accountData))
                val document = jsonElementToDocument(json)

                // The account data is untrusted: a document whose id is a different DID is rejected,
                // never rewritten to the requested DID (that would let any account impersonate it)
                // and never cached.
                if (document.id.value != didString) {
                    return@withContext DidMethodUtils.createErrorResolutionResult(
                        "invalidDidDocument",
                        "Document ID mismatch: expected $didString, got ${document.id.value}",
                        method,
                        didString,
                    )
                }

                storeDocument(document.id.value, document)
                DidMethodUtils.createSuccessResolutionResult(document, method)
            } catch (e: org.trustweave.did.exception.DidException.InvalidDidFormat) {
                DidMethodUtils.createErrorResolutionResult("invalidDid", e.message, method, did.value)
            } catch (e: IllegalArgumentException) {
                DidMethodUtils.createErrorResolutionResult("invalidDid", e.message, method, did.value)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: kotlinx.serialization.SerializationException) {
                DidMethodUtils.createErrorResolutionResult("invalidDidDocument", e.message, method, did.value)
            } catch (e: Exception) {
                // RPC/transport failures are internal errors, not a verdict on the DID's syntax.
                DidMethodUtils.createErrorResolutionResult("internalError", e.message, method, did.value)
            }
        }

    override suspend fun updateDid(
        did: Did,
        updater: (DidDocument) -> DidDocument,
    ): DidDocument =
        withContext(Dispatchers.IO) {
            try {
                validateDidFormat(did)

                val didString = did.value
                // Resolve current document
                val currentResult = resolveDid(did)
                val currentDocument =
                    when (currentResult) {
                        is DidResolutionResult.Success -> currentResult.document
                        else -> throw TrustWeaveException.NotFound(
                            message = "DID document not found: $didString",
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                throw TrustWeaveException.Unknown(
                    code = "UPDATE_FAILED",
                    message = "Failed to update did:sol: ${e.message}",
                    cause = e,
                )
            }
        }

    override suspend fun deactivateDid(did: Did): Boolean =
        withContext(Dispatchers.IO) {
            try {
                validateDidFormat(did)

                val didString = did.value

                // Resolve current document
                val currentResult = resolveDid(did)
                val currentDocument =
                    when (currentResult) {
                        is DidResolutionResult.Success -> currentResult.document
                        else -> return@withContext false
                    }

                // Create deactivated document
                val deactivatedDocument =
                    currentDocument.copy(
                        verificationMethod = emptyList(),
                        authentication = emptyList(),
                        assertionMethod = emptyList(),
                        keyAgreement = emptyList(),
                        capabilityInvocation = emptyList(),
                        capabilityDelegation = emptyList(),
                    )

                // Deactivate on Solana
                deactivateDocumentOnBlockchain(didString, deactivatedDocument)

                true
            } catch (e: TrustWeaveException.NotFound) {
                false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                throw TrustWeaveException.Unknown(
                    code = "DEACTIVATE_FAILED",
                    message = "Failed to deactivate did:sol: ${e.message}",
                    cause = e,
                )
            }
        }

    /**
     * Derives a Solana address from a key handle: the base58 encoding of the 32-byte Ed25519
     * public key (see [solanaAddressFromKeyHandle]).
     */
    private fun deriveSolanaAddress(keyHandle: org.trustweave.kms.KeyHandle): String = solanaAddressFromKeyHandle(keyHandle)

    /**
     * Extracts Solana address from did:sol identifier.
     */
    private fun extractSolanaAddress(did: String): String {
        // For did:sol:7xK... or did:sol:mainnet:7xK...
        val parsed =
            DidMethodUtils.parseDid(did)
                ?: throw IllegalArgumentException("Invalid DID format: $did")

        if (parsed.first != "sol") {
            throw IllegalArgumentException("Not a did:sol DID: $did")
        }

        val identifier = parsed.second

        // Check if network prefix exists. The network the DID names must be the network this
        // instance talks to: a devnet DID is never answered from a mainnet RPC (or the reverse),
        // since the same address on two clusters is two unrelated accounts. A DID without a
        // network segment is a mainnet DID.
        val colonIndex = identifier.indexOf(':')
        val didNetwork = if (colonIndex >= 0) identifier.substring(0, colonIndex) else SolDidConfig.MAINNET
        require(didNetwork == config.network) {
            "did:sol network '$didNetwork' does not match the configured network '${config.network}': $did"
        }
        val address =
            if (colonIndex >= 0) {
                // Network-prefixed: did:sol:mainnet:address
                identifier.substring(colonIndex + 1)
            } else {
                // Direct: did:sol:address
                identifier
            }
        // A Solana address is the base58 encoding of a 32-byte public key.
        val decoded =
            try {
                address.decodeBase58()
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid Solana address in $did: not base58", e)
            }
        require(decoded.size == 32) { "Invalid Solana address in $did: expected 32 bytes, got ${decoded.size}" }
        return address
    }
}
