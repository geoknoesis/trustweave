package org.trustweave.credential.oidc4vp.exchange.spi

import org.trustweave.credential.exchange.CredentialExchangeProtocol
import org.trustweave.credential.spi.exchange.CredentialExchangeProtocolProvider
import org.trustweave.credential.oidc4vp.Oidc4VpService
import org.trustweave.credential.oidc4vp.exchange.Oidc4VpExchangeProtocol
import org.trustweave.kms.KeyManagementService
import okhttp3.OkHttpClient

/**
 * SPI Provider for OIDC4VP exchange protocol.
 *
 * Automatically discovers and provides OIDC4VP protocol implementation.
 *
 * **ServiceLoader Registration:**
 * Create a file `META-INF/services/org.trustweave.credential.exchange.spi.CredentialExchangeProtocolProvider`
 * with the content:
 * ```
 * org.trustweave.credential.oidc4vp.exchange.spi.Oidc4VpExchangeProtocolProvider
 * ```
 */
class Oidc4VpExchangeProtocolProvider : CredentialExchangeProtocolProvider {
    override val name = "oidc4vp"
    override val supportedProtocols = listOf("oidc4vp")

    override fun create(
        protocolName: String,
        options: Map<String, Any?>
    ): CredentialExchangeProtocol? {
        if (protocolName != "oidc4vp") return null

        val kms = options["kms"] as? KeyManagementService
            ?: throw IllegalArgumentException("Missing 'kms' in options")

        val httpClient = options["httpClient"] as? OkHttpClient
            ?: OkHttpClient()

        val oidc4vpService = Oidc4VpService(
            kms = kms,
            httpClient = httpClient
        )

        return Oidc4VpExchangeProtocol(oidc4vpService)
    }
}

