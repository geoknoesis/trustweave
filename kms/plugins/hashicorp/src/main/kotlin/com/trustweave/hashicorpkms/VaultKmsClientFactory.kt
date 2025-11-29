package com.trustweave.hashicorpkms

import com.bettercloud.vault.Vault
import com.bettercloud.vault.VaultConfig
import com.bettercloud.vault.response.AuthResponse
import java.io.Closeable

/**
 * Factory for creating HashiCorp Vault clients.
 *
 * Handles authentication (token or AppRole) and client configuration.
 */
object VaultKmsClientFactory {
    /**
     * Creates a Vault client from configuration.
     *
     * @param config Vault configuration
     * @return Configured Vault client
     */
    fun createClient(config: VaultKmsConfig): Vault {
        val vaultConfig = VaultConfig()
            .address(config.address)
            .engineVersion(config.engineVersion)

        // Set namespace if provided (Vault Enterprise)
        config.namespace?.let {
            vaultConfig.nameSpace(it)
        }

        // Configure authentication
        if (config.token != null) {
            vaultConfig.token(config.token)
        } else if (config.roleId != null && config.secretId != null) {
            // AppRole authentication
            val appRolePath = config.appRolePath ?: "approle"
            vaultConfig.build()

            // Authenticate using AppRole
            val tempVault = Vault(vaultConfig)
            val authResponse: AuthResponse = tempVault.auth()
                .loginByAppRole(appRolePath, config.roleId, config.secretId)

            // Update config with the token from AppRole authentication
            vaultConfig.token(authResponse.authClientToken)
        } else {
            throw IllegalArgumentException(
                "Vault authentication requires either 'token' or 'roleId' + 'secretId' (AppRole)"
            )
        }

        return Vault(vaultConfig.build())
    }
}

