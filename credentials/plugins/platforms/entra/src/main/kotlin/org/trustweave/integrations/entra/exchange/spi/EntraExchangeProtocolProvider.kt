package org.trustweave.integrations.entra.exchange.spi

import okhttp3.OkHttpClient
import org.trustweave.credential.exchange.CredentialExchangeProtocol
import org.trustweave.credential.spi.exchange.CredentialExchangeProtocolProvider
import org.trustweave.integrations.entra.EntraConfig
import org.trustweave.integrations.entra.EntraIssuanceClient
import org.trustweave.integrations.entra.EntraPresentationClient
import org.trustweave.integrations.entra.EntraTokenClient
import org.trustweave.integrations.entra.exchange.EntraExchangeProtocol

/**
 * SPI provider that exposes the Microsoft Entra Verified ID exchange protocol
 * via `META-INF/services`.
 *
 * **Required options:**
 * - `config` ([EntraConfig]) — full configuration, OR the four scalar options below
 * - `tenantId`, `clientId`, `clientSecret`, `authorityDid` (Strings) — short-form config
 *
 * **Optional options:**
 * - `httpClient` ([OkHttpClient]) — shared HTTP client; one is constructed if missing
 * - `apiBaseUrl` (String) — override Verified ID base URL (for sovereign cloud / tests)
 * - `tokenEndpointBaseUrl` (String) — override AAD token endpoint base
 *
 * ServiceLoader registration:
 * `META-INF/services/org.trustweave.credential.spi.exchange.CredentialExchangeProtocolProvider`
 */
class EntraExchangeProtocolProvider : CredentialExchangeProtocolProvider {

    override val name: String = "entra"
    override val supportedProtocols: List<String> = listOf("entra")

    override fun create(
        protocolName: String,
        options: Map<String, Any?>,
    ): CredentialExchangeProtocol? {
        if (protocolName != "entra") return null

        val httpClient = (options["httpClient"] as? OkHttpClient) ?: OkHttpClient()
        val config = resolveConfig(options)

        val tokenClient = (options["tokenClient"] as? EntraTokenClient)
            ?: EntraTokenClient(config = config, httpClient = httpClient)
        val issuanceClient = EntraIssuanceClient(config, tokenClient, httpClient)
        val presentationClient = EntraPresentationClient(config, tokenClient, httpClient)
        return EntraExchangeProtocol(issuanceClient, presentationClient)
    }

    private fun resolveConfig(options: Map<String, Any?>): EntraConfig {
        (options["config"] as? EntraConfig)?.let { return it }

        val tenantId = options.requireString("tenantId")
        val clientId = options.requireString("clientId")
        val clientSecret = options.requireString("clientSecret")
        val authorityDid = options.requireString("authorityDid")
        val apiBaseUrl = options["apiBaseUrl"] as? String ?: EntraConfig.DEFAULT_API_BASE_URL
        val tokenEndpointBaseUrl = options["tokenEndpointBaseUrl"] as? String
            ?: EntraConfig.DEFAULT_TOKEN_ENDPOINT_BASE
        val scope = options["scope"] as? String ?: EntraConfig.DEFAULT_SCOPE
        val verificationKeyId = options["authorityVerificationKeyId"] as? String
        return EntraConfig(
            tenantId = tenantId,
            clientId = clientId,
            clientSecret = clientSecret,
            authorityDid = authorityDid,
            authorityVerificationKeyId = verificationKeyId,
            apiBaseUrl = apiBaseUrl,
            tokenEndpointBaseUrl = tokenEndpointBaseUrl,
            scope = scope,
        )
    }

    private fun Map<String, Any?>.requireString(key: String): String =
        this[key] as? String
            ?: throw IllegalArgumentException(
                "Entra exchange protocol provider requires option '$key' (String) or a 'config' EntraConfig instance",
            )
}
