package org.trustweave.credential.eudiw.exchange.spi

import org.trustweave.credential.exchange.CredentialExchangeProtocol
import org.trustweave.credential.eudiw.exchange.EudiwExchangeProtocol
import org.trustweave.credential.spi.exchange.CredentialExchangeProtocolProvider

/**
 * SPI provider for the EUDIW (EU Digital Identity Wallet) exchange protocol.
 *
 * Produces an [EudiwExchangeProtocol] that enforces EUDIW ARF compliance rules
 * (response mode, client_id_scheme, nonce freshness) on top of OID4VP flows.
 *
 * **ServiceLoader registration**: `META-INF/services/org.trustweave.credential.spi.exchange.CredentialExchangeProtocolProvider`
 */
class EudiwExchangeProtocolProvider : CredentialExchangeProtocolProvider {

    override val name = "eudiw"
    override val supportedProtocols = listOf("eudiw")

    override fun create(
        protocolName: String,
        options: Map<String, Any?>,
    ): CredentialExchangeProtocol? {
        if (protocolName != "eudiw") return null
        return EudiwExchangeProtocol()
    }
}
