package com.trustweave.credential.exchange

import com.trustweave.credential.exchange.capability.ExchangeProtocolCapabilities
import com.trustweave.credential.exchange.model.ExchangeMessageEnvelope
import com.trustweave.credential.exchange.model.ExchangeMessageType
import com.trustweave.credential.exchange.request.ExchangeRequest
import com.trustweave.credential.exchange.request.ProofExchangeRequest
import com.trustweave.credential.identifiers.ExchangeProtocolName
import com.trustweave.credential.model.vc.VerifiableCredential
import com.trustweave.credential.model.vc.VerifiablePresentation

/**
 * Protocol-agnostic interface for credential exchange protocols.
 * 
 * All credential exchange protocols (DIDComm, OIDC4VCI, CHAPI, OIDC4VP, etc.)
 * implement this interface, allowing them to be used interchangeably with
 * VerifiableCredential, VerifiablePresentation, and ExchangeMessageEnvelope.
 * 
 * **Design Principles:**
 * - Protocol-agnostic: Uses opaque ExchangeMessageEnvelope for protocol-specific data
 * - Capability-aware: Exposes ExchangeProtocolCapabilities
 * - Type-safe: Returns protocol-agnostic sealed types
 * - Extensible: Sealed hierarchies allow future operations
 * 
 * **Example Implementation:**
 * ```kotlin
 * class DidCommExchangeProtocol : CredentialExchangeProtocol {
     *     override val protocolName = ExchangeProtocolName.DidComm
 *     override val capabilities = ExchangeProtocolCapabilities(
 *         supportedOperations = setOf(
 *             ExchangeOperation.OFFER_CREDENTIAL,
 *             ExchangeOperation.REQUEST_CREDENTIAL,
 *             ExchangeOperation.ISSUE_CREDENTIAL,
 *             ExchangeOperation.REQUEST_PROOF,
 *             ExchangeOperation.PRESENT_PROOF
 *         )
 *     )
 *     
 *     override suspend fun offer(request: ExchangeRequest.Offer): ExchangeMessageEnvelope {
 *         // Convert to DIDComm-specific format, create message
 *         val didCommMessage = createDidCommOffer(request)
 *         // Wrap in opaque envelope
 *         return ExchangeMessageEnvelope(
 *             protocolName = protocolName,
 *             messageType = ExchangeMessageType.Offer,
 *             messageData = didCommMessage.toJsonElement()
 *         )
 *     }
 * }
 * ```
 */
interface CredentialExchangeProtocol {
    /**
     * Protocol identifier.
     */
    val protocolName: ExchangeProtocolName
    
    /**
     * Protocol capabilities.
     * 
     * Describes what operations and features this protocol supports.
     */
    val capabilities: ExchangeProtocolCapabilities
    
    /**
     * Check if protocol supports an operation.
     */
    fun supports(operation: ExchangeOperation): Boolean = 
        operation in capabilities.supportedOperations
    
    /**
     * Check if protocol supports a capability.
     */
    fun supportsCapability(predicate: ExchangeProtocolCapabilities.() -> Boolean): Boolean =
        predicate(capabilities)
    
    /**
     * Offer a credential.
     * 
     * Returns an opaque ExchangeMessageEnvelope containing the protocol-specific offer message.
     */
    suspend fun offer(request: ExchangeRequest.Offer): ExchangeMessageEnvelope
    
    /**
     * Request a credential (holder requests after receiving offer).
     */
    suspend fun request(request: ExchangeRequest.Request): ExchangeMessageEnvelope
    
        /**
         * Issue a credential (issuer issues after receiving request).
         *
         * Returns the issued VerifiableCredential and an opaque ExchangeMessageEnvelope
         * containing the protocol-specific issue message.
         */
        suspend fun issue(request: ExchangeRequest.Issue): Pair<VerifiableCredential, ExchangeMessageEnvelope>

        /**
         * Request a proof presentation.
         */
        suspend fun requestProof(request: ProofExchangeRequest.Request): ExchangeMessageEnvelope

        /**
         * Present a proof (prover presents after receiving request).
         *
         * Returns the VerifiablePresentation and an opaque ExchangeMessageEnvelope
         * containing the protocol-specific presentation message.
         */
        suspend fun presentProof(request: ProofExchangeRequest.Presentation): Pair<VerifiablePresentation, ExchangeMessageEnvelope>
}

