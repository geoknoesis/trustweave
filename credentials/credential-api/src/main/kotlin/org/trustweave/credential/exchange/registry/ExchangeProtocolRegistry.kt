package org.trustweave.credential.exchange.registry

import org.trustweave.credential.exchange.CredentialExchangeProtocol
import org.trustweave.credential.exchange.ExchangeOperation
import org.trustweave.credential.exchange.capability.ExchangeProtocolCapabilities
import org.trustweave.credential.identifiers.ExchangeProtocolName

/**
 * Registry for credential exchange protocols.
 * 
 * Public interface for protocol registration and lookup.
 * Implementation is in internal package.
 * 
 * Similar to ProofEngineRegistry in the credential API.
 */
interface ExchangeProtocolRegistry {
    /**
     * Register a protocol.
     */
    fun register(protocol: CredentialExchangeProtocol)
    
    /**
     * Get a protocol by name.
     */
    fun get(protocolName: ExchangeProtocolName): CredentialExchangeProtocol?
    
    /**
     * Get all registered protocols.
     */
    fun getAll(): Map<ExchangeProtocolName, CredentialExchangeProtocol>
    
    /**
     * Get all supported protocol names.
     */
    fun getSupportedProtocols(): List<ExchangeProtocolName>
    
    /**
     * Check if a protocol is registered.
     */
    fun isRegistered(protocolName: ExchangeProtocolName): Boolean
    
    /**
     * Unregister a protocol.
     */
    fun unregister(protocolName: ExchangeProtocolName): CredentialExchangeProtocol?
    
    /**
     * Get protocol capabilities.
     */
    fun getCapabilities(protocolName: ExchangeProtocolName): ExchangeProtocolCapabilities?
    
    /**
     * Check if a protocol supports an operation.
     */
    fun supports(protocolName: ExchangeProtocolName, operation: ExchangeOperation): Boolean
    
    /**
     * Clear all registered protocols.
     */
    fun clear()
}

