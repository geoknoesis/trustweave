package com.trustweave.credential.didcomm.exchange.spi

import com.trustweave.credential.didcomm.DidCommFactory
import com.trustweave.credential.didcomm.DidCommService
import com.trustweave.credential.didcomm.exchange.DidCommExchangeProtocol
import com.trustweave.credential.exchange.CredentialExchangeProtocol
import com.trustweave.credential.exchange.spi.CredentialExchangeProtocolProvider
import com.trustweave.did.DidDocument
import com.trustweave.kms.KeyManagementService

/**
 * SPI Provider for DIDComm exchange protocol.
 *
 * Automatically discovers and provides DIDComm protocol implementation.
 *
 * **ServiceLoader Registration:**
 * Create a file `META-INF/services/com.trustweave.credential.exchange.spi.CredentialExchangeProtocolProvider`
 * with the content:
 * ```
 * com.trustweave.credential.didcomm.exchange.spi.DidCommExchangeProtocolProvider
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

