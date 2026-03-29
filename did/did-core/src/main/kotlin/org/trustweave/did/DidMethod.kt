package org.trustweave.did

import org.trustweave.did.identifiers.Did
import org.trustweave.did.model.DidDocument
import org.trustweave.did.model.MethodCapabilities
import org.trustweave.did.resolver.DidResolutionResult
import org.trustweave.did.validation.DidValidator

/**
 * Single-method resolver contract extracted from [DidMethod] for ISP compliance.
 *
 * Components that only need DID resolution (e.g. proof-engine verifiers, presentation
 * validators) can depend on this narrow interface instead of the full [DidMethod].
 *
 * Being a `fun interface`, it also enables SAM-conversion lambdas:
 * ```kotlin
 * val resolver: DidMethodResolver = DidMethodResolver { did ->
 *     // custom resolution logic
 * }
 * ```
 */
fun interface DidMethodResolver {
    /**
     * Resolves a DID to its DID Document.
     *
     * @param did Type-safe DID identifier
     * @return A [org.trustweave.did.resolver.DidResolutionResult] containing the document and metadata
     */
    suspend fun resolveDid(did: Did): DidResolutionResult
}

/**
 * Interface for DID method implementations.
 * Each DID method (e.g., "web", "key", "ion") should implement this interface.
 *
 * Extends [DidMethodResolver] so any [DidMethod] can be used wherever a resolver
 * is needed without wrapping.
 */
interface DidMethod : DidMethodResolver {

    /**
     * The DID method name (e.g., "web", "key", "ion").
     */
    val method: String

    /**
     * Capabilities advertised by this DID method implementation.
     *
     * Returns `null` when the implementation does not publish explicit capability metadata.
     * Callers should treat a `null` return as "capabilities unknown" and apply a conservative
     * default (e.g. resolve-only) rather than assuming full support.
     *
     * Implementations backed by a [org.trustweave.did.registration.model.DidRegistrationSpec]
     * (e.g. [org.trustweave.did.registrar.method.HttpDidMethod]) override this to return the
     * spec-declared capabilities so that discovery services can reflect them accurately.
     */
    val capabilities: MethodCapabilities? get() = null

    /**
     * Creates a new DID and returns its initial DID Document.
     *
     * @param options Method-specific options for DID creation
     * @return The initial DID Document
     */
    suspend fun createDid(options: DidCreationOptions = DidCreationOptions()): DidDocument

    /**
     * **Implementation Note:** Implementations should validate the DID format
     * before processing. The DID must start with "did:{method}:" where {method}
     * matches this method's name. Use [DidValidator.validateFormat] for validation.
     */
    override suspend fun resolveDid(did: Did): DidResolutionResult

    /**
     * Updates a DID Document.
     *
     * @param did Type-safe DID identifier to update
     * @param updater Function that transforms the current document to the new document
     * @return The updated DID Document
     */
    suspend fun updateDid(
        did: Did,
        updater: (DidDocument) -> DidDocument
    ): DidDocument

    /**
     * Deactivates a DID.
     *
     * @param did Type-safe DID identifier to deactivate
     * @return true if the DID was successfully deactivated
     */
    suspend fun deactivateDid(did: Did): Boolean
}

