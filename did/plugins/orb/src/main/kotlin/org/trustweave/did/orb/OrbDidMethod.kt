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

            val (createOp, longFormDid) = sidetree.buildCreateOperation(publicKeyJwk)
            val response = sidetree.submitOperation(createOp)

            val resolvedDid: String = response.did ?: longFormDid

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
            val updateOp = sidetree.buildUpdateOperation(did.value, updated)
            val response = sidetree.submitOperation(updateOp)
            if (!response.success) {
                throw OrbException.httpError(response.httpStatus, response.error ?: response.rawBody)
            }

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
     * Deactivates a did:orb DID by building a Sidetree deactivate operation
     * and submitting it to the Orb node, then removing the local cache entry.
     *
     * Returns `false` if the Orb node rejected the deactivation or the DID was
     * not previously known to this instance.
     */
    override suspend fun deactivateDid(did: Did): Boolean = withContext(Dispatchers.IO) {
        try {
            validateDidFormat(did)
            val deactivateOp = sidetree.buildDeactivateOperation(did.value)
            val response = sidetree.submitOperation(deactivateOp)
            if (!response.success) {
                return@withContext false
            }
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
     * Internal access to the underlying Sidetree client for tests and advanced users.
     */
    internal val sidetreeClient: SidetreeOrbClient get() = sidetree
}
