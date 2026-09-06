package org.trustweave.credential.exchange.registry.internal

import org.trustweave.credential.exchange.CredentialExchangeProtocol
import org.trustweave.credential.exchange.ExchangeOperation
import org.trustweave.credential.exchange.capability.ExchangeProtocolCapabilities
import org.trustweave.credential.exchange.registry.ExchangeProtocolRegistry
import org.trustweave.credential.identifiers.ExchangeProtocolName
import java.util.concurrent.ConcurrentHashMap

/**
 * Default implementation of ExchangeProtocolRegistry.
 *
 * Internal implementation detail. Use factory functions to create instances.
 */
internal class DefaultExchangeProtocolRegistry(
    initialProtocols: Map<ExchangeProtocolName, CredentialExchangeProtocol> = emptyMap(),
) : ExchangeProtocolRegistry {
    private val protocols = ConcurrentHashMap<ExchangeProtocolName, CredentialExchangeProtocol>(initialProtocols)

    override fun register(protocol: CredentialExchangeProtocol) {
        protocols[protocol.protocolName] = protocol
    }

    override fun get(protocolName: ExchangeProtocolName): CredentialExchangeProtocol? = protocols[protocolName]

    override fun getAll(): Map<ExchangeProtocolName, CredentialExchangeProtocol> = protocols.toMap()

    override fun getSupportedProtocols(): List<ExchangeProtocolName> = protocols.keys.toList()

    override fun isRegistered(protocolName: ExchangeProtocolName): Boolean = protocols.containsKey(protocolName)

    override fun unregister(protocolName: ExchangeProtocolName): CredentialExchangeProtocol? = protocols.remove(protocolName)

    override fun getCapabilities(protocolName: ExchangeProtocolName): ExchangeProtocolCapabilities? = protocols[protocolName]?.capabilities

    override fun supports(
        protocolName: ExchangeProtocolName,
        operation: ExchangeOperation,
    ): Boolean = protocols[protocolName]?.supports(operation) ?: false

    override fun clear() {
        protocols.clear()
    }
}
