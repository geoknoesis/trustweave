package com.trustweave.credential.exchange

import com.trustweave.credential.exchange.capability.ExchangeProtocolCapabilities
import com.trustweave.credential.exchange.request.ExchangeRequest
import com.trustweave.credential.exchange.request.ProofExchangeRequest
import com.trustweave.credential.exchange.response.ExchangeResponse
import com.trustweave.credential.exchange.response.ProofExchangeResponse
import com.trustweave.credential.exchange.result.ExchangeResult
import com.trustweave.credential.identifiers.ExchangeProtocolName

/**
 * Main exchange service API (protocol-agnostic).
 * 
 * Provides a unified interface for credential exchange operations
 * across all supported protocols (DIDComm, OIDC4VCI, CHAPI, etc.).
 * 
 * **Design Principles:**
 * - Protocol-agnostic: Works with any registered protocol
 * - Type-safe: Uses sealed result types for error handling
 * - Capability-aware: Can query protocol capabilities
 * - Extensible: Sealed hierarchies allow future operations
 * 
 * **Example Usage:**
 * ```kotlin
 * val service = ExchangeServices.createExchangeServiceWithAutoDiscovery(...)
 * 
 * when (val result = service.offer(
 *     ExchangeRequest.Offer(issuerDid, holderDid, preview)
 * )) {
 *     is ExchangeResult.Success -> {
 *         val response = result.value
 *         // Handle success
 *     }
 *     is ExchangeResult.Failure.ProtocolNotSupported -> {
 *         // Handle error
 *     }
 * }
 * ```
 */
interface ExchangeService {
    /**
     * Offer a credential to a holder.
     */
    suspend fun offer(request: ExchangeRequest.Offer): ExchangeResult<ExchangeResponse.Offer>
    
    /**
     * Request a credential (holder requests after receiving offer).
     */
    suspend fun request(request: ExchangeRequest.Request): ExchangeResult<ExchangeResponse.Request>
    
    /**
     * Issue a credential (issuer issues after receiving request).
     */
    suspend fun issue(request: ExchangeRequest.Issue): ExchangeResult<ExchangeResponse.Issue>
    
    /**
     * Request a proof presentation.
     */
    suspend fun requestProof(request: ProofExchangeRequest.Request): ExchangeResult<ProofExchangeResponse.Request>
    
    /**
     * Present a proof (prover presents after receiving request).
     */
    suspend fun presentProof(request: ProofExchangeRequest.Presentation): ExchangeResult<ProofExchangeResponse.Presentation>
    
    /**
     * Check if a protocol is supported.
     */
    fun supports(protocolName: ExchangeProtocolName): Boolean
    
    /**
     * Check if a protocol supports a specific operation.
     */
    fun supports(protocolName: ExchangeProtocolName, operation: ExchangeOperation): Boolean
    
    /**
     * Check if any registered protocol supports an operation.
     */
    fun supports(operation: ExchangeOperation): Boolean
    
    /**
     * Check if a protocol supports a capability.
     */
    fun supportsCapability(
        protocolName: ExchangeProtocolName,
        predicate: ExchangeProtocolCapabilities.() -> Boolean
    ): Boolean
    
    /**
     * Get all supported protocols.
     */
    fun supportedProtocols(): List<ExchangeProtocolName>
    
    /**
     * Get protocol capabilities.
     */
    fun getCapabilities(protocolName: ExchangeProtocolName): ExchangeProtocolCapabilities?
}

