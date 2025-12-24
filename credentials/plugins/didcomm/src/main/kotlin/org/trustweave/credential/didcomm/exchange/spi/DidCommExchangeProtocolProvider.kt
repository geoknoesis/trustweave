package org.trustweave.credential.didcomm.exchange.spi

import org.trustweave.credential.didcomm.DidCommFactory
import org.trustweave.credential.didcomm.DidCommService
import org.trustweave.credential.didcomm.exchange.DidCommExchangeProtocol
import org.trustweave.credential.exchange.CredentialExchangeProtocol
import org.trustweave.credential.spi.exchange.CredentialExchangeProtocolProvider
import org.trustweave.did.model.DidDocument
import org.trustweave.kms.KeyManagementService

/**
 * SPI Provider for DIDComm exchange protocol.
 *
 * Automatically discovers and provides DIDComm protocol implementation.
 *
 * **ServiceLoader Registration:**
 * Create a file `META-INF/services/org.trustweave.credential.exchange.spi.CredentialExchangeProtocolProvider`
 * with the content:
 * ```
 * org.trustweave.credential.didcomm.exchange.spi.DidCommExchangeProtocolProvider
 * ```
 */
class DidCommExchangeProtocolProvider : CredentialExchangeProtocolProvider {
    override val name = "didcomm"
    override val supportedProtocols = listOf("didcomm")

    override fun create(
        protocolName: String,
        options: Map<String, Any?>
    ): CredentialExchangeProtocol? {
        if (protocolName != "didcomm") return null

        val kms = options["kms"] as? KeyManagementService
            ?: return null

        val resolveDid = options["resolveDid"] as? (suspend (String) -> DidDocument?)
            ?: return null

        val useProductionCrypto = options["useProductionCrypto"] as? Boolean ?: false

        val didCommService = DidCommFactory.createInMemoryService(
            kms = kms,
            resolveDid = resolveDid,
            useProductionCrypto = useProductionCrypto
        )

        return DidCommExchangeProtocol(didCommService)
    }
}

