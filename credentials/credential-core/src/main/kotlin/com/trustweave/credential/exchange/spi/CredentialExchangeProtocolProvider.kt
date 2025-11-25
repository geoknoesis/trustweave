package com.trustweave.credential.exchange.spi

import com.trustweave.credential.exchange.CredentialExchangeProtocol

/**
 * SPI Provider for credential exchange protocols.
 * 
 * Allows automatic discovery of protocol implementations via Java ServiceLoader.
 * 
 * **ServiceLoader Registration:**
 * Create a file `META-INF/services/com.trustweave.credential.exchange.spi.CredentialExchangeProtocolProvider`
 * with the content:
 * ```
 * com.trustweave.credential.didcomm.exchange.spi.DidCommExchangeProtocolProvider
 * ```
 */
interface CredentialExchangeProtocolProvider {
    /**
     * Provider name (e.g., "didcomm", "oidc4vci", "chapi").
     */
    val name: String
    
    /**
     * Supported protocol names.
     */
    val supportedProtocols: List<String>
    
    /**
     * Creates a credential exchange protocol instance.
     * 
     * @param protocolName The protocol name (e.g., "didcomm")
     * @param options Configuration options
     * @return Protocol instance, or null if creation failed
     */
    fun create(
        protocolName: String,
        options: Map<String, Any?> = emptyMap()
    ): CredentialExchangeProtocol?
}

