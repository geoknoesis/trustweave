package org.trustweave.credential.federation.exchange.spi

import okhttp3.OkHttpClient
import org.trustweave.credential.exchange.CredentialExchangeProtocol
import org.trustweave.credential.federation.TrustChainResolver
import org.trustweave.credential.federation.exchange.FederationExchangeProtocol
import org.trustweave.credential.spi.exchange.CredentialExchangeProtocolProvider

/**
 * SPI provider for the OpenID Federation 1.0 exchange protocol.
 *
 * **Options**:
 * - `trustedAnchorIds` (`Set<String>` or comma-separated `String`) — entity identifiers
 *   of trust anchors the verifier accepts. Required for [FederationExchangeProtocol].
 * - `httpClient` (`OkHttpClient`) — optional HTTP client for trust-chain HTTP calls.
 * - `maxChainLength` (`Int`) — maximum trust-chain depth (default 5).
 *
 * **ServiceLoader registration**: `META-INF/services/org.trustweave.credential.spi.exchange.CredentialExchangeProtocolProvider`
 */
class FederationExchangeProtocolProvider : CredentialExchangeProtocolProvider {

    override val name = "openid-federation"
    override val supportedProtocols = listOf("openid-federation")

    @Suppress("UNCHECKED_CAST")
    override fun create(
        protocolName: String,
        options: Map<String, Any?>,
    ): CredentialExchangeProtocol? {
        if (protocolName != "openid-federation") return null

        val trustedAnchorIds: Set<String> = when (val raw = options["trustedAnchorIds"]) {
            is Set<*> -> raw.filterIsInstance<String>().toSet()
            is Collection<*> -> raw.filterIsInstance<String>().toSet()
            is String -> raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            else -> emptySet()
        }

        val httpClient = options["httpClient"] as? OkHttpClient ?: OkHttpClient()
        val maxChainLength = (options["maxChainLength"] as? Int) ?: 5

        val resolver = TrustChainResolver(
            httpClient = httpClient,
            maxChainLength = maxChainLength,
        )

        return FederationExchangeProtocol(
            resolver = resolver,
            trustedAnchorIds = trustedAnchorIds,
        )
    }
}
