package com.trustweave.did.registrar

import com.trustweave.core.exception.TrustWeaveException
import com.trustweave.did.model.DidDocument
import com.trustweave.did.registrar.model.*

/**
 * Interface for DID Registrar implementations.
 *
 * A DID Registrar is responsible for creating, updating, and deactivating DIDs
 * according to the DID Registration specification (https://identity.foundation/did-registration/).
 *
 * This interface provides a method-agnostic abstraction for DID lifecycle operations,
 * allowing different DID methods to delegate registration operations to registrar services
 * (such as Universal Registrar) or implement them directly.
 *
 * **Example Usage:**
 * ```kotlin
 * // Using a registrar with a DID method
 * class MyDidMethod(
 *     override val method: String,
 *     private val registrar: DidRegistrar?
 * ) : DidMethod {
 *     override suspend fun createDid(options: DidCreationOptions): DidDocument {
 *         val specOptions = CreateDidOptions(
 *             keyManagementMode = KeyManagementMode.INTERNAL_SECRET,
 *             methodSpecificOptions = buildMap {
 *                 put("algorithm", options.algorithm.algorithmName)
 *                 put("purposes", options.purposes.map { it.purposeName })
 *             }
 *         )
 *         val response = registrar?.createDid(method, specOptions)
 *             ?: throw TrustWeaveException("Registrar not available")
 *         return response.didState.didDocument
 *             ?: throw TrustWeaveException("DID creation failed: ${response.didState.state}")
 *     }
 * }
 * ```
 */
interface DidRegistrar {

    /**
     * Creates a new DID according to the DID Registration specification.
     *
     * Returns a [DidRegistrationResponse] containing:
     * - `jobId`: For long-running operations (if applicable)
     * - `didState`: Current state of the operation (finished, failed, action, wait)
     *
     * @param method The DID method name (e.g., "web", "key", "ion")
     * @param options Creation options according to DID Registration spec
     * @return Registration response with jobId and didState
     * @throws TrustWeaveException if creation fails
     */
    suspend fun createDid(method: String, options: CreateDidOptions): DidRegistrationResponse

    /**
     * Updates a DID Document according to the DID Registration specification.
     *
     * Returns a [DidRegistrationResponse] containing:
     * - `jobId`: For long-running operations (if applicable)
     * - `didState`: Current state of the operation
     *
     * @param did The DID to update
     * @param document The updated DID Document
     * @param options Update options according to DID Registration spec
     * @return Registration response with jobId and didState
     * @throws TrustWeaveException if update fails
     */
    suspend fun updateDid(
        did: String,
        document: DidDocument,
        options: UpdateDidOptions = UpdateDidOptions()
    ): DidRegistrationResponse

    /**
     * Deactivates a DID according to the DID Registration specification.
     *
     * Returns a [DidRegistrationResponse] containing:
     * - `jobId`: For long-running operations (if applicable)
     * - `didState`: Current state of the operation
     *
     * @param did The DID to deactivate
     * @param options Deactivation options according to DID Registration spec
     * @return Registration response with jobId and didState
     * @throws TrustWeaveException if deactivation fails
     */
    suspend fun deactivateDid(
        did: String,
        options: DeactivateDidOptions = DeactivateDidOptions()
    ): DidRegistrationResponse
}

