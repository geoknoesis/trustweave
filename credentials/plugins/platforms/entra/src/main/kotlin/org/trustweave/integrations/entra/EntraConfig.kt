package org.trustweave.integrations.entra

import org.trustweave.core.net.HttpUrlPolicy

/**
 * Configuration for the Microsoft Entra Verified ID integration.
 *
 * @param tenantId Azure AD tenant identifier (GUID).
 * @param clientId Azure AD application (client) identifier registered with permission
 *   to call the Verified ID service.
 * @param clientSecret Client secret for the OAuth2 client_credentials flow.
 * @param authorityDid The DID issued by Entra Verified ID for your authority. Required
 *   to embed in issuance/presentation requests so the wallet can identify the issuer/verifier.
 * @param authorityVerificationKeyId Optional key identifier used by the authority for signing.
 *   When omitted the Verified ID service uses the default registered key.
 * @param apiBaseUrl Base URL of the Verified ID Request Service API. Defaults to the public endpoint.
 * @param tokenEndpointBaseUrl Base URL of the AAD v2 token endpoint. Defaults to login.microsoftonline.com.
 *   Override for sovereign clouds (e.g., `https://login.microsoftonline.us`).
 * @param scope OAuth2 scope for the Verified ID API. The default value is the documented resource id.
 */
data class EntraConfig(
    val tenantId: String,
    val clientId: String,
    val clientSecret: String,
    val authorityDid: String,
    val authorityVerificationKeyId: String? = null,
    val apiBaseUrl: String = DEFAULT_API_BASE_URL,
    val tokenEndpointBaseUrl: String = DEFAULT_TOKEN_ENDPOINT_BASE,
    val scope: String = DEFAULT_SCOPE,
) {
    init {
        require(tenantId.isNotBlank()) { "Microsoft Entra tenant ID must be specified" }
        require(clientId.isNotBlank()) { "Microsoft Entra client ID must be specified" }
        require(clientSecret.isNotBlank()) { "Microsoft Entra client secret must be specified" }
        require(authorityDid.isNotBlank()) { "Entra Verified ID authority DID must be specified" }
        require(TENANT_ID_PATTERN.matches(tenantId)) {
            "Microsoft Entra tenant ID must be a tenant GUID or domain name (letters, digits, '.' and '-' only)"
        }
        require(apiBaseUrl.startsWith("http")) {
            "apiBaseUrl must be an http(s) URL, got: $apiBaseUrl"
        }
        // The token endpoint receives the client secret and the API receives bearer tokens: plaintext
        // is only acceptable to a loopback host (local tests / sidecars).
        require(HttpUrlPolicy.isHttpsOrLoopbackHttp(tokenEndpointBaseUrl)) {
            "tokenEndpointBaseUrl must use https (plain http is only allowed for loopback hosts), got: $tokenEndpointBaseUrl"
        }
        require(HttpUrlPolicy.isHttpsOrLoopbackHttp(apiBaseUrl)) {
            "apiBaseUrl must use https (plain http is only allowed for loopback hosts), got: $apiBaseUrl"
        }
        require(scope.isNotBlank()) { "scope must not be blank" }
    }

    /** Redacts [clientSecret] so the config can be logged safely. */
    override fun toString(): String =
        "EntraConfig(tenantId=$tenantId, clientId=$clientId, clientSecret=***, authorityDid=$authorityDid, " +
            "authorityVerificationKeyId=$authorityVerificationKeyId, apiBaseUrl=$apiBaseUrl, " +
            "tokenEndpointBaseUrl=$tokenEndpointBaseUrl, scope=$scope)"

    /**
     * Full token endpoint URL for this tenant.
     */
    val tokenEndpointUrl: String
        get() = "${tokenEndpointBaseUrl.trimEnd('/')}/$tenantId/oauth2/v2.0/token"

    /**
     * Full issuance request endpoint URL.
     */
    val issuanceRequestUrl: String
        get() = "${apiBaseUrl.trimEnd('/')}/v1.0/verifiableCredentials/createIssuanceRequest"

    /**
     * Full presentation request endpoint URL.
     */
    val presentationRequestUrl: String
        get() = "${apiBaseUrl.trimEnd('/')}/v1.0/verifiableCredentials/createPresentationRequest"

    companion object {
        private val TENANT_ID_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9.-]{0,253}$")

        /** Public Verified ID Request Service endpoint. */
        const val DEFAULT_API_BASE_URL: String = "https://verifiedid.did.msidentity.com"

        /** Public AAD v2 token endpoint base. */
        const val DEFAULT_TOKEN_ENDPOINT_BASE: String = "https://login.microsoftonline.com"

        /**
         * OAuth2 scope (resource id) for the Verified ID Request Service.
         * See: https://learn.microsoft.com/entra/verified-id/verifiable-credentials-configure-tenant
         */
        const val DEFAULT_SCOPE: String = "3db474b9-6a0c-4840-96ac-1fceb342124f/.default"
    }
}
