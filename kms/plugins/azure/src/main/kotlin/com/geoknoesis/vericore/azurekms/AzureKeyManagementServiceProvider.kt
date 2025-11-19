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

