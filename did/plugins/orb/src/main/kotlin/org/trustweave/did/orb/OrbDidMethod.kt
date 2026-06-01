package org.trustweave.did.orb

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.DidCreationOptions
import org.trustweave.did.KeyPurpose
import org.trustweave.did.base.AbstractDidMethod
import org.trustweave.did.base.DidMethodUtils
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.parser.DidDocumentJsonParser
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.sidetree.InMemorySidetreeKeyStore
import org.trustweave.did.sidetree.SidetreeKeyPair
import org.trustweave.did.sidetree.SidetreeKeyStore
import org.trustweave.did.sidetree.SidetreeP256KeyPair
import org.trustweave.kms.KeyManagementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Implementation of the did:orb DID method.
 *
 * [Orb](https://trustbloc.github.io/did-method-orb/) is a federated, Sidetree-based
 * DID method built by the TrustBloc / DIF community. Orb nodes batch operations,
 * publish them through ActivityPub, and anchor batches via witnesses (typically
 * other Orb nodes or VCT logs).
 *
 * **DID formats**
 * - Short-form: `did:orb:<unique-suffix>` — points to an anchored DID.
 * - Long-form: `did:orb:<unique-suffix>:<base64url-of-create-request>` — usable
 *   immediately before anchoring completes (resolution is purely deterministic).
 *
 * **Operations**
 * - [createDid] generates ephemeral recovery/update keys, builds a Sidetree
 *   create operation, computes the deterministic long-form DID, and posts the
 *   operation to the Orb node.
 * - [resolveDid] performs a GET against the Orb identifiers endpoint and parses
 *   the returned DID resolution result.
 * - [updateDid] / [deactivateDid] build the corresponding Sidetree operations
 *   and submit them to the Orb node.
 *
 * **Example:**
 * ```kotlin
 * val cfg = OrbDidConfig(baseUrl = "https://orb.example.com")
 * val method = OrbDidMethod(InMemoryKeyManagementService(), cfg)
 * val doc = method.createDid(didCreationOptions { algorithm = KeyAlgorithm.P256 })
 * val resolved = method.resolveDid(doc.id)
 * ```
 */
class OrbDidMethod(
    kms: KeyManagementService,
    private val config: OrbDidConfig,
    httpClient: OkHttpClient? = null,
    private val keyStore: SidetreeKeyStore = InMemorySidetreeKeyStore(),
) : AbstractDidMethod("orb", kms) {

    private val client: OkHttpClient = httpClient ?: OkHttpClient.Builder()
        .connectTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(config.timeoutSeconds, TimeUnit.SECONDS)
        .build()

    private val sidetree = SidetreeOrbClient(client, config)

    /**
     * Creates a did:orb DID by generating a signing key, building a Sidetree
     * create operation, and submitting it to the Orb node.
     *
     * The returned DID is the long-form DID if the Orb node hasn't yet anchored
     * the operation. Callers can resolve it immediately via [resolveDid] (Orb
     * accepts long-form DIDs and returns the deterministic resolution).
     */
    override suspend fun createDid(options: DidCreationOptions): DidDocument = withContext(Dispatchers.IO) {
        try {
            val algorithm = options.algorithm.algorithmName
            val keyHandle = generateKey(algorithm, options.additionalProperties)

            val publicKeyJwk = keyHandle.publicKeyJwk
                ?: throw OrbException(
                    code = "ORB_MISSING_JWK",
                    message = "Public key JWK is required for did:orb (KMS returned a key with no JWK).",
                )

            val created = sidetree.buildCreateOperation(publicKeyJwk)
            val response = sidetree.submitOperation(created.operation)

            val resolvedDid: String = response.did ?: created.longFormDid

            keyStore.put(
                created.didSuffix,
                SidetreeKeyPair(
                    updatePrivateJwk = created.updateKeyPair.privateJwk,
                    updatePublicJwk = created.updateKeyPair.publicJwk,
                    recoveryPrivateJwk = created.recoveryKeyPair.privateJwk,
                    recoveryPublicJwk = created.recoveryKeyPair.publicJwk,
                ),
            )

            val verificationMethod = DidMethodUtils.createVerificationMethod(
                did = resolvedDid,
                keyHandle = keyHandle,
                algorithm = options.algorithm,
            )

            val document = DidMethodUtils.buildDidDocument(
                did = resolvedDid,
                verificationMethod = listOf(verificationMethod),
                authentication = listOf(verificationMethod.id.value),
                assertionMethod = if (options.purposes.contains(KeyPurpose.ASSERTION)) {
                    listOf(verificationMethod.id.value)
                } else {
                    null
                },
            )

            storeDocument(resolvedDid, document)
            document
        } catch (e: OrbException) {
            throw e
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                code = "ORB_CREATE_FAILED",
                message = "Failed to create did:orb: ${e.message}",
                cause = e,
            )
        }
    }

    /**
     * Resolves a did:orb DID by querying the Orb identifiers endpoint.
     *
     * Fallback order:
     * 1. Orb GET `/sidetree/v1/identifiers/{did}` — primary source of truth.
     * 2. On 404 with a local cache hit, returns the cached document. This keeps
     *    newly-created long-form DIDs resolvable before the Orb node has acked
     *    them.
     * 3. Otherwise returns a `notFound` failure.
     */
    override suspend fun resolveDid(did: Did): DidResolutionResult = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)
        } catch (e: Exception) {
            return@withContext DidMethodUtils.createErrorResolutionResult(
                "invalidDid",
                e.message,
                method,
                did.value,
            )
        }

        val response = sidetree.resolveDid(did.value)
        val document = response.document
        if (response.success && document != null) {
            val parsed = try {
                DidDocumentJsonParser.parse(document)
            } catch (e: Exception) {
                return@withContext DidMethodUtils.createErrorResolutionResult(
                    "invalidDid",
                    "Failed to parse Orb DID document: ${e.message}",
                    method,
                    did.value,
                )
            }
            storeDocument(did, parsed)
            return@withContext DidMethodUtils.createSuccessResolutionResult(
                parsed,
                method,
                getDocumentMetadata(did)?.created,
                getDocumentMetadata(did)?.updated,
            )
        }

        // Fallback: locally cached document (newly-created long-form DIDs, offline scenarios).
        val cached = getStoredDocument(did)
        if (cached != null) {
            return@withContext DidMethodUtils.createSuccessResolutionResult(
                cached,
                method,
                getDocumentMetadata(did)?.created,
                getDocumentMetadata(did)?.updated,
            )
        }

        DidMethodUtils.createErrorResolutionResult(
            "notFound",
            response.error ?: "Orb DID not found: ${did.value}",
            method,
            did.value,
        )
    }

    /**
     * Updates a did:orb DID by building a Sidetree update operation and
     * submitting it to the Orb node, then refreshing the local cache.
     */
    override suspend fun updateDid(
        did: Did,
        updater: (DidDocument) -> DidDocument,
    ): DidDocument = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)

            val current = when (val r = resolveDid(did)) {
                is DidResolutionResult.Success -> r.document
                else -> throw TrustWeaveException.NotFound(
                    resource = did.value,
                    message = "DID document not found: ${did.value}",
                )
            }

            val updated = updater(current)

            val suffix = extractSuffixOrThrow(did.value)
            val previousKeys = keyStore.get(suffix)
                ?: throw OrbException(
                    code = "ORB_KEYS_NOT_FOUND",
                    message = "Update keys for $did are not in the key store. " +
                        "Updates can only be issued by the instance that created the DID, " +
                        "or one configured with a persistent SidetreeKeyStore containing the prior keys.",
                )
            val nextUpdateKeyPair = sidetree.generateP256KeyPair()
            val previousUpdateKeyPair = SidetreeP256KeyPair(
                privateJwk = previousKeys.updatePrivateJwk,
                publicJwk = previousKeys.updatePublicJwk,
            )

            val updateOp = sidetree.buildUpdateOperation(
                did = did.value,
                updatedDocument = updated,
                previousUpdateKeyPair = previousUpdateKeyPair,
                nextUpdatePublicJwk = nextUpdateKeyPair.publicJwk,
            )
            val response = sidetree.submitOperation(updateOp)
            if (!response.success) {
                throw OrbException.httpError(response.httpStatus, response.error ?: response.rawBody)
            }

            keyStore.put(
                suffix,
                previousKeys.copy(
                    updatePrivateJwk = nextUpdateKeyPair.privateJwk,
                    updatePublicJwk = nextUpdateKeyPair.publicJwk,
                ),
            )
            storeDocument(did, updated)
            updated
        } catch (e: OrbException) {
            throw e
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                code = "ORB_UPDATE_FAILED",
                message = "Failed to update did:orb: ${e.message}",
                cause = e,
            )
        }
    }

    /**
     * Recovers a did:orb DID. Used when the update key has been lost or
     * compromised but the recovery key is still available. Rotates BOTH the
     * update and recovery keys in one operation and replaces the document state
     * with the result of [updater] (typically used to also rotate the signing
     * key embedded in the document).
     *
     * The previous recovery key is loaded from the [keyStore]; the operation
     * fails fast with `ORB_KEYS_NOT_FOUND` when it is absent.
     */
    suspend fun recoverDid(
        did: Did,
        updater: (DidDocument) -> DidDocument,
    ): DidDocument = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)

            val current = when (val r = resolveDid(did)) {
                is DidResolutionResult.Success -> r.document
                else -> throw TrustWeaveException.NotFound(
                    resource = did.value,
                    message = "DID document not found: ${did.value}",
                )
            }
            val recovered = updater(current)

            val suffix = extractSuffixOrThrow(did.value)
            val previousKeys = keyStore.get(suffix)
                ?: throw OrbException(
                    code = "ORB_KEYS_NOT_FOUND",
                    message = "Recovery keys for $did are not in the key store. " +
                        "Recover can only be issued by the instance that created the DID, " +
                        "or one configured with a persistent SidetreeKeyStore containing the prior keys.",
                )
            val previousRecoveryKeyPair = SidetreeP256KeyPair(
                privateJwk = previousKeys.recoveryPrivateJwk,
                publicJwk = previousKeys.recoveryPublicJwk,
            )
            val nextUpdateKeyPair = sidetree.generateP256KeyPair()
            val nextRecoveryKeyPair = sidetree.generateP256KeyPair()

            val recoverOp = sidetree.buildRecoverOperation(
                did = did.value,
                newDocument = recovered,
                previousRecoveryKeyPair = previousRecoveryKeyPair,
                nextUpdatePublicJwk = nextUpdateKeyPair.publicJwk,
                nextRecoveryPublicJwk = nextRecoveryKeyPair.publicJwk,
            )
            val response = sidetree.submitOperation(recoverOp)
            if (!response.success) {
                throw OrbException.httpError(response.httpStatus, response.error ?: response.rawBody)
            }

            keyStore.put(
                suffix,
                SidetreeKeyPair(
                    updatePrivateJwk = nextUpdateKeyPair.privateJwk,
                    updatePublicJwk = nextUpdateKeyPair.publicJwk,
                    recoveryPrivateJwk = nextRecoveryKeyPair.privateJwk,
                    recoveryPublicJwk = nextRecoveryKeyPair.publicJwk,
                ),
            )
            storeDocument(did, recovered)
            recovered
        } catch (e: OrbException) {
            throw e
        } catch (e: TrustWeaveException) {
            throw e
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                code = "ORB_RECOVER_FAILED",
                message = "Failed to recover did:orb: ${e.message}",
                cause = e,
            )
        }
    }

    /**
     * Deactivates a did:orb DID by building a Sidetree deactivate operation
     * and submitting it to the Orb node, then removing the local cache entry.
     *
     * Returns `false` if the Orb node rejected the deactivation or the DID was
     * not previously known to this instance.
     */
    override suspend fun deactivateDid(did: Did): Boolean = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)

            val suffix = extractSuffixOrThrow(did.value)
            val previousKeys = keyStore.get(suffix)
                ?: return@withContext false

            val previousRecoveryKeyPair = SidetreeP256KeyPair(
                privateJwk = previousKeys.recoveryPrivateJwk,
                publicJwk = previousKeys.recoveryPublicJwk,
            )

            val deactivateOp = sidetree.buildDeactivateOperation(
                did = did.value,
                previousRecoveryKeyPair = previousRecoveryKeyPair,
            )
            val response = sidetree.submitOperation(deactivateOp)
            if (!response.success) {
                return@withContext false
            }

            keyStore.remove(suffix)
            documents.remove(did.value)
            documentMetadata.remove(did.value)
            true
        } catch (e: OrbException) {
            throw e
        } catch (e: TrustWeaveException) {
            false
        } catch (e: Exception) {
            throw TrustWeaveException.Unknown(
                code = "ORB_DEACTIVATE_FAILED",
                message = "Failed to deactivate did:orb: ${e.message}",
                cause = e,
            )
        }
    }

    /**
     * Pick the Sidetree suffix out of any of the canonical Orb DID forms — including
     * the anchored form `did:orb:<anchor-segment>:<suffix>` that Orb's
     * `/sidetree/v1/operations` response returns. Delegates to the shared
     * [org.trustweave.did.sidetree.SidetreeOperationBuilder.extractDidSuffix].
     */
    private fun extractSuffixOrThrow(did: String): String =
        sidetree.builderForExtraction.extractDidSuffix(did)

    /**
     * Internal access to the underlying Sidetree client for tests and advanced users.
     */
    internal val sidetreeClient: SidetreeOrbClient get() = sidetree

    /**
     * Anchoring facade that surfaces Orb's `OperatorCredit` cost model so a
     * Trusted Domain Manager can account for Sidetree submissions. The Orb
     * node operator pays the underlying chain — this client only meters
     * operator credits (one per Sidetree operation) and enforces
     * [org.trustweave.anchor.payment.PaymentContext.maxFee].
     */
    val anchorClient: OrbAnchorClient = OrbAnchorClient(sidetree, config)
}
