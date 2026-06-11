package org.trustweave.ethrdid

import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.*
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.base.AbstractBlockchainDidMethod
import org.trustweave.did.base.DidMethodUtils
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.GetPublicKeyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.web3j.crypto.Keys
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.protocol.http.HttpService
import org.web3j.tx.RawTransactionManager
import org.web3j.tx.TransactionManager
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.util.Base64

/**
 * Implementation of did:ethr method for Ethereum blockchain.
 *
 * **⚠️ EXPERIMENTAL — NOT SPEC-COMPLIANT ⚠️**
 *
 * This implementation does NOT resolve DIDs through the ERC-1056 (`EthereumDIDRegistry`)
 * contract that the did:ethr specification is built on. Resolution relies on TrustWeave's
 * generic blockchain anchoring plus a local in-memory cache, so DIDs created by other
 * did:ethr implementations will generally not resolve here, and DIDs created here are not
 * visible to standard ethr-did resolvers. Use for experimentation only.
 *
 * What IS real: Ethereum addresses are derived correctly from the secp256k1 public key
 * (Keccak-256 over the 64-byte uncompressed key, last 20 bytes, EIP-55 checksummed), so
 * the `did:ethr:0x...` identifiers themselves are genuine Ethereum addresses.
 *
 * did:ethr uses Ethereum addresses as DID identifiers:
 * - Format: `did:ethr:{network}:{address}` or `did:ethr:{address}`
 * - Stores DID documents on Ethereum blockchain via anchoring
 * - Requires secp256k1 keys (other algorithms cannot derive an Ethereum address)
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
            // For did:ethr, we need to derive an Ethereum address from a key.
            // Only secp256k1 keys can produce an Ethereum address.
            val algorithm = options.algorithm.algorithmName
            if (algorithm.uppercase() != "SECP256K1") {
                throw IllegalArgumentException(
                    "did:ethr requires the secp256k1 algorithm: Ethereum addresses are derived " +
                        "from secp256k1 public keys. Requested: $algorithm"
                )
            }

            val keyHandle = generateKey(algorithm, options.additionalProperties)

            // Derive the Ethereum address from the secp256k1 public key (Keccak-256)
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
     * Derives the Ethereum address for a secp256k1 key handle.
     *
     * Standard Ethereum address derivation: Keccak-256 over the 64-byte uncompressed
     * secp256k1 public key (`x || y`, without the `0x04` prefix), last 20 bytes,
     * EIP-55 checksummed and `0x`-prefixed (via web3j [Keys]).
     *
     * The public key coordinates are read from the KMS key handle's JWK; if the handle
     * does not carry one, it is fetched from the KMS.
     *
     * @throws IllegalArgumentException if the key is not a secp256k1 key
     * @throws TrustWeaveException.Unknown if the KMS does not expose a usable public key JWK
     */
    private suspend fun deriveEthereumAddress(keyHandle: org.trustweave.kms.KeyHandle): String {
        if (!keyHandle.algorithm.equals("secp256k1", ignoreCase = true)) {
            throw IllegalArgumentException(
                "did:ethr requires a secp256k1 key to derive an Ethereum address, " +
                    "but key '${keyHandle.id.value}' uses algorithm '${keyHandle.algorithm}'"
            )
        }

        val jwk = keyHandle.publicKeyJwk
            ?: when (val result = kms.getPublicKey(keyHandle.id)) {
                is GetPublicKeyResult.Success -> result.keyHandle.publicKeyJwk
                is GetPublicKeyResult.Failure -> null
            }
            ?: throw TrustWeaveException.Unknown(
                code = "ETHR_ADDRESS_DERIVATION_FAILED",
                message = "Cannot derive Ethereum address: the KMS did not expose a public key " +
                    "JWK for key '${keyHandle.id.value}'"
            )

        val kty = jwk["kty"] as? String
        val crv = jwk["crv"] as? String
        if (!"EC".equals(kty, ignoreCase = true) || !"secp256k1".equals(crv, ignoreCase = true)) {
            throw TrustWeaveException.Unknown(
                code = "ETHR_ADDRESS_DERIVATION_FAILED",
                message = "Cannot derive Ethereum address: expected an EC/secp256k1 JWK for key " +
                    "'${keyHandle.id.value}', got kty='$kty', crv='$crv'"
            )
        }

        val x = decodeJwkCoordinate(jwk["x"], "x", keyHandle.id.value)
        val y = decodeJwkCoordinate(jwk["y"], "y", keyHandle.id.value)

        // Uncompressed public key without the 0x04 prefix: x || y (64 bytes).
        // Keys.getAddress = last 20 bytes of Keccak-256 over those 64 bytes.
        val publicKey = BigInteger(1, x + y)
        return Keys.toChecksumAddress(Keys.getAddress(publicKey))
    }

    /**
     * Decodes a base64url JWK EC coordinate, validating it fits the secp256k1 32-byte field.
     */
    private fun decodeJwkCoordinate(value: Any?, name: String, keyId: String): ByteArray {
        val encoded = value as? String
            ?: throw TrustWeaveException.Unknown(
                code = "ETHR_ADDRESS_DERIVATION_FAILED",
                message = "Cannot derive Ethereum address: JWK for key '$keyId' is missing " +
                    "the '$name' coordinate"
            )
        val bytes = try {
            Base64.getUrlDecoder().decode(encoded)
        } catch (e: IllegalArgumentException) {
            throw TrustWeaveException.Unknown(
                code = "ETHR_ADDRESS_DERIVATION_FAILED",
                message = "Cannot derive Ethereum address: JWK '$name' coordinate of key " +
                    "'$keyId' is not valid base64url",
                cause = e
            )
        }
        if (bytes.size > 32) {
            throw TrustWeaveException.Unknown(
                code = "ETHR_ADDRESS_DERIVATION_FAILED",
                message = "Cannot derive Ethereum address: JWK '$name' coordinate of key " +
                    "'$keyId' is ${bytes.size} bytes, expected at most 32 for secp256k1"
            )
        }
        // Left-pad to the 32-byte field width.
        return if (bytes.size == 32) bytes else ByteArray(32 - bytes.size) + bytes
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

