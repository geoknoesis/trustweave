package com.trustweave.credential.chapi.exchange.spi

import com.trustweave.credential.chapi.ChapiService
import com.trustweave.credential.chapi.exchange.ChapiExchangeProtocol
import com.trustweave.credential.exchange.CredentialExchangeProtocol
import com.trustweave.credential.exchange.spi.CredentialExchangeProtocolProvider

/**
 * SPI Provider for CHAPI exchange protocol.
 *
 * Automatically discovers and provides CHAPI protocol implementation.
 *
 * **ServiceLoader Registration:**
 * Create a file `META-INF/services/com.trustweave.credential.exchange.spi.CredentialExchangeProtocolProvider`
 * with the content:
 * ```
 * com.trustweave.credential.chapi.exchange.spi.ChapiExchangeProtocolProvider
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

