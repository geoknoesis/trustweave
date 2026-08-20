package org.trustweave.did.base

import org.trustweave.core.exception.TrustWeaveException
import org.trustweave.did.*
import org.trustweave.did.exception.DidException
import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.DidDocumentMetadata
import org.trustweave.did.parser.DidDocumentJsonParser
import org.trustweave.did.representation.DidDocumentJsonProducer
import org.trustweave.kms.KeyHandle
import org.trustweave.kms.KeyManagementService
import org.trustweave.kms.results.GenerateKeyResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Abstract base class for DID method implementations.
 *
 * Provides common functionality shared across DID method adapters:
 * - In-memory document storage for testing
 * - Common updateDid and deactivateDid implementations
 * - Document metadata helpers
 * - Error handling patterns
 *
 * Subclasses should implement:
 * - [createDid]: Create a new DID and return its initial DID Document
 * - [resolveDid]: Resolve a DID to its DID Document
 *
 * Pattern: Similar to `AbstractBlockchainAnchorClient` (~40% code reduction).
 *
 * **Example Usage:**
 * ```kotlin
 * class MyDidMethod(
 *     kms: KeyManagementService
 * ) : AbstractDidMethod("mymethod", kms) {
 *
 *     override suspend fun createDid(options: DidCreationOptions): DidDocument {
 *         // Implement method-specific creation logic
 *     }
 *
 *     override suspend fun resolveDid(did: Did): DidResolutionResult {
 *         // Implement method-specific resolution logic
 *     }
 * }
 * ```
 */
abstract class AbstractDidMethod(
    override val method: String,
    protected val kms: KeyManagementService,
) : DidMethod {
    /**
     * In-memory storage for DID documents (for testing and fallback).
     * Used by default implementations of updateDid and deactivateDid.
     */
    protected val documents = ConcurrentHashMap<String, DidDocument>()

    /**
     * Guards every mutation of [documents] and [documentMetadata].
     *
     * **Invariant: no code may write either map outside this lock.** Both maps are
     * `ConcurrentHashMap`s, so each individual write is atomic on its own — but the writers do
     * read-modify-write (read the existing metadata, merge, write back), and [storeDocument] runs
     * on *every successful resolve*. An unlocked write landing between another writer's read and
     * its write is therefore silently overwritten. For a deactivation that is a security defect,
     * not a bookkeeping one: the DID resolves live again, breaking the DID Resolution 1.0 §4.4
     * guarantee that a deactivated DID resolves to no document.
     *
     * The writers held to this invariant are [updateDid], [deactivateDid], [storeDocument],
     * [AbstractWebDidMethod.updateDocumentOnHttp], [AbstractWebDidMethod.deactivateDocumentOnHttp],
     * [AbstractBlockchainDidMethod.updateDocumentOnBlockchain],
     * [AbstractBlockchainDidMethod.deactivateDocumentOnBlockchain] and the inline cache-store in
     * `KeyDidMethod.resolveDid`.
     *
     * The `Mutex` is **not reentrant**: a holder that calls another lock-taking helper
     * self-deadlocks. Keep remote I/O (HTTP publish, chain anchor) and any call that reaches
     * [storeDocument] *outside* the critical section — the blockchain helpers in particular call
     * `anchorDocument()`, which stores, before taking the lock for their own writes.
     */
    protected val updateMutex = Mutex()

    /**
     * Document metadata storage.
     */
    protected val documentMetadata = ConcurrentHashMap<String, DidDocumentMetadata>()

    /**
     * When this instance last fetched (or wrote) each DID's document — i.e. how old the cached
     * copy in [documents] is.
     *
     * Deliberately **not** part of [DidDocumentMetadata]. §4.3 document metadata describes the
     * *document* (when it was created, when it was last Updated, whether it is deactivated); "when
     * did we last talk to the verifiable data registry" describes the *resolution process*, which
     * is §4.2 territory. Keeping it in a private map here means the §4.3 surface stays exactly
     * what the spec defines, and nothing about it can leak into a serialized document-metadata
     * structure by accident.
     *
     * Read it via [getLastFetched] and hand it to
     * [DidMethodUtils.createSuccessResolutionResult]'s `retrieved` parameter, which puts it on
     * §4.2 [org.trustweave.did.resolver.DidResolutionMetadata.retrieved] — the pre-existing,
     * spec-defined home for exactly this value, and where cache-freshness checks such as
     * `DecentralizedResolutionStrategy.isFresh()` now read it from.
     *
     * Written only under [updateMutex], alongside the maps it describes.
     */
    private val lastFetched = ConcurrentHashMap<String, Instant>()

    /**
     * Default implementation of updateDid using in-memory storage.
     *
     * Subclasses can override for methods that require external updates.
     */
    override suspend fun updateDid(
        did: Did,
        updater: (DidDocument) -> DidDocument,
    ): DidDocument =
        withContext(Dispatchers.IO) {
            validateDidFormat(did)

            val didString = did.value

            // Atomically read-compute-write both documents and metadata to avoid lost-update races.
            updateMutex.withLock {
                val current =
                    documents[didString]
                        ?: throw org.trustweave.did.exception.DidException.DidNotFound(
                            did = did,
                            availableMethods = listOf(method),
                        )
                val updatedDocument = updater(current)
                documents[didString] = updatedDocument
                val now = Clock.System.now()
                // A genuine Update operation, so §4.3 `updated` moves. Contrast storeDocument,
                // which is a cache-store and must leave `updated` alone.
                documentMetadata[didString] =
                    (documentMetadata[didString] ?: DidDocumentMetadata(created = now))
                        .copy(updated = now)
                lastFetched[didString] = now
                updatedDocument
            }
        }

    /**
     * Default implementation of deactivateDid using in-memory storage.
     *
     * Subclasses can override for methods that require external deactivation.
     */
    override suspend fun deactivateDid(did: Did): Boolean =
        withContext(Dispatchers.IO) {
            validateDidFormat(did)

            val didString = did.value
            // Both maps must be updated atomically under updateMutex so a concurrent
            // storeDocument/updateDid cannot observe a state where the document is gone
            // but the metadata still exists (or vice versa).
            var removed = false
            updateMutex.withLock {
                removed = documents.remove(didString) != null
                documentMetadata.remove(didString)
                lastFetched.remove(didString)
            }
            removed
        }

    /**
     * Validates that the DID matches this method's format.
     *
     * @param did The DID to validate
     * @throws DidException.InvalidDidFormat if the DID format is invalid
     */
    protected fun validateDidFormat(did: Did) {
        if (!did.value.startsWith("did:$method:")) {
            throw DidException.InvalidDidFormat(
                did = did.value,
                reason = "Expected did:$method:*, got ${did.value}",
            )
        }
        if (did.method != method) {
            throw DidException.InvalidDidFormat(
                did = did.value,
                reason = "Method mismatch: expected $method, got ${did.method}",
            )
        }
    }

    /**
     * Stores a DID document in memory.
     *
     * Useful for methods that need to cache resolved documents.
     *
     * **A cache-store is not a DID operation, and this method never pretends otherwise.** It runs
     * on every successful resolve, not only on writes, so it leaves existing [DidDocumentMetadata]
     * completely untouched: `created`, `updated` and `deactivated` all keep the values the last
     * real Create/Update/Deactivate operation gave them. Metadata is *seeded* (with `created`
     * only) the first time a DID is stored and never rewritten afterwards.
     *
     * That is what DID Resolution 1.0 §4.3 requires: `updated` is "the timestamp of the last
     * Update operation for the document version which was resolved", and is omitted entirely when
     * no Update operation has ever happened. This method used to bump `updated` to now on every
     * store, so a live DID reported a fabricated timestamp that advanced on every read. It also
     * makes the §7.3 rule fall out for free — deactivation is terminal, so a deactivated DID's
     * `updated` stays pinned at the deactivation time — rather than needing a special case, and it
     * means a re-cache can never resurrect a DID this instance recorded as deactivated.
     *
     * The genuinely useful "when did we last fetch this?" signal is recorded separately in
     * [lastFetched] and surfaced as §4.2 resolution metadata; see [getLastFetched].
     *
     * @param did The DID identifier (can be Did object or String)
     * @param document The DID document
     * @param created Optional creation timestamp (defaults to now); only used to seed metadata
     *   the first time this DID is stored — ignored once metadata already exists
     */
    protected suspend fun storeDocument(
        did: Any,
        document: DidDocument,
        created: Instant? = null,
    ) {
        val didString =
            when (did) {
                is Did -> did.value
                is String -> did
                else -> throw IllegalArgumentException("did must be Did or String, got ${did::class}")
            }
        // Acquire updateMutex so that both map writes are atomic with respect to
        // concurrent updateDid / deactivateDid / storeDocument calls (RACE-1). Reading existing
        // metadata and merging it here — rather than callers pre-reading it outside the lock —
        // also closes a lost-update race: a concurrent deactivateDid can no longer be missed
        // (a stale pre-lock read) or clobbered (an unconditional overwrite) by this store.
        updateMutex.withLock {
            val fetchedAt = Clock.System.now()
            documents[didString] = document
            lastFetched[didString] = fetchedAt
            // Seed §4.3 metadata on first store only. Never rewrite it: a cache-store is not a
            // Create, an Update or a Deactivate, so it has nothing to say about `created`,
            // `updated` or `deactivated`.
            documentMetadata.putIfAbsent(didString, DidDocumentMetadata(created = created ?: fetchedAt))
        }
    }

    /**
     * Gets a stored DID document.
     *
     * @param did The DID identifier (can be Did object or String)
     * @return The DID document or null if not found
     */
    protected fun getStoredDocument(did: Any): DidDocument? {
        val didString =
            when (did) {
                is Did -> did.value
                is String -> did
                else -> throw IllegalArgumentException("did must be Did or String, got ${did::class}")
            }
        return documents[didString]
    }

    /**
     * Gets document metadata.
     *
     * @param did The DID identifier (can be Did object or String)
     * @return The document metadata or null if not found
     */
    protected fun getDocumentMetadata(did: Any): DidDocumentMetadata? {
        val didString =
            when (did) {
                is Did -> did.value
                is String -> did
                else -> throw IllegalArgumentException("did must be Did or String, got ${did::class}")
            }
        return documentMetadata[didString]
    }

    /**
     * When this instance last fetched or wrote the DID's document, or `null` if it has never
     * stored one.
     *
     * This is the value to pass as `retrieved` to
     * [DidMethodUtils.createSuccessResolutionResult]: it becomes §4.2
     * [org.trustweave.did.resolver.DidResolutionMetadata.retrieved], which is how a caching
     * resolver (e.g. `DecentralizedResolutionStrategy`) judges whether a locally cached answer is
     * still fresh. Do not substitute §4.3 `updated` for it — `updated` moves only on a real Update
     * operation and may be years old (or absent) on a document that was fetched a second ago.
     *
     * @param did The DID identifier (can be Did object or String)
     */
    protected fun getLastFetched(did: Any): Instant? {
        val didString =
            when (did) {
                is Did -> did.value
                is String -> did
                else -> throw IllegalArgumentException("did must be Did or String, got ${did::class}")
            }
        return lastFetched[didString]
    }

    /**
     * Creates resolution metadata for successful resolution.
     *
     * @return Map of resolution metadata
     */
    protected fun createSuccessResolutionMetadata(): Map<String, Any?> =
        mapOf(
            "method" to method,
            "driver" to this.javaClass.simpleName,
        )

    /**
     * Creates resolution metadata for error cases.
     *
     * @param error Error code
     * @param message Error message
     * @return Map of resolution metadata
     */
    protected fun createErrorResolutionMetadata(
        error: String,
        message: String? = null,
    ): Map<String, Any?> =
        buildMap {
            put("error", error)
            if (message != null) {
                put("errorMessage", message)
            }
            put("method", method)
        }

    /**
     * Serialises a [DidDocument] to a JSON representation (DID 1.1, v1.1 @context).
     *
     * Single definition shared by all AbstractDidMethod subclasses.
     * Delegates to [DidDocumentJsonProducer].
     */
    protected fun documentToJsonElement(document: DidDocument): JsonElement =
        DidDocumentJsonProducer.toJsonObject(document, useV1_1Context = true)

    /**
     * Deserialises a [JsonElement] to a [DidDocument] using the canonical [DidDocumentJsonParser].
     *
     * Replaces the duplicated hand-rolled parsing that previously lived in
     * [AbstractWebDidMethod] and [AbstractBlockchainDidMethod].
     */
    protected fun jsonElementToDocument(json: JsonElement): DidDocument = DidDocumentJsonParser.parse(json.jsonObject)

    /**
     * Generates a key via the KMS and returns the [KeyHandle].
     *
     * Encapsulates the duplicated [GenerateKeyResult] when-expression that previously appeared in
     * every DID method's createDid() implementation. Throws [TrustWeaveException.Unknown] for all
     * failure cases so callers only need to handle the happy path.
     *
     * @param algorithm The key algorithm name (e.g. "Ed25519", "secp256k1").
     * @param options   Additional properties forwarded to the KMS (e.g. from [DidCreationOptions.additionalProperties]).
     * @return The [KeyHandle] for the generated key.
     */
    protected suspend fun generateKey(
        algorithm: String,
        options: Map<String, Any?> = emptyMap(),
    ): KeyHandle =
        when (val result = kms.generateKey(algorithm, options)) {
            is GenerateKeyResult.Success -> result.keyHandle
            is GenerateKeyResult.Failure.UnsupportedAlgorithm -> throw TrustWeaveException.Unknown(
                code = "UNSUPPORTED_ALGORITHM",
                message = result.reason ?: "Algorithm not supported: $algorithm",
            )
            is GenerateKeyResult.Failure.InvalidOptions -> throw TrustWeaveException.Unknown(
                code = "INVALID_OPTIONS",
                message = result.reason,
                cause = result.cause,
            )
            is GenerateKeyResult.Failure.DuplicateKeyId -> throw TrustWeaveException.Unknown(
                code = "DUPLICATE_KEY_ID",
                message = "Key with ID '${result.keyId.value}' already exists",
            )
            is GenerateKeyResult.Failure.Error -> throw TrustWeaveException.Unknown(
                code = "KEY_GENERATION_ERROR",
                message = result.reason,
                cause = result.cause,
            )
        }
}
