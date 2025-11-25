package com.trustweave.credential.oidc4vci.exchange.spi

import com.trustweave.credential.exchange.CredentialExchangeProtocol
import com.trustweave.credential.exchange.spi.CredentialExchangeProtocolProvider
import com.trustweave.credential.oidc4vci.Oidc4VciService
import com.trustweave.credential.oidc4vci.exchange.Oidc4VciExchangeProtocol
import com.trustweave.kms.KeyManagementService
import okhttp3.OkHttpClient

/**
 * SPI Provider for OIDC4VCI exchange protocol.
 * 
 * Automatically discovers and provides OIDC4VCI protocol implementation.
 * 
 * **ServiceLoader Registration:**
 * Create a file `META-INF/services/com.trustweave.credential.exchange.spi.CredentialExchangeProtocolProvider`
 * with the content:
 * ```
 * com.trustweave.credential.oidc4vci.exchange.spi.Oidc4VciExchangeProtocolProvider
 * ```
 */
class Oidc4VciExchangeProtocolProvider : CredentialExchangeProtocolProvider {
    override val name = "oidc4vci"
    override val supportedProtocols = listOf("oidc4vci")
    
    override fun create(
        protocolName: String,
        options: Map<String, Any?>
    ): CredentialExchangeProtocol? {
        if (protocolName != "oidc4vci") return null
        
        val credentialIssuerUrl = options["credentialIssuerUrl"] as? String
            ?: throw IllegalArgumentException("Missing 'credentialIssuerUrl' in options")
        
        val kms = options["kms"] as? KeyManagementService
            ?: throw IllegalArgumentException("Missing 'kms' in options")
        
        val httpClient = options["httpClient"] as? OkHttpClient
            ?: OkHttpClient()
        
        val oidc4vciService = Oidc4VciService(
            credentialIssuerUrl = credentialIssuerUrl,
            kms = kms,
            httpClient = httpClient
        )
        
        return Oidc4VciExchangeProtocol(oidc4vciService)
    }
}

