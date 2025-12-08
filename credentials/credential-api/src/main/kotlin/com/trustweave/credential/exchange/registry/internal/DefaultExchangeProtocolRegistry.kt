package com.trustweave.credential.exchange.registry.internal

import com.trustweave.credential.exchange.CredentialExchangeProtocol
import com.trustweave.credential.exchange.ExchangeOperation
import com.trustweave.credential.exchange.capability.ExchangeProtocolCapabilities
import com.trustweave.credential.exchange.registry.ExchangeProtocolRegistry
import com.trustweave.credential.identifiers.ExchangeProtocolName
import java.util.concurrent.ConcurrentHashMap

/**
 * Default implementation of ExchangeProtocolRegistry.
 * 
 * Internal implementation detail. Use factory functions to create instances.
 */
internal class DefaultExchangeProtocolRegistry(
    initialProtocols: Map<ExchangeProtocolName, CredentialExchangeProtocol> = emptyMap()
) : ExchangeProtocolRegistry {
    
    private val protocols = ConcurrentHashMap<ExchangeProtocolName, CredentialExchangeProtocol>(initialProtocols)
    
    override fun register(protocol: CredentialExchangeProtocol) {
        protocols[protocol.protocolName] = protocol
    }
    
        override fun get(protocolName: ExchangeProtocolName): CredentialExchangeProtocol? {
        return protocols[protocolName]
    }
    
    override fun getAll(): Map<ExchangeProtocolName, CredentialExchangeProtocol> {
        return protocols.toMap()
    }
    
    override fun getSupportedProtocols(): List<ExchangeProtocolName> {
        return protocols.keys.toList()
    }
    
    override fun isRegistered(protocolName: ExchangeProtocolName): Boolean {
        return protocols.containsKey(protocolName)
    }
    
    override fun unregister(protocolName: ExchangeProtocolName): CredentialExchangeProtocol? {
        return protocols.remove(protocolName)
    }
    
    override fun getCapabilities(protocolName: ExchangeProtocolName): ExchangeProtocolCapabilities? {
        return protocols[protocolName]?.capabilities
    }
    
    override fun supports(protocolName: ExchangeProtocolName, operation: ExchangeOperation): Boolean {
        return protocols[protocolName]?.supports(operation) ?: false
    }
    
    override fun clear() {
        protocols.clear()
    }
}

