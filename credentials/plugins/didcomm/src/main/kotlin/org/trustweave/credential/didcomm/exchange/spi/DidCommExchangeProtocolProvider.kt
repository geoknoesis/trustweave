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
 * **Crypto:** Option `useProductionCrypto` defaults to `false` (placeholder crypto). Set it to `true` only with a
 * non-null `secretResolver` (didcomm-java); otherwise [create] throws [IllegalArgumentException].
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

        val secretResolver = options["secretResolver"] as? SecretResolver
        val useProductionCrypto = options["useProductionCrypto"] as? Boolean ?: false
        if (useProductionCrypto && secretResolver == null) {
            throw IllegalArgumentException(
                "useProductionCrypto=true requires secretResolver (didcomm-java). " +
                    "Set useProductionCrypto=false or omit it for placeholder crypto, or supply secretResolver.",
            )
        }

        val didCommService =
            if (useProductionCrypto) {
                DidCommFactory.createInMemoryService(
                    kms,
                    resolveDid,
                    checkNotNull(secretResolver) { "secretResolver required when useProductionCrypto is true" },
                )
            } else {
                DidCommFactory.createInMemoryServiceWithPlaceholderCrypto(kms, resolveDid)
            }

        return DidCommExchangeProtocol(didCommService)
    }
}

