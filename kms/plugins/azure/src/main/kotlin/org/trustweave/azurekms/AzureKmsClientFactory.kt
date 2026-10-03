package org.trustweave.azurekms

import com.azure.core.credential.TokenCredential
import com.azure.identity.ClientSecretCredentialBuilder
import com.azure.identity.DefaultAzureCredentialBuilder
import com.azure.security.keyvault.keys.KeyClient
import com.azure.security.keyvault.keys.KeyClientBuilder
import java.net.URI

/**
 * Factory for creating Azure Key Vault KeyClient instances.
 *
 * Handles authentication (Managed Identity or Service Principal) and client configuration.
 */
object AzureKmsClientFactory {
    /**
     * Creates an Azure Key Vault KeyClient from configuration.
     *
     * When [AzureKmsConfig.endpointOverride] is set (e.g. a local Key Vault emulator), the client
     * talks to that endpoint instead of [AzureKmsConfig.vaultUrl], and the challenge-resource
     * check (which requires the token audience to match a `*.vault.azure.net` host) is disabled
     * for it. The override must be an absolute `http(s)` URL.
     *
     * @param config Azure Key Vault configuration
     * @return Configured KeyClient
     * @throws IllegalArgumentException if the endpoint override is not an absolute http(s) URL
     */
    fun createClient(config: AzureKmsConfig): KeyClient {
        val builder =
            KeyClientBuilder()
                .vaultUrl(effectiveVaultUrl(config))
                .credential(createCredential(config))

        if (config.endpointOverride != null) {
            builder.disableChallengeResourceVerification()
        }

        return builder.buildClient()
    }

    /** The URL the client talks to: the endpoint override if set, else the vault URL. */
    internal fun effectiveVaultUrl(config: AzureKmsConfig): String {
        val override = config.endpointOverride ?: return config.vaultUrl
        val uri =
            try {
                URI(override)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid Azure Key Vault endpointOverride: $override", e)
            }
        require(uri.isAbsolute && uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) {
            "Azure Key Vault endpointOverride must be an absolute http(s) URL, got: $override"
        }
        return override
    }

    /**
     * Creates credentials provider based on configuration.
     *
     * If client ID, secret, and tenant are provided, uses Service Principal credentials.
     * Otherwise, uses DefaultAzureCredential (Managed Identity, environment variables, etc.).
     *
     * @param config Azure Key Vault configuration
     * @return TokenCredential for authentication
     */
    private fun createCredential(config: AzureKmsConfig): TokenCredential =
        if (config.clientId != null && config.clientSecret != null && config.tenantId != null) {
            // Use Service Principal authentication
            ClientSecretCredentialBuilder()
                .clientId(config.clientId)
                .clientSecret(config.clientSecret)
                .tenantId(config.tenantId)
                .build()
        } else {
            // Use DefaultAzureCredential (Managed Identity, environment variables, etc.)
            DefaultAzureCredentialBuilder()
                .build()
        }
}
