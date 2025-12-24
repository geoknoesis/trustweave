package org.trustweave.credential.chapi.exchange.spi

import org.trustweave.credential.chapi.ChapiService
import org.trustweave.credential.chapi.exchange.ChapiExchangeProtocol
import org.trustweave.credential.exchange.CredentialExchangeProtocol
import org.trustweave.credential.spi.exchange.CredentialExchangeProtocolProvider

/**
 * SPI Provider for CHAPI exchange protocol.
 *
 * Automatically discovers and provides CHAPI protocol implementation.
 *
 * **ServiceLoader Registration:**
 * Create a file `META-INF/services/org.trustweave.credential.exchange.spi.CredentialExchangeProtocolProvider`
 * with the content:
 * ```
 * org.trustweave.credential.chapi.exchange.spi.ChapiExchangeProtocolProvider
 * ```
 */
class ChapiExchangeProtocolProvider : CredentialExchangeProtocolProvider {
    override val name = "chapi"
    override val supportedProtocols = listOf("chapi")

    override fun create(
        protocolName: String,
        options: Map<String, Any?>
    ): CredentialExchangeProtocol? {
        if (protocolName != "chapi") return null

        val chapiService = ChapiService()
        return ChapiExchangeProtocol(chapiService)
    }
}

