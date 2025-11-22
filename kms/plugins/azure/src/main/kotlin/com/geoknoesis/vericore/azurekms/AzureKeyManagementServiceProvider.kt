package com.geoknoesis.vericore.azurekms

import com.geoknoesis.vericore.kms.Algorithm
import com.geoknoesis.vericore.kms.KeyManagementService
import com.geoknoesis.vericore.kms.spi.KeyManagementServiceProvider

/**
 * SPI provider for Azure Key Vault KeyManagementService.
 * 
 * Automatically discovered via Java ServiceLoader when the module is on the classpath.
 * 
 * **Example:**
 * ```kotlin
 * val providers = ServiceLoader.load(KeyManagementServiceProvider::class.java)
 * val azureProvider = providers.find { it.name == "azure" }
 * val kms = azureProvider?.create(mapOf("vaultUrl" to "https://myvault.vault.azure.net"))
 * ```
 */
class AzureKeyManagementServiceProvider : KeyManagementServiceProvider {
    override val name: String = "azure"
    
    override val supportedAlgorithms: Set<Algorithm> = AzureKeyManagementService.SUPPORTED_ALGORITHMS
    
    /**
     * Azure Key Vault required environment variables.
     * AZURE_VAULT_URL is required.
     * AZURE_CLIENT_ID, AZURE_CLIENT_SECRET, AZURE_TENANT_ID are optional if using Managed Identity.
     */
    override val requiredEnvironmentVariables: List<String> = listOf(
        "AZURE_VAULT_URL",
        "?AZURE_CLIENT_ID",  // Optional if using Managed Identity
        "?AZURE_CLIENT_SECRET",  // Optional if using Managed Identity
        "?AZURE_TENANT_ID"  // Optional if using Managed Identity
    )
    
    override fun hasRequiredEnvironmentVariables(): Boolean {
        // AZURE_VAULT_URL must be set
        val hasVaultUrl = System.getenv("AZURE_VAULT_URL") != null
        
        if (!hasVaultUrl) {
            return false
        }
        
        // If vault URL is set, check if we have credentials OR are running on Azure (Managed Identity)
        val hasCredentials = System.getenv("AZURE_CLIENT_ID") != null &&
                            System.getenv("AZURE_CLIENT_SECRET") != null &&
                            System.getenv("AZURE_TENANT_ID") != null
        
        // Check if running on Azure (Managed Identity available)
        val hasManagedIdentity = System.getenv("MSI_ENDPOINT") != null ||
                                 System.getenv("IDENTITY_ENDPOINT") != null
        
        return hasCredentials || hasManagedIdentity
    }

    override fun create(options: Map<String, Any?>): KeyManagementService {
        val config = try {
            AzureKmsConfig.fromMap(options)
        } catch (e: Exception) {
            // Try environment variables as fallback
            AzureKmsConfig.fromEnvironment()
                ?: throw IllegalArgumentException(
                    "Azure Key Vault configuration requires 'vaultUrl' in options or AZURE_VAULT_URL environment variable. " +
                    "Error: ${e.message}",
                    e
                )
        }
        
        return AzureKeyManagementService(config)
    }
}

