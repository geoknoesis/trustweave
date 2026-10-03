package org.trustweave.cheqddid

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.trustweave.anchor.BlockchainAnchorClient
import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.core.util.decodeBase58
import org.trustweave.core.util.encodeBase58
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.KeyPurpose
import org.trustweave.did.base.AbstractBlockchainDidMethod
import org.trustweave.did.base.DidMethodUtils
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.kms.KeyManagementService
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of did:cheqd method for Cheqd network.
 *
 * did:cheqd uses Cheqd blockchain for DID resolution with payment features:
 * - Format: `did:cheqd:{network}:{identifier}`
 * - Stores DID documents on Cheqd blockchain
 * - Supports payment-enabled DID operations
 *
 * **Example Usage:**
 * ```kotlin
 * val kms = InMemoryKeyManagementService()
 * val config = CheqdDidConfig.mainnet("https://api.cheqd.net")
 * val anchorClient = createCheqdAnchorClient(config)
 * val method = CheqdDidMethod(kms, anchorClient, config)
 *
 * // Create DID
 * val options = didCreationOptions {
 *     algorithm = KeyAlgorithm.ED25519
 * }
 * val document = method.createDid(options)
 *
 * // Resolve DID
 * val result = method.resolveDid("did:cheqd:mainnet:...")
 * ```
 */
class CheqdDidMethod(
    kms: KeyManagementService,
    private val anchorClient: BlockchainAnchorClient,
    private val config: CheqdDidConfig,
) : AbstractBlockchainDidMethod("cheqd", kms) {
    private val httpClient: OkHttpClient

    init {
        httpClient =
            OkHttpClient
                .Builder()
                .connectTimeout(config.timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(config.timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(config.timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
                .build()
    }

    override fun getBlockchainAnchorClient(): BlockchainAnchorClient = anchorClient

    override fun getChainId(): String = "cheqd:${config.network}"

    override suspend fun canSubmitTransaction(): Boolean = config.privateKey != null || config.accountAddress != null

    /**
     * Transaction hashes of documents this instance anchored (create/update). There is no
     * on-chain index from DID to anchor transaction available here, so a DID this instance did not
     * anchor is resolved through the Cheqd REST API ([CheqdDidConfig.cheqdApiUrl]) instead, and
     * resolution fails with notFound when neither source knows it.
     */
    private val didToTxHash = ConcurrentHashMap<String, String>()

    override suspend fun findDocumentTxHash(did: String): String? = didToTxHash[did]

    override suspend fun createDid(options: DidCreationOptions): DidDocument =
        withContext(Dispatchers.IO) {
            try {
                val algorithm = options.algorithm.algorithmName
                val keyHandle = generateKey(algorithm, options.additionalProperties)

                // Create DID identifier
                val did = generateCheqdDid(keyHandle)

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

                // Anchor the document. A failure fails the creation: reporting a DID as created when
                // nothing was anchored would hand out an identifier no one else can resolve.
                didToTxHash[did] = anchorDocument(document)

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
                    message = "Failed to create did:cheqd: ${e.message}",
                    cause = e,
                )
            }
        }

    override suspend fun resolveDid(did: Did): DidResolutionResult =
        withContext(Dispatchers.IO) {
            try {
                validateDidFormat(did)
                val didString = did.value
                validateCheqdIdentifier(didString)

                // Anchored (or previously resolved) by this instance: read it from the chain via the
                // anchor client; resolveFromBlockchain fails loudly if that read fails.
                val txHash = findDocumentTxHash(didString)
                if (txHash != null || getStoredDocument(did) != null) {
                    return@withContext resolveFromBlockchain(didString, txHash)
                }

                val resolved =
                    resolveFromCheqdApi(didString)
                        ?: return@withContext DidMethodUtils.createErrorResolutionResult(
                            "notFound",
                            if (config.cheqdApiUrl == null) {
                                "DID was not anchored by this instance and no cheqdApiUrl is configured to look it up"
                            } else {
                                "DID document not found on the Cheqd network"
                            },
                            method,
                            didString,
                        )
                storeDocument(resolved.id.value, resolved)
                DidMethodUtils.createSuccessResolutionResult(resolved, method, retrieved = getLastFetched(did))
            } catch (e: TrustWeaveException.NotFound) {
                DidMethodUtils.createErrorResolutionResult("notFound", e.message, method, did.value)
            } catch (e: org.trustweave.did.exception.DidException.InvalidDidFormat) {
                DidMethodUtils.createErrorResolutionResult("invalidDid", e.message, method, did.value)
            } catch (e: IllegalArgumentException) {
                DidMethodUtils.createErrorResolutionResult("invalidDid", e.message, method, did.value)
            } catch (e: TrustWeaveException) {
                val error = if (e.code == DOCUMENT_ID_MISMATCH) "invalidDidDocument" else "internalError"
                DidMethodUtils.createErrorResolutionResult(error, e.message, method, did.value)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
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

                // Update on Cheqd blockchain
                didToTxHash[didString] = updateDocumentOnBlockchain(didString, updatedDocument)

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
                    message = "Failed to update did:cheqd: ${e.message}",
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

                // Deactivate on Cheqd blockchain
                deactivateDocumentOnBlockchain(didString, deactivatedDocument)

                true
            } catch (e: TrustWeaveException.NotFound) {
                false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                throw TrustWeaveException.Unknown(
                    code = "DEACTIVATE_FAILED",
                    message = "Failed to deactivate did:cheqd: ${e.message}",
                    cause = e,
                )
            }
        }

    /**
     * Generates a Cheqd DID identifier per the did:cheqd method specification: for an Ed25519
     * key, the base58btc encoding of the first 16 bytes of the public key; for any other key, a
     * random UUID (the specification allows UUIDs for any DID).
     */
    private fun generateCheqdDid(keyHandle: org.trustweave.kms.KeyHandle): String {
        val identifier =
            ed25519PublicKey(keyHandle)?.copyOfRange(0, 16)?.encodeBase58()
                ?: UUID.randomUUID().toString()
        return "did:cheqd:${config.network}:$identifier"
    }

    private fun ed25519PublicKey(keyHandle: org.trustweave.kms.KeyHandle): ByteArray? {
        if (!keyHandle.algorithm.equals("Ed25519", ignoreCase = true)) return null
        (keyHandle.publicKeyJwk?.get("x") as? String)?.let { x ->
            return java.util.Base64
                .getUrlDecoder()
                .decode(x)
                .takeIf { it.size == 32 }
                ?: throw TrustWeaveException.Unknown(code = "INVALID_PUBLIC_KEY", message = "Ed25519 JWK 'x' is not 32 bytes")
        }
        val multibase =
            keyHandle.publicKeyMultibase
                ?: throw TrustWeaveException.Unknown(
                    code = "MISSING_PUBLIC_KEY",
                    message = "Key '${keyHandle.id.value}' exposes no public key to derive a did:cheqd identifier from",
                )
        require(multibase.startsWith("z")) { "Unsupported multibase encoding for key '${keyHandle.id.value}'" }
        val (algorithm, key) =
            DidMethodUtils.parseMulticodecKey(multibase.substring(1).decodeBase58())
                ?: throw TrustWeaveException.Unknown(code = "INVALID_PUBLIC_KEY", message = "Unrecognised multikey")
        require(algorithm == "ED25519") { "Expected an Ed25519 multikey, got $algorithm" }
        return key
    }

    /**
     * Checks the method-specific id: `[network:]identifier`, where the identifier is a UUID or
     * the base58btc encoding of 16 bytes.
     */
    private fun validateCheqdIdentifier(did: String) {
        val msid = did.removePrefix("did:cheqd:")
        val parts = msid.split(":")
        require(parts.size in 1..2 && parts.none { it.isEmpty() }) { "Invalid did:cheqd identifier: $did" }
        val id = parts.last()
        val isUuid = runCatching { UUID.fromString(id) }.isSuccess && id.length == 36
        val isBase58 = runCatching { id.decodeBase58().size == 16 }.getOrDefault(false)
        require(isUuid || isBase58) { "Invalid did:cheqd identifier (expected a UUID or base58 of 16 bytes): $did" }
    }

    /**
     * Resolves a DID document from the Cheqd REST API.
     *
     * @return the document, or `null` when no API is configured or the API answers 404
     * @throws TrustWeaveException when the API fails or returns a document for a different DID
     */
    private suspend fun resolveFromCheqdApi(did: String): DidDocument? =
        withContext(Dispatchers.IO) {
            val apiUrl = config.cheqdApiUrl ?: return@withContext null

            val request =
                Request
                    .Builder()
                    .url("$apiUrl/dids/$did")
                    .get()
                    .addHeader("Accept", "application/json")
                    .build()

            val jsonString =
                httpClient.newCall(request).execute().use { response ->
                    if (response.code == 404) return@withContext null
                    if (!response.isSuccessful) {
                        throw TrustWeaveException.Unknown(
                            code = "RESOLVE_FAILED",
                            message = "Cheqd API returned HTTP ${response.code} for $did",
                        )
                    }
                    response.body?.string()
                        ?: throw TrustWeaveException.Unknown(code = "EMPTY_RESPONSE", message = "Empty Cheqd API response")
                }

            val document = jsonElementToDocument(Json.parseToJsonElement(jsonString))
            // A document for a different DID is rejected, never rewritten to the requested DID.
            if (document.id.value != did) {
                throw TrustWeaveException(
                    code = DOCUMENT_ID_MISMATCH,
                    message = "Cheqd API returned a document for ${document.id.value}, expected $did",
                )
            }
            document
        }
}
