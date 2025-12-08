package com.trustweave.credential.exchange.registry

import com.trustweave.credential.exchange.registry.internal.DefaultExchangeProtocolRegistry

/**
 * Registry factory object for exchange protocols.
 * 
 * Provides controlled construction of registry instances.
 * This keeps DefaultExchangeProtocolRegistry as a complete implementation detail.
 * 
 * Similar to proofEngineRegistry() in the credential API.
 */
object ExchangeProtocolRegistries {
    /**
     * Create a default exchange protocol registry instance.
     * 
     * Internal implementation details are hidden.
     */
    fun default(): ExchangeProtocolRegistry {
        return DefaultExchangeProtocolRegistry()
    }
}

