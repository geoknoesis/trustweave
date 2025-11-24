package com.trustweave.did.registrar.adapter

import com.trustweave.did.DidDocument
import com.trustweave.did.registrar.model.*

/**
 * Protocol adapter for Universal Registrar implementations.
 * 
 * Similar to [UniversalResolverProtocolAdapter], this interface abstracts
 * the differences between various Universal Registrar implementations:
 * - Standard Universal Registrar (dev.uniregistrar.io)
 * - GoDiddy Universal Registrar
 * - Custom registrar implementations
 * 
 * Different implementations may use different:
 * - Endpoint patterns (e.g., `/1.0/operations` vs `/1.0.0/operations`)
 * - Request/response formats
 * - Authentication mechanisms
 * - Error handling
 * 
 * **Example Usage:**
 * ```kotlin
 * val adapter = StandardUniversalRegistrarAdapter()
 * val response = adapter.createDid(
 *     baseUrl = "https://dev.uniregistrar.io",
 *     method = "web",
 *     options = CreateDidOptions()
 * )
 * ```
 */
interface UniversalRegistrarProtocolAdapter {
    
    /**
     * Creates a new DID using the Universal Registrar protocol.
     * 
     * @param baseUrl Base URL of the Universal Registrar instance
     * @param method DID method name (e.g., "web", "key", "ion")
     * @param options Creation options (key management mode, secrets, etc.)
     * @return Registration response with jobId and didState
     */
    suspend fun createDid(
        baseUrl: String,
        method: String,
        options: CreateDidOptions
    ): DidRegistrationResponse
    
    /**
     * Updates a DID Document using the Universal Registrar protocol.
     * 
     * @param baseUrl Base URL of the Universal Registrar instance
     * @param did The DID to update
     * @param document The updated DID Document
     * @param options Update options (authorization secrets, etc.)
     * @return Registration response with jobId and didState
     */
    suspend fun updateDid(
        baseUrl: String,
        did: String,
        document: DidDocument,
        options: UpdateDidOptions
    ): DidRegistrationResponse
    
    /**
     * Deactivates a DID using the Universal Registrar protocol.
     * 
     * @param baseUrl Base URL of the Universal Registrar instance
     * @param did The DID to deactivate
     * @param options Deactivation options (authorization secrets, etc.)
     * @return Registration response with jobId and didState
     */
    suspend fun deactivateDid(
        baseUrl: String,
        did: String,
        options: DeactivateDidOptions
    ): DidRegistrationResponse
    
    /**
     * Gets the status of a long-running operation.
     * 
     * @param baseUrl Base URL of the Universal Registrar instance
     * @param jobId Job identifier from a previous operation
     * @return Registration response with current didState
     */
    suspend fun getOperationStatus(
        baseUrl: String,
        jobId: String
    ): DidRegistrationResponse
}

