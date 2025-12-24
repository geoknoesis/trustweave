package org.trustweave.azurekms

import com.azure.core.credential.TokenCredential
import com.azure.identity.DefaultAzureCredentialBuilder
import com.azure.identity.ClientSecretCredentialBuilder
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
     * @param config Azure Key Vault configuration
     * @return Configured KeyClient
     */
    fun createClient(config: AzureKmsConfig): KeyClient {
        val builder = KeyClientBuilder()
            .vaultUrl(config.vaultUrl)

        // Configure credentials
        val credential = createCredential(config)
        builder.credential(credential)

        // Configure endpoint override (for testing with local emulators)
        config.endpointOverride?.let { endpoint ->
            // Note: Azure SDK doesn't directly support endpoint override in KeyClientBuilder
            // This would need to be handled via custom HttpClient if needed
            // For now, we'll skip this as it requires more complex setup
        }

        return builder.buildClient()
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
    private fun createCredential(config: AzureKmsConfig): TokenCredential {
        return if (config.clientId != null && config.clientSecret != null && config.tenantId != null) {
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
}

