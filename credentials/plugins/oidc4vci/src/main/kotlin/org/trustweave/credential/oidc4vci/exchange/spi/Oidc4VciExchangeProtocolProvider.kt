package org.trustweave.credential.oidc4vci.exchange.spi

import org.trustweave.credential.exchange.CredentialExchangeProtocol
import org.trustweave.credential.spi.exchange.CredentialExchangeProtocolProvider
import org.trustweave.credential.oidc4vci.Oidc4VciService
import org.trustweave.credential.oidc4vci.exchange.Oidc4VciExchangeProtocol
import org.trustweave.kms.KeyManagementService
import okhttp3.OkHttpClient

/**
 * SPI Provider for OIDC4VCI exchange protocol.
 *
 * Automatically discovers and provides OIDC4VCI protocol implementation.
 *
 * **ServiceLoader Registration:**
 * Create a file `META-INF/services/org.trustweave.credential.exchange.spi.CredentialExchangeProtocolProvider`
 * with the content:
 * ```
 * org.trustweave.credential.oidc4vci.exchange.spi.Oidc4VciExchangeProtocolProvider
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

