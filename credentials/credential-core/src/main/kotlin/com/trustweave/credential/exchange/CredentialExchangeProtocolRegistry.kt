package com.trustweave.credential.exchange

import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for credential exchange protocols.
 * 
 * Allows registration and discovery of different exchange protocols
 * (DIDComm, OIDC4VCI, CHAPI, etc.) for use in credential workflows.
 * 
 * **Example Usage:**
 * ```kotlin
 * val registry = CredentialExchangeProtocolRegistry()
 * 
 * // Register protocols
 * registry.register(DidCommExchangeProtocol(didCommService))
 * registry.register(Oidc4VciExchangeProtocol(oidc4vciService))
 * 
 * // Use protocol
 * val offer = registry.offerCredential(
 *     protocolName = "didcomm",
 *     request = CredentialOfferRequest(...)
 * )
 * ```
 */
class CredentialExchangeProtocolRegistry(
    initialProtocols: Map<String, CredentialExchangeProtocol> = emptyMap()
) {
    private val protocols = ConcurrentHashMap<String, CredentialExchangeProtocol>(initialProtocols)
    
    /**
     * Registers a credential exchange protocol.
     */
    fun register(protocol: CredentialExchangeProtocol) {
        protocols[protocol.protocolName] = protocol
    }
    
    /**
     * Unregisters a protocol.
     */
    fun unregister(protocolName: String) {
        protocols.remove(protocolName)
    }
    
    /**
     * Gets a protocol by name.
     */
    fun get(protocolName: String): CredentialExchangeProtocol? {
        return protocols[protocolName]
    }
    
    /**
     * Gets all registered protocols.
     */
    fun getAll(): Map<String, CredentialExchangeProtocol> = protocols.toMap()
    
    /**
     * Gets all registered protocol names.
     */
    fun getAllProtocolNames(): List<String> = protocols.keys.toList()
    
    /**
     * Checks if a protocol is registered.
     */
    fun isRegistered(protocolName: String): Boolean = protocols.containsKey(protocolName)
    
    /**
     * Creates a credential offer using the specified protocol.
     */
    suspend fun offerCredential(
        protocolName: String,
        request: CredentialOfferRequest
    ): CredentialOfferResponse {
        val protocol = protocols[protocolName]
            ?: throw IllegalArgumentException(
                "Protocol '$protocolName' not registered. Available: ${protocols.keys.joinToString()}"
            )
        
        if (!protocol.supportedOperations.contains(ExchangeOperation.OFFER_CREDENTIAL)) {
            throw UnsupportedOperationException(
                "Protocol '$protocolName' does not support OFFER_CREDENTIAL operation"
            )
        }
        
        return protocol.offerCredential(request)
    }
    
    /**
     * Requests a credential using the specified protocol.
     */
    suspend fun requestCredential(
        protocolName: String,
        request: CredentialRequestRequest
    ): CredentialRequestResponse {
        val protocol = protocols[protocolName]
            ?: throw IllegalArgumentException(
                "Protocol '$protocolName' not registered. Available: ${protocols.keys.joinToString()}"
            )
        
        if (!protocol.supportedOperations.contains(ExchangeOperation.REQUEST_CREDENTIAL)) {
            throw UnsupportedOperationException(
                "Protocol '$protocolName' does not support REQUEST_CREDENTIAL operation"
            )
        }
        
        return protocol.requestCredential(request)
    }
    
    /**
     * Issues a credential using the specified protocol.
     */
    suspend fun issueCredential(
        protocolName: String,
        request: CredentialIssueRequest
    ): CredentialIssueResponse {
        val protocol = protocols[protocolName]
            ?: throw IllegalArgumentException(
                "Protocol '$protocolName' not registered. Available: ${protocols.keys.joinToString()}"
            )
        
        if (!protocol.supportedOperations.contains(ExchangeOperation.ISSUE_CREDENTIAL)) {
            throw UnsupportedOperationException(
                "Protocol '$protocolName' does not support ISSUE_CREDENTIAL operation"
            )
        }
        
        return protocol.issueCredential(request)
    }
    
    /**
     * Requests a proof using the specified protocol.
     */
    suspend fun requestProof(
        protocolName: String,
        request: ProofRequestRequest
    ): ProofRequestResponse {
        val protocol = protocols[protocolName]
            ?: throw IllegalArgumentException(
                "Protocol '$protocolName' not registered. Available: ${protocols.keys.joinToString()}"
            )
        
        if (!protocol.supportedOperations.contains(ExchangeOperation.REQUEST_PROOF)) {
            throw UnsupportedOperationException(
                "Protocol '$protocolName' does not support REQUEST_PROOF operation"
            )
        }
        
        return protocol.requestProof(request)
    }
    
    /**
     * Presents a proof using the specified protocol.
     */
    suspend fun presentProof(
        protocolName: String,
        request: ProofPresentationRequest
    ): ProofPresentationResponse {
        val protocol = protocols[protocolName]
            ?: throw IllegalArgumentException(
                "Protocol '$protocolName' not registered. Available: ${protocols.keys.joinToString()}"
            )
        
        if (!protocol.supportedOperations.contains(ExchangeOperation.PRESENT_PROOF)) {
            throw UnsupportedOperationException(
                "Protocol '$protocolName' does not support PRESENT_PROOF operation"
            )
        }
        
        return protocol.presentProof(request)
    }
    
    /**
     * Clears all registered protocols.
     */
    fun clear() {
        protocols.clear()
    }
    
    /**
     * Creates a snapshot of the registry.
     */
    fun snapshot(): CredentialExchangeProtocolRegistry {
        return CredentialExchangeProtocolRegistry(protocols.toMap())
    }
    
    companion object {
        /**
         * Creates an empty registry.
         */
        fun create(): CredentialExchangeProtocolRegistry = CredentialExchangeProtocolRegistry()
    }
}

