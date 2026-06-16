package org.trustweave.credential.siop.exchange.spi

import okhttp3.OkHttpClient
import org.trustweave.credential.exchange.CredentialExchangeProtocol
import org.trustweave.credential.siop.SiopV2Service
import org.trustweave.credential.siop.exchange.SiopV2ExchangeProtocol
import org.trustweave.credential.spi.exchange.CredentialExchangeProtocolProvider
import org.trustweave.kms.KeyManagementService

/**
 * SPI Provider for SIOPv2 exchange protocol.
 *
 * Automatically discovers and provides the SIOPv2 protocol implementation.
 *
 * **ServiceLoader Registration:**
 * The file `META-INF/services/org.trustweave.credential.spi.exchange.CredentialExchangeProtocolProvider`
 * contains:
 * ```
 * org.trustweave.credential.siop.exchange.spi.SiopV2ExchangeProtocolProvider
 * ```
 */
class SiopV2ExchangeProtocolProvider : CredentialExchangeProtocolProvider {
    override val name = "siop-v2"
    override val supportedProtocols = listOf("siop-v2")

    override fun create(
        protocolName: String,
        options: Map<String, Any?>,
    ): CredentialExchangeProtocol? {
        if (protocolName != "siop-v2") return null

        val kms = options["kms"] as? KeyManagementService
            ?: throw IllegalArgumentException("Missing 'kms' in options")

        val httpClient = options["httpClient"] as? OkHttpClient
            ?: org.trustweave.core.net.ssrfGuardedOkHttpClient()

        val siopV2Service = SiopV2Service(
            kms = kms,
            httpClient = httpClient,
        )

        return SiopV2ExchangeProtocol(siopV2Service)
    }
}
