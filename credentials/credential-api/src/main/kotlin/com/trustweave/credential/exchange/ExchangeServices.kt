package com.trustweave.credential.exchange

import com.trustweave.credential.CredentialService
import com.trustweave.credential.exchange.capability.ExchangeProtocolCapabilities
import com.trustweave.credential.exchange.registry.ExchangeProtocolRegistries
import com.trustweave.credential.exchange.registry.ExchangeProtocolRegistry
import com.trustweave.credential.exchange.internal.ExchangeProtocols
import com.trustweave.credential.exchange.internal.DefaultExchangeService
import com.trustweave.credential.identifiers.ExchangeProtocolName
import com.trustweave.did.resolver.DidResolver

/**
 * Factory functions for creating ExchangeService instances.
 * 
 * Provides multiple ways to create services:
 * - With custom registry
 * - With explicit protocols
 * - With auto-discovery
 * 
 * Similar to CredentialServices factory pattern.
 */
object ExchangeServices {
    /**
     * Create an ExchangeService with a custom protocol registry.
     * 
     * Use this when you need fine-grained control over protocol registration.
     * 
     * **Example:**
     * ```kotlin
     * val registry = ExchangeProtocolRegistries.default()
     * registry.register(didCommProtocol)
     * registry.register(oidc4vciProtocol)
     * 
     * val service = ExchangeServices.createExchangeService(
     *     registry = registry,
     *     credentialService = credentialService,
     *     didResolver = didResolver
     * )
     * ```
     */
    fun createExchangeService(
        protocolRegistry: ExchangeProtocolRegistry,
        credentialService: CredentialService,
        didResolver: DidResolver
    ): ExchangeService {
        return DefaultExchangeService(protocolRegistry, credentialService, didResolver)
    }
    
    /**
     * Create an ExchangeService with explicit protocols.
     * 
     * Creates a default registry internally and registers the provided protocols.
     * 
     * **Example:**
     * ```kotlin
     * val service = ExchangeServices.createExchangeService(
     *     credentialService = credentialService,
     *     didResolver = didResolver,
     *     didCommProtocol,
     *     oidc4vciProtocol
     * )
     * ```
     */
    fun createExchangeService(
        credentialService: CredentialService,
        didResolver: DidResolver,
        vararg protocols: CredentialExchangeProtocol
    ): ExchangeService {
        val registry = ExchangeProtocolRegistries.default()
        protocols.forEach { registry.register(it) }
        return DefaultExchangeService(registry, credentialService, didResolver)
    }
    
    /**
     * Create an ExchangeService with auto-discovery of all protocols.
     * 
     * Automatically discovers and registers all CredentialExchangeProtocol implementations
     * found on the classpath via Java ServiceLoader.
     * 
     * **Example:**
     * ```kotlin
     * val service = ExchangeServices.createExchangeServiceWithAutoDiscovery(
     *     credentialService = credentialService,
     *     didResolver = didResolver
     * )
     * ```
     */
    fun createExchangeServiceWithAutoDiscovery(
        credentialService: CredentialService,
        didResolver: DidResolver,
        options: Map<String, Any?> = emptyMap()
    ): ExchangeService {
        val registry = ExchangeProtocolRegistries.default()
        ExchangeProtocols.autoRegister(registry, options)
        return DefaultExchangeService(registry, credentialService, didResolver)
    }
    
    /**
     * Create an ExchangeService with auto-discovery for specific protocols.
     * 
     * **Example:**
     * ```kotlin
     * val service = ExchangeServices.createExchangeServiceWithAutoDiscovery(
     *     credentialService = credentialService,
     *     didResolver = didResolver,
     *     protocols = listOf(ExchangeProtocolName.DidComm, ExchangeProtocolName.Oidc4Vci)
     * )
     * ```
     */
    fun createExchangeServiceWithAutoDiscovery(
        credentialService: CredentialService,
        didResolver: DidResolver,
        protocols: List<ExchangeProtocolName>,
        options: Map<String, Any?> = emptyMap()
    ): ExchangeService {
        val registry = ExchangeProtocolRegistries.default()
        ExchangeProtocols.autoRegisterProtocols(registry, protocols, options)
        return DefaultExchangeService(registry, credentialService, didResolver)
    }
}

