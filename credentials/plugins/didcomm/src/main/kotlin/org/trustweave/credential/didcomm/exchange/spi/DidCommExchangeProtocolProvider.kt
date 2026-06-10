package org.trustweave.credential.didcomm.exchange.spi

import org.trustweave.credential.didcomm.DidCommFactory
import org.trustweave.credential.didcomm.DidCommService
import org.trustweave.credential.didcomm.exchange.DidCommExchangeProtocol
import org.trustweave.credential.exchange.CredentialExchangeProtocol
import org.trustweave.credential.spi.exchange.CredentialExchangeProtocolProvider
import org.trustweave.did.model.DidDocument
import org.didcommx.didcomm.secret.SecretResolver
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
 *
 * **Crypto:** A non-null `secretResolver` (didcomm-java) is required; otherwise [create] throws
 * [IllegalArgumentException]. The legacy option `useProductionCrypto` is only accepted as `true`
 * (its former default `false` selected placeholder crypto, which has been removed); passing
 * `false` throws [UnsupportedOperationException].
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

        @Suppress("UNCHECKED_CAST")
        val resolveDid = options["resolveDid"] as? (suspend (String) -> DidDocument?)
            ?: return null

        val useProductionCrypto = options["useProductionCrypto"] as? Boolean ?: true
        if (!useProductionCrypto) {
            throw UnsupportedOperationException(
                "useProductionCrypto=false is no longer supported: placeholder DIDComm crypto has " +
                    "been removed. DIDComm encryption requires an ECDH-capable crypto provider; " +
                    "supply a secretResolver (didcomm-java).",
            )
        }

        val secretResolver = options["secretResolver"] as? SecretResolver
            ?: throw IllegalArgumentException(
                "DIDComm exchange protocol requires a 'secretResolver' option (didcomm-java) " +
                    "supplying JWK private key material for the key IDs used in pack/unpack.",
            )

        val didCommService = DidCommFactory.createInMemoryService(kms, resolveDid, secretResolver)

        return DidCommExchangeProtocol(didCommService)
    }
}

