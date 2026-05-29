package org.trustweave.integrations.entra

import okhttp3.OkHttpClient
import org.trustweave.integrations.entra.exchange.EntraExchangeProtocol

/**
 * High-level facade for Microsoft Entra Verified ID integration.
 *
 * Wires the OAuth2 token client, issuance client, and presentation client together
 * and exposes them along with a ready-to-register [EntraExchangeProtocol].
 *
 * For low-level access (custom request bodies, sovereign clouds, callback parsing)
 * use the individual clients directly.
 *
 * **Example:**
 * ```kotlin
 * val integration = EntraIntegration(
 *     EntraConfig(
 *         tenantId = "0000…",
 *         clientId = "0000…",
 *         clientSecret = System.getenv("ENTRA_CLIENT_SECRET"),
 *         authorityDid = "did:web:verifiedid.example.com",
 *     ),
 * )
 *
 * val envelope = integration.exchangeProtocol.offer(
 *     ExchangeRequest.Offer(
 *         protocolName = ExchangeProtocolName("entra"),
 *         issuerDid = Did(integration.config.authorityDid),
 *         holderDid = Did("did:web:holder.example.com"),
 *         credentialPreview = CredentialPreview(attributes = emptyList()),
 *         options = entraIssuanceOptions(
 *             manifestUrl = "https://verifiedid.did.msidentity.com/v1.0/.../manifest",
 *             credentialType = "EmployeeCredential",
 *             callbackUrl = "https://example.com/entra/callback",
 *             clientName = "Acme Corp",
 *         ),
 *     ),
 * )
 * ```
 */
class EntraIntegration(
    val config: EntraConfig,
    httpClient: OkHttpClient = OkHttpClient(),
) {
    /** OAuth2 client_credentials token client with in-memory cache. */
    val tokenClient: EntraTokenClient = EntraTokenClient(config = config, httpClient = httpClient)

    /** Issuance Request Service client. */
    val issuanceClient: EntraIssuanceClient = EntraIssuanceClient(config, tokenClient, httpClient)

    /** Presentation Request Service client (also parses webhook callbacks). */
    val presentationClient: EntraPresentationClient =
        EntraPresentationClient(config, tokenClient, httpClient)

    /** Protocol-agnostic [EntraExchangeProtocol] wired to the above clients. */
    val exchangeProtocol: EntraExchangeProtocol =
        EntraExchangeProtocol(issuanceClient, presentationClient)

    companion object {
        /**
         * Backwards-compatible constructor for callers that previously used the four-string form.
         */
        @JvmStatic
        fun fromCredentials(
            tenantId: String,
            clientId: String,
            clientSecret: String,
            authorityDid: String,
            apiBaseUrl: String = EntraConfig.DEFAULT_API_BASE_URL,
            tokenEndpointBaseUrl: String = EntraConfig.DEFAULT_TOKEN_ENDPOINT_BASE,
        ): EntraIntegration = EntraIntegration(
            EntraConfig(
                tenantId = tenantId,
                clientId = clientId,
                clientSecret = clientSecret,
                authorityDid = authorityDid,
                apiBaseUrl = apiBaseUrl,
                tokenEndpointBaseUrl = tokenEndpointBaseUrl,
            ),
        )
    }
}
