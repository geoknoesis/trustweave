package org.trustweave.credential.exchange.internal

import org.trustweave.credential.exchange.registry.ExchangeProtocolRegistry
import org.trustweave.credential.identifiers.ExchangeProtocolName
import org.trustweave.credential.spi.exchange.CredentialExchangeProtocolProvider
import java.util.ServiceLoader

/**
 * Internal utility for discovering and auto-registering credential exchange protocol implementations.
 * 
 * This is an implementation detail. For public API, use extension functions or factory methods.
 */
internal object ExchangeProtocols {
    
    /**
     * Automatically discover and register all CredentialExchangeProtocol implementations.
     * 
     * Scans the classpath for CredentialExchangeProtocolProvider implementations
     * and registers the protocols they provide.
     * 
     * @param registry The protocol registry to register with
     * @param options Optional configuration options for protocol creation
     */
    fun autoRegister(
        registry: ExchangeProtocolRegistry,
        options: Map<String, Any?> = emptyMap()
    ) {
        val loader = ServiceLoader.load(CredentialExchangeProtocolProvider::class.java)
        loader.forEach { provider ->
            provider.supportedProtocols.forEach { protocolName ->
                provider.create(protocolName, options)?.let { protocol ->
                    registry.register(protocol)
                }
            }
        }
    }
    
    /**
     * Automatically register protocols for specific protocol names.
     * 
     * @param registry The protocol registry to register with
     * @param protocolNames List of protocol names to register
     * @param options Optional configuration options
     */
    fun autoRegisterProtocols(
        registry: ExchangeProtocolRegistry,
        protocolNames: List<ExchangeProtocolName>,
        options: Map<String, Any?> = emptyMap()
    ) {
        val loader = ServiceLoader.load(CredentialExchangeProtocolProvider::class.java)
        val protocolSet = protocolNames.map { it.value }.toSet()
        
        loader.forEach { provider ->
            provider.supportedProtocols
                .filter { it in protocolSet }
                .forEach { protocolNameString ->
                    ExchangeProtocolName(protocolNameString).let { protocolName ->
                        provider.create(protocolNameString, options)?.let { protocol ->
                            registry.register(protocol)
                        }
                    }
                }
        }
    }
    
    /**
     * Get all available protocol providers.
     * 
     * @return List of all discovered protocol providers
     */
    fun getAvailableProviders(): List<CredentialExchangeProtocolProvider> {
        val loader = ServiceLoader.load(CredentialExchangeProtocolProvider::class.java)
        return loader.toList()
    }
    
    /**
     * Get all available protocol names from discovered providers.
     * 
     * @return Set of all available protocol names
     */
    fun getAvailableProtocolNames(): Set<ExchangeProtocolName> {
        val loader = ServiceLoader.load(CredentialExchangeProtocolProvider::class.java)
        return loader.flatMap { provider ->
            provider.supportedProtocols.map { ExchangeProtocolName(it) }
        }.toSet()
    }
}

